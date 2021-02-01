package at.ac.fhstp.sonitalk;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;
import uk.me.berndporr.iirj.Butterworth;

/**
 * Class for analyzing acoustic communication channels
 * and determining which channels are available
 * @author Erik Gustafson
 */
public class ChannelAnalyzer {
    private final int bandpassFilterOrder = 8;//todo figure out what this does
    private final int messageHeaderFactor = 2;//todo test this a little bit more

    private final List<SoniTalkConfig> configList;
    private final boolean[] channelsAvailable;
    private final CircularArray historyBuffer;
    private int messageDuration;//milliseconds
    private int analysisWindowLength;// (1/2 a bit period, converted to discrete)

    private Thread analysisThread;
    private boolean isAnalyzing;
    private Handler delayedTaskHandler;

    /**
     * @param configList
     *          List of configurations to track the occupancy of
     * @param historyBuffer
     *          Reference to the microphone history buffer
     */
    public ChannelAnalyzer(List<SoniTalkConfig> configList, CircularArray historyBuffer) {
        this.configList = new ArrayList<>();
        this.configList.addAll(configList);//create of the config, don't use the same reference
        this.channelsAvailable = new boolean[configList.size()];
        Arrays.fill(this.channelsAvailable, true);// all channels are set to available at first
        this.historyBuffer = historyBuffer;
        isAnalyzing = false;
        this.delayedTaskHandler = new Handler();
        this.messageDuration = getMessageDuration();
        this.analysisWindowLength = getAnalysisWindowLength();
    }

    /**
     * start analyzing the history buffer in a separate thread for
     * new messages.
     */
    public void startAnalysis() {
        isAnalyzing = true;
        analysisThread = new Thread() {
            @Override
            public void run() {
                super.run();
                boolean run = true;
                float[] analysisHistoryBuffer;
                while(run) {
                    synchronized (historyBuffer) {
                        analysisHistoryBuffer = historyBuffer.getLastWindow(analysisWindowLength);
                    }
                    for (int i = 0; i < configList.size(); i++) {
                        boolean available;
                        synchronized (channelsAvailable) {
                            available =  channelsAvailable[i];
                        }
                        //don't run the analysis if the channel is occupied.
                        if (available) {
                            //copy the samples from the buffer that were added most recently
                            float[] responseUpper = new float[analysisWindowLength];
                            float[] responseLower = new float[analysisWindowLength];
                            System.arraycopy(analysisHistoryBuffer, 0, responseUpper, 0, analysisWindowLength);
                            System.arraycopy(analysisHistoryBuffer, 0, responseLower, 0, analysisWindowLength);

                            //create the filtered arrays
                            int nextPowerOfTwo = DecoderUtils.nextPowerOfTwo(analysisWindowLength);
                            double[] responseUpperDouble = new double[nextPowerOfTwo];
                            double[] responseLowerDouble = new double[nextPowerOfTwo];
                            int bandpassWidth = DecoderUtils.getBandpassWidth(configList.get(i).getnFrequencies(), configList.get(i).getFrequencySpace());
                            int centerFrequencyLower = configList.get(i).getFrequencyZero() + (bandpassWidth/2);
                            int centerFrequencyUpper = configList.get(i).getFrequencyZero() + bandpassWidth + (bandpassWidth/2);

                            //filter the arrays
                            Butterworth butterworthUpper = new Butterworth();
                            butterworthUpper.bandPass(bandpassFilterOrder, GaltonChat.SAMPLE_RATE, centerFrequencyUpper, bandpassWidth);
                            Butterworth butterworthLower = new Butterworth();
                            butterworthLower.bandPass(bandpassFilterOrder, GaltonChat.SAMPLE_RATE, centerFrequencyLower, bandpassWidth);
                            for (int k = 0; k < responseLower.length; k++) {
                                responseUpperDouble[k] = butterworthUpper.filter(responseUpper[k]);
                                responseLowerDouble[k] = butterworthLower.filter(responseLower[k]);
                            }

                            ComplexArray complexArrayUpper = Hilbert.transform(responseUpperDouble);
                            ComplexArray complexArrayLower = Hilbert.transform(responseLowerDouble);

                            double sumAbsResponseUpper = 0.0;
                            double sumAbsResponseLower = 0.0;

                            for (int k = 0; k < complexArrayUpper.real.length; k++) {
                                sumAbsResponseUpper += DecoderUtils.getComplexAbsolute(complexArrayUpper.real[k], complexArrayUpper.imag[k]);
                                sumAbsResponseLower += DecoderUtils.getComplexAbsolute(complexArrayLower.real[k], complexArrayLower.imag[k]);
                            }

                            if (sumAbsResponseUpper > messageHeaderFactor * sumAbsResponseLower) {
                                //if this is true, a message block was found in the most recently added samples to the buffer
                                //set this channel to occupied and set a timer to reset the channel
                                synchronized (channelsAvailable) {
                                    channelsAvailable[i] = false;
                                    Log.e("channel " + i, "lower: " + sumAbsResponseLower + " upper: " + sumAbsResponseUpper);
                                }
                                ChannelAvailableRunnable waitMessageDuration = new ChannelAvailableRunnable(channelsAvailable, i);
                                delayedTaskHandler.postDelayed(waitMessageDuration, messageDuration);
                            }
                        }
                    }

                    if(Thread.currentThread().isInterrupted()) {
                        run = false;
                    }
                }

            }
        };
        analysisThread.start();
    }

    public void stopAnalysis() {
        isAnalyzing = false;
        analysisThread.interrupt();//tell thread to shutdown
    }

    public int getSendingChannel() {
        List<Integer> channelAvailableIndices = new ArrayList<>();
        boolean[] channelsAvailableCpy = new boolean[channelsAvailable.length];
        synchronized (channelsAvailable) {
            System.arraycopy(channelsAvailable, 0, channelsAvailableCpy, 0, channelsAvailable.length);
        }
        for (int i = 0; i < channelsAvailableCpy.length; i++) {
            if (channelsAvailableCpy[i]) {
                channelAvailableIndices.add(i);
            }
        }

        //handle special cases
        if (channelAvailableIndices.size() == 0) {
            return -1;//todo determine what to do if both channels are full
        }
        if (channelAvailableIndices.size() == 1) {
            return channelAvailableIndices.get(0);
        }
        //return a random index of available channels
        return getRandomItemFromList(channelAvailableIndices);
    }

    /**
     * private inner class used to set a channel back to available after
     * the duration of the message on that channel
     */
    private class ChannelAvailableRunnable implements Runnable {
        private final boolean[] channels;
        private final int channelIndex;

        /**
         * @param channels
         *          boolean list of available channels
         * @param channelIndex
         *          index of channel to be set to available after a timeout
         */
        public ChannelAvailableRunnable(boolean[] channels, int channelIndex) {
            this.channels = channels;
            this.channelIndex = channelIndex;
        }

        @Override
        public void run() {
            //set the channel to available when this is ran
            synchronized (channels) {
                //ensure concurrency
                channels[channelIndex] = true;
                Log.e("test", "channel " + channelIndex + " available");
            }
        }
    }

    private int getAnalysisWindowLength() {
        return Math.round((float)((configList.get(0).getBitperiod() * (float) GaltonChat.SAMPLE_RATE/1000)/2));
    }

    private int getMessageDuration() {
        //todo figure out why its *1.5 and not what i think it is
        return (int) (((configList.get(0).getnMessageBlocks() + 2) * configList.get(0).getBitperiod()) * (1.5));
    }

    private int getRandomItemFromList(List<Integer> items) {
        return items.get(new Random().nextInt(items.size()));
    }
}
