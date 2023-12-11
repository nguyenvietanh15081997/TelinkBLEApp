package vn.com.rangdong.fastscan.Service.Mqtt;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttHandler {
    private static final String TAG = "MqttHandler";
    private MqttAndroidClient mqttAndroidClient;
    private String serverUri;
    private String clientId;
    private String username;
    private String password;
    private static boolean isConnected;
    private CustomMqttCallback customMqttCallback;

    public MqttHandler(Context context, String serverUri, String clientId, String username, String password) {
        this.serverUri = serverUri;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.isConnected = false;
        mqttAndroidClient = new MqttAndroidClient(context, serverUri, clientId);
        customMqttCallback = new CustomMqttCallback();
        mqttAndroidClient.setCallback(customMqttCallback);

    }

    public void connect() {
        try {
            if (!mqttAndroidClient.isConnected()) {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setUserName(username);
                options.setPassword(password.toCharArray());
                options.setAutomaticReconnect(true);
                options.setCleanSession(false);
                IMqttToken token = mqttAndroidClient.connect(options);
                token.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "Connect success");
                        isConnected = true;
//                        subscribe(MqttService.topicReceive);
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.w(TAG, "Connect failure: " + exception.getMessage());
                        isConnected = false;
                        connect();
                    }
                });
            } else {
                Log.i(TAG, "Client is connected");
            }
        } catch (Exception e) {
            Log.e(TAG, "Connect exception: " + e.getMessage());
            isConnected = false;
            connect();
        }
    }

    public void disconnect() {
        try {
            Log.i(TAG, "disconnect: disconnecting");
            mqttAndroidClient.disconnect();
            isConnected = false;
            Log.i(TAG, "disconnect: disconnected");

        } catch (Exception e) {
            Log.e(TAG, "Disconnect exception: " + e.getMessage());
        }
    }

    public void subscribe(final String topic) {
        try {
            mqttAndroidClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Subscribe success to topic: " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w(TAG, "Subscribe failure: " + exception.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Subscribe exception: " + e.getMessage());
        }
    }

    public void unsubscribe(String topic) {
        try {
            mqttAndroidClient.unsubscribe(topic, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Unsubscribe success");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w(TAG, "Unsubscribe failure: " + exception.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unsubscribe exception: " + e.getMessage());
        }
    }

    public void publish(String topic, String payload) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(0);
            mqttAndroidClient.publish(topic, message);
            Log.d(TAG, "Publish success");
        } catch (Exception e) {
            Log.e(TAG, "Publish exception: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setCustomMqttCallback(CustomMqttCallback customMqttCallback) {
        this.customMqttCallback = customMqttCallback;
        mqttAndroidClient.setCallback(customMqttCallback);
    }
}
