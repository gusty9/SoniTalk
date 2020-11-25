package at.ac.fhstp.sonitalk;

public interface SoniTalkChannelMessageReceiver {
    /**
     * called when a message is decoded on a specific channel
     * @param message
     * @param channel
     */
    public void onMessageReceived(String message, int channel);
}
