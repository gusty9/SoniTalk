package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import at.ac.fhstp.sonitalk.GaltonChat;
import at.ac.fhstp.sonitalk.SoniTalkConfig;
import at.ac.fhstp.sonitalk.utils.ConfigFactory;
import at.ac.fhstp.sonitalk.utils.ID;
import at.ac.fhstp.sonitalk.utils.RSUtils;

public class GaltonTestActivity extends AppCompatActivity {
    private Button startListeningButton;
    private Button sendChannel0Button;
    private Button sendChannel1Button;
    private Button sendAlgoButton;

    private GaltonChat galton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //set views
        setContentView(R.layout.galton_tester);
        startListeningButton = findViewById(R.id.start_listen);
        sendChannel0Button = findViewById(R.id.channel_0);
        sendChannel1Button = findViewById(R.id.channel_1);
        sendAlgoButton = findViewById(R.id.algo_send);

        //create the api object
        try {
            SoniTalkConfig channel1 = ConfigFactory.loadFromJson("channel1.json", this);
            SoniTalkConfig channel2 = ConfigFactory.loadFromJson("channel2.json", this);
            galton = new GaltonChat(channel1, channel2);
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

        sendAlgoButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                galton.sendMessage(generateNewMessage());
            }
        });

    }

    private String generateNewMessage() {
        return ID.generateRandomID();
    }
}
