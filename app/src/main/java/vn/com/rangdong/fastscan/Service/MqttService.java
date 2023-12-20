package vn.com.rangdong.fastscan.Service;

import android.content.Context;
import android.util.Log;
import android.util.SparseIntArray;

import vn.com.rangdong.fastscan.DeviceListAdapter;
import vn.com.rangdong.fastscan.TelinkMeshApplication;
import vn.com.rangdong.fastscan.Util.Converter;
import vn.com.rangdong.fastscan.Util.RandomRequestIdGenerator;
import vn.com.rangdong.fastscan.model.FUCacheService;
import vn.com.rangdong.fastscan.Controller.DeviceProvisionController;
import vn.com.rangdong.fastscan.Controller.FastProvisionController;
import vn.com.rangdong.fastscan.Service.Mqtt.CustomMqttCallback;
import vn.com.rangdong.fastscan.Service.Mqtt.MqttHandler;
import vn.com.rangdong.fastscan.model.MeshInfo;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.util.MeshLogger;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class MqttService {
    public final String TAG = "MqttService";
    // mqtt config for test
//    public static final String mqttServerURI = "tcp://broker.emqx.io:1883";
//    public static final String mqttClientId = RandomRequestIdGenerator.generateRandomRequestId();
//    public static final String mqttUsername = "RD";
//    public static final String mqttPassword = "1";

    // mqtt config for hc-core
    public static final String mqttServerURI = "tcp://localhost:1883";
    public static final String mqttClientId = RandomRequestIdGenerator.generateRandomRequestId();
    ;
    public static final String mqttUsername = "RD";
    public static final String mqttPassword = "";
    //    // mqtt topic
    public static final String topicSend = "androidBle/HC";
    public static final String topicReceive = "HC/androidBle";
    DeviceProvisionController deviceProvisionController = new DeviceProvisionController();
    public static FastProvisionController fastProvisionController = new FastProvisionController();
    public static MqttService mThis = new MqttService();

    public static MqttService getInstance() {
        return mThis;
    }

    private MqttHandler mqttHandler;

    public void connect(Context context) {
        MeshLogger.i("CALL CONNECT");
        CustomMqttCallback customMqttCallback = new CustomMqttCallback() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.i(TAG, "connectComplete: " + ", " + serverURI);
                super.connectComplete(reconnect, serverURI);
                if (!reconnect) {
                    mqttHandler.subscribe(topicReceive);
                    requestBLEInfor();
                    Log.i(TAG, "Connect MQTT complete");
//                    Toast.makeText(context, "KẾT NỐI THÀNH CÔNG", Toast.LENGTH_LONG).show();
                } else {
                    mqttHandler.subscribe(topicReceive);
                    Log.i(TAG, "Disconnect to MQTT broker");
//                    Toast.makeText(context, "MẤT KẾT NỐI", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                JSONObject messageStatus = new JSONObject(String.valueOf(message));
                try {
                    handleResponse(messageStatus);
                } catch (Exception e) {
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

    public void publish(String topic, String payload) {
        mqttHandler.publish(topic, payload);
    }

    public void requestBLEInfor() {
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
            deviceObject.put("id", Converter.convertToUUID(deviceUUID).toLowerCase());
            deviceObject.put("addr", meshAdr);
            deviceObject.put("mac", macAddress.toLowerCase());

            // Tạo đối tượng JSONObject con data trong device
            JSONObject dataInnerObject = new JSONObject();
            dataInnerObject.put("deviceKey", Converter.convertToUUID(deviceKey).toLowerCase());
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

    void handleResponse(JSONObject jsonMessage) throws JSONException {
        String cmd = jsonMessage.getString("cmd");
        JSONObject data = jsonMessage.getJSONObject("data");
        String rqi = jsonMessage.getString("rqi");
        if (cmd.equals("bleInfoRsp")) {
            handleInfoBLEResponse(data);
        }
        if (cmd.equals("startScanBle")) {
            handleStartScan(jsonMessage);
        }
        if (cmd.equals("kickDevice")) {
            handleKickOut(data.getInt("meshAdr"));
        }
        if (cmd.equals("stopScanBle")) {
            handleStopScan();
        }
    }

    void handleInfoBLEResponse(JSONObject data) throws JSONException {
        int code = data.getInt("code");
        String netKey = Converter.convertToHexString(data.getString("netKey"));
        String appKey = Converter.convertToHexString(data.getString("appKey"));
        MeshLogger.i("net-key received: " + netKey);
        MeshLogger.i("app-key received: " + appKey);
        int ivIndex = data.getInt("ivIndex");
        MeshService.getInstance().idle(true);
        MeshInfo meshInfo = MeshInfo.createNewMeshFromMqtt(appKey, netKey, ivIndex);
        TelinkMeshApplication.getInstance().setupMesh(meshInfo);
        MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfiguration());
        MeshLogger.i("Update mesh info success");
    }

    void handleStartScan(JSONObject jsonMessage) throws JSONException {
        JSONObject data = jsonMessage.getJSONObject("data");
        String netKey = Converter.convertToHexString(data.getString("netKey"));
        String appKey = Converter.convertToHexString(data.getString("appKey"));
        int addProvision = data.getInt("addProvision");
        int ivIndex = data.getInt("ivIndex");
        JSONObject mapTypeElement= data.getJSONObject("mapTypeElement");
        MeshService.getInstance().idle(true);
        MeshInfo meshInfo = MeshInfo.meshForFastScan(appKey, netKey, ivIndex, addProvision);
        TelinkMeshApplication.getInstance().setupMesh(meshInfo);
        MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfiguration());
        MeshLogger.i("Update mesh info success");
        //call scan
        SparseIntArray targetDevicePid = setTargetDevicePid(mapTypeElement);
        fastProvisionController.actionStart(targetDevicePid);
        jsonMessage.getJSONObject("data").put("code", 0);
        publish(topicSend, jsonMessage.toString());
    }

    void handleResetMesh() {
        Log.i(TAG, "Create new Mesh");
        MeshService.getInstance().idle(true);
        FUCacheService.getInstance().clear(TelinkMeshApplication.getInstance());
        MeshInfo meshInfo = TelinkMeshApplication.getInstance().createNewMesh();
        TelinkMeshApplication.getInstance().setupMesh(meshInfo);
        MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfiguration());
    }

    void handleKickOut(int meshAdr) {
        Log.i(TAG, "kick device with mesh adr: " + meshAdr);
        DeviceListAdapter.kickOut(meshAdr);
    }

    void handleStopScan(){
        MeshService.getInstance().stopScan();
    }
    public void callProvisionNormal() {
        JSONObject msgProvisionNormal = new JSONObject();
        try {
            msgProvisionNormal.put("cmd", "provisionNormal");
            msgProvisionNormal.put("rqi", RandomRequestIdGenerator.generateRandomRequestId());
            msgProvisionNormal.put("data", new JSONObject());
            publish(MqttService.topicSend, msgProvisionNormal.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public SparseIntArray setTargetDevicePid (JSONObject mapTypeElement) {
        SparseIntArray targetDevicePid = new SparseIntArray();
        // Duyệt qua từng phần tử trong "mapTypeElement"
        for (Iterator<String> it = mapTypeElement.keys(); it.hasNext(); ) {
            String key = it.next();
            try {
                int value = mapTypeElement.getInt(key);
                byte[] result = Converter.convertToTwoBytes(Integer.parseInt(key));
                // Ghép 2 byte thành một chuỗi
                int demicalNumber1 = result[0];
                String hexString2 = Integer.toHexString(result[1]);
                if (hexString2.length() == 1) {
                    hexString2 = "0" + hexString2;
                }
                String resultString = String.valueOf(demicalNumber1) + hexString2;
                Log.i(TAG, "Result String: " + resultString);
                int pidKey = Integer.parseInt(resultString,16);
                targetDevicePid.put(pidKey, value);
                System.out.println("pidKey: " + pidKey + ", Value: " + value);
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
        return targetDevicePid;
    }
}
