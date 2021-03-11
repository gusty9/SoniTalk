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
    private boolean testIsRunning;

    public GaltonChatTest(GaltonChat chat){
        this.chat = chat;
        this.random = new Random(System.nanoTime());//seed a random number generator
        delayedHandler = new Handler();
        sendMessage = new SendMessageRunnable();
        this.testIsRunning = true;
    }

    public void startTest() {
        testIsRunning = true;
        chat.startListeningThread();
        delayedHandler.postDelayed(sendMessage, generateRandom(0, 12000)); //random time to send first id between 0-10 seconds
    }

    public void stopTest() {
        testIsRunning = false;
        delayedHandler.removeCallbacks(sendMessage);
        chat.stopListeningThread();
    }

    public void onSuccessfulMessageSent() {
        delayedHandler.postDelayed(sendMessage, generateRandom(15000, 25000));//send the next message random between 8-16 seconds
    }

    private class SendMessageRunnable implements Runnable {
        @Override
        public void run() {
            chat.sendMessage(generateNewMessage());
        }
    }

    private String generateNewMessage() {
        String id = ID.generateRandomID();
        return id;
    }

    private long generateRandom(int min, int max) {
        return random.nextInt((max - min) + 1) + min;
    }

    public boolean isRunning() {
        return testIsRunning;
    }
}
