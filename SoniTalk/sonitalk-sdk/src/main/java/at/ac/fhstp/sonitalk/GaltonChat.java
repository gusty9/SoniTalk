package at.ac.fhstp.sonitalk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import edu.emory.mathcs.backport.java.util.Arrays;

/**
 * API Class for the GaltonChat API. Named after Francis Galton, the inventor of the dog whistle
 * @author Erik Gustafson
 */
public class GaltonChat {
    //minimum readable frequency = SAMPLE_RATE/2 = 22050
    public static final int SAMPLE_RATE = 44100;//should work with ~most~ devices
    private final int SONITALK_SENDER_REQUEST_CODE = 2;//todo uhh not sure what the request code is for but 2 works

    //configuration variables
    private List<SoniTalkConfig> configList;

    //audio recording
    private final CircularArray historyBuffer;//this is NOT thread safe. wrap in 'synchronized' block when accessing
    private int audioRecorderBufferSize;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording;
    private ChannelAnalyzer channelAnalyzer;

    /**
     * Constructor. Each config should represent and individual non-overlapping channel
     * @param configs
     *          each channel configuration
     */
    public GaltonChat(SoniTalkConfig... configs) {
        //init config variables
        configList = new ArrayList<>();
        configList.addAll(Arrays.asList(configs));

        //init audio recording variables
        historyBuffer = new CircularArray(getLargestRequiredBufferSize());
        this.channelAnalyzer = new ChannelAnalyzer(configList, historyBuffer);
        audioRecorderBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioRecorderBufferSize);
        isRecording = false;

    }

    public void sendMessage(String message) {
        int channelToSend = channelAnalyzer.getSendingChannel();
        if (channelToSend != -1) {
            //do not care about context, pass null
            SoniTalkSender sender = new SoniTalkSender(null);
            sender.send(encodeMessage(message, channelToSend), SONITALK_SENDER_REQUEST_CODE);
        } else {
            //all channels were occupied. Do something?
            Log.e("GaltonChat", "all channels are occupied");
        }
    }

    private SoniTalkMessage encodeMessage(String message, int channel) {
        //we don't care about permissions so just make the context null
        SoniTalkEncoder encoder = new SoniTalkEncoder(null, configList.get(channel));
        return encoder.messageAsHexByteString(message);
    }

    /**
     * Begin a new thread to save audio samples into
     * a circular history buffer
     */
    public void startListeningThread() {
        isRecording = true;
        recordingThread = new Thread() {
            @Override
            public void run() {
                super.run();
                boolean run = true;//flag for exiting while loop
                audioRecord.startRecording();
                short[] temp = new short[audioRecorderBufferSize];
                float[] current = new float[audioRecorderBufferSize];
                while (run) {
                    //read in the data
                    int bytesRead = audioRecord.read(temp, 0 , audioRecorderBufferSize);
                    if (bytesRead == audioRecorderBufferSize) { //ensure we read enough bytes
                        synchronized (historyBuffer) {
                            convertShortToFloat(temp, current, audioRecorderBufferSize);
                            historyBuffer.add(current);
                        }
                    }
                    //check to see if the recording thread should be stopped
                    if (Thread.currentThread().isInterrupted()) {
                        run = false;
                        audioRecord.stop();
                    }
                }
            }
        };
        recordingThread.start();
        channelAnalyzer.startAnalysis();
    }

    /**
     * Tells the recording thread to stop collecting data
     */
    public void stopListeningThread() {
        isRecording = false;
        channelAnalyzer.stopAnalysis();//stop the channel analyzer
        recordingThread.interrupt();//tell the thread to stop
    }

    //method used for testing
    public void sendChannel0(String message) {
        SoniTalkSender sender = new SoniTalkSender(null);
        sender.send(encodeMessage(message, 0), SONITALK_SENDER_REQUEST_CODE);
    }

    //method used for testing
    public void sendChannel1(String message) {
        SoniTalkSender sender = new SoniTalkSender(null);
        sender.send(encodeMessage(message, 1), SONITALK_SENDER_REQUEST_CODE);
    }

    /**
     * calculate the size of the history buffer needed to fit
     * an entire message for the slowest configuration
     * @return
     *          The size of the history buffer
     */
    private int getLargestRequiredBufferSize() {
        int bufferSize = -1;
        for (int i = 0; i < configList.size(); i++) {
            int temp = configList.get(i).getHistoryBufferSize(SAMPLE_RATE);
            if (temp > bufferSize) {
                bufferSize = temp;
            }
        }
        return bufferSize;
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
