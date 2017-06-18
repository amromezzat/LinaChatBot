package com.sourcey.linachatbot;

import android.app.Activity;
import android.util.Log;

import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

/**
 * Created by amrezzat on 3/18/2017.
 */

public class getResponse extends WebSocketClient {

    private final String LOG_TAG = getResponse.class.getSimpleName();
    private OnTaskCompleted listener;
    private Activity activity;
    Boolean open = false;
    private URI serverUri;


    public getResponse(URI serverUri, Draft draft, OnTaskCompleted listener, Activity activity) {
        super(serverUri, draft);
        this.listener = listener;
        this.activity = activity;
        this.serverUri = serverUri;
    }

    public getResponse(URI serverURI, OnTaskCompleted listener, Activity activity) {
        super(serverURI);
        this.listener = listener;
        this.activity = activity;
        this.serverUri = serverUri;
    }

    @Override
    public void onOpen(ServerHandshake handShakeData) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new CustomToast(activity, "Connected", true);
            }
        });
        Log.i(LOG_TAG, "opened connection");
        open = true;

    }

    @Override
    public void onMessage(String message) {
        Log.i(LOG_TAG, "received: " + message);
        JSONObject replyJSON;
        try {
            replyJSON = new JSONObject(message);

            String owner = replyJSON.getString("owner");
            if (owner.equals("bot")) {
                String type = replyJSON.getString("type");
                String replyMsg = replyJSON.getString("msg");
                String messageTime = replyJSON.getString("formated_timestamp");
                DefaultHashMap<String, String> data = new DefaultHashMap<>("");
                if (type.equals("intent")) {
                    JSONArray intentData = replyJSON.getJSONArray("intent_data");
                    for(int i=0;i<intentData.length();i+=2){
                        data.put(intentData.getString(i), intentData.getString(i+1));
                    }
                }
                data.put("type", type);
                data.put("formattedTime", messageTime);
                data.put("message", replyMsg);

                listener.onTaskCompleted(data);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new CustomToast(activity, "Disconnected", true);
            }
        });
        Log.i(LOG_TAG, "Connection closed by " + (remote ? "remote peer" : "us") + " with code " + code);
        open = false;
        if (serverUri != null) {
            getResponse response = new getResponse(serverUri, listener, activity);
            if (serverUri.toString().indexOf("wss") == 0) {
                try {
                    SSLContext sslContext = SSLContext.getDefault();
                    response.setWebSocketFactory(new DefaultSSLWebSocketClientFactory(sslContext));
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
            response.connect();
        }
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
        // if the error is fatal then onClose will be called additionally
    }
}
