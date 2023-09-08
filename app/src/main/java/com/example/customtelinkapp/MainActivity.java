package com.example.customtelinkapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.customtelinkapp.Controller.DeviceProvisionController;
import com.example.customtelinkapp.model.AppSettings;
import com.example.customtelinkapp.model.FUCache;
import com.example.customtelinkapp.model.FUCacheService;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NetworkingDevice;
import com.example.customtelinkapp.model.NetworkingState;
import com.example.customtelinkapp.model.NodeInfo;
import com.example.customtelinkapp.model.OnlineState;
import com.example.customtelinkapp.model.SharedPreferenceHelper;
import com.example.customtelinkapp.model.UnitConvert;
import com.telink.ble.mesh.core.MeshUtils;
import com.telink.ble.mesh.core.message.MeshMessage;
import com.telink.ble.mesh.core.message.NotificationMessage;
import com.telink.ble.mesh.core.message.config.ModelPublicationStatusMessage;
import com.telink.ble.mesh.core.message.firmwaredistribution.DistributionPhase;
import com.telink.ble.mesh.core.message.firmwaredistribution.FDCancelMessage;
import com.telink.ble.mesh.core.message.firmwaredistribution.FDStatusMessage;
import com.telink.ble.mesh.core.message.firmwareupdate.DistributionStatus;
import com.telink.ble.mesh.core.message.generic.OnOffGetMessage;
import com.telink.ble.mesh.core.message.time.TimeSetMessage;
import com.telink.ble.mesh.entity.AdvertisingDevice;
import com.telink.ble.mesh.entity.BindingDevice;
import com.telink.ble.mesh.foundation.Event;
import com.telink.ble.mesh.foundation.EventListener;
import com.telink.ble.mesh.foundation.MeshConfiguration;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.event.AutoConnectEvent;
import com.telink.ble.mesh.foundation.event.BindingEvent;
import com.telink.ble.mesh.foundation.event.MeshEvent;
import com.telink.ble.mesh.foundation.event.ProvisioningEvent;
import com.telink.ble.mesh.foundation.event.ScanEvent;
import com.telink.ble.mesh.foundation.event.StatusNotificationEvent;
import com.telink.ble.mesh.foundation.parameter.AutoConnectParameters;
import com.telink.ble.mesh.foundation.parameter.ScanParameters;
import com.telink.ble.mesh.util.MeshLogger;

public class MainActivity extends BaseActivity implements EventListener<String> {
    private Handler mHandler = new Handler();
    /**
     * local mesh info
     */
    private MeshInfo mesh;
    public DeviceProvisionController deviceProvisionController;
    private static final int OP_VENDOR_GET = 0x0211E0;

    private static final int OP_VENDOR_STATUS = 0x0211E1;

    Button btnScan, btnProvision, btnType;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 3);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 3);
        }

        btnScan = (Button) findViewById(R.id.btnScan);
        btnProvision = (Button) findViewById(R.id.btnProvision);
        btnType = (Button) findViewById(R.id.btnType);

        TelinkMeshApplication.getInstance().addEventListener(AutoConnectEvent.EVENT_TYPE_AUTO_CONNECT_LOGIN, this);
        TelinkMeshApplication.getInstance().addEventListener(MeshEvent.EVENT_TYPE_DISCONNECTED, this);
        TelinkMeshApplication.getInstance().addEventListener(MeshEvent.EVENT_TYPE_MESH_EMPTY, this);
        TelinkMeshApplication.getInstance().addEventListener(FDStatusMessage.class.getName(), this);
        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_BEGIN, this);
        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_SUCCESS, this);
        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_FAIL, this);
        TelinkMeshApplication.getInstance().addEventListener(BindingEvent.EVENT_TYPE_BIND_SUCCESS, this);
        TelinkMeshApplication.getInstance().addEventListener(BindingEvent.EVENT_TYPE_BIND_FAIL, this);
        TelinkMeshApplication.getInstance().addEventListener(ScanEvent.EVENT_TYPE_SCAN_TIMEOUT, this);
        TelinkMeshApplication.getInstance().addEventListener(ScanEvent.EVENT_TYPE_DEVICE_FOUND, this);
        TelinkMeshApplication.getInstance().addEventListener(ModelPublicationStatusMessage.class.getName(), this);
        mesh = TelinkMeshApplication.getInstance().getMeshInfo();
        startMeshService();

        deviceProvisionController = new DeviceProvisionController();

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceProvisionController.startScan();
            }
        });
        btnProvision.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(deviceProvisionController.addAll()){
                    deviceProvisionController.provisionNext();
                }
            }
        });
        btnType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for(NetworkingDevice device : deviceProvisionController.devices){
                    if(device.nodeInfo.macAddress.equalsIgnoreCase("A4:C1:38:37:7B:ED")){
//                        sendTypeAsk();
                    }
                }
            }
        });
    }
    /**
     * init and setup mesh network
     */
    private void startMeshService() {
        // init
        MeshService.getInstance().init(this, TelinkMeshApplication.getInstance());

        // convert meshInfo to mesh configuration
        Log.i("TAG", "startMeshService: " + TelinkMeshApplication.getInstance());
        MeshConfiguration meshConfiguration = TelinkMeshApplication.getInstance().getMeshInfo().convertToConfiguration();
        MeshService.getInstance().setupMeshNetwork(meshConfiguration);

        // check if system bluetooth enabled
        MeshService.getInstance().checkBluetoothState();
        /// set DLE enable
        MeshService.getInstance().resetExtendBearerMode(SharedPreferenceHelper.getExtendBearerMode(this));

    }
    private void resetNodeState() {
        MeshInfo mesh = TelinkMeshApplication.getInstance().getMeshInfo();
        if (mesh.nodes != null) {
            for (NodeInfo deviceInfo : mesh.nodes) {
                deviceInfo.setOnlineState(OnlineState.OFFLINE);
                deviceInfo.lum = 0;
                deviceInfo.temp = 0;
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        TelinkMeshApplication.getInstance().removeEventListener(this);
        MeshService.getInstance().clear();
    }


    @Override
    protected void onResume() {
        super.onResume();
        this.autoConnect();
//        showMeshOTATipsDialog(2);
    }
    private void autoConnect() {
        MeshLogger.log("main auto connect");
        MeshInfo meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
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
    public void performed(Event<String> event) {
        super.performed(event);
        Log.i(TAG, "performed: " + event.getType());
        if (event.getType().equals(MeshEvent.EVENT_TYPE_MESH_EMPTY)) {
            MeshLogger.log(TAG + "#EVENT_TYPE_MESH_EMPTY");
        } else if (event.getType().equals(AutoConnectEvent.EVENT_TYPE_AUTO_CONNECT_LOGIN)) {
            // get all device on off status when auto connect success
            AppSettings.ONLINE_STATUS_ENABLE = MeshService.getInstance().getOnlineStatus();
            if (!AppSettings.ONLINE_STATUS_ENABLE) {
                MeshService.getInstance().getOnlineStatus();
                int rspMax = TelinkMeshApplication.getInstance().getMeshInfo().getOnlineCountInAll();
                int appKeyIndex = TelinkMeshApplication.getInstance().getMeshInfo().getDefaultAppKeyIndex();
                OnOffGetMessage message = OnOffGetMessage.getSimple(0xFFFF, appKeyIndex, rspMax);
                MeshService.getInstance().sendMeshMessage(message);
            } else {
                MeshLogger.log("online status enabled");
            }
            sendTimeStatus();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkMeshOtaState();
                }
            }, 3 * 1000);
        } else if (event.getType().equals(MeshEvent.EVENT_TYPE_DISCONNECTED)) {
            mHandler.removeCallbacksAndMessages(null);
        } else if (event.getType().equals(FDStatusMessage.class.getName())) {
            NotificationMessage notificationMessage = ((StatusNotificationEvent) event).getNotificationMessage();
            int msgSrc = notificationMessage.getSrc();
            FUCache fuCache = FUCacheService.getInstance().get();
            if (fuCache != null && fuCache.distAddress == msgSrc) {
                FDStatusMessage fdStatusMessage = (FDStatusMessage) notificationMessage.getStatusMessage();
                MeshLogger.d("FDStatus in main : " + fdStatusMessage.toString());
                if (fdStatusMessage.status == DistributionStatus.SUCCESS.code) {
                    if (fdStatusMessage.distPhase == DistributionPhase.IDLE.value) {
                        MeshLogger.d("clear meshOTA state");
                        FUCacheService.getInstance().clear(this);
                    } else if (fdStatusMessage.distPhase == DistributionPhase.CANCELING_UPDATE.value) {
                        // if canceling, resend cancel
                        FDCancelMessage cancelMessage = FDCancelMessage.getSimple(fuCache.distAddress, 0);
                        MeshService.getInstance().sendMeshMessage(cancelMessage);
                    }

                }
            }
        } else if (event.getType().equals(ScanEvent.EVENT_TYPE_DEVICE_FOUND)) {
            AdvertisingDevice device = ((ScanEvent) event).getAdvertisingDevice();
            deviceProvisionController.onDeviceFound(device);
        } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_BEGIN)) {
            deviceProvisionController.onProvisionStart((ProvisioningEvent) event);
        } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_SUCCESS)) {
            deviceProvisionController.onProvisionSuccess((ProvisioningEvent) event);
        } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_FAIL)) {
            deviceProvisionController.onProvisionFail((ProvisioningEvent) event);
            deviceProvisionController.provisionNext();
        } else if (event.getType().equals(BindingEvent.EVENT_TYPE_BIND_FAIL)) {
            deviceProvisionController.onKeyBindFail((BindingEvent) event);
        } else if (event.getType().equals(BindingEvent.EVENT_TYPE_BIND_SUCCESS)) {
            deviceProvisionController.onKeyBindSuccess((BindingEvent) event);
        }
    }
    /**
     * check if last mesh OTA flow completed,
     * if mesh OTA is still running , show MeshOTA tips dialog
     */
    public void checkMeshOtaState() {
        FUCache fuCache = FUCacheService.getInstance().get();
        if (fuCache != null) {
            MeshLogger.d("FU cache: distAdr-" + fuCache.distAddress);
//            showMeshOTATipsDialog(fuCache.distAddress);
        } else {
            MeshLogger.d("FU cache: not found");
        }
    }

//    public void showMeshOTATipsDialog(final int distributorAddress) {
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        DialogInterface.OnClickListener dialogBtnClick = (dialog, which) -> {
//            if (which == DialogInterface.BUTTON_POSITIVE) {
//                // GO
//                startActivity(new Intent(MainActivity.this, FUActivity.class)
//                        .putExtra(FUActivity.KEY_FU_CONTINUE, true));
//            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
//                // STOP
//                FDCancelMessage cancelMessage = FDCancelMessage.getSimple(distributorAddress, 0);
//                MeshService.getInstance().sendMeshMessage(cancelMessage);
//            } else if (which == DialogInterface.BUTTON_NEUTRAL) {
//                FUCacheService.getInstance().clear(MainActivity.this);
//            }
//        };
//
//        builder.setTitle("Warning - MeshOTA is still running")
//                .setMessage("MeshOTA distribution is still running, continue?\n" +
//                        "click GO to enter MeshOTA processing page \n"
//                        + "click STOP to stop distribution \n" +
//                        "click CLEAR to clear cache")
//                .setPositiveButton("GO", dialogBtnClick)
//                .setNegativeButton("STOP", dialogBtnClick)
//                .setNeutralButton("CLEAR", dialogBtnClick);
//        builder.show();
//    }

    public void sendTimeStatus() {
        mHandler.postDelayed(() -> {
            long time = MeshUtils.getTaiTime();
            int offset = UnitConvert.getZoneOffset();
            final int address = 0xFFFF;
            MeshInfo meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
            TimeSetMessage timeSetMessage = TimeSetMessage.getSimple(address, meshInfo.getDefaultAppKeyIndex(), time, offset, 1);
            timeSetMessage.setAck(false);
            MeshService.getInstance().sendMeshMessage(timeSetMessage);
        }, 1500);
    }
    public void sendTypeAsk (BindingDevice device){
        MeshMessage meshMessage = new MeshMessage();
        meshMessage.setDestinationAddress(device.getMeshAddress());
        meshMessage.setOpcode(OP_VENDOR_GET);
        meshMessage.setResponseOpcode(OP_VENDOR_STATUS);
        meshMessage.setParams(new byte[]{3, 0});
        MeshService.getInstance().sendMeshMessage(meshMessage);
    }
}