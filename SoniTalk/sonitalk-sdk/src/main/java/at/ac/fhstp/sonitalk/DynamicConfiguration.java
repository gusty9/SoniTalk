package at.ac.fhstp.sonitalk;

import java.util.ArrayList;
import java.util.List;

import at.ac.fhstp.sonitalk.utils.CircularArray;

/**
 * Holds several configurations for a dynamic multi-channel acoustic
 * communication experience. A configuration is simply a list of SoniTalkConfig objects
 * @author Erik Gustafson
 */
public class DynamicConfiguration extends AudioController {
    private List<List<SoniTalkConfig>> configurations;
    private int currentConfigIndex;
    private boolean escalationRequired;
    private boolean deescalationRequired;

    public DynamicConfiguration(CircularArray historyBuffer, List<List<SoniTalkConfig>> configs) {
        super(historyBuffer, getAnalysisWindowLength(configs.get(0).get(0)));
        this.configurations = new ArrayList<>();
        this.configurations.addAll(configs);
        this.currentConfigIndex = 0;
        this.escalationRequired = false;
        this.deescalationRequired = false;
    }

    @Override
    void analyzeSamples(float[] analysisHistoryBuffer) {
        //todo
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
        currentConfigIndex++;
    }

    /**
     * deescalate the current configuration to the previous one
     * in the configuration list
     * todo bounds check
     */
    public void deescalateConfig() {
        currentConfigIndex--;
    }

    /**
     * This should be called before a message is sent.
     * The method will check if escalation is required, and will escalate
     * or deescalate accordingly.
     * @return
     *      The determined configuration for sending
     */
    public List<SoniTalkConfig> onPreMessageSend() {
        if (escalationRequired) {
            escalateConfig();
        }
        if (deescalationRequired) {
            deescalateConfig();
        }

        return getCurrentConfiguration();
    }

}
