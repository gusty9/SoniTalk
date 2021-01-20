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
    private Button button;
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
            Log.e("test", "doh");
            e.printStackTrace();
        }

        button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                String message = ID.generateRandomID();
                message = message.toUpperCase();
                message = RSUtils.getEDC(message) + message;

                protocol.sendMessage(message, 0);
                Log.e("test", "send");

            }
        });
    }
}
