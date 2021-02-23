package at.ac.fhstp.sonitalk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import edu.emory.mathcs.backport.java.util.Arrays;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;

/**
 * API Class for the GaltonChat API. Named after Francis Galton, the inventor of the dog whistle
 * @author Erik Gustafson
 */
public class GaltonChat implements SoniTalkDecoder.MessageListener {

    public interface MessageCallback {
        /**
         * This is NOT called on the UI THREAD!!!!
         * if you would like to update the UI with the result of this call back you must
         * call runOnUiThread(new Runnable(){})!!!!
         * @param message
         *          the message that was successfully decoded
         */
        void onMessageReceived(String message, int configIndex, int channelIndex);
        void onMessageSent(String message, int configIndex, int channelIndex);
    }

    public static final String TAG = "GaltonChat";
    //minimum readable frequency = SAMPLE_RATE/2 = 22050
    public static final int SAMPLE_RATE = 44100;//should work with ~most~ devices
    private final int SONITALK_SENDER_REQUEST_CODE = 2;//todo uhh not sure what the request code is for but 2 works
    private final int ATTEMPT_RESEND_THRESHOLD = 1;

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

    private MessageCallback callback;
    private Handler delayedTaskHandler;
    private int attemptResendCounter;
    private Random random;

    /**
     * Constructor. Each config should represent and individual non-overlapping channel
     * @param configs
     *          each channel configuration
     */
    public GaltonChat(List<List<SoniTalkConfig>> configs, MessageCallback callback, DynamicConfiguration.ConfigurationChangeListener configurationChangeListener, ChannelAnalyzer.ChannelListener channelListener) {
        //init config variables

        //init audio recording variables
        this.historyBuffer = new CircularArray(getLargestRequiredBufferSize(configs));/*perhaps make this smaller, as each decoder now has its own buffer?*/
        this.dynamicConfiguration = new DynamicConfiguration(historyBuffer, configs);
        dynamicConfiguration.passCallback(configurationChangeListener);
        this.channelAnalyzer = new ChannelAnalyzer(dynamicConfiguration, historyBuffer);
        channelAnalyzer.passCallback(channelListener);

        this.audioRecorderBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        this.audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioRecorderBufferSize);
        this.isRecording = false;
        this.callback = callback;
        this.delayedTaskHandler = new Handler();
        this.attemptResendCounter = 0;
        this.random = new Random(System.nanoTime());

        //decoding variables
        this.decoderList = new ArrayList<>();
        for (int j = 0; j < configs.size(); j++) {
            for (int i = 0; i < configs.get(j).size(); i++) {
                SoniTalkDecoder decoder = new SoniTalkDecoder(configs.get(j).get(i), j, i);
                decoder.addMessageListener(this);
                this.decoderList.add(decoder);
            }
        }

        this.recordingThread = new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                boolean run = true;//flag for exiting while loop
                audioRecord.startRecording();
                short[] temp = new short[audioRecorderBufferSize];
                float[] current = new float[audioRecorderBufferSize];
                while (run) {
                    //read in the data
                    int bytesRead = audioRecord.read(temp, 0 , audioRecorderBufferSize);
                    if (bytesRead == audioRecorderBufferSize) { //ensure we read enough bytes
                        convertShortToFloat(temp, current, audioRecorderBufferSize);
                        synchronized (GaltonChat.this.historyBuffer) {
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
        //dynamicConfiguration.onPreMessageSend();
        if (attemptResendCounter > ATTEMPT_RESEND_THRESHOLD) {
            dynamicConfiguration.escalateConfig();
            //channels might still be occupied after an escalation has been done, so reset the sender counter
            attemptResendCounter = 0;
        }

        int channelToSend = channelAnalyzer.getSendingChannel();
        if (channelToSend != -1) {
            //do not care about context, pass null
            SoniTalkSender sender = new SoniTalkSender(null);
            sender.send(encodeMessage(message, channelToSend), SONITALK_SENDER_REQUEST_CODE);
            callback.onMessageSent(message, dynamicConfiguration.getCurrentConfigIndex(), channelToSend);
            attemptResendCounter = 0;
        } else {
            //all channels were occupied. Do something?
            Log.e(TAG, "all channels are occupied, attempting to resend message");
            AttemptResendRunnable resendRunnable = new AttemptResendRunnable(message);
            int messageDur = dynamicConfiguration.getCurrentMessageLength();
            delayedTaskHandler.postDelayed(resendRunnable, generateRandom(messageDur, 2*messageDur));//maybe not over 2
            attemptResendCounter++;
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
        Log.e(TAG, receivedMessage.getDecodedMessage() + " config " + configIndex + " channel " + channelIndex);
        dynamicConfiguration.onMessageReceived(configIndex);
        callback.onMessageReceived(receivedMessage.getDecodedMessage(), configIndex, channelIndex);
        //todo sometimes messages that do not exist are being decoded. Refine that more or add extra error checking?
    }

    @Override
    public void onDecoderError(String errorMessage, int configIndex, int channelIndex) {
        //Log.e(TAG, errorMessage + " on config " + configIndex + " channel "+ channelIndex);
        //todo consider what to do with error messages..?
        //dynamicConfiguration.onMessageReceived(configIndex);
    }

    /**
     * Begin a new thread to save audio samples into
     * a circular history buffer
     */
    public void startListeningThread() {
        isRecording = true;
        recordingThread.start();
        //dynamicConfiguration.startAnalysis();
        channelAnalyzer.startAnalysis();
        for (int i = 0; i < decoderList.size(); i++) {
            //todo make this not fucking dumb as hell
            final int temp = i;
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    decoderList.get(temp).startDecoding();
                }
            }.start();
        }
    }

    /**
     * Tells the recording thread to stop collecting data
     */
    public void stopListeningThread() {
        isRecording = false;
        channelAnalyzer.stopAnalysis();//stop the channel analyzer
        for (int i = 0; i < decoderList.size(); i++) {
            decoderList.get(i).stopDecoder();
        }
        recordingThread.interrupt();//tell the thread to stop
    }

    //method used for testing - force a send on what I want to see if the system is working appropriately
    public void force_config_and_channel_send(String message, int config, int channel) {
        SoniTalkSender sender = new SoniTalkSender(null);
        sender.send(encodeMessage(message, config, channel), SONITALK_SENDER_REQUEST_CODE);
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

    public class AttemptResendRunnable implements Runnable {
        private String message;
        public AttemptResendRunnable(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            sendMessage(message);
        }
    }

    private long generateRandom(int min, int max) {
        return random.nextInt((max - min) + 1) + min;
    }
}
