package at.ac.fhstp.sonitalk;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import marytts.util.math.ComplexArray;
import marytts.util.math.Hilbert;
import uk.me.berndporr.iirj.Butterworth;

public class ChannelAnalyzer {

    private final List<SoniTalkConfig> configList;
    private final boolean[] channelsAvailable;
    private final CircularArray historyBuffer;
    private int analysisWindowLength;
    private int messageDurationMS;

    private Thread analysisThread;
    private boolean isAnalyzing;
    private Handler delayedTaskHandler;

    public ChannelAnalyzer(List<SoniTalkConfig> configList, CircularArray buffer, int analysisWindowLength) {
        this.configList = new ArrayList<>();
        this.configList.addAll(configList);
        this.analysisWindowLength = analysisWindowLength;
        this.historyBuffer = buffer;
        this.delayedTaskHandler = new Handler();
        channelsAvailable = new boolean[configList.size()];
        for (int i = 0; i < channelsAvailable.length; i++){ //set with an initial value of 'true'
            channelsAvailable[i] = true;
        }
        isAnalyzing = false;
        messageDurationMS = (configList.get(0).getnMessageBlocks() + 2) * configList.get(0).getBitperiod();
    }

    public void beginChannelAnalysis() {
        isAnalyzing = true;
        analysisThread = new Thread() {
            @Override
            public void run() {
                super.run();
                boolean run = true;
                float[] analysisHistoryBuffer;
                while (run) {
                    synchronized (historyBuffer) {
                        //pull from the last window to get the most recent data in the buffer
                        //we dont care about the rest of the signal data here,
                        //only the current state of the buffer is what matters right now
                        analysisHistoryBuffer = historyBuffer.getLastWindow(analysisWindowLength);
                    }
                    for (int i = 0; i < configList.size(); i++) {
                        boolean available;//don't bother running this loop if we know channel has a message on it
                        synchronized (channelsAvailable) {
                            available = channelsAvailable[i];
                        }
                        if (available) {
                            float[] frontWindow = new float[analysisWindowLength];
                            float[] frontWindowFlag = new float[analysisWindowLength];
                            System.arraycopy(analysisHistoryBuffer, 0, frontWindow, 0, analysisWindowLength);
                            System.arraycopy(analysisHistoryBuffer, 0, frontWindowFlag, 0, analysisWindowLength);
                            int channelWidth = DecoderUtils.getBandpassWidth(configList.get(i).getnFrequencies(), configList.get(i).getFrequencySpace());
                            int centerFrequencyChannel = configList.get(i).getFrequencyZero() + (channelWidth/2);
                            Butterworth butterworthChannel = new Butterworth();
                            Butterworth butterworthFlag = new Butterworth();
                            butterworthChannel.bandPass(8, DynamicConfigProtocol.SAMPLE_RATE, centerFrequencyChannel, channelWidth);
                            butterworthFlag.bandPass(8, DynamicConfigProtocol.SAMPLE_RATE, configList.get(i).getFlagFrequency(), 100);
                            int nextPowerTwo = DecoderUtils.nextPowerOfTwo(analysisWindowLength);
                            double[] channelResponse = new double[nextPowerTwo];
                            double[] flagResponse = new double[nextPowerTwo];

                            for (int j = 0; j <  frontWindow.length; j ++) {
                                channelResponse[j] = butterworthChannel.filter(frontWindow[j]);
                                flagResponse[j] = butterworthFlag.filter(frontWindowFlag[j]);
                            }

                            ComplexArray complexArrayChannel = Hilbert.transform(channelResponse);
                            ComplexArray complexArrayFlag = Hilbert.transform(flagResponse);

                            double sumChannel = 0.0;
                            double sumFlag = 0.0;

                            for (int j = 0; j  <complexArrayFlag.real.length; j++) {
                                sumChannel += DecoderUtils.getComplexAbsolute(complexArrayChannel.real[j], complexArrayChannel.imag[j]);
                                sumFlag += DecoderUtils.getComplexAbsolute(complexArrayFlag.real[j], complexArrayFlag.imag[j]);
                            }
                            if (sumFlag > sumChannel * 2.5) {
                                //this channel is occupied
                                synchronized (channelsAvailable) {
                                    channelsAvailable[i] = false;
                                }
                                //set a timer to set the channel to available after the duration of the signal
                                ChannelAvailableRunnable waitMessageDuration = new ChannelAvailableRunnable(channelsAvailable, i);
                                delayedTaskHandler.postDelayed(waitMessageDuration, messageDurationMS);
                            }
                        }
                    }
                    if (Thread.currentThread().isInterrupted()) { //break from analysis infinite loop
                        run = false;
                    }
                }
            }
        };
        analysisThread.start();
    }

    private class ChannelAvailableRunnable implements Runnable {
        private final boolean[] channels;
        private final int channelIndex;
        public ChannelAvailableRunnable(boolean[] channels, int channelIndex) {
            this.channels = channels;
            this.channelIndex = channelIndex;
        }
        @Override
        public void run() {
            synchronized (channels) {
                //set this channel to available after waiting the duration of the message
                channels[channelIndex] = true;
            }
        }
    }

    public void logAvailableChannels() {
        synchronized (channelsAvailable) {
            for (int i = 0; i < channelsAvailable.length; i++) {
                if (channelsAvailable[i]) {
                    Log.e("Channel " + i, "available");
                } else {
                    Log.e("Channel " + i, "occupied");
                }
            }
        }

    }
}
