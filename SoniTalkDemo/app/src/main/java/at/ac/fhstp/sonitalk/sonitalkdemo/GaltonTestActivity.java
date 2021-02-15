package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import at.ac.fhstp.sonitalk.GaltonChat;
import at.ac.fhstp.sonitalk.SoniTalkConfig;
import at.ac.fhstp.sonitalk.utils.ConfigFactory;
import at.ac.fhstp.sonitalk.utils.ID;
import at.ac.fhstp.sonitalk.utils.RSUtils;

public class GaltonTestActivity extends AppCompatActivity implements GaltonChat.MessageCallback {
    private Button startListeningButton;
    private Button sendChannel0Button;
    private Button sendChannel1Button;
    private Button sendChannel2Button;
    private Button sendAlgoButton;
    private TextView lastSentId;
    private TextView lastReceivedId;

    private GaltonChat galton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //set views
        setContentView(R.layout.galton_tester);
        startListeningButton = findViewById(R.id.start_listen);
        sendChannel0Button = findViewById(R.id.channel_0);
        sendChannel1Button = findViewById(R.id.channel_1);
        sendChannel2Button = findViewById(R.id.channel_2);
        sendAlgoButton = findViewById(R.id.algo_send);
        lastSentId = findViewById(R.id.last_generated_id);
        lastReceivedId = findViewById(R.id.last_received_id);

        //create the api object
        try {
            SoniTalkConfig channel0 = ConfigFactory.loadFromJson("channel0.json", this);
            List<SoniTalkConfig> config0 = new ArrayList<>();
            config0.add(channel0);
            SoniTalkConfig channel1 = ConfigFactory.loadFromJson("channel1.json", this);
            SoniTalkConfig channel2 = ConfigFactory.loadFromJson("channel2.json", this);
            List<SoniTalkConfig> config1 = new ArrayList<>();
            config1.add(channel1);
            config1.add(channel2);
            List<List<SoniTalkConfig>> configs = new ArrayList<>();
            configs.add(config0);
            configs.add(config1);
            galton = new GaltonChat(configs, this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //respond to inputs
        startListeningButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.startListeningThread();
            }
        });

        sendChannel0Button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.sendChannel0(generateNewMessage());
            }
        });

        sendChannel1Button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.sendChannel1(generateNewMessage());
            }
        });

        sendChannel2Button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.sendChannel2(generateNewMessage());
            }
        });

        sendAlgoButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.sendMessage(generateNewMessage());
            }
        });

    }

    private String generateNewMessage() {
        String id = ID.generateRandomID();
        lastSentId.setText(id);
        return id;
    }

    @Override
    public void onMessageReceived(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.e(GaltonChat.TAG, "successfully decoded " + message);
                lastReceivedId.setText(message);
            }
        });

    }
}
