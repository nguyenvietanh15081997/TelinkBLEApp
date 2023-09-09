package com.example.customtelinkapp.Service;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.example.customtelinkapp.Service.Mqtt.CustomMqttCallback;
import com.example.customtelinkapp.Service.Mqtt.MqttHandler;
import com.telink.ble.mesh.foundation.MeshConfiguration;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.parameter.ScanParameters;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.MeshLogger;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

public class MqttService {
    public final String TAG = "MqttService";
    // mqtt config
    public static final String mqttServerURI = "tcp://broker.emqx.io:1883";
    public static final String mqttClientId = "android-client";
    public static final String mqttUsername = "RD";
    public static final String mqttPassword = "1";
    // mqtt topic
    public static final String topicSend = "RD_CONTROL";
    public static final String topicReceive = "RD_STATUS";

    public static MqttService mThis = new MqttService();
    public static MqttService getInstance(){
        return mThis;
    }
    private MqttHandler mqttHandler;
    public void connect (Context context) {
        CustomMqttCallback customMqttCallback = new CustomMqttCallback(){
            @Override
            public void connectComplete(boolean reconnect, String serverURI){
                Log.i(TAG, "connectComplete: "  + ", " + serverURI);
                super.connectComplete(reconnect,serverURI);
                if(!reconnect){
                    mqttHandler.subscribe(topicReceive);
                    requestBLEInfor();
                    Toast.makeText(context,"KẾT NỐI THÀNH CÔNG", Toast.LENGTH_LONG).show();
                }
                else{
                    mqttHandler.subscribe(topicReceive);
                    Toast.makeText(context,"MẤT KẾT NỐI", Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void messageArrived(String topic , MqttMessage message) throws Exception{
                JSONObject messageStatus = new JSONObject(String.valueOf(message));
                try {
                    handleResponse(messageStatus);
                }
                catch (Exception e) {
                    MeshLogger.i("Handle mqtt res error: " + e.getMessage());
                }
                String payload = new String(message.getPayload());
                Log.i(TAG, "messageArrived from mqtt: " + payload);
            }
        };
        mqttHandler = new MqttHandler(context, mqttServerURI, mqttClientId, mqttUsername, mqttPassword);
        mqttHandler.setCustomMqttCallback(customMqttCallback);
        mqttHandler.connect();
    }
    public void publish (String topic, String payload){
        mqttHandler.publish(topic, payload);
    }
    public void requestBLEInfor () {
        JSONObject msgRequestBLEInfor = new JSONObject();
        try {
            msgRequestBLEInfor.put("cmd", "bleInfo");
            msgRequestBLEInfor.put("rqi", "abc-xyz");
            msgRequestBLEInfor.put("data", new JSONObject());
            publish("RD_CONTROL", msgRequestBLEInfor.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    void handleResponse (JSONObject jsonMessage) throws JSONException{
        String cmd = jsonMessage.getString("cmd");
        JSONObject data = jsonMessage.getJSONObject("data");
        String rqi = jsonMessage.getString("rqi");
        if(cmd.equals("bleInfoRsp")){
            handleInfoBLEResponse(data);
        }
        if(cmd.equals("startScanBle")){
            ScanParameters parameters = ScanParameters.getDefault(false, false);
            Log.i(TAG, "startScan: " + parameters);
            parameters.setScanTimeout(10 * 1000);
            MeshService.getInstance().startScan(parameters);
            jsonMessage.getJSONObject("data").put("code" , 0);
            publish(topicSend, jsonMessage.toString());
        }
    }
    void handleInfoBLEResponse(JSONObject data) throws JSONException {
        int code = data.getInt("code");
        String netKey = convertToHexString(data.getString("netKey"));
        String appKey = convertToHexString(data.getString("appkey"));
        MeshLogger.i("net-key received: " + netKey);
        MeshLogger.i("app-key received: " + appKey);

        String ivIndex = data.getString("ivIndex");
        int addrGw = data.getInt("addrGw");

    }

    public static String convertToHexString(String input) {
        String output = input.replace("-", "").toUpperCase();
        return output;
    }
}
