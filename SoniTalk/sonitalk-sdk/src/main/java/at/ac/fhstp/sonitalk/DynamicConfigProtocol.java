package at.ac.fhstp.sonitalk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;
import uk.me.berndporr.iirj.Butterworth;

public class DynamicConfigProtocol {
    public final int SAMPLE_RATE = 44100;

    private List<SoniTalkConfig> configList;

    //audio recording
    private int audioRecorderBufferSize;
    private final CircularArray historyBuffer;
    private AudioRecord audioRecorder;
    private Thread recordingThread;
    private boolean isRecording;
    private int analysisWindowLength;

    public DynamicConfigProtocol(SoniTalkConfig... configs) {
        configList = new ArrayList<>();
        configList.addAll(Arrays.asList(configs));
        historyBuffer = new CircularArray(getLargestRequiredBufferSize());
        audioRecorderBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioRecorderBufferSize);
        isRecording = false;
        analysisWindowLength = Math.round((float)((configList.get(0).getBitperiod() * (float) SAMPLE_RATE/1000)/2));
    }

    private SoniTalkMessage encodeMessage(String message, int channel) {
        SoniTalkEncoder encoder = new SoniTalkEncoder(null, configList.get(channel));
        return encoder.messageAsHexByteString(message);
    }

    public void sendMessage(String message, int channel) {
        SoniTalkSender sender = new SoniTalkSender(null);
        sender.send(encodeMessage(message, channel), 2);
    }

    public void beginAudioAnalysis() {
        isRecording = true;
        recordingThread = new Thread(){
            @Override
            public void run() {
                super.run();
                boolean run = true;
                audioRecorder.startRecording();
                short[] temp = new short[audioRecorderBufferSize];
                float[] current = new float[audioRecorderBufferSize];
                while(run) {
                    audioRecorder.read(temp, 0, audioRecorderBufferSize);
                    convertShortToFloat(temp, current, audioRecorderBufferSize);
                    synchronized (historyBuffer) {
                        historyBuffer.add(current);
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        run = false;
                        audioRecorder.stop();
                    }
                }
            }
        };
        recordingThread.start();
        beginChannelAnalysis();
    }

    public void beginChannelAnalysis() {
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    float[] analysisHistoryBuffer;
                    synchronized (historyBuffer) {
                        analysisHistoryBuffer = historyBuffer.getArray();
                    }
                    float[] frontWindow = new float[analysisWindowLength];
                    float[] frontWindowFlag = new float[analysisWindowLength];
                    System.arraycopy(analysisHistoryBuffer, 0, frontWindow, 0, analysisWindowLength);
                    System.arraycopy(analysisHistoryBuffer, 0, frontWindowFlag, 0, analysisWindowLength);

                    int channelWidth = DecoderUtils.getBandpassWidth(configList.get(0).getnFrequencies(), configList.get(0).getFrequencySpace());
                    int centerFrequencyChannel = configList.get(0).getFrequencyZero() + (channelWidth/2);
                    Butterworth butterworthChannel = new Butterworth();
                    Butterworth butterworthFlag = new Butterworth();
                    butterworthChannel.bandPass(8, SAMPLE_RATE, centerFrequencyChannel, channelWidth);
                    butterworthFlag.bandPass(8, SAMPLE_RATE, configList.get(0).getFlagFrequency(), 100);

                    int nextPowerTwo = DecoderUtils.nextPowerOfTwo(analysisWindowLength);
                    double[] channelResponse = new double[nextPowerTwo];
                    double[] flagResponse = new double[nextPowerTwo];

                    for (int i = 0; i <  frontWindow.length; i ++) {
                        channelResponse[i] = butterworthChannel.filter(frontWindow[i]);
                        flagResponse[i] = butterworthFlag.filter(frontWindowFlag[i]);
                    }

                    ComplexArray complexArrayChannel = Hilbert.transform(channelResponse);
                    ComplexArray complexArrayFlag = Hilbert.transform(flagResponse);

                    double sumChannel = 0.0;
                    double sumFlag = 0.0;

                    for (int i = 0; i  <complexArrayFlag.real.length; i++) {
                        sumChannel += DecoderUtils.getComplexAbsolute(complexArrayChannel.real[i], complexArrayChannel.imag[i]);
                        sumFlag += DecoderUtils.getComplexAbsolute(complexArrayFlag.real[i], complexArrayFlag.imag[i]);
                    }

                    if (sumFlag > sumChannel) {
                        //if this is true, i ~think~ that the channel is occupied
                        Log.e("Channel Energy:", Double.toString(sumChannel));
                        Log.e("Flag Energy:", Double.toString(sumFlag));
                    }
                }

            }
        }.start();
    }

    private int getLargestRequiredBufferSize() {
        return configList.get(0).getHistoryBufferSize(SAMPLE_RATE);
    }

    /**
     * Converts an input array from short to [-1.0;1.0] float, result is put into the (pre-allocated) output array
     * @param input
     * @param output Should be allocated beforehand
     * @param arrayLength
     */
    private void convertShortToFloat(short[] input, float[] output, int arrayLength) {
        for (int i = 0; i < arrayLength; i++) {
            // Do we actually need float anywhere ? Switch to double ?
            output[i] = ((float) input[i]) / Short.MAX_VALUE;
        }
    }

}
