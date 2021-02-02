package at.ac.fhstp.sonitalk;

import at.ac.fhstp.sonitalk.utils.CircularArray;

/**
 * Abstract class for creating objects that have to react
 * to audio inputs in some way. Reads samples from a circular history
 * buffer and gives updates after every time read access is gained to
 * the subclass
 * @author Erik Gustafson
 */
public abstract class AudioController {

    private final CircularArray historyBuffer;
    private boolean isAnalyzing;
    private Thread analysisThread;
    private int analysisWindowLength;

    /**
     * create a new audio controller
     * @param historyBuffer
     *          microphone history buffer used as input
     * @param analysisWindowLength
     *          how much of the buffer should be analyzed at a time
     */
    public AudioController(CircularArray historyBuffer, int analysisWindowLength) {
        this.historyBuffer = historyBuffer;
        this.isAnalyzing = false;
        this.analysisWindowLength = analysisWindowLength;
    }

    /**
     * Start recording samples and giving the subclass a callback
     */
    public void startAnalysis() {
        isAnalyzing = true;
        analysisThread = new Thread() {
            @Override
            public void run() {
                super.run();
                boolean run = true;
                float[] analysisHistoryBuffer;
                while (run) {
                    synchronized (historyBuffer) {
                        analysisHistoryBuffer = historyBuffer.getLastWindow(analysisWindowLength);
                    }
                    analyzeSamples(analysisHistoryBuffer);
                }

                if (Thread.currentThread().isInterrupted()) {
                    run = false;
                }
            }
        };
        analysisThread.start();
    }

    /**
     * Shut down the analysis thread
     */
    public void stopAnalysis() {
        isAnalyzing = false;
        analysisThread.isInterrupted();
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
        return Math.round((float)((config.getBitperiod() * (float) GaltonChat.SAMPLE_RATE/1000)/4));
    }

    abstract void analyzeSamples(float[] analysisHistoryBuffer);
}
