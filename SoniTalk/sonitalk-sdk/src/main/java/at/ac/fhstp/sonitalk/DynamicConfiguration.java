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


    public DynamicConfiguration(CircularArray historyBuffer, List<List<SoniTalkConfig>> configs) {
        super(historyBuffer, getAnalysisWindowLength(configs.get(0).get(0)));
        configurations.addAll(configs);

    }

    @Override
    void analyzeSamples(float[] analysisHistoryBuffer) {

    }


}
