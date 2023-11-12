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
import com.example.customtelinkapp.model.FUCacheService;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NetworkingDevice;
import com.example.customtelinkapp.model.NodeInfo;
import com.telink.ble.mesh.core.Encipher;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.parameter.AutoConnectParameters;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.MeshLogger;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {
    protected final String TAG = getClass().getSimpleName();

    private Button btnFastProvision, btnNewMesh, btnProvision;
    public static FastProvisionDeviceAdapter fastProvisionDeviceAdapter;
    ListView lvDevices;
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1001; // Đây là request code

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lvDevices = findViewById(R.id.lv_devices);
        fastProvisionDeviceAdapter = new FastProvisionDeviceAdapter(this,R.layout.fast_provisioning_device, FastProvisionController.devices);
        lvDevices.setAdapter(fastProvisionDeviceAdapter);

//        MqttService.getInstance().connect(getApplicationContext());

        // Kiểm tra xem ứng dụng đã có quyền ACCESS_FINE_LOCATION chưa
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Yêu cầu quyền ACCESS_FINE_LOCATION từ người dùng
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
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
            MeshService.getInstance().idle(true);
            FUCacheService.getInstance().clear(this);
            MeshInfo meshInfo = TelinkMeshApplication.getInstance().createNewMesh();
            TelinkMeshApplication.getInstance().setupMesh(meshInfo);
            MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfiguration());
        });
        btnProvision.setOnClickListener(v -> {
            MeshInfo meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
            for (NetworkingDevice networkingDevice : FastProvisionController.devices) {
                Log.i(TAG, "appkey: " + meshInfo.getAppKeyStr());
                Log.i(TAG, "netkey: " + meshInfo.getNetKeyStr());
                Log.i(TAG, "devkey: " + Arrays.bytesToHexString(networkingDevice.nodeInfo.deviceKey));
                Log.i(TAG, "ivIndev: " + meshInfo.ivIndex);
                Log.i(TAG, "elementCount: " + networkingDevice.nodeInfo.elementCnt);
                Log.i(TAG, "meshADR: " + networkingDevice.nodeInfo.meshAddress);
            }
        });
    }
    public static void autoConnect() {
        MeshLogger.log("main auto connect");
        MeshInfo meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
        Log.i("TAG", "nodes size: " + meshInfo.nodes.size());
        if (meshInfo.nodes.size() == 0) {
            MeshService.getInstance().idle(true);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }
}