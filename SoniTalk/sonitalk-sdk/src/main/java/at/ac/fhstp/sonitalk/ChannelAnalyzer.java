package at.ac.fhstp.sonitalk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import uk.me.berndporr.iirj.Butterworth;

/**
 * Class for analyzing acoustic communication channels
 * and determining which channels are available
 * @author Erik Gustafson
 */
public class ChannelAnalyzer {
    private final int bandpassFilterOrder = 8;//todo figure out what this does
    private final int messageHeaderFactor = 6;//todo test this a little bit more

    private List<boolean[]> channelsAvailable;
    private final Object mutex = new Object();
    private DynamicConfiguration dynamicConfiguration;
    private int[] bucketCenterFreq;
    private long[] smoothingCounter;
    private long[] holdTimer;
    private double[] varianceThresholds;
    private int bucketWidth;
    private int analysisWindowLength;
    private CircularArray historyBuffer;
    private final int TIMER_FOR_SMOOTHING = 750; //has to be 'low' for .5 sec
    private Random random;

    private ChannelListener callback;

    public interface ChannelListener {
        void channelsUpdated(List<boolean[]> channels);
    }

    /**
     * @param dynamicConfiguration
     *          reference to the dynamic configuration object
     */
    public ChannelAnalyzer(DynamicConfiguration dynamicConfiguration) {
        analysisWindowLength = dynamicConfiguration.getConfigurations().get(0).get(0).getAnalysisWinLen(GaltonChat.SAMPLE_RATE) / 2;
        this.dynamicConfiguration = dynamicConfiguration;
        this.channelsAvailable = new ArrayList<>();
        for (int i = 0; i < this.dynamicConfiguration.getNumberOfConfigs(); i++) {
            boolean[] available = new boolean[this.dynamicConfiguration.getConfigSize(i)];
            Arrays.fill(available, true);
            this.channelsAvailable.add(available);
        }
        random = new Random(System.currentTimeMillis());
        bucketCenterFreq = new int[]{18575, 19955, 21335};;
        smoothingCounter = new long[]{0,0,0};
        holdTimer = new long[]{generateRandom(750, 1250), generateRandom(750, 1250),generateRandom(750, 1250)};
        varianceThresholds = new double[]{2.2E-5, 2.2E-5, 2.2E-5};
        bucketWidth = 1000;//?
        historyBuffer = new CircularArray(dynamicConfiguration.getConfigurations().get(0).get(0).getAnalysisWinLen(GaltonChat.SAMPLE_RATE) *6);
    }

    /**
     * analyze new samples pulled from the history buffer
     * @param samples
     *          fresh set of samples pulled from the front of the circular
     *          array microphone history buffer
     */
    public void analyzeSamples(float[] samples) {
        historyBuffer.add(samples);
        boolean[] bucketAvailable = new boolean[]{true, true, true};
        float[] analysisHistoryBuffer = historyBuffer.getArray();
        for (int i = 0; i < bucketCenterFreq.length; i++) {
            float[] response = new float[analysisWindowLength];
            System.arraycopy(analysisHistoryBuffer, 0, response, 0, analysisWindowLength);
            Butterworth butterworthFilter = new Butterworth();
            butterworthFilter.bandPass(bandpassFilterOrder, GaltonChat.SAMPLE_RATE, bucketCenterFreq[i], bucketWidth);
            double[] freqResponse = new double[analysisWindowLength * 2];
            for (int k = 0; k < response.length; k++) {
                freqResponse[k] = butterworthFilter.filter(response[k]);
            }
            DoubleFFT_1D fft = new DoubleFFT_1D(response.length);
            fft.complexForward(freqResponse);

            double sumFft = 0.0;

            double variance = 0.0;
            double[] normalized = new double[analysisWindowLength];
            int helper = 0;
            double normalizedSum = 0.0;
            double normalizedAvg = 0.0;

            for (int k = 0; k < freqResponse.length; k+=2) {
                double d = DecoderUtils.getComplexAbsolute(freqResponse[k], freqResponse[k+1]);
                sumFft += d;
                normalized[helper] = d;
                helper++;
            }

            for (int k = 0; k < normalized.length; k++) {
                normalized[k] = normalized[k] / sumFft;
                normalizedSum += normalized[k];
            }
            normalizedAvg = normalizedSum / normalized.length;

            for (int k = 0; k < normalized.length; k++) {
               variance += Math.pow((normalized[k] - normalizedAvg), 2.0);
            }

            variance = variance / (normalized.length - 1);



            if (variance > varianceThresholds[i]) {
                bucketAvailable[i] = false;
                smoothingCounter[i] = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - smoothingCounter[i] < holdTimer[i]) {
                bucketAvailable[i] = false;
            }
//            Log.e(GaltonChat.TAG, "Bucket " + i + " var: " + variance);
        }
        updateAvailableChannels(bucketAvailable);

    }

    private void updateAvailableChannels(boolean[] buckets) {
        List<boolean[]> cpy;
        synchronized (mutex) {
            channelsAvailable.get(0)[0] = buckets[0] && buckets[1] && buckets[2];

            channelsAvailable.get(1)[0] = buckets[0];
            channelsAvailable.get(1)[1] = buckets[2];

            channelsAvailable.get(2)[0] = buckets[0];
            channelsAvailable.get(2)[1] = buckets[1];
            channelsAvailable.get(2)[2] = buckets[2];
            cpy = new ArrayList<>(channelsAvailable);
        }
        callback.channelsUpdated(cpy);
    }

    public void passCallback(ChannelListener callback) {
        this.callback = callback;
    }

    /**
     * @return
     *      an integer index of a sending channel
     *      -1 if one is not available
     */
    public int getSendingChannel() {
        List<Integer> channelAvailableIndices = new ArrayList<>();
        int currentConfig = dynamicConfiguration.getCurrentConfigIndex();

        boolean[] channelsAvailableCpy = new boolean[channelsAvailable.get(currentConfig).length];
        synchronized (mutex) {
            System.arraycopy(channelsAvailable.get(currentConfig), 0, channelsAvailableCpy, 0, channelsAvailableCpy.length);
        }
        for (int i = 0; i < channelsAvailableCpy.length; i++) {
            if (channelsAvailableCpy[i]) {
                channelAvailableIndices.add(i);
            }
        }

        //handle special cases
        //no channels are available
        if (channelAvailableIndices.size() == 0) {
            return -1;//todo determine what to do if both channels are full
        }
        //exactly one channel is available
        if (channelAvailableIndices.size() == 1) {
            return channelAvailableIndices.get(0);
        }
        //return a random index of available channels
        return getRandomItemFromList(channelAvailableIndices);
    }

    /**
     * private inner class used to set a channel back to available after
     * a duration equal to the duration of a single message
     */
    private class ChannelAvailableRunnable implements Runnable {
        private List<boolean[]> channels;
        private final int config;
        private final int channelIndex;

        /**
         * @param channels
         *          boolean list of available channels
         * @param channelIndex
         *          index of channel to be set to available after a timeout
         */
        public ChannelAvailableRunnable(List<boolean[]> channels,int config, int channelIndex) {
            this.channels = channels;
            this.config = config;
            this.channelIndex = channelIndex;
        }

        @Override
        public void run() {
            //set the channel to available when this is ran
            List<boolean[]> cpy;
            synchronized (mutex) {
                //ensure concurrency
                channels.get(config)[channelIndex] = true;
                cpy = new ArrayList<>(channels);
                //Log.e(GaltonChat.TAG, "channel " + channelIndex + " available");
            }
            callback.channelsUpdated(cpy);
        }
    }

    /**
     * @param items
     *          a list of integer values
     * @return
     *          a random value from the list
     */
    private int getRandomItemFromList(List<Integer> items) {
        return items.get(new Random().nextInt(items.size()));
    }

    /**
     * Set flags for channels that overlap the spectrum
     * only set channels occupied to configurations that are HIGHER
     * than the current configuration - to avoid false occupation
     * @param config
     *          configuration that a occupied channel was detected on
     * @param channel
     *          the channel that was occupied
     * @return
     *      a list of {config, channel} pairs that should also be considered occupiedd
     *      based on the detection of this message
     */
    private List<int[]> getOccupiedChannelsByIndex(int config, int channel) {
        List<int[]> occupiedIndices = new ArrayList<>();
        switch(config) {
            case 0:
                occupiedIndices.add(new int[]{config,channel});
                break;

            case 1:
                occupiedIndices.add(new int[]{0,0});
                occupiedIndices.add(new int[]{config, channel});
                break;

            case 2:
                occupiedIndices.add(new int[]{config, channel});
                occupiedIndices.add(new int[]{0,0});
                switch (channel) {
                    case 0:
                        occupiedIndices.add(new int[]{1,0});
                        break;
                    case 1:
                        occupiedIndices.add(new int[]{1,0});
                        occupiedIndices.add(new int[]{1,1});
                        break;

                    case 2:
                        occupiedIndices.add(new int[]{1,1});
                        break;
                }
                break;
        }
        return occupiedIndices;
    }

    private long generateRandom(int min, int max) {
        return random.nextInt((max - min) + 1) + min;
    }
}
