package at.ac.fhstp.sonitalk;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import at.ac.fhstp.sonitalk.utils.CircularArray;

/**
 * Holds several configurations for a dynamic multi-channel acoustic
 * communication experience. A configuration is simply a list of SoniTalkConfig objects
 * @author Erik Gustafson
 */
public class DynamicConfiguration {

    private List<List<SoniTalkConfig>> configurations;
    private List<boolean[]> availability;
    private int currentConfigIndex;
    private Handler delayedTaskHandler;
    private Runnable deescalateRunnable;


    private ConfigurationChangeListener callback;

    public interface ConfigurationChangeListener {
        public void onConfigurationChange(int newConfigIndex);
    }

    public DynamicConfiguration(CircularArray historyBuffer, List<List<SoniTalkConfig>> configs) {
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
        this.delayedTaskHandler = new Handler();
        deescalateRunnable = new ConfigDescalationTimeoutRunnable();
    }


    /**
     * @return
     *      The current configuration that should be used
     */
    public List<SoniTalkConfig> getCurrentConfiguration() {
        return configurations.get(currentConfigIndex);
    }

    public int getCurrentConfigIndex() {
        return currentConfigIndex;
    }

    /**
     * escalate the current configuration to the next highest
     * in the configuration list
     * todo bounds check
     */
    public void escalateConfig() {
        if (currentConfigIndex != configurations.size() -1) {
            Log.e(GaltonChat.TAG, "Configuration Escalation");
            currentConfigIndex++;
            //start the de-escalation timer
            updateDeescalationTimer();
        }
        if (callback != null) {
            callback.onConfigurationChange(currentConfigIndex);
        }
    }
    /**
     * deescalate the current configuration to the previous one
     * in the configuration list
     * todo bounds check
     */
    public void deescalateConfig() {
        if (currentConfigIndex != 0) {
            Log.e(GaltonChat.TAG, "Configuration Deescalation");
            currentConfigIndex--;
            if (callback != null) {
                callback.onConfigurationChange(currentConfigIndex);
            }
            if (currentConfigIndex != 0) {
                updateDeescalationTimer();
            }
        }
    }

    public void setConfigurationIndex(int index) {
        if (index != currentConfigIndex) {
            currentConfigIndex = index;
            Log.e(GaltonChat.TAG, "setting configuration to index " + index);
            if (callback != null) {
                callback.onConfigurationChange(index);
            }
            if (index != 0) {
                //state the deescalation timer since there is a configuration that is lower
                updateDeescalationTimer();
            }
        }
    }

    /**
     * This should be called to remove the de-escalation timer and restart it.
     * This is desired in 2 cases:
     *      1: a channel just escalated
     *      2: all  channels were overlapping full at the same time
     * These are used as a rough estimate of a way to max throughput and reduce collisions
     */
    public void updateDeescalationTimer() {
        delayedTaskHandler.removeCallbacks(deescalateRunnable);
        delayedTaskHandler.postDelayed(deescalateRunnable, (int)Math.round(configurations.get(currentConfigIndex).get(0).getMessageDurationMS() * 2.5));
    }

    public void passCallback(ConfigurationChangeListener callback) {
        this.callback = callback;
    }

    public List<List<SoniTalkConfig>> getConfigurations() {
        return this.configurations;
    }


    public void onMessageReceived(int configIndex) {
        setConfigurationIndex(configIndex);
    }

    public int getConfigSize(int config) {
        return configurations.get(config).size();
    }

    public int getNumberOfConfigs() {
        return configurations.size();
    }

    public int getCurrentMessageLength() {
        return configurations.get(currentConfigIndex).get(0).getMessageDurationMS();
    }

    public int getMessageLength(int configIndex) {
        return configurations.get(configIndex).get(0).getMessageDurationMS();
    }


    private class ConfigDescalationTimeoutRunnable implements Runnable {
        @Override
        public void run() {
            DynamicConfiguration.this.deescalateConfig();
        }
    }
}
