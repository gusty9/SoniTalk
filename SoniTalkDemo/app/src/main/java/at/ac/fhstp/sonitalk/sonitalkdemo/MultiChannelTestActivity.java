package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import at.ac.fhstp.sonitalk.SoniTalkConfig;
import at.ac.fhstp.sonitalk.SoniTalkContext;
import at.ac.fhstp.sonitalk.SoniTalkEncoder;
import at.ac.fhstp.sonitalk.SoniTalkMessage;
import at.ac.fhstp.sonitalk.SoniTalkPermissionsResultReceiver;
import at.ac.fhstp.sonitalk.SoniTalkSender;
import at.ac.fhstp.sonitalk.utils.ConfigFactory;
import at.ac.fhstp.sonitalk.utils.ID;
import at.ac.fhstp.sonitalk.utils.RSUtils;

public class MultiChannelTestActivity extends AppCompatActivity implements SoniTalkPermissionsResultReceiver.Receiver {
    private Button button;
    private SoniTalkPermissionsResultReceiver resultReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.multi_test_activity);
        resultReceiver = new SoniTalkPermissionsResultReceiver(new Handler());
        resultReceiver.setReceiver(this);
        button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                try {
                    SoniTalkConfig config = ConfigFactory.getDefaultConfig(getApplicationContext());
                    SoniTalkContext context = SoniTalkContext.getInstance(getApplicationContext(), resultReceiver);
                    SoniTalkEncoder encoder = context.getEncoder(config);
                    String message = ID.generateRandomID();
                    message = message.toUpperCase();
                    message = RSUtils.getEDC(message) + message;
                    SoniTalkMessage msg = encoder.messageAsHexByteString(message);
                    SoniTalkSender sender = context.getSender();
                    sender.send(msg, 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onSoniTalkPermissionResult(int resultCode, Bundle resultData) {

    }
}
