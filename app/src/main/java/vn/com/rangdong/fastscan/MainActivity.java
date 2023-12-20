package vn.com.rangdong.fastscan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import vn.com.rangdong.fastscan.R;

import vn.com.rangdong.fastscan.Util.Converter;
import vn.com.rangdong.fastscan.model.FUCacheService;
import vn.com.rangdong.fastscan.Service.MyBleService;
import vn.com.rangdong.fastscan.model.AppSettings;
import vn.com.rangdong.fastscan.model.MeshInfo;
import vn.com.rangdong.fastscan.model.NodeInfo;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.parameter.AutoConnectParameters;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.MeshLogger;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    protected final String TAG = getClass().getSimpleName();

    private Button btnFastProvision, btnNewMesh, btnProvision;
    //    public static FastProvisionDeviceAdapter fastProvisionDeviceAdapter;
    public static DeviceListAdapter deviceListAdapter;
    RecyclerView lvDevices;
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1001; // Đây là request code

    public List<NodeInfo> listDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listDevices = TelinkMeshApplication.getInstance().getMeshInfo().nodes;
        lvDevices = findViewById(R.id.lv_devices);

        deviceListAdapter = new DeviceListAdapter(this, listDevices);
        lvDevices.setAdapter(deviceListAdapter);
        lvDevices.setLayoutManager(new LinearLayoutManager(getApplication()));
        deviceListAdapter.notifyDataSetChanged();
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
        btnFastProvision = findViewById(R.id.btnFastProvision);
        btnNewMesh = findViewById(R.id.btnNewMesh);
        btnProvision = findViewById(R.id.btnProvision);
        btnFastProvision.setOnClickListener(v -> {
            startService(new Intent(this, MyBleService.class));
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
//            Log.i(TAG, "onCreate: " + Arrays.bytesToHexString([0x]));
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

    public static void resetUI() {
        deviceListAdapter.notifyDataSetChanged();
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