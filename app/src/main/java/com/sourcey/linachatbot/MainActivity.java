package com.sourcey.linachatbot;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;

import javax.net.ssl.SSLContext;

import co.intentservice.chatui.ChatView;
import co.intentservice.chatui.models.ChatMessage;


public class MainActivity extends AppCompatActivity implements OnTaskCompleted {
    private static final int REQUEST_AUTHENTICATION = 0;
    ChatView chatView;
    getResponse getResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.primary)));
        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, 0);
    }

    private void setGetResponse(String token) {
        Uri.Builder webSocketUriBuilder = new Uri.Builder();
        webSocketUriBuilder.scheme("wss")
                .authority("linachatbot.herokuapp.com")
                .appendPath("api")
                .appendPath("chat")
                .appendQueryParameter("token", token);
        URI webSocketUri = null;
        try {
            webSocketUri = new URI(webSocketUriBuilder.toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        getResponse = new getResponse(webSocketUri, MainActivity.this, this);
        if (webSocketUriBuilder.toString().indexOf("wss") == 0) {
            try {
                SSLContext sslContext = SSLContext.getDefault();
                getResponse.setWebSocketFactory(new DefaultSSLWebSocketClientFactory(sslContext));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        getResponse.connect();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_AUTHENTICATION && resultCode == RESULT_OK && data != null) {
            Log.v("token", data.getStringExtra("token"));
            chatView = (ChatView) findViewById(R.id.chat_view);

            final String token = data.getStringExtra("token");
            getOldMessages getToken = new getOldMessages();
            getToken.execute("jwt " + token, "4");
            setGetResponse(token);
            chatView.setOnSentMessageListener(new ChatView.OnSentMessageListener() {

                @Override
                public boolean sendMessage(ChatMessage chatMessage) {
                    if (getResponse.open) {
                        JSONObject messageJSON = new JSONObject();
                        try {
                            messageJSON.put("command", "send");
                            messageJSON.put("message", chatMessage.getMessage());
                            messageJSON.put("token", token);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(getBaseContext(), "failed to send message", Toast.LENGTH_LONG).show();
                            return false;
                        }
                        getResponse.send(messageJSON.toString());
                        return true;
                    } else {
                        setGetResponse(token);
                        Toast.makeText(getBaseContext(), "failed to send message", Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTaskCompleted(DefaultHashMap<String, String> data) {
        String messageText = data.get("message");
        final ChatMessage message = new ChatMessage(messageText, System.nanoTime(), ChatMessage.Type.RECEIVED);
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                chatView.addMessage(message);
            }
        });
    }

    public class getOldMessages extends AsyncTask<String, Void, ArrayList<ChatMessage>> {

        private final String LOG_TAG = getOldMessages.class.getSimpleName();

        private ArrayList<ChatMessage> getOldMessagesFromJson(String oldMessagesJsonStr) throws JSONException, IOException, ParseException {
            JSONObject oldMessagesJsonObj = new JSONObject(oldMessagesJsonStr);
            JSONArray oldMessagesJsonArray = oldMessagesJsonObj.getJSONArray("results");
            ArrayList<ChatMessage> oldMessages = new ArrayList<>(oldMessagesJsonArray.length());

            for (int i = 0; i < oldMessagesJsonArray.length(); i++) {
                JSONObject oldMessage = oldMessagesJsonArray.getJSONObject(i);

                String messageText = oldMessage.getString("message");
                String humanUser = oldMessage.getString("owner");
                String messageTime = oldMessage.getString("timestamp");
                ChatMessage.Type messageType = ChatMessage.Type.RECEIVED;
                if (humanUser.equals("user")) {
                    messageType = ChatMessage.Type.SENT;
                }
                oldMessages.add(new ChatMessage(messageText, System.nanoTime(), messageType));
            }
            Collections.reverse(oldMessages);
            return oldMessages;
        }

        protected ArrayList<ChatMessage> doInBackground(String... params) {
            String token = params[0];
            String oldMessagesRetrieveLimit = params[1];
            StringBuilder sb = new StringBuilder();
            String oldMessagesJsonStr = "[]";
            Uri.Builder chatHistoryUrl = new Uri.Builder();
            HttpURLConnection urlConnection = null;
            try {
                chatHistoryUrl.scheme("https")
                        .authority("linachatbot.herokuapp.com")
                        .appendPath("api")
                        .appendPath("chat")
                        .appendPath("messages")
                        .appendQueryParameter("limit", oldMessagesRetrieveLimit);
                URL url = new URL(chatHistoryUrl.toString());
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setRequestMethod("GET");
                urlConnection.setUseCaches(false);
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);
                urlConnection.setRequestProperty("Authorization", token);
                urlConnection.connect();

                int HttpResult = urlConnection.getResponseCode();
                if (HttpResult == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "utf-8"));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                    oldMessagesJsonStr = sb.toString();
                } else {
                    Log.i(LOG_TAG, urlConnection.getResponseMessage());
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
            try {
                return getOldMessagesFromJson(oldMessagesJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            } catch (ParseException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<ChatMessage> oldMessages) {
            if (oldMessages != null) {
                chatView.addMessages(oldMessages);
            }
        }
    }
}
