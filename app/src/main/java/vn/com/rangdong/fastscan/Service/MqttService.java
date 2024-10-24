package vn.com.rangdong.fastscan.Service;

import android.content.Context;
import android.os.Build;
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

import java.net.NetworkInterface;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class MqttService {
    public final String TAG = "MqttService";
    // mqtt config for test
//    public static final String mqttServerURI = "tcp://broker.emqx.io:1883";
//    public static final String mqttClientId = RandomRequestIdGenerator.generateRandomRequestId();
//    public static final String mqttUsername = "RD";
//    public static final String mqttPassword = "1";
    private static final String RD_HC_KEY="RANGDONGRALSMART";
    private static final String RD_HC_PREFIX="2804";
    // mqtt config for hc-core
    public static final String mqttServerURI = "tcp://localhost:1883";
    public static final String mqttClientId = RandomRequestIdGenerator.generateRandomRequestId();
    ;
    public static final String mqttUsername = "RD";
    public static String mqttPassword = "";
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

    public void connect(Context context) throws Exception {
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
        mqttPassword = base64ToHex(encrypt(RD_HC_KEY, RD_HC_PREFIX)).toUpperCase();
        Log.d("info", "HCCoreMqttClient | connect, password: " + mqttPassword);
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
            dataInnerObject.put("devicekey", Converter.convertToUUID(deviceKey).toLowerCase());
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
        Log.i(TAG, "addProvision: " + addProvision);
        int ivIndex = data.getInt("ivIndex");
        JSONObject mapTypeElement= data.getJSONObject("mapTypeElement");
        MeshService.getInstance().idle(true);
        MeshInfo meshInfo = MeshInfo.meshForFastScan(appKey, netKey, ivIndex, addProvision);
        TelinkMeshApplication.getInstance().setupMesh(meshInfo);
        MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfigurationWithLocalAddress(addProvision));
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
        MeshService.getInstance().idle(true);
        MeshInfo meshInfo = TelinkMeshApplication.getInstance().createNewMesh();
        TelinkMeshApplication.getInstance().setupMesh(meshInfo);
        MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfiguration());
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
                JSONArray value = mapTypeElement.getJSONArray(key);
                // Duyệt qua từng phần tử trong JSONArray
                for (int i = 0; i < value.length(); i++) {
                    int element = value.getInt(i);
                    int result = Converter.convertToThreeBytes(Integer.parseInt(String.valueOf(element)));
                    targetDevicePid.put(result, Integer.parseInt(key));
                    System.out.println("pidKey: " + result + ", Value: " + key);
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
        return targetDevicePid;
    }

    public String encrypt(String key, String prefix) throws Exception {
        String macAddr = getMacAddr();
        Log.d("info", "HCCoreMqttClient | encrypt, macAddress: " + macAddr);
        String plaintext = prefix + macAddr.replace(":","");
        plaintext = plaintext.toLowerCase();
        Log.d("info", "HCCoreMqttClient | encrypt, plaintext: " + plaintext);
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes());
        String encryptedString = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            encryptedString = Base64.getEncoder().encodeToString(encryptedBytes);
        }
        Log.d("info", "HCCoreMqttClient | encrypt, encryptedString: " + encryptedString);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Base64.getEncoder().encodeToString(encryptedBytes);
        }
        return "";
    }
    public String base64ToHex(String base64) {
        byte[] bytes = new byte[0];
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            bytes = Base64.getDecoder().decode(base64);
        }
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                Log.d("info", "dcMac: "+nif.getName());
                if (!nif.getName().equalsIgnoreCase("eth0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                Log.d("info", "HardwareAddress: " + macBytes);
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    // res1.append(Integer.toHexString(b & 0xFF) + ":");
                    res1.append(String.format("%02X:",b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
            //handle exception
        }
        return "";
    }
}
