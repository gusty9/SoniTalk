package at.ac.fhstp.sonitalk;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import at.ac.fhstp.sonitalk.utils.ConfigFactory;

public class DynamicConfigProtocol {
    private List<SoniTalkConfig> configList;

    public DynamicConfigProtocol(SoniTalkConfig... configs) {
        configList = new ArrayList<>();
        configList.addAll(Arrays.asList(configs));

    }

    private SoniTalkMessage encodeMessage(String message, int channel) {
        SoniTalkEncoder encoder = new SoniTalkEncoder(null, configList.get(channel));
        return encoder.messageAsHexByteString(message);
    }

    public void sendMessage(String message, int channel) {
        SoniTalkSender sender = new SoniTalkSender(null);
        Log.e("test", "sender created");
        sender.send(encodeMessage(message, channel), 2);
    }


}
