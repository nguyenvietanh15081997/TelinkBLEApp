package com.example.customtelinkapp.Service;

import static com.example.customtelinkapp.Util.Converter.convertToHexString;
import static com.example.customtelinkapp.Util.Converter.convertToUUID;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.example.customtelinkapp.Controller.DeviceProvisionController;
import com.example.customtelinkapp.Service.Mqtt.CustomMqttCallback;
import com.example.customtelinkapp.Service.Mqtt.MqttHandler;
import com.example.customtelinkapp.TelinkMeshApplication;
import com.example.customtelinkapp.Util.RandomRequestIdGenerator;
import com.example.customtelinkapp.model.MeshInfo;
import com.telink.ble.mesh.foundation.MeshConfiguration;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.parameter.ScanParameters;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.MeshLogger;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MqttService {
    public final String TAG = "MqttService";
//    // mqtt config for test
//    public static final String mqttServerURI = "tcp://broker.emqx.io:1883";
//    public static final String mqttClientId = "client1502@00";
//    public static final String mqttUsername = "RD";
//    public static final String mqttPassword = "1";
//    // mqtt topic
//    public static final String topicSend = "RD_CONTROL";
//    public static final String topicReceive = "RD_STATUS";
    // mqtt config for real
    public static final String mqttServerURI = "tcp://localhost:1883";
    public static final String mqttClientId = "android-client";
    public static final String mqttUsername = "RD";
    public static final String mqttPassword = "";
    // mqtt topic
    public static final String topicSend = "device/androidBle";
    public static final String topicReceive = "HC/androidBle";
    DeviceProvisionController deviceProvisionController = new DeviceProvisionController();
    public static MqttService mThis = new MqttService();
    public static MqttService getInstance(){
        return mThis;
    }
    private MqttHandler mqttHandler;
    public int gwAdr = 0;
    public void connect (Context context) {
        MeshLogger.i("CALL CONNECT");
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
            msgRequestBLEInfor.put("rqi", RandomRequestIdGenerator.generateRandomRequestId());
            msgRequestBLEInfor.put("data", new JSONObject());
            publish(MqttService.topicSend, msgRequestBLEInfor.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public void sendBindedDeviceInfo(String deviceUUID, String macAddress, String deviceKey, int vid, int pid, int meshAdr) {
        JSONObject msgBindedDeviceInfo = new JSONObject();
        try {
            msgBindedDeviceInfo.put("cmd", "newDev");
            msgBindedDeviceInfo.put("rqi", RandomRequestIdGenerator.generateRandomRequestId());

            // Tạo đối tượng JSONObject con data
            JSONObject dataObject = new JSONObject();
            // Tạo mảng device
            JSONArray deviceArray = new JSONArray();

            // Tạo đối tượng JSONObject con trong mảng device
            JSONObject deviceObject = new JSONObject();
            deviceObject.put("id", convertToUUID(deviceUUID));
            deviceObject.put("addr", meshAdr);
            deviceObject.put("mac", macAddress);

            // Tạo đối tượng JSONObject con data trong device
            JSONObject dataInnerObject = new JSONObject();
            dataInnerObject.put("devKey", convertToUUID(deviceKey));
            dataInnerObject.put("vid", vid); // Giá trị int tùy chọn
            dataInnerObject.put("pid", pid); // Giá trị int tùy chọn

            // Thêm đối tượng dataInnerObject vào deviceObject
            deviceObject.put("data", dataInnerObject);

            // Thêm deviceObject vào mảng device
            deviceArray.put(deviceObject);

            // Thêm mảng device vào đối tượng data
            dataObject.put("device", deviceArray);

            // Thêm đối tượng data vào đối tượng gốc
            msgBindedDeviceInfo.put("data", dataObject);
            MeshLogger.i(msgBindedDeviceInfo.toString());
            publish(MqttService.topicSend, msgBindedDeviceInfo.toString());
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
            handleStartScan(jsonMessage);
        }
    }
    void handleInfoBLEResponse(JSONObject data) throws JSONException {
        int code = data.getInt("code");
        String netKey = convertToHexString(data.getString("netKey"));
        String appKey = convertToHexString(data.getString("appKey"));
        MeshLogger.i("net-key received: " + netKey);
        MeshLogger.i("app-key received: " + appKey);
        int ivIndex = data.getInt("ivIndex");
        int addrGw = data.getInt("addrGw");
        gwAdr = addrGw;
        MeshService.getInstance().idle(true);
        MeshInfo meshInfo = MeshInfo.createNewMeshFromMqtt(appKey,netKey,ivIndex);
        TelinkMeshApplication.getInstance().setupMesh(meshInfo);
        MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfiguration());
        MeshLogger.i("Update mesh info success");
    }
    void handleStartScan(JSONObject jsonMessage) throws JSONException {
        deviceProvisionController.startScan();
        jsonMessage.getJSONObject("data").put("code" , 0);
        publish(topicSend, jsonMessage.toString());
    }
}
