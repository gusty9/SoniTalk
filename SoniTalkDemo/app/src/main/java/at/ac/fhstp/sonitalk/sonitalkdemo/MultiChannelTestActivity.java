package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import at.ac.fhstp.sonitalk.DynamicConfigProtocol;
import at.ac.fhstp.sonitalk.SoniTalkConfig;
import at.ac.fhstp.sonitalk.SoniTalkContext;
import at.ac.fhstp.sonitalk.SoniTalkEncoder;
import at.ac.fhstp.sonitalk.SoniTalkMessage;
import at.ac.fhstp.sonitalk.SoniTalkPermissionsResultReceiver;
import at.ac.fhstp.sonitalk.SoniTalkSender;
import at.ac.fhstp.sonitalk.utils.ConfigFactory;
import at.ac.fhstp.sonitalk.utils.ID;
import at.ac.fhstp.sonitalk.utils.RSUtils;

public class MultiChannelTestActivity extends AppCompatActivity {
    private Button channel1;
    private Button channel2;
    private Button startListen;
    private DynamicConfigProtocol protocol;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.multi_test_activity);
        try {
            SoniTalkConfig channel1 = ConfigFactory.loadFromJson("channel1.json", this);
            SoniTalkConfig channel2 = ConfigFactory.loadFromJson("channel2.json",this);
            protocol = new DynamicConfigProtocol(channel1, channel2);
        } catch (Exception e) {
            e.printStackTrace();
        }

        channel1 = findViewById(R.id.channel1);
        channel2 = findViewById(R.id.channel2);
        startListen = findViewById(R.id.start_listen);
        channel1.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                sendNewMessage(0);
            }
        });
        channel2.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                sendNewMessage(1);
            }
        });
        startListen.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                protocol.beginAudioAnalysis();
            }
        });
    }

    private void sendNewMessage(int channel) {
        String message = ID.generateRandomID();
        message = message.toUpperCase();
        message = RSUtils.getEDC(message) + message;

        protocol.sendMessage(message, channel);
    }
}
