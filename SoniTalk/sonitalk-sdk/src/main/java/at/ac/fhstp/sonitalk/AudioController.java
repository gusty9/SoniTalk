package at.ac.fhstp.sonitalk;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;

/**
 * Abstract class for creating objects that have to react
 * to audio inputs in some way. Reads samples from a circular history
 * buffer and gives updates after every time read access is gained to
 * the subclass
 * @author Erik Gustafson
 */
public class AudioController {

    private AudioRecord audioRecord;
    private boolean isAnalyzing;
    private Thread analysisThread;
    private int minBufferSize;
    private GaltonChatDecoder decoder;
    private ChannelAnalyzer channelAnalyzer;
    private ExecutorService executor;

    /**
     */
    public AudioController(GaltonChatDecoder decoder, ChannelAnalyzer channelAnalyzer) {
        this.isAnalyzing = false;
        this.decoder = decoder;
        this.channelAnalyzer = channelAnalyzer;
        executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Start recording samples and giving the subclass a callback
     */
    public void startAnalysis() {
        this.analysisThread = new Thread() {
            @Override
            public void run() {
                super.run();
                boolean run = true;

                audioRecord = getInitializedAudioRecorder();
                audioRecord.startRecording();
                int readBytes = 0;
                int neededBytes = decoder.getAnalysisWinLen();
                short tempBuffer[] = new short[neededBytes];
                final float currentData[] = new float[neededBytes];

                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO); //maybe this helps?
                } catch (IllegalArgumentException | SecurityException ex) {
                    // Ignore
                    Log.e(GaltonChat.TAG, "error");
                }

                while (run) {

                    readBytes = audioRecord.read(tempBuffer, 0, neededBytes);
                    if (readBytes == neededBytes) {
                        SoniTalkDecoder.convertShortToFloat(tempBuffer, currentData, readBytes);
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                channelAnalyzer.analyzeSamples(currentData);
                            }
                        });
                        decoder.analyzeSamples(currentData);
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        audioRecord.stop();
                        run = false;
                    }
                }
            }
        };
        analysisThread.setPriority(Thread.MAX_PRIORITY);
        analysisThread.start();
        isAnalyzing = true;
    }

    private AudioRecord getInitializedAudioRecorder() {
        //int minBufferSize = analysisWindowLength;
        int doubleAnalysisWin = 2 * decoder.getAnalysisWinLen();
        int buffSize = DecoderUtils.nextPowerOfTwo(doubleAnalysisWin);
        minBufferSize = AudioRecord.getMinBufferSize(GaltonChat.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (buffSize > minBufferSize) {
            minBufferSize = buffSize;
        }
        return new AudioRecord(MediaRecorder.AudioSource.MIC, GaltonChat.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
    }

    /**
     * Shut down the analysis thread
     */
    public void stopAnalysis() {
        if (isAnalyzing) {
            analysisThread.interrupt();
        }
        isAnalyzing = false;
    }

    /**
     * @return
     *      The size of 1 analysis window for the channel detection algorithm
     *      the window size is smaller than the decoder because there is less room
     *      for error. The decoder can have a false positive for a head block and
     *      have no unexpected behavior since it needs to detect a tail block
     *
     *      having a false positive in the channel selector reduces the throughput of
     *      the whole system, so make the analysis window shorter
     *
     *      do not but this in the super class as it might be changed/different from
     *      the channel analyzer analysis window length
     */
    public static int getAnalysisWindowLength(SoniTalkConfig config) {
        return Math.round((float)((config.getBitperiod() * (float) GaltonChat.SAMPLE_RATE/1000)/2));
    }

}
