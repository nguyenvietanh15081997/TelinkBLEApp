package com.example.customtelinkapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ListView;

import com.example.customtelinkapp.Controller.FastProvisionController;
import com.example.customtelinkapp.Service.MqttService;
import com.example.customtelinkapp.Service.MyBleService;
import com.example.customtelinkapp.model.AppSettings;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NodeInfo;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.parameter.AutoConnectParameters;
import com.telink.ble.mesh.util.MeshLogger;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {
    protected final String TAG = getClass().getSimpleName();

    private Button btnFastProvision, btnNewMesh, btnProvision;
    public static FastProvisionDeviceAdapter fastProvisionDeviceAdapter;
    ListView lvDevices;
    private String namespaceUUID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lvDevices = findViewById(R.id.lv_devices);
        fastProvisionDeviceAdapter = new FastProvisionDeviceAdapter(this,R.layout.fast_provisioning_device, FastProvisionController.devices);
        lvDevices.setAdapter(fastProvisionDeviceAdapter);

//        MqttService.getInstance().connect(getApplicationContext());

        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 3);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 3);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 3);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 3);
        }
//        startService(new Intent( this, MyBleService.class ) );
        btnFastProvision = findViewById(R.id.btnFastProvision);
        btnNewMesh = findViewById(R.id.btnNewMesh);
        btnProvision = findViewById(R.id.btnProvision);
        btnFastProvision.setOnClickListener(v -> {
            startService(new Intent( this, MyBleService.class ) );
        });
        btnNewMesh.setOnClickListener(v -> {
            Log.i(TAG, "Create new Mesh");
            TelinkMeshApplication.getInstance().createNewMesh();
        });
        btnProvision.setOnClickListener(v -> {
            md5("A4:C1:38:9B:98:21", namespaceUUID);
        });
    }
    public static void autoConnect() {
        MeshLogger.log("main auto connect");
        MeshInfo meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
        if (meshInfo.nodes.size() == 0) {
            MeshService.getInstance().idle(true);
            MeshService.getInstance().autoConnect(new AutoConnectParameters());
        } else {
            int directAdr = MeshService.getInstance().getDirectConnectedNodeAddress();
            NodeInfo nodeInfo = meshInfo.getDeviceByMeshAddress(directAdr);
            if (nodeInfo != null && nodeInfo.compositionData != null && nodeInfo.compositionData.pid == AppSettings.PID_REMOTE) {
                // if direct connected device is remote-control, disconnect
                MeshService.getInstance().idle(true);
            }
            MeshService.getInstance().autoConnect(new AutoConnectParameters());
        }

    }

    private void md5(String macAddress, String uuid){
        try {
            // Convert the MAC address to bytes
            byte[] macBytes = macAddress.replace(":", "").getBytes();
            Log.i(TAG, "mac: "+ Arrays.toString(macBytes));
//            byte[] macBytes = com.telink.ble.mesh.util.Arrays.hexToBytes(macAddress.replace(":", ""));
            Log.i(TAG, "mac Using util: " + Arrays.toString(com.telink.ble.mesh.util.Arrays.hexToBytes(macAddress.replace(":", ""))));
            // Convert the UUID to bytes
            byte[] uuidBytes = uuid.toString().getBytes();
//            byte[] uuidBytes = com.telink.ble.mesh.util.Arrays.hexToBytes(uuid.replace("-", ""));

            Log.i(TAG, "uuid namespace: " + Arrays.toString(uuidBytes));
            Log.i(TAG, "uuid using util: " + Arrays.toString(com.telink.ble.mesh.util.Arrays.hexToBytes(uuid.replace("-", ""))));
            // Create a SecretKeySpec object with the UUID as the key
            SecretKeySpec secretKeySpec = new SecretKeySpec(uuidBytes, "HmacMD5");

            // Create a Mac object with the HMAC-MD5 algorithm
            Mac mac = Mac.getInstance("HmacMD5");
            mac.init(secretKeySpec);

            // Compute the HMAC
            byte[] hmac = mac.doFinal(macBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hmac) {
                sb.append(String.format("%02x", b & 0xff));
            }

            String hmacHash = sb.toString();
            Log.i(TAG, "md5: " + "HMAC-MD5 hash of MAC address \"" + macAddress + "\" and UUID \"" + uuid + "\" is: " + hmacHash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }
}