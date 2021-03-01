package at.ac.fhstp.sonitalk;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import at.ac.fhstp.sonitalk.utils.HammingWindow;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;
import uk.me.berndporr.iirj.Butterworth;

/**
 * Class for analyzing acoustic communication channels
 * and determining which channels are available
 * @author Erik Gustafson
 */
public class ChannelAnalyzer extends AudioController {
    private final int bandpassFilterOrder = 8;//todo figure out what this does
    private final int messageHeaderFactor = 6;//todo test this a little bit more

    private List<boolean[]> channelsAvailable;
    private final Object mutex = new Object();
    private Handler delayedTaskHandler;
    private DynamicConfiguration dynamicConfiguration;

    private ChannelListener callback;

    public interface ChannelListener {
        void channelsUpdated(List<boolean[]> channels);
    }

    /**
     * @param dynamicConfiguration
     *          reference to the dynamic configuration object
     * @param historyBuffer
     *          Reference to the microphone history buffer
     */
    public ChannelAnalyzer(DynamicConfiguration dynamicConfiguration, CircularArray historyBuffer) {
        super(historyBuffer, getAnalysisWindowLength(dynamicConfiguration.getConfigurations().get(0).get(0)));
        this.dynamicConfiguration = dynamicConfiguration;
        this.channelsAvailable = new ArrayList<>();
        for (int i = 0; i < this.dynamicConfiguration.getNumberOfConfigs(); i++) {
            boolean[] available = new boolean[this.dynamicConfiguration.getConfigSize(i)];
            Arrays.fill(available, true);
            this.channelsAvailable.add(available);
        }
        this.delayedTaskHandler = new Handler();

    }

    /**
     * analyze new samples pulled from the history buffer
     * @param analysisHistoryBuffer
     *          fresh set of samples pulled from the front of the circular
     *          array microphone history buffer
     */
    @Override
    void analyzeSamples(float[] analysisHistoryBuffer) {

        for (int i = 0; i < channelsAvailable.size(); i++) {
            for (int j = 0; j < channelsAvailable.get(i).length; j++) {
                boolean available = false;
                synchronized (mutex) {
                    available = channelsAvailable.get(i)[j];
                   // Log.e(GaltonChat.TAG, "config " + i + " channel " + j + " " + available);
                }
                //don't run the analysis if the channel is occupied.
                if (available) {
                    SoniTalkConfig config = dynamicConfiguration.getConfigurations().get(i).get(j);
                    int analysisWindowLength = getAnalysisWindowLength(config);
                    //copy the samples from the buffer that were added most recently
                    float[] responseUpper = new float[analysisWindowLength];
                    float[] responseLower = new float[analysisWindowLength];
                    System.arraycopy(analysisHistoryBuffer, 0, responseUpper, 0, analysisWindowLength);
                    System.arraycopy(analysisHistoryBuffer, 0, responseLower, 0, analysisWindowLength);

                    //create the filtered arrays
                    double[] responseUpperDouble = new double[analysisWindowLength * 2];
                    double[] responseLowerDouble = new double[analysisWindowLength * 2];
                    int bandpassWidth = DecoderUtils.getBandpassWidth(config.getnFrequencies(), config.getFrequencySpace());
                    int centerFrequencyLower = config.getFrequencyZero() + (bandpassWidth/2);
                    int centerFrequencyUpper = config.getFrequencyZero() + bandpassWidth + (bandpassWidth/2);

                    //filter the arrays
                    //only filter 1/2 the bandpass width in order to decrease overlap
                    Butterworth butterworthUpper = new Butterworth();
                    butterworthUpper.bandPass(bandpassFilterOrder, GaltonChat.SAMPLE_RATE, centerFrequencyUpper, bandpassWidth/2);
                    Butterworth butterworthLower = new Butterworth();
                    butterworthLower.bandPass(bandpassFilterOrder, GaltonChat.SAMPLE_RATE, centerFrequencyLower, bandpassWidth/2);

                    for (int k = 0; k < responseLower.length; k++) {
                        responseUpperDouble[k] = butterworthUpper.filter(responseUpper[k]);
                        responseLowerDouble[k] = butterworthLower.filter(responseLower[k]);
                    }
                    DoubleFFT_1D fft = new DoubleFFT_1D(responseUpper.length);
                    fft.complexForward(responseUpperDouble);
                    fft.complexForward(responseLowerDouble);

                    double sumAbsResponseUpper = 0.0;
                    double sumAbsResponseLower = 0.0;

                    for (int k = 0; k < responseUpperDouble.length; k+=2) {
                        sumAbsResponseUpper += DecoderUtils.getComplexAbsolute(responseUpperDouble[k], responseUpperDouble[k+1]);
                        sumAbsResponseLower += DecoderUtils.getComplexAbsolute(responseLowerDouble[k], responseLowerDouble[k+1]);
                    }

                    if (sumAbsResponseUpper > messageHeaderFactor * sumAbsResponseLower) {
                        //if this is true, a message block was found in the most recently added samples to the buffer
                        //set this channel to occupied and set a timer to reset the channel
                        List<boolean[]> cpy;
                        List<int[]> occupied = getOccupiedChannelsByIndex(i, j);
                        synchronized (mutex) {
                            for (int k = 0; k < occupied.size(); k++) {
                                int[] temp = occupied.get(k);
                                channelsAvailable.get(temp[0])[temp[1]] = false;
                            }
                            cpy = new ArrayList<>(channelsAvailable);
                        }
                        callback.channelsUpdated(cpy);

                        for (int k = 0; k < occupied.size(); k++) {
                            int[] temp = occupied.get(k);
                            ChannelAvailableRunnable waitMessageDuration = new ChannelAvailableRunnable(channelsAvailable, temp[0], temp[1]);
                            //only wait for the current config if the message was found in said buffer
                            delayedTaskHandler.postDelayed(waitMessageDuration, dynamicConfiguration.getMessageLength(i));//set it equal to the length the message was found on

                        }
                    }
                }
            }

            //only run this check if it is the current config
            if (i == dynamicConfiguration.getCurrentConfigIndex() && i !=0) {
                boolean allChannelsFullCheck = true;//set to true because a channel is 'false' if it is occupied
                for (int j = 0; j < channelsAvailable.get(i).length; j++) {
                    allChannelsFullCheck = allChannelsFullCheck && channelsAvailable.get(i)[j];
                }
                if (!allChannelsFullCheck) {
                    dynamicConfiguration.updateDeescalationTimer();
                }
            }

        }
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
        synchronized (channelsAvailable.get(currentConfig)) {
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

    private List<int[]> getOccupiedChannelsByIndex(int config, int channel) {
        List<int[]> occupiedIndices = new ArrayList<>();
        switch(config) {
            case 0:
                occupiedIndices.add(new int[]{config,channel});
//                occupiedIndices.add(new int[]{1,0});
//                occupiedIndices.add(new int[]{1,1});
//                occupiedIndices.add(new int[]{2,0});
//                occupiedIndices.add(new int[]{2,1});
//                occupiedIndices.add(new int[]{2,2});
                break;

            case 1:
                occupiedIndices.add(new int[]{0,0});
//                occupiedIndices.add(new int[]{2,1});
                occupiedIndices.add(new int[]{config, channel});
                switch(channel) {
                    case 0:
//                        occupiedIndices.add(new int[]{2,0});
                        break;
                    case 1:
//                        occupiedIndices.add(new int[]{2,2});
                        break;
                }
                break;

            case 2:
                occupiedIndices.add(new int[]{config, channel});
                occupiedIndices.add(new int[]{0,0});
                switch (channel) {
                    case 0:
//                        occupiedIndices.add(new int[]{1,0});
                        break;
                    case 1:
//                        occupiedIndices.add(new int[]{1,0});
//                        occupiedIndices.add(new int[]{1,1});
                        break;

                    case 2:
//                        occupiedIndices.add(new int[]{1,1});
                        break;
                }
                break;
        }
        return occupiedIndices;
    }
}
