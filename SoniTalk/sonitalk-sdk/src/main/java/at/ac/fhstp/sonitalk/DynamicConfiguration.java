package at.ac.fhstp.sonitalk;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

import at.ac.fhstp.sonitalk.utils.CircularArray;
import at.ac.fhstp.sonitalk.utils.DecoderUtils;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import uk.me.berndporr.iirj.Butterworth;

/**
 * Holds several configurations for a dynamic multi-channel acoustic
 * communication experience. A configuration is simply a list of SoniTalkConfig objects
 * @author Erik Gustafson
 */
public class DynamicConfiguration extends AudioController {
    private final int bandpassFilterOrder = 8;//todo figure out what this does
    private final int messageHeaderFactor = 4;//todo test this a little bit more

    private List<List<SoniTalkConfig>> configurations;
    private List<boolean[]> availability;
    private int currentConfigIndex;
    private boolean deescalationRequired;
    private Handler delayedTaskHandler;

    public DynamicConfiguration(CircularArray historyBuffer, List<List<SoniTalkConfig>> configs) {
        super(historyBuffer, getAnalysisWindowLength(configs.get(0).get(0)));
        this.configurations = new ArrayList<>();
        this.configurations.addAll(configs);
        this.availability = new ArrayList<>();
        for (int i = 0; i < configurations.size(); i++) {
            availability.add(new boolean[configurations.get(i).size()]);
            for (int j = 0; j < configurations.get(i).size(); j++) {
                availability.get(i)[j] = true;
            }
        }
        this.currentConfigIndex = 0;
        this.deescalationRequired = false;
        this.delayedTaskHandler = new Handler();
    }

    @Override
    void analyzeSamples(float[] analysisHistoryBuffer) {
        for (int i = 0; i < configurations.get(currentConfigIndex).size(); i++) {
            int analysisWindowLength = getAnalysisWindowLength(configurations.get(currentConfigIndex).get(i));
            //copy the samples from the buffer that were added most recently
            float[] responseUpper = new float[analysisWindowLength];
            float[] responseLower = new float[analysisWindowLength];
            System.arraycopy(analysisHistoryBuffer, 0, responseUpper, 0, analysisWindowLength);
            System.arraycopy(analysisHistoryBuffer, 0, responseLower, 0, analysisWindowLength);

            //create the filtered arrays
            double[] responseUpperDouble = new double[analysisWindowLength * 2];
            double[] responseLowerDouble = new double[analysisWindowLength * 2];
            int bandpassWidth = DecoderUtils.getBandpassWidth(configurations.get(currentConfigIndex).get(i).getnFrequencies(), configurations.get(currentConfigIndex).get(i).getFrequencySpace());
            int centerFrequencyLower = configurations.get(currentConfigIndex).get(i).getFrequencyZero() + (bandpassWidth/2);
            int centerFrequencyUpper = configurations.get(currentConfigIndex).get(i).getFrequencyZero() + bandpassWidth + (bandpassWidth/2);

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
                int timeoutTime = configurations.get(currentConfigIndex).get(i).getMessageDurationMS();
                synchronized (availability.get(currentConfigIndex)) {
                    availability.get(currentConfigIndex)[i] = false;//todo set equal to true in t = m_dur / 2
                }
                ConfigEscalationTimeoutRunnable runnable = new ConfigEscalationTimeoutRunnable(availability.get(currentConfigIndex), i);
                delayedTaskHandler.postDelayed(runnable, timeoutTime);
            }
        }
    }

    /**
     * @return
     *      The current configuration that should be used
     */
    public List<SoniTalkConfig> getCurrentConfiguration() {
        return configurations.get(currentConfigIndex);
    }

    /**
     * escalate the current configuration to the next highest
     * in the configuration list
     * todo bounds check
     */
    public void escalateConfig() {
        if (currentConfigIndex != configurations.size() -1) {
            currentConfigIndex++;
        }
    }

    /**
     * deescalate the current configuration to the previous one
     * in the configuration list
     * todo bounds check
     */
    public void deescalateConfig() {
        if (currentConfigIndex != 0) {
            currentConfigIndex--;
        }

    }

    /**
     * This should be called before a message is sent.
     * The method will check if escalation is required, and will escalate
     * or deescalate accordingly.
     * @return
     *      The determined configuration for sending
     */
    public List<SoniTalkConfig> onPreMessageSend() {
        boolean escalationRequired = false;
        synchronized (availability.get(currentConfigIndex)) {
            for (int i = 0; i < availability.get(currentConfigIndex).length; i++) {
                escalationRequired = (escalationRequired || !availability.get(currentConfigIndex)[i]);
            }
        }

        if (escalationRequired) {
            escalateConfig();
        } else if (deescalationRequired) {
            deescalateConfig();
        }

        return getCurrentConfiguration();
    }


    private class ConfigEscalationTimeoutRunnable implements Runnable {
        private final boolean[] availability;
        private final int index;

        public ConfigEscalationTimeoutRunnable(boolean[] availability, int index) {
            this.availability = availability;
            this.index = index;
        }

        @Override
        public void run() {
            //ensure concurrency
            synchronized (availability) {
                availability[index] = true;
            }
        }
    }

}
