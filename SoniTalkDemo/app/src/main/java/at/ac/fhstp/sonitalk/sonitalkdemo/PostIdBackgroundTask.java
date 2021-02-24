package at.ac.fhstp.sonitalk.sonitalkdemo;

import android.os.AsyncTask;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class PostIdBackgroundTask extends AsyncTask<String, String, String> {
    private JSONObject data;
    private String path;
    public static final String IP = "192.168.1.110";
    public static final String PORT = "8080";

    public PostIdBackgroundTask(String message, int configIndex, int channelindex, String serial, String endpoint) {
        data = new JSONObject();
        try {
            data.put("id", message);
            data.put("config", configIndex);
            data.put("channel", channelindex);
            data.put("serial", serial);//todo this should be unique
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.path = endpoint;

    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(String... strings) {
        String urlStr = "http://" + IP + ":" + PORT + path;
        OutputStream out = null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            out = new BufferedOutputStream(urlConnection.getOutputStream());
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.write(data.toString());
            writer.flush();
            writer.close();
            out.close();
            urlConnection.connect();
            int htmlCode = urlConnection.getResponseCode();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
    }

}
