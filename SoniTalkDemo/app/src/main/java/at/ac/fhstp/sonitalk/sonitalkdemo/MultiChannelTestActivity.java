package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import at.ac.fhstp.sonitalk.SoniTalkChannel;
import at.ac.fhstp.sonitalk.SoniTalkConfig;
import at.ac.fhstp.sonitalk.SoniTalkContext;
import at.ac.fhstp.sonitalk.SoniTalkPermissionsResultReceiver;
import at.ac.fhstp.sonitalk.SoniTalkChannelManager;
import at.ac.fhstp.sonitalk.utils.ConfigFactory;
import at.ac.fhstp.sonitalk.utils.ID;

public class MultiChannelTestActivity extends AppCompatActivity implements SoniTalkPermissionsResultReceiver.Receiver {

    //views
    private Button mChannelOneButton;
    private Button mChannelTwoButton;
    private SoniTalkChannelManager channelManager;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.multi_channel_layout);
        setupViews();
        setupSoniTalk();
        startSoniTalk();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        channelManager.stopListening();
    }

    @Override
    public void onSoniTalkPermissionResult(int resultCode, Bundle resultData) {
        //todo
    }

    private void setupViews() {
        //instantiate views and listeners
        mChannelOneButton = findViewById(R.id.channel_one_button);
        mChannelTwoButton = findViewById(R.id.channel_two_button);
        mChannelOneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage(1);
            }
        });
        mChannelTwoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage(2);
            }
        });
    }

    private void setupSoniTalk() {
        //create the soniTalk objects
        SoniTalkPermissionsResultReceiver resultReceiver = new SoniTalkPermissionsResultReceiver(new Handler());
        resultReceiver.setReceiver(this);
        SoniTalkContext soniTalkContext = SoniTalkContext.getInstance(this, resultReceiver);
        try {

            SoniTalkConfig config1 = ConfigFactory.loadFromJson("channel1.json", getApplicationContext());
            SoniTalkConfig config2 = ConfigFactory.loadFromJson("channel2.json", getApplicationContext());
            SoniTalkChannel channel1 = new SoniTalkChannel(config1, soniTalkContext);
            SoniTalkChannel channel2 = new SoniTalkChannel(config2, soniTalkContext );
            channelManager = new SoniTalkChannelManager(soniTalkContext,channel1, channel2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startSoniTalk() {
        try {
            channelManager.startListening();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(int channel) {
        channelManager.sendMessage(ID.generateRandomID());
    }

}
