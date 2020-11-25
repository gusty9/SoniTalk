package at.ac.fhstp.sonitalk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.TypeUtils;

/**
 * A class that manages sending and receiving
 * SoniTalk messages on multiple channels
 * @author
 *          Erik Gustafson
 */
public class SoniTalkChannelManager {
    private final int ON_SENDING_REQUEST_CODE = 2001;
    //member variables for creating this object
    private SoniTalkContext mSoniTalkContext;
    private List<SoniTalkChannel> mChannels;
    private boolean mIsListening;
    private int mSampleRate = 44100;
    private Thread mRecordMicThread;

    //member variables for sending messages
    private SoniTalkSender mSoniTalkSender;

    //member variables for decoding
    private AudioRecord mAudioRecord;
    private final CircularArray mHistoryBuffer;
    private int analysisWinLen;
    private int analysisWinStep;
    private int stepFactor = 8;
    private int nBlocks;
    private int nAnalysisWindowsPerBit;
    private int nAnalysisWindowsPerPause;

    /**
     * Create a new SoniTalkChannel manager object
     * @param soniTalkContext
     *          The sonitalk context to use
     * @param channels
     *          variable length number of channels
     */
    public SoniTalkChannelManager(SoniTalkContext soniTalkContext, SoniTalkChannelMessageReceiver receiver, SoniTalkChannel... channels) {
        mSoniTalkContext = soniTalkContext;
        mChannels = Arrays.asList(channels);
        mIsListening = false;
        int bitperiodInSamples = (int)Math.round(mChannels.get(0).getSoniTalkConfig().getBitperiod() * (float)mSampleRate/1000);
        analysisWinLen = (int)Math.round((float) bitperiodInSamples / 2 );
        analysisWinStep = (int)Math.round((float) analysisWinLen / this.stepFactor);
        nBlocks = (int) Math.ceil(mChannels.get(0).getSoniTalkConfig().getnMessageBlocks()*2)+2;
        int pauseperiodInSamples = (int) Math.round(mChannels.get(0).getSoniTalkConfig().getPauseperiod() * (float)mSampleRate/1000);
        nAnalysisWindowsPerBit =  Math.round((bitperiodInSamples+pauseperiodInSamples)/(float)analysisWinStep); //number of analysis windows of bit+pause
        nAnalysisWindowsPerPause =  Math.round(pauseperiodInSamples/(float)analysisWinStep) ; //number of analysis windows during a pause

        mSoniTalkSender = mSoniTalkContext.getSender();
        initializedAudioRecorder();

        int historyBufferSize = ((bitperiodInSamples*nBlocks+pauseperiodInSamples*(nBlocks-1)));
        mHistoryBuffer = new CircularArray(historyBufferSize);
        for (int i = 0; i < mChannels.size(); i++) {
            mChannels.get(i).addMessageReceiver(receiver, i+1);
        }
    }

    /**
     * Send a message on a specific channel
     * @param msg
     *          The message to send
     */
    public void sendMessage(String msg) {
        //make sure we can can get the microphone buffer
        if (mHistoryBuffer == null) {
            throw new IllegalStateException("Cannot send a message unless we are recording. Access to the microphone buffer is needed for channel selection");
        }

        int sendingChannel = getSendingChannel();
        SoniTalkMessage toSend = mChannels.get(sendingChannel-1).createMessage(msg);
        mSoniTalkSender.send(toSend, ON_SENDING_REQUEST_CODE);
        Log.e("Sending on channel " + sendingChannel, msg);
        //loop until we are able to successfully send the message
//        float[] buffer;
//        while (true) {
//            synchronized (mHistoryBuffer) {
//                buffer = mHistoryBuffer.getArray();
//            }
//            if (mChannels.get(sendingChannel-1).isChannelAvailable(buffer)) {
//                Log.e("Sending on channel " + sendingChannel, msg);
//                SoniTalkMessage toSend = mChannels.get(sendingChannel-1).createMessage(msg);
//                mSoniTalkSender.send(toSend, ON_SENDING_REQUEST_CODE);
//                return;
//            } else {
//                Log.e("Channel " + sendingChannel, "Channel is unavailable");
//
//                //generate a new random number
//                int temp = getRandomSendingChannel();
//                while (sendingChannel == temp) {
//                    temp = getRandomSendingChannel();
//                }
//                sendingChannel = temp;
//            }
//        }
    }

    /**
     * Begin writing to the history buffer and analyzing for new messages
     */
    public void startListening() {
        if (mIsListening) {
            throw new IllegalStateException("Cannot call startListening() on an object that is already listening");
        }
        mIsListening = true;
        mRecordMicThread = new Thread() {
            @Override
            public void run() {
                super.run();

                boolean run = true;
                int counter = 1;
                int neededBytes = analysisWinStep;
                mAudioRecord.startRecording();
                while (run) {
                    //read audio data

                    short[] tempBuffer = new short[neededBytes];
                    float[] currentData = new float[neededBytes];
                    int readBytes = mAudioRecord.read(tempBuffer, 0, neededBytes);
                    if (readBytes == neededBytes) {
                        TypeUtils.convertShortToFloat(tempBuffer, currentData, readBytes);
                        synchronized (mHistoryBuffer) {
                            mHistoryBuffer.add(currentData);
                        }
                        if (counter >= (nBlocks*nAnalysisWindowsPerBit-nAnalysisWindowsPerPause)) {
                            synchronized (mHistoryBuffer) {
                                //todo decode on all channels for some reason this doesn't work
                                for (SoniTalkChannel channel : mChannels) {
                                    channel.analyzeHistoryBuffer(mHistoryBuffer);
                                }
                               // mChannels.get(1).analyzeHistoryBuffer(mHistoryBuffer);
                                mHistoryBuffer.incrementAnalysisIndex(analysisWinStep);
                            }
                        }
                        counter++;
                    }

                    //see if we need to stop writing to the history buffer
                    if (Thread.currentThread().isInterrupted()) {
                        run = false;
                        mAudioRecord.stop();
                    }
                }

            }


        };
        mRecordMicThread.start();;
    }

    /**
     * Stop recording to the history buffer and cleanup objects
     */
    public void stopListening() {
        if (!mIsListening) {
            throw new IllegalStateException("Cannot call stopListening() on an object this is not listening");
        }
        mIsListening = false;
        mRecordMicThread.interrupt();
    }

    private void initializedAudioRecorder() {
        int minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {

            int audioRecorderBufferSize = analysisWinLen*10;

            if (audioRecorderBufferSize < minBufferSize) {
                audioRecorderBufferSize = minBufferSize;
            }

            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mSampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, audioRecorderBufferSize);
            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                //todo error handling
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            //todo error handling
        }
    }

    private int getSendingChannel() {
        double[] energies = new double[mChannels.size()];
        float[] buffer;
        synchronized (mHistoryBuffer) {
            buffer = mHistoryBuffer.getArray();
        }
        for (int i = 0; i < mChannels.size(); i++) {
            energies[i] = mChannels.get(i).getChannelEnergy(buffer);
        }
        double min = energies[0];
        int sendingChannel = 0;
        //todo check if all channels are not available
        for (int i = 1; i < energies.length; i++) {
            if (energies[i] < min) {
                min = energies[i];
                sendingChannel = i;
            }
        }
        return sendingChannel  + 1; //convert from index to channel #
    }

    /**
     * Return a channel number between 1 - mChannels.length (inclusive)
     * @return
     *          a random integer in the range [1, mChannels.length]
     */
    private int getRandomSendingChannel() {
        return (int) ((Math.random() * mChannels.size()) + 1);
    }

}
