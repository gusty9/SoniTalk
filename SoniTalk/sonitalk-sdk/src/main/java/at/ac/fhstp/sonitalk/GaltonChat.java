package at.ac.fhstp.sonitalk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import edu.emory.mathcs.backport.java.util.Arrays;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;

/**
 * API Class for the GaltonChat API. Named after Francis Galton, the inventor of the dog whistle
 * @author Erik Gustafson
 */
public class GaltonChat implements SoniTalkDecoder.MessageListener {
    //minimum readable frequency = SAMPLE_RATE/2 = 22050
    public static final int SAMPLE_RATE = 44100;//should work with ~most~ devices
    private final int SONITALK_SENDER_REQUEST_CODE = 2;//todo uhh not sure what the request code is for but 2 works

    //configuration variables
    private List<SoniTalkDecoder> decoderList;

    //audio recording
    private final CircularArray historyBuffer;//this is NOT thread safe. wrap in 'synchronized' block when accessing
    private int audioRecorderBufferSize;
    private AudioRecord audioRecord;
    private final Thread recordingThread;
    private boolean isRecording;
    private ChannelAnalyzer channelAnalyzer;
    private DynamicConfiguration dynamicConfiguration;

    /**
     * Constructor. Each config should represent and individual non-overlapping channel
     * @param configs
     *          each channel configuration
     */
    public GaltonChat(List<List<SoniTalkConfig>> configs) {
        //init config variables

        //init audio recording variables
        this.historyBuffer = new CircularArray(getLargestRequiredBufferSize(configs));
        this.dynamicConfiguration = new DynamicConfiguration(historyBuffer, configs);
        this.channelAnalyzer = new ChannelAnalyzer(dynamicConfiguration, historyBuffer);
        dynamicConfiguration.addConfigChangeListener(channelAnalyzer);
        this.audioRecorderBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        this.audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioRecorderBufferSize);
        this.isRecording = false;

        //decoding variables
        this.decoderList = new ArrayList<>();
        for (int j = 0; j < configs.size(); j++) {
            for (int i = 0; i < configs.get(j).size(); i++) {
                SoniTalkDecoder decoder = new SoniTalkDecoder(configs.get(j).get(i), historyBuffer, j, i);
                decoder.addMessageListener(this);
                this.decoderList.add(decoder);
            }
        }

        this.recordingThread = new Thread() {
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
                        synchronized (GaltonChat.this.historyBuffer) {
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

    }

    /**
     * Send a hexadecimal string message on a given channel
     * the channel is determined by the channel analyzer object
     * as to reduce collisions
     * @param message
     *          The hexadecimal string message to send
     */
    public void sendMessage(String message) {
        dynamicConfiguration.onPreMessageSend();
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

    /**
     * Encode a hexadecimal string message at a given channel index
     * @param message
     *          The hexadecimal string to encode
     * @param channel
     *          The channel to encode the message for
     * @return
     *          The encoded sonitalk message
     */
    private SoniTalkMessage encodeMessage(String message, int channel) {
        //we don't care about permissions so just make the context null
        List<SoniTalkConfig> configList = dynamicConfiguration.getCurrentConfiguration();
        SoniTalkEncoder encoder = new SoniTalkEncoder(null, configList.get(channel));
        return encoder.messageAsHexByteString(message);
    }

    private SoniTalkMessage encodeMessage(String message, int config, int channel) {
        SoniTalkConfig sendingConfig = dynamicConfiguration.getConfigurations().get(config).get(channel);
        SoniTalkEncoder encoder = new SoniTalkEncoder(null, sendingConfig);
        return encoder.messageAsHexByteString(message);
    }

    /**
     * Callback method from implementing the Message Listener interface
     * receives a callback whenever a decoder detects a message in the buffer
     * @param receivedMessage message detected and received by the SDK.
     */
    @Override
    public void onMessageReceived(SoniTalkMessage receivedMessage, int configIndex, int channelIndex) {
        Log.e("GaltonChat Message Received", receivedMessage.getDecodedMessage());
        dynamicConfiguration.onMessageReceived(configIndex);
    }

    @Override
    public void onDecoderError(String errorMessage, int configIndex, int channelIndex) {
        Log.e("GaltonChat", errorMessage);
        //dynamicConfiguration.onMessageReceived(configIndex);
    }

    /**
     * Begin a new thread to save audio samples into
     * a circular history buffer
     */
    public void startListeningThread() {
        isRecording = true;
        recordingThread.start();
        dynamicConfiguration.startAnalysis();
        channelAnalyzer.startAnalysis();
        for (int i = 0; i < decoderList.size(); i++) {
            decoderList.get(i).startDecoder();
        }
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
        sender.send(encodeMessage(message, 0, 0), SONITALK_SENDER_REQUEST_CODE);
    }

    //method used for testing
    public void sendChannel1(String message) {
        SoniTalkSender sender = new SoniTalkSender(null);
        sender.send(encodeMessage(message, 1,0), SONITALK_SENDER_REQUEST_CODE);
    }

    //method used for testing
    public void sendChannel2(String message) {
        SoniTalkSender sender = new SoniTalkSender(null);
        sender.send(encodeMessage(message, 1,1), SONITALK_SENDER_REQUEST_CODE);
    }

    /**
     * calculate the size of the history buffer needed to fit
     * an entire message for the slowest configuration
     * @return
     *          The size of the history buffer
     */
    private int getLargestRequiredBufferSize(List<List<SoniTalkConfig>> configList) {
        //by convention the largest buffer size should simply be the last one in the list (slowest channel)
        return configList.get(configList.size() -1).get(configList.get(configList.size()-1).size() -1 ).getHistoryBufferSize(SAMPLE_RATE);
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
