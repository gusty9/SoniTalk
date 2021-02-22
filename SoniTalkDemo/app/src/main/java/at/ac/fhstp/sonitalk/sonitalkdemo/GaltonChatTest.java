package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.os.Handler;

import java.util.Random;


import at.ac.fhstp.sonitalk.GaltonChat;
import at.ac.fhstp.sonitalk.utils.ID;

public class GaltonChatTest {
    private GaltonChat chat;
    private Random random;
    private Handler delayedHandler;
    private Runnable sendMessage;
    private TestCallback callback;

    public interface TestCallback {
        public void idSent(String id);
    }

    public GaltonChatTest(GaltonChat chat, TestCallback callback){
        this.chat = chat;
        this.random = new Random(System.nanoTime());//seed a random number generator
        delayedHandler = new Handler();
        sendMessage = new SendMessageRunnable();
        this.callback = callback;
    }

    public void startTest() {
        chat.startListeningThread();
        delayedHandler.postDelayed(sendMessage, generateRandom(5000, 8000));
    }

    public void stopTest() {
        delayedHandler.removeCallbacks(sendMessage);
    }

    private class SendMessageRunnable implements Runnable {
        @Override
        public void run() {
            //todo maybe add callbacks to the main activity to see this
            chat.sendMessage(generateNewMessage());
            delayedHandler.postDelayed(sendMessage, generateRandom(5000,8000));
            //todo probably have a way to post delayed after we can confirm the message was sent
        }
    }

    private String generateNewMessage() {
        String id = ID.generateRandomID();
        callback.idSent(id);
        return id;
    }

    private long generateRandom(int min, int max) {
        return random.nextInt((max - min) + 1) + min;
    }
}
