package at.ac.fhstp.sonitalk;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import at.ac.fhstp.sonitalk.utils.HammingWindow;
import edu.emory.mathcs.backport.java.util.Collections;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import uk.me.berndporr.iirj.Butterworth;

public class GaltonChatDecoder {
    private List<SoniTalkConfig> configList;
    private ExecutorService threadAnalyzeExecutor;
    private final Object syncThreadAnalyzeExecutor = new Object();
    private boolean[] isDecoding;
    private Object[] sync;
    private final int bandPassFilterOrder = 8;
    private final double startFactor = 2.0;
    private final double endFactor = 2.0;


    public GaltonChatDecoder(List<List<SoniTalkConfig>> configs) {
        this.configList = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            for (int j = 0; j < configs.get(i).size(); j++) {
                configList.add(configs.get(i).get(j));
            }
        }
        threadAnalyzeExecutor =  Executors.newFixedThreadPool(configList.size());
        isDecoding = new boolean[configList.size()];
        sync = new Object[configList.size()];
        Arrays.fill(isDecoding, false);
        Arrays.fill(sync, new Object());
    }

    public void analyzeHistoryBufferOtherThread(final CircularArray historyBuffer) {
        for (int i = 0; i < configList.size(); i++) {
            synchronized (sync[i]) {
                if (!isDecoding[i]) {
                    isDecoding[i] = true;
                    final int index = i;
                    final float[] buffer;
                    synchronized (GaltonChat.historyBufferMutex) {
                        buffer = historyBuffer.getLastWindow(configList.get(index).getHistoryBufferSize(GaltonChat.SAMPLE_RATE));
                    }

                    threadAnalyzeExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            checkForMessage(buffer, configList.get(index), index);
                        }
                    });
                }
            }
        }

    }

    public void checkForMessage(final float[] analysisHistoryBuffer, final SoniTalkConfig config, int index){
        //Log.e("tesT", "check");
        int analysisWinLen = config.getAnalysisWinLen(GaltonChat.SAMPLE_RATE);
        float firstWindow[] = new float[analysisWinLen];
        float lastWindow[] = new float[analysisWinLen];
        System.arraycopy(analysisHistoryBuffer, 0, firstWindow, 0, analysisWinLen);
        System.arraycopy(analysisHistoryBuffer, analysisHistoryBuffer.length - analysisWinLen, lastWindow, 0, analysisWinLen);

        float[] startResponseUpper = firstWindow.clone();
        float[] startResponseLower = firstWindow.clone();
        double[] startResponseUpperDouble = new double[startResponseUpper.length * 2];
        double[] startResponseLowerDouble = new double[startResponseLower.length * 2];
        int centerFrequencyBandPassDown = config.getFrequencyZero() + (config.getBandpassWidth()/2);
        int centerFrequencyBandPassUp = config.getFrequencyZero() + config.getBandpassWidth() + (config.getBandpassWidth()/2);

        Butterworth butterworthDown = new Butterworth();
        butterworthDown.bandPass(bandPassFilterOrder,GaltonChat.SAMPLE_RATE,centerFrequencyBandPassDown,config.getBandpassWidth());
        Butterworth butterworthUp = new Butterworth();
        butterworthUp.bandPass(bandPassFilterOrder,GaltonChat.SAMPLE_RATE,centerFrequencyBandPassUp,config.getBandpassWidth());
        for(int i = 0; i<startResponseLower.length; i++) {
            startResponseUpperDouble[i] = butterworthUp.filter(startResponseUpper[i]);
            startResponseLowerDouble[i] = butterworthDown.filter(startResponseLower[i]);
        }
        DoubleFFT_1D fft = new DoubleFFT_1D(startResponseUpper.length);
        fft.complexForward(startResponseUpperDouble);
        fft.complexForward(startResponseLowerDouble);

        double sumAbsStartResponseUpper = 0.0;
        double sumAbsStartResponseLower = 0.0;
        for(int i = 0; i< startResponseLowerDouble.length; i+=2){
            sumAbsStartResponseUpper += DecoderUtils.getComplexAbsolute(startResponseUpperDouble[i], startResponseUpperDouble[i+1]);
            sumAbsStartResponseLower += DecoderUtils.getComplexAbsolute(startResponseLowerDouble[i], startResponseLowerDouble[i+1]);
        }

        if(sumAbsStartResponseUpper > startFactor * sumAbsStartResponseLower){
            float[] endResponseUpper = lastWindow.clone();
            float[] endResponseLower = lastWindow.clone();
            double[] endResponseUpperDouble = new double[endResponseUpper.length * 2];
            double[] endResponseLowerDouble = new double[endResponseLower.length * 2];
            Butterworth butterworthDownEnd = new Butterworth();
            butterworthDownEnd.bandPass(bandPassFilterOrder,GaltonChat.SAMPLE_RATE,centerFrequencyBandPassDown,config.getBandpassWidth());
            Butterworth butterworthUpEnd = new Butterworth();
            butterworthUpEnd.bandPass(bandPassFilterOrder,GaltonChat.SAMPLE_RATE,centerFrequencyBandPassUp,config.getBandpassWidth());

            for(int i = 0; i<endResponseLower.length; i++) {
                endResponseUpperDouble[i] = butterworthUpEnd.filter(endResponseUpper[i]);
                endResponseLowerDouble[i] = butterworthDownEnd.filter(endResponseLower[i]);
            }

            fft.complexForward(endResponseUpperDouble);
            fft.complexForward(endResponseLowerDouble);

            double sumAbsEndResponseUpper = 0;
            double sumAbsEndResponseLower = 0;
            for(int i = 0; i< endResponseUpperDouble.length; i+=2){
                sumAbsEndResponseUpper += DecoderUtils.getComplexAbsolute(endResponseUpperDouble[i], endResponseUpperDouble[i+1]);
                sumAbsEndResponseLower += DecoderUtils.getComplexAbsolute(endResponseLowerDouble[i], endResponseLowerDouble[i+1]);
            }

            if(sumAbsEndResponseLower > endFactor * sumAbsEndResponseUpper) {
               analyzeMessage(analysisHistoryBuffer, config);
            }
        }
        synchronized (sync[index]) {
            isDecoding[index] = false;
        }
    }

    private void analyzeMessage(float[] analysisHistoryBuffer, SoniTalkConfig config) {
        int winLenForSpectrogramInSamples = Math.round(GaltonChat.SAMPLE_RATE * (float) config.getBitperiod()/1000);
        if (winLenForSpectrogramInSamples % 2 != 0) {
            winLenForSpectrogramInSamples ++; // Make sure winLenForSpectrogramInSamples is even
        }
        int stepFactor = 8;
        int analysisWinStep = (int)Math.round((float) config.getAnalysisWinLen(GaltonChat.SAMPLE_RATE)/ stepFactor);

        int overlapForSpectrogramInSamples = winLenForSpectrogramInSamples - analysisWinStep;
        int overlapFactor = Math.round((float) winLenForSpectrogramInSamples / (winLenForSpectrogramInSamples - overlapForSpectrogramInSamples));
        int nbWinLenForSpectrogram = Math.round(overlapFactor * (float) config.getHistoryBufferSize(GaltonChat.SAMPLE_RATE) / (float) winLenForSpectrogramInSamples);
        double[][] historyBufferDouble = new double[nbWinLenForSpectrogram][winLenForSpectrogramInSamples];
        for(int j = 0; j<historyBufferDouble.length;j++ ) {
            int helpArrayCounter = 0;
            //Log.d("ForLoopVal", String.valueOf(j*analysisWinLen));
            for (int i = (j/overlapFactor)*winLenForSpectrogramInSamples + ((j%overlapFactor) * winLenForSpectrogramInSamples/overlapFactor); i < analysisHistoryBuffer.length && i < (1 + j/overlapFactor)*winLenForSpectrogramInSamples + ((j%overlapFactor) * winLenForSpectrogramInSamples/overlapFactor); i++) {
                historyBufferDouble[j][helpArrayCounter] = (double) analysisHistoryBuffer[i];
                helpArrayCounter++;
            }
        }
        HammingWindow hammWin = new HammingWindow(winLenForSpectrogramInSamples);
        DoubleFFT_1D mFFT = new DoubleFFT_1D(winLenForSpectrogramInSamples);
        double[][] historyBufferDoubleAbsolute = new double[nbWinLenForSpectrogram][winLenForSpectrogramInSamples / 2];
        float[][] historyBufferFloatNormalized = new float[nbWinLenForSpectrogram][historyBufferDoubleAbsolute[0].length];
        double fftSum = 0;
        int helpCounter;

        for(int j = 0; j<historyBufferDoubleAbsolute.length;j++ ) {
            hammWin.applyWindow(historyBufferDouble[j]);
            mFFT.realForward(historyBufferDouble[j]);
            // Get absolute value of the complex FFT result
            helpCounter = 0;
            //fftSum = 0;
            for (int l = 0; l < historyBufferDouble[j].length ; l++) {
                if (l % 2 == 0) { //Modulo 2 is used to get only the real (every second) value
                    double absolute = DecoderUtils.getComplexAbsolute(historyBufferDouble[j][l],historyBufferDouble[j][l+1]);
                    historyBufferDoubleAbsolute[j][helpCounter] = absolute;
                    fftSum += absolute;
                    helpCounter++;
                }
            }
        }
        for(int j = 0; j<historyBufferFloatNormalized.length;j++ ) {
            for (int i = 0; i < historyBufferDoubleAbsolute[0].length; i++) {
                float normalized = 0.0001F;
                if(fftSum != 0) {
                    //  Normalize over one block at a time and check if it improves the visualization [NOTE: It looks like results are better with fftSum over the whole spectrum, maybe because of the overlap]
                    normalized = (float) (historyBufferDoubleAbsolute[j][i]/fftSum);
                }
                historyBufferFloatNormalized[j][i] = normalized;

            }
        }
        int frequencyOffsetForSpectrogram = 50;
        int lowerCutoffFrequency = config.getFrequencyZero()-frequencyOffsetForSpectrogram;
        int upperCutoffFrequency = config.getFrequencyZero() + ((config.getnFrequencies()-1) * config.getFrequencySpace()) +frequencyOffsetForSpectrogram;
        int lowerCutoffFrequencyIdx = (int)((float)lowerCutoffFrequency/(float)GaltonChat.SAMPLE_RATE*(float)winLenForSpectrogramInSamples);// + 1;
        int upperCutoffFrequencyIdx = (int)((float)upperCutoffFrequency/(float)GaltonChat.SAMPLE_RATE*(float)winLenForSpectrogramInSamples);// + 1;
        double[][] P = new double[nbWinLenForSpectrogram][upperCutoffFrequencyIdx-lowerCutoffFrequencyIdx + 1];
        double[][] input = new double[nbWinLenForSpectrogram][upperCutoffFrequencyIdx-lowerCutoffFrequencyIdx + 1];
        int arrayCounter;
        double logSum;
        for(int j = 0; j<historyBufferDoubleAbsolute.length; j++) {
            arrayCounter = 0;
            logSum = 0;
            for(int i = lowerCutoffFrequencyIdx; i <= upperCutoffFrequencyIdx;i++) {
                if(historyBufferDoubleAbsolute[j][i]==0){
                    P[j][arrayCounter] = 0.0000001;
                }else {
                    P[j][arrayCounter] = historyBufferDoubleAbsolute[j][i];
                }
                P[j][arrayCounter] = Math.log(P[j][arrayCounter]);
                logSum += P[j][arrayCounter];
                arrayCounter++;
            }

            // Normalization
            for(int i = 0; i <= upperCutoffFrequencyIdx-lowerCutoffFrequencyIdx; i++) {
                input[j][i] = (float) (P[j][i] / logSum);
            }
        }
        int pauseperiodInSamples = (int)Math.round(config.getPauseperiod() * (float)GaltonChat.SAMPLE_RATE/1000);
        int nBlocks = (int)Math.ceil(config.getnMessageBlocks()*2)+2;
        int step = winLenForSpectrogramInSamples-overlapForSpectrogramInSamples;
        int nVectorsPerBlock =  overlapFactor;//Math.round(bitperiodInSamples/step); // Isn't nVectorsPerBlock equal to overlapFactor ?! Not in matlab.
        int nVectorsPerPause =   Math.round((float) pauseperiodInSamples/step);
        int[] blockCenters = new int[nBlocks]; //Math.round(nVectorsPerBlock/2);
        int[] pauseCenters  = new int[nBlocks - 1]; //Math.round(nVectorsPerBlock+(nVectorsPerPause)/2);

        // Compute block centers
        blockCenters[0] = Math.round((float) nVectorsPerBlock/2) - 1; //We substract one compared to Octave because indexes start at 0 in Java
        pauseCenters[0] =  Math.round(nVectorsPerBlock+((float) nVectorsPerPause)/2); // No need to substract one again (it is based on blockCenters).
        for(int i=1; i < nBlocks; i++) {
            blockCenters[i] = blockCenters[i - 1] + nVectorsPerBlock + nVectorsPerPause;
            if(i<nBlocks-1) pauseCenters[i] = pauseCenters[i - 1] + nVectorsPerBlock + nVectorsPerPause;
            //Log.d("Block center ", i + " --> " + blockCenters[i]);
        }
        float[][] frequencyCenterIndices = new float[nBlocks][config.getnFrequencies()];
        // TODO: First loop not really needed
        for(int k = 0; k<nBlocks; k++) {
            for (int idxFrequencies = 0; idxFrequencies < config.getnFrequencies(); idxFrequencies++) {
                // TODO: Upper and Lower inverted (hence the need for the loop going reverse a dozen lines below)
                frequencyCenterIndices[k][idxFrequencies] = findClosestValueIn1DArray(config.getFrequencyZero() + (config.getFrequencySpace() * idxFrequencies), winLenForSpectrogramInSamples, input[k].length, upperCutoffFrequencyIdx, lowerCutoffFrequencyIdx); //computation is different than in Matlab
            }
        }
        int[] messageDecodedBySpec = new int[(nBlocks-2)/2 * config.getnFrequencies()]; // -2 for the start and end block, divide by two because a bit is encoded by two consecutive pulses on one frequency: either (send, notSend) or (notSend, send)
        arrayCounter = 0;
        int nNeighborsFreqUpDown = 1;
        int nNeighborsTimeLeftRight = 1;
        String aggFcn = "median";
        // Go through all message blocks, skipping start and end block with a stepsize of 2 (because we always have a normal block and an inverted block)
        for(int j = 1; j<nBlocks-1; j=j+2){
            for(int m = frequencyCenterIndices[j].length-1; m>=0; m--){
                int currentCenterFreqIdx = (int)frequencyCenterIndices[j][m];
                double currentBit = getPointAndNeighborsAggreagate(input, currentCenterFreqIdx, blockCenters[j], nNeighborsFreqUpDown, nNeighborsTimeLeftRight, aggFcn);
                double currentBitInv = getPointAndNeighborsAggreagate(input, currentCenterFreqIdx, blockCenters[j + 1], nNeighborsFreqUpDown, nNeighborsTimeLeftRight, aggFcn);
                if (currentBit < currentBitInv) {
                    messageDecodedBySpec[arrayCounter] = 1;
                }
                else{
                    messageDecodedBySpec[arrayCounter] = 0;
                }
                arrayCounter++;
            }
        }
        String decodedBitSequence = Arrays.toString(messageDecodedBySpec).replace(", ", "").replace("[","").replace("]","");
        final byte[] receivedMessage = DecoderUtils.binaryToBytes(decodedBitSequence);
        SoniTalkMessage message = new SoniTalkMessage(receivedMessage);
        try {
            String s = message.getDecodedMessage();
            Log.e("test", s);
        } catch (Exception e) {
           // Log.e("test", "rs error " + message.getHexString());
          //  Log.e("test", "" + configList.indexOf(config));
            //notifyMessageListenersOfError("Error decoding RS Error correction code. Raw received output was\n" + message.getHexString());
        }
    }

    /**
     * Returns an aggregation (e.g. mean) of the values contained in the cell(s) around the one at [row][col] position
     * Row and column are "reversed" compared to the matlab prototype
     * @param data
     * @param row Frequency center index
     * @param col Block center index
     * @param nRowsNeighborsLeftRight How many frequency-index rows to include (on the left AND right side)
     * @param nColsNeighborsLeftRight How many block-index columns to include (on the left AND right side)
     * @param aggFunction
     * @return
     */
    private double getPointAndNeighborsAggreagate(double[][] data,int row,int col,int nRowsNeighborsLeftRight, int nColsNeighborsLeftRight, String aggFunction){
        double val = -1;
        int valuesRange = (nRowsNeighborsLeftRight+nColsNeighborsLeftRight+1)*(nRowsNeighborsLeftRight+nColsNeighborsLeftRight+1);//1+nRowsNeighborsLeftRight*2+nColsNeighborsLeftRight*2;
        double[] values = new double[valuesRange];
        int valuecounter = 0;

        for(int i = nRowsNeighborsLeftRight*(-1); i <= nRowsNeighborsLeftRight; i++){
            for(int j = nColsNeighborsLeftRight*(-1); j <= nColsNeighborsLeftRight; j++){
                if(i!=0 || j!=0){ //[0,0] is done lower
                    values[valuecounter] = data[col+j][row+i];
                    valuecounter++;

                }
            }
        }
        values[valuecounter] = data[col][row];

        switch(aggFunction){
            case "mean":
                val = DecoderUtils.mean(values);
                break;
            case "max":
                val = DecoderUtils.max(values);
                break;
            case "median":
                Arrays.sort(values);
                val = DecoderUtils.median(values);
                break;
        }
        return val;
    }

    private float findClosestValueIn1DArray(int value, int winlen, int arraylength, int upperIdx, int lowerIdx){
        float arrayIndexRelative;
        float arrayIndex;

        float frequencyIndex = DecoderUtils.freq2idx(value, GaltonChat.SAMPLE_RATE, winlen);
        arrayIndexRelative = DecoderUtils.getRelativeIndexPosition(frequencyIndex, upperIdx, lowerIdx);
        arrayIndex = ((float)arraylength*arrayIndexRelative);
        //Log.d("findClosest", "arrayIndex: " + arrayIndex);
        //arrayIndex = frequencyIndex;
        return arrayIndex;
    }
}
