package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import at.ac.fhstp.sonitalk.ChannelAnalyzer;
import at.ac.fhstp.sonitalk.DynamicConfiguration;
import at.ac.fhstp.sonitalk.GaltonChat;
import at.ac.fhstp.sonitalk.SoniTalkConfig;
import at.ac.fhstp.sonitalk.utils.ConfigFactory;
import at.ac.fhstp.sonitalk.utils.ID;
import io.reactivex.functions.Consumer;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompMessage;

public class GaltonTestActivity extends AppCompatActivity implements GaltonChat.MessageCallback, DynamicConfiguration.ConfigurationChangeListener, ChannelAnalyzer.ChannelListener {
    private Button startListeningButton;
    private Button sendConfig0Channel0;
    private Button sendConfig1Channel0;
    private Button sendConfig1Channel1;
    private Button sendConfig2Channel0;
    private Button sendConfig2Channel1;
    private Button sendConfig2Channel2;
    private Button sendAlgoButton;
    private Button startTestButton;
    private Button stopTestButton;
    private TextView lastSentId;
    private TextView lastReceivedId;
    private TextView deviceId;

    private TextView config0;
    private TextView config0Chan0;
    private TextView config1;
    private TextView config1Chan0;
    private TextView config1Chan1;
    private TextView config2;
    private TextView config2Chan0;
    private TextView config2Chan1;
    private TextView config2Chan2;

    private GaltonChat galton;
    private GaltonChatTest test;

    private StompClient stompClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //set views
        setContentView(R.layout.galton_tester);
        startListeningButton = findViewById(R.id.start_listen);
        sendConfig0Channel0 = findViewById(R.id.config0_channel0);
        sendConfig1Channel0 = findViewById(R.id.config1_channel0);
        sendConfig1Channel1 = findViewById(R.id.config1_channel1);
        sendConfig2Channel0 = findViewById(R.id.config2_channel0);
        sendConfig2Channel1 = findViewById(R.id.config2_channel1);
        sendConfig2Channel2 = findViewById(R.id.config2_channel2);
        sendAlgoButton = findViewById(R.id.algo_send);
        startTestButton = findViewById(R.id.start_test);
        stopTestButton = findViewById(R.id.stop_test);
        lastSentId = findViewById(R.id.last_generated_id);
        lastReceivedId = findViewById(R.id.last_received_id);
        config0 = findViewById(R.id.config0Text);
        config0Chan0 = findViewById(R.id.config0chan0Text);
        config1 = findViewById(R.id.config1Text);
        config1Chan0 = findViewById(R.id.config1chan0Text);
        config1Chan1 = findViewById(R.id.config1chan1Text);
        config2 = findViewById(R.id.config2Text);
        config2Chan0 = findViewById(R.id.config2chan0Text);
        config2Chan1 = findViewById(R.id.config2chan1Text);
        config2Chan2 = findViewById(R.id.config2chan2Text);
        deviceId = findViewById(R.id.deviceID);
        deviceId.setText(Build.MODEL + "_" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));

        //create the api object
        try {
            SoniTalkConfig config0_channel0 = ConfigFactory.loadFromJson("config0_channel0.json", this);
            List<SoniTalkConfig> config0 = new ArrayList<>();
            config0.add(0, config0_channel0);
            SoniTalkConfig config1_channel0 = ConfigFactory.loadFromJson("config1_channel0.json", this);
            SoniTalkConfig config1_channel1 = ConfigFactory.loadFromJson("config1_channel1.json", this);
            List<SoniTalkConfig> config1 = new ArrayList<>();
            config1.add(0, config1_channel0);
            config1.add(1, config1_channel1);
            SoniTalkConfig config2_channel0 = ConfigFactory.loadFromJson("config2_channel0.json", this);
            SoniTalkConfig config2_channel1 = ConfigFactory.loadFromJson("config2_channel1.json", this);
            SoniTalkConfig config2_channel2 = ConfigFactory.loadFromJson("config2_channel2.json", this);
            List<SoniTalkConfig> config2 = new ArrayList<>();
            config2.add(0, config2_channel0);
            config2.add(1, config2_channel1);
            config2.add(2, config2_channel2);
            List<List<SoniTalkConfig>> configs = new ArrayList<>();
            configs.add(0, config0);
            configs.add(1, config1);
            configs.add(2, config2);
            galton = new GaltonChat(configs, this, this, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        test = new GaltonChatTest(galton);

        //respond to inputs
        startListeningButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.startListeningThread();
            }
        });

        sendConfig0Channel0.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.force_config_and_channel_send(generateNewMessage(), 0, 0);
            }
        });

        sendConfig1Channel0.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.force_config_and_channel_send(generateNewMessage(), 1, 0);
            }
        });

        sendConfig1Channel1.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.force_config_and_channel_send(generateNewMessage(), 1, 1);
            }
        });

        sendConfig2Channel0.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                galton.force_config_and_channel_send(generateNewMessage(), 2, 0);
            }
        });

        sendConfig2Channel1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                galton.force_config_and_channel_send(generateNewMessage(), 2, 1);
            }
        });

        sendConfig2Channel2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                galton.force_config_and_channel_send(generateNewMessage(), 2, 2);
            }
        });

        sendAlgoButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.sendMessage(generateNewMessage());
            }
        });

        startTestButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

            }
        });

        stopTestButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                test.stopTest();
            }
        });

        connectStomp();
    }

    private String generateNewMessage() {
        String id = ID.generateRandomID();
        lastSentId.setText(id);
        return id;
    }

    @Override
    public void onMessageReceived(final String message, int configIndex, int channelIndex) {

       String serial = Build.MODEL + "_" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        PostIdBackgroundTask task = new PostIdBackgroundTask(message, configIndex, channelIndex, serial,"/dynamic/receive/");
        task.execute();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lastReceivedId.setText(message);
            }
        });
    }

    @Override
    public void onConfigurationChange(final int newConfigIndex) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                config0.setTextColor(Color.RED);
                config1.setTextColor(Color.RED);
                config2.setTextColor(Color.RED);
                switch (newConfigIndex) {
                    case 0:
                        config0.setTextColor(Color.GREEN);
                        break;
                    case 1:
                        config1.setTextColor(Color.GREEN);
                        break;
                    case 2:
                        config2.setTextColor(Color.GREEN);
                        break;
                }
            }
        });
    }

    @Override
    public void channelsUpdated(List<boolean[]> channels) {
        final int[] colors = new int[6];//hard coded for this tester activity
        int helper = 0;
        for (int i = 0; i < channels.size(); i++) {
            for (int j = 0; j < channels.get(i).length; j++) {
                if (channels.get(i)[j]) {
                    colors[helper] = Color.GREEN;
                } else {
                    colors[helper] = Color.RED;
                }
                helper++;
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                config0Chan0.setTextColor(colors[0]);
                config1Chan0.setTextColor(colors[1]);
                config1Chan1.setTextColor(colors[2]);
                config2Chan0.setTextColor(colors[3]);
                config2Chan1.setTextColor(colors[4]);
                config2Chan2.setTextColor(colors[5]);
            }
        });
    }


    @Override
    public void onMessageSent(final String message, int configIndex, int channelIndex) {
        if (test.isRunning()) {
            test.onSuccessfulMessageSent();
        }
        String serial = Build.MODEL + "_" +  Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        PostIdBackgroundTask task = new PostIdBackgroundTask(message, configIndex, channelIndex, serial, "/dynamic/send");
        task.execute();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lastSentId.setText(message);
            }
        });
    }

    private void connectStomp() {
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, "ws://" + PostIdBackgroundTask.IP + ":" + PostIdBackgroundTask.PORT + "/sockets/websocket");
        stompClient.connect();
        stompClient.topic("/waiting").subscribe(new Consumer<StompMessage>() {
            @Override
            public void accept(StompMessage stompMessage) throws Exception {
                test.startTest();
            }
        });
        stompClient.topic("/sending").subscribe(new Consumer<StompMessage>() {
            @Override
            public void accept(StompMessage stompMessage) throws Exception {
                test.stopTest();
            }
        });
    }

}
