package at.ac.fhstp.sonitalk;

import android.util.Log;

import java.util.Arrays;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import at.ac.fhstp.sonitalk.utils.HammingWindow;
import at.ac.fhstp.sonitalk.utils.TypeUtils;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;
import uk.me.berndporr.iirj.Butterworth;

/**
 * A class that represent a SoniTalkChannel
 * A SoniTalkChannel is a portion of the ultrasonic spectrum
 * to send messages over. Channels should not have overlapping spectrum
 * in order to ensure reliable message decoding
 * @author
 *          Erik Gustafson
 */
public class SoniTalkChannel {
    //tag for sending a message on this channel
    //sending tag = 2001 + channel index (known by the SoniTalkChannelManager)

    //member variables
    private SoniTalkConfig mSoniTalkConfig;
    private SoniTalkContext mSoniTalkContext;
    private SoniTalkEncoder mSoniTalkEncoder;
    private int mSampleRate = 44100;
    private double startFactor = 2.0;
    private double endFactor = 2.0;
    private int stepFactor = 8;
    private double channelOccupiedEnergyThreshold = 100.0; //todo potentially test or calculated different thresholds
    private final Object sync = new Object();
    private boolean mHistoryBeingAnalyzed;

    private int[] frequencies;

    private int nNeighborsFreqUpDown = 1;
    private int nNeighborsTimeLeftRight = 1;
    private String aggFcn = "median";

    private int mChannelNumber;
    private SoniTalkChannelMessageReceiver callback;

    /**
     * Constructor to create the channel with the json
     * file name of the channel configuration
     * @param config
     *          The config file for this channel
     */
    public SoniTalkChannel(SoniTalkConfig config, SoniTalkContext soniTalkContext) {
        mSoniTalkConfig = config;
        mSoniTalkContext = soniTalkContext;
        mSoniTalkEncoder = mSoniTalkContext.getEncoder(mSoniTalkConfig);
        mHistoryBeingAnalyzed = false;
        int f0 = config.getFrequencyZero();
        int nFrequencies = config.getnFrequencies();
        int frequencySpace = config.getFrequencySpace();
        frequencies = new int[nFrequencies];
        for(int i = 0; i < nFrequencies; i++){
            frequencies[i] = f0 + frequencySpace *i;
        }
    }

    public void addMessageReceiver(SoniTalkChannelMessageReceiver receiver, int channel) {
        this.callback = receiver;
        this.mChannelNumber = channel;
    }

    /**
     * Create a message to send on this channel
     * @param msg
     *          the message to encode
     * @return
     *          A Sonitalk message for sending
     */
    public SoniTalkMessage createMessage(String msg) {
        return mSoniTalkEncoder.generateMessage(msg);
    }

    /**
     * Analyze the history buffer looking for messages sent
     * on this channel
     * @param historyBuffer
     *          the history buffer to analyze
     */
    public void analyzeHistoryBuffer(CircularArray historyBuffer) {
        float[] temp = historyBuffer.getArray();
        final float[] buffer = new float[temp.length];
        System.arraycopy(temp, 0, buffer, 0, temp.length);
        //analyze the buffer in a new thread so more data can be collected
        new Thread() {
            @Override
            public void run() {
                super.run();
                if (!mHistoryBeingAnalyzed) {
                    synchronized (sync) {
                        mHistoryBeingAnalyzed = true;
                        checkForMessage(buffer);
                        mHistoryBeingAnalyzed = false;
                    }
                }

            }
        }.start();
    }

    /**
     * detect if there is either a block in the message buffer.
     * If there is a head block, the channel is assumed to be busy
     * If there is NOT a head block, the channel is free for sending
     * @param historyBuffer
     * @return
     */
    public boolean isChannelAvailable(float[] historyBuffer) {
        boolean hasStartBlock = checkForStartBlock(historyBuffer);
        boolean hasEndBlock = checkForEndBlock(historyBuffer);
        return (!hasEndBlock && !hasStartBlock) && getChannelEnergy(historyBuffer) < channelOccupiedEnergyThreshold; //idk if necessary
    }

    private boolean checkForStartBlock(float[] historyBuffer) {
        int bitperiodInSamples = (int)Math.round(mSoniTalkConfig.getBitperiod() * (float)mSampleRate/1000);
        int analysisWinLen = (int)Math.round((float) bitperiodInSamples / 2 );;
        float[] firstWindow = new float[analysisWinLen];
        System.arraycopy(historyBuffer, 0, firstWindow, 0, analysisWinLen);
        int bandpassWidth = DecoderUtils.getBandpassWidth(mSoniTalkConfig.getnFrequencies(), mSoniTalkConfig.getFrequencySpace());
        int centerFrequencyBandPassDown = mSoniTalkConfig.getFrequencyZero() + (bandpassWidth/2);
        int centerFrequencyBandPassUp = mSoniTalkConfig.getFrequencyZero() + bandpassWidth + (bandpassWidth/2);
        float[] startResponseUpper = firstWindow.clone();
        float[] startResponseLower = firstWindow.clone();
        int nextPowerOfTwo = DecoderUtils.nextPowerOfTwo(analysisWinLen);
        double[] startResponseUpperDouble = new double[nextPowerOfTwo];
        double[] startResponseLowerDouble = new double[nextPowerOfTwo];
        Butterworth butterworthDown = new Butterworth();
        butterworthDown.bandPass(8, mSampleRate, centerFrequencyBandPassDown, bandpassWidth);
        Butterworth butterworthUp = new Butterworth();
        butterworthUp.bandPass(8, mSampleRate, centerFrequencyBandPassUp, bandpassWidth);
        for(int i = 0; i<startResponseLower.length; i++) {
            startResponseUpperDouble[i] = butterworthUp.filter(startResponseUpper[i]);
            startResponseLowerDouble[i] = butterworthDown.filter(startResponseLower[i]);
        }
        ComplexArray complexArrayStartResponseUpper = TypeUtils.threadSafeHilbertTransform(startResponseUpperDouble);
        ComplexArray complexArrayStartResponseLower = TypeUtils.threadSafeHilbertTransform(startResponseLowerDouble);
        double sumAbsStartResponseUpper = 0;
        double sumAbsStartResponseLower = 0;
        for(int i = 0; i<complexArrayStartResponseUpper.real.length; i++){
            sumAbsStartResponseUpper += DecoderUtils.getComplexAbsolute(complexArrayStartResponseUpper.real[i], complexArrayStartResponseUpper.imag[i]);
            sumAbsStartResponseLower += DecoderUtils.getComplexAbsolute(complexArrayStartResponseLower.real[i], complexArrayStartResponseLower.imag[i]);
        }
        return sumAbsStartResponseUpper > startFactor * sumAbsStartResponseLower;
    }

    private boolean checkForEndBlock(float[] historyBuffer) {
        int bitperiodInSamples = (int)Math.round(mSoniTalkConfig.getBitperiod() * (float)mSampleRate/1000);
        int analysisWinLen = (int)Math.round((float) bitperiodInSamples / 2 );;
        float[] lastWindow = new float[analysisWinLen];
        System.arraycopy(historyBuffer, historyBuffer.length - analysisWinLen, lastWindow, 0, analysisWinLen);
        float[] endResponseUpper = lastWindow.clone();
        float[] endResponseLower = lastWindow.clone();
        int nextPowerOfTwo = DecoderUtils.nextPowerOfTwo(analysisWinLen);
        int bandpassWidth = DecoderUtils.getBandpassWidth(mSoniTalkConfig.getnFrequencies(), mSoniTalkConfig.getFrequencySpace());
        int centerFrequencyBandPassDown = mSoniTalkConfig.getFrequencyZero() + (bandpassWidth/2);
        int centerFrequencyBandPassUp = mSoniTalkConfig.getFrequencyZero() + bandpassWidth + (bandpassWidth/2);
        double[] endResponseUpperDouble = new double[nextPowerOfTwo];
        double[] endResponseLowerDouble = new double[nextPowerOfTwo];
        Butterworth butterworthDownEnd = new Butterworth();
        butterworthDownEnd.bandPass(8,mSampleRate,centerFrequencyBandPassDown,bandpassWidth);
        Butterworth butterworthUpEnd = new Butterworth();
        butterworthUpEnd.bandPass(8,mSampleRate,centerFrequencyBandPassUp,bandpassWidth);

        for(int i = 0; i<endResponseLower.length; i++) {
            endResponseUpperDouble[i] = butterworthUpEnd.filter(endResponseUpper[i]);
            endResponseLowerDouble[i] = butterworthDownEnd.filter(endResponseLower[i]);
        }

        //todo the hilbert transform function is not thread safe!!
        ComplexArray complexArrayEndResponseUpper = TypeUtils.threadSafeHilbertTransform(endResponseUpperDouble);
        ComplexArray complexArrayEndResponseLower = TypeUtils.threadSafeHilbertTransform(endResponseLowerDouble);

        double sumAbsEndResponseUpper = 0;
        double sumAbsEndResponseLower = 0;
        for(int i = 0; i<complexArrayEndResponseUpper.real.length; i++){
            sumAbsEndResponseUpper += DecoderUtils.getComplexAbsolute(complexArrayEndResponseUpper.real[i], complexArrayEndResponseUpper.imag[i]);
            sumAbsEndResponseLower += DecoderUtils.getComplexAbsolute(complexArrayEndResponseLower.real[i], complexArrayEndResponseLower.imag[i]);
        }
        return sumAbsEndResponseLower > endFactor * sumAbsEndResponseUpper;
    }

    public double getChannelEnergy(float[] historyBuffer) {
        Butterworth channelFilter = new Butterworth();
        double freqWidth = DecoderUtils.getBandpassWidth(mSoniTalkConfig.getnFrequencies(), mSoniTalkConfig.getFrequencySpace()) * 2;
        double centerFreq = mSoniTalkConfig.getFrequencyZero() + (freqWidth / 2);
        channelFilter.bandPass(8, mSampleRate, centerFreq, freqWidth);
        double[] filtered = new double[historyBuffer.length];
        for (int i = 0; i < historyBuffer.length; i++) {
            filtered[i] = channelFilter.filter(historyBuffer[i]);
        }
        ComplexArray complexArrayChannel = TypeUtils.threadSafeHilbertTransform(filtered);
        double energy = 0;
        for (int i = 0; i < complexArrayChannel.real.length; i++) {
            energy += DecoderUtils.getComplexAbsolute(complexArrayChannel.real[i], complexArrayChannel.imag[i]);
        }
        return energy;
    }

    private void checkForMessage(float[] historyBuffer) {
        long readTimestamp = System.nanoTime();
        if (checkForStartBlock(historyBuffer) && checkForEndBlock(historyBuffer)) {
            analyzeMessage(historyBuffer, readTimestamp);
        }
    }

    private void analyzeMessage(float[] historyBuffer, long readTimestamp) {
        int winLenForSpectrogramInSamples = Math.round(mSampleRate * (float)mSoniTalkConfig.getBitperiod()/1000);
        if (winLenForSpectrogramInSamples % 2 != 0) {
            winLenForSpectrogramInSamples ++; // Make sure winLenForSpectrogramInSamples is even
        }
        int bitperiodInSamples = (int)Math.round(mSoniTalkConfig.getBitperiod() * (float)mSampleRate/1000);
        int analysisWinLen = (int)Math.round((float) bitperiodInSamples / 2 );;
        int analysisWinStep = (int)Math.round((float) analysisWinLen/ this.stepFactor);
        int nBlocks = (int)Math.ceil(mSoniTalkConfig.getnMessageBlocks()*2)+2;
        int pauseperiodInSamples = (int)Math.round(mSoniTalkConfig.getPauseperiod() * (float)mSampleRate/1000);
        int historyBufferSize = ((bitperiodInSamples*nBlocks+pauseperiodInSamples*(nBlocks-1)));


        int overlapForSpectrogramInSamples = winLenForSpectrogramInSamples - analysisWinStep;
        int overlapFactor = Math.round((float) winLenForSpectrogramInSamples / (winLenForSpectrogramInSamples - overlapForSpectrogramInSamples));
        int nbWinLenForSpectrogram = Math.round(overlapFactor * (float) historyBufferSize / (float) winLenForSpectrogramInSamples);

        double[][] historyBufferDouble = new double[nbWinLenForSpectrogram][winLenForSpectrogramInSamples];
        for(int j = 0; j<historyBufferDouble.length;j++ ) {
            int helpArrayCounter = 0;
            //Log.d("ForLoopVal", String.valueOf(j*analysisWinLen));
            for (int i = (j/overlapFactor)*winLenForSpectrogramInSamples + ((j%overlapFactor) * winLenForSpectrogramInSamples/overlapFactor); i < historyBuffer.length && i < (1 + j/overlapFactor)*winLenForSpectrogramInSamples + ((j%overlapFactor) * winLenForSpectrogramInSamples/overlapFactor); i++) {
                historyBufferDouble[j][helpArrayCounter] = (double) historyBuffer[i];
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
        int lowerCutoffFrequency = frequencies[0]-frequencyOffsetForSpectrogram;
        int upperCutoffFrequency = frequencies[frequencies.length-1]+frequencyOffsetForSpectrogram;
        int lowerCutoffFrequencyIdx = (int)((float)lowerCutoffFrequency/(float)mSampleRate*(float)winLenForSpectrogramInSamples);// + 1;
        int upperCutoffFrequencyIdx = (int)((float)upperCutoffFrequency/(float)mSampleRate*(float)winLenForSpectrogramInSamples);// + 1;

        // Check if the normalization on a column instead on all the whole message really improved the detection.
        // Cut away unimportant frequencies, logarithmize and then normalize
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
        float[][] frequencyCenterIndices = new float[nBlocks][mSoniTalkConfig.getnFrequencies()];
        // TODO: First loop not really needed
        for(int k = 0; k<nBlocks; k++) {
            for (int idxFrequencies = 0; idxFrequencies < mSoniTalkConfig.getnFrequencies(); idxFrequencies++) {
                // TODO: Upper and Lower inverted (hence the need for the loop going reverse a dozen lines below)
                frequencyCenterIndices[k][idxFrequencies] = findClosestValueIn1DArray(frequencies[idxFrequencies], winLenForSpectrogramInSamples, input[k].length, upperCutoffFrequencyIdx, lowerCutoffFrequencyIdx); //computation is different than in Matlab
            }
        }

        //decode using spectrogram
        int[] messageDecodedBySpec = new int[(nBlocks-2)/2 * mSoniTalkConfig.getnFrequencies()]; // -2 for the start and end block, divide by two because a bit is encoded by two consecutive pulses on one frequency: either (send, notSend) or (notSend, send)
        arrayCounter = 0;
        // Go through all message blocks, skipping start and end block with a stepsize of 2 (because we always have a normal block and an inverted block)
        for(int j = 1; j<nBlocks-1; j=j+2){
            for(int m = frequencyCenterIndices[j].length-1; m>=0; m--){
                int currentCenterFreqIdx = (int)frequencyCenterIndices[j][m];

                // Matlab values range between 0 and -20 or so, always negative and not so small
                // Android values do not seem to have a clear range, sometimes positive sometimes negative, often close to 0
                double currentBit = getPointAndNeighborsAggreagate(input, currentCenterFreqIdx, blockCenters[j], nNeighborsFreqUpDown, nNeighborsTimeLeftRight, aggFcn);
                double currentBitInv = getPointAndNeighborsAggreagate(input, currentCenterFreqIdx, blockCenters[j + 1], nNeighborsFreqUpDown, nNeighborsTimeLeftRight, aggFcn);

                // Check why we had to change > to <
                if (currentBit < currentBitInv) {
                    messageDecodedBySpec[arrayCounter] = 1;
                }
                else{
                    messageDecodedBySpec[arrayCounter] = 0;
                }
                arrayCounter++;
            }
            //Log.d("arraycounter", String.valueOf(arrayCounter));
        }
        String decodedBitSequence = Arrays.toString(messageDecodedBySpec).replace(", ", "").replace("[","").replace("]","");
        final byte[] receivedMessage = DecoderUtils.binaryToBytes(decodedBitSequence);
        SoniTalkMessage message = new SoniTalkMessage(receivedMessage);
        if (callback != null) {
            callback.onMessageReceived(message.getDecodedMessage(), mChannelNumber);
        }
    }

    private float findClosestValueIn1DArray(int value, int winlen, int arraylength, int upperIdx, int lowerIdx){
        float arrayIndexRelative;
        float arrayIndex;

        float frequencyIndex = DecoderUtils.freq2idx(value, mSampleRate, winlen);
        arrayIndexRelative = DecoderUtils.getRelativeIndexPosition(frequencyIndex, upperIdx, lowerIdx);
        arrayIndex = ((float)arraylength*arrayIndexRelative);
        //Log.d("findClosest", "arrayIndex: " + arrayIndex);
        //arrayIndex = frequencyIndex;
        return arrayIndex;
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
        // Note: Values are extremely similar on the same row (same frequency), but different for the frequencies above and under.
        // Handling the [0,0] case
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


    public SoniTalkConfig getSoniTalkConfig() {
        return mSoniTalkConfig;
    }

}
