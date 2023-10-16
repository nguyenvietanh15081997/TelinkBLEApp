package com.example.customtelinkapp.Service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.customtelinkapp.Controller.FastProvisionController;
import com.example.customtelinkapp.MainActivity;
import com.example.customtelinkapp.Message.SecurityMessage;
import com.example.customtelinkapp.TelinkMeshApplication;
import com.example.customtelinkapp.model.AppSettings;
import com.example.customtelinkapp.model.FUCache;
import com.example.customtelinkapp.model.FUCacheService;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.SharedPreferenceHelper;
import com.example.customtelinkapp.model.UnitConvert;
import com.telink.ble.mesh.core.MeshUtils;
import com.telink.ble.mesh.core.message.NotificationMessage;
import com.telink.ble.mesh.core.message.config.ModelPublicationStatusMessage;
import com.telink.ble.mesh.core.message.firmwaredistribution.DistributionPhase;
import com.telink.ble.mesh.core.message.firmwaredistribution.FDCancelMessage;
import com.telink.ble.mesh.core.message.firmwaredistribution.FDStatusMessage;
import com.telink.ble.mesh.core.message.firmwareupdate.DistributionStatus;
import com.telink.ble.mesh.core.message.generic.OnOffGetMessage;
import com.telink.ble.mesh.core.message.time.TimeSetMessage;
import com.telink.ble.mesh.entity.AdvertisingDevice;
import com.telink.ble.mesh.foundation.Event;
import com.telink.ble.mesh.foundation.EventListener;
import com.telink.ble.mesh.foundation.MeshConfiguration;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.event.AutoConnectEvent;
import com.telink.ble.mesh.foundation.event.BindingEvent;
import com.telink.ble.mesh.foundation.event.BluetoothEvent;
import com.telink.ble.mesh.foundation.event.FastProvisioningEvent;
import com.telink.ble.mesh.foundation.event.MeshEvent;
import com.telink.ble.mesh.foundation.event.ProvisioningEvent;
import com.telink.ble.mesh.foundation.event.ScanEvent;
import com.telink.ble.mesh.foundation.event.StatusNotificationEvent;
import com.telink.ble.mesh.util.MeshLogger;

import java.util.Arrays;

public class MyBleService extends Service implements EventListener<String> {
    protected final String TAG = getClass().getSimpleName();
    private final int OPCODE_SECURE_RESPONSE =  0x0211e1;
    private Handler mHandler = new Handler();
    private MeshInfo mesh;
    public static Context context;
    public FastProvisionController fastProvisionController;
    private static final int OP_VENDOR_GET = 0x0211E0;
    private static final int OP_VENDOR_STATUS = 0x0211E1;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "start MyBleService");
        context = this;
        TelinkMeshApplication.getInstance().addEventListener(ScanEvent.EVENT_TYPE_SCAN_LOCATION_WARNING, this);
        TelinkMeshApplication.getInstance().addEventListener(BluetoothEvent.EVENT_TYPE_BLUETOOTH_STATE_CHANGE, this);
        TelinkMeshApplication.getInstance().addEventListener(AutoConnectEvent.EVENT_TYPE_AUTO_CONNECT_LOGIN, this);
        TelinkMeshApplication.getInstance().addEventListener(MeshEvent.EVENT_TYPE_DISCONNECTED, this);
        TelinkMeshApplication.getInstance().addEventListener(MeshEvent.EVENT_TYPE_MESH_EMPTY, this);
        TelinkMeshApplication.getInstance().addEventListener(FDStatusMessage.class.getName(), this);
//        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_BEGIN, this);
//        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_SUCCESS, this);
//        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_FAIL, this);
//        TelinkMeshApplication.getInstance().addEventListener(BindingEvent.EVENT_TYPE_BIND_SUCCESS, this);
//        TelinkMeshApplication.getInstance().addEventListener(BindingEvent.EVENT_TYPE_BIND_FAIL, this);
        TelinkMeshApplication.getInstance().addEventListener(ScanEvent.EVENT_TYPE_SCAN_TIMEOUT, this);
        TelinkMeshApplication.getInstance().addEventListener(ScanEvent.EVENT_TYPE_DEVICE_FOUND, this);
        //fast provision event
        TelinkMeshApplication.getInstance().addEventListener(FastProvisioningEvent.EVENT_TYPE_FAST_PROVISIONING_ADDRESS_SET, this);
        TelinkMeshApplication.getInstance().addEventListener(FastProvisioningEvent.EVENT_TYPE_FAST_PROVISIONING_FAIL, this);
        TelinkMeshApplication.getInstance().addEventListener(FastProvisioningEvent.EVENT_TYPE_FAST_PROVISIONING_SUCCESS, this);
        TelinkMeshApplication.getInstance().addEventListener(ModelPublicationStatusMessage.class.getName(), this);
        // unknownmessage
        TelinkMeshApplication.getInstance().addEventListener(StatusNotificationEvent.EVENT_TYPE_NOTIFICATION_MESSAGE_UNKNOWN, this);
//        //connect mqtt
//        MqttService.getInstance().connect(getApplicationContext());

        mesh = TelinkMeshApplication.getInstance().getMeshInfo();
        startMeshService();

        fastProvisionController = new FastProvisionController();
        fastProvisionController.actionStart();

        // returns the status
        // of the program
        return START_STICKY;
    }

    private void startMeshService() {
        // init
        MeshService.getInstance().init(this, TelinkMeshApplication.getInstance());

        // convert meshInfo to mesh configuration
        Log.i(TAG, "startMeshService: " + TelinkMeshApplication.getInstance());
        MeshConfiguration meshConfiguration = TelinkMeshApplication.getInstance().getMeshInfo().convertToConfiguration();
        MeshService.getInstance().setupMeshNetwork(meshConfiguration);

        // check if system bluetooth enabled
        MeshService.getInstance().checkBluetoothState();
        /// set DLE enable
        MeshService.getInstance().resetExtendBearerMode(SharedPreferenceHelper.getExtendBearerMode(this));

    }

    @Override
    public void onDestroy() {
        TelinkMeshApplication.getInstance().removeEventListener(this);
        MeshService.getInstance().clear();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void performed(Event<String> event) {
        Log.i(TAG, "thinpv performed: " + event.getType());
        if (event.getType().equals(MeshEvent.EVENT_TYPE_MESH_EMPTY)) {
            MeshLogger.log(TAG + "#EVENT_TYPE_MESH_EMPTY");
        } else if (event.getType().equals(AutoConnectEvent.EVENT_TYPE_AUTO_CONNECT_LOGIN)) {
            //can ask device status but didnt
            // start securing device
            // Tạo một luồng để gửi các bản tin bảo mật
            Thread sendThread = new Thread(() -> {
                MeshLogger.i("Start sendThread");
                fastProvisionController.startSecureDevice();
                // Kiểm tra xem luồng có bị hủy hay không
                if (Thread.currentThread().isInterrupted()) {
                    MeshLogger.i("sendThread has been interrupted. Exiting...");
                    return; // Thoát khỏi luồng
                }

                // Các công việc khác sau khi công việc chính được thực hiện xong

                // Gửi tín hiệu hủy đến luồng
                Thread.currentThread().interrupt();
            });
            sendThread.start();

            // Tạo một luồng khác để kiểm tra phản hồi sau 8 giây
            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(8000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                MeshLogger.i("Start responseThread");
                // In danh sách các thiết bị bảo mật thành công
                MeshLogger.i("SecureAgain");
                fastProvisionController.secureAgain();

                // Kiểm tra xem luồng có bị hủy hay không
                if (Thread.currentThread().isInterrupted()) {
                    MeshLogger.i("sendThread has been interrupted. Exiting...");
                    return; // Thoát khỏi luồng
                }

                // Các công việc khác sau khi công việc chính được thực hiện xong

                // Gửi tín hiệu hủy đến luồng
                Thread.currentThread().interrupt();
            });
            responseThread.start();

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
//        } else if (event.getType().equals(ScanEvent.EVENT_TYPE_DEVICE_FOUND)) {
//            AdvertisingDevice device = ((ScanEvent) event).getAdvertisingDevice();
//            deviceProvisionController.onDeviceFound(device);
//        } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_BEGIN)) {
//            deviceProvisionController.onProvisionStart((ProvisioningEvent) event);
//        } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_SUCCESS)) {
//            deviceProvisionController.onProvisionSuccess((ProvisioningEvent) event);
//        } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_FAIL)) {
//            deviceProvisionController.onProvisionFail((ProvisioningEvent) event);
//            deviceProvisionController.provisionNext();
//        } else if (event.getType().equals(BindingEvent.EVENT_TYPE_BIND_FAIL)) {
//            deviceProvisionController.onKeyBindFail((BindingEvent) event);
//        } else if (event.getType().equals(BindingEvent.EVENT_TYPE_BIND_SUCCESS)) {
//            deviceProvisionController.onKeyBindSuccess((BindingEvent) event);
        } else if (event.getType().equals(FastProvisioningEvent.EVENT_TYPE_FAST_PROVISIONING_ADDRESS_SET)) {
            fastProvisionController.onDeviceFound(((FastProvisioningEvent) event).getFastProvisioningDevice());
        } else if (event.getType().equals(FastProvisioningEvent.EVENT_TYPE_FAST_PROVISIONING_FAIL)) {
            fastProvisionController.onFastProvisionComplete(false);
        } else if (event.getType().equals(FastProvisioningEvent.EVENT_TYPE_FAST_PROVISIONING_SUCCESS)) {
            fastProvisionController.onFastProvisionComplete(true);
        }
        else if (event.getType().equals(StatusNotificationEvent.EVENT_TYPE_NOTIFICATION_MESSAGE_UNKNOWN)) {
            NotificationMessage notificationMessage = ((StatusNotificationEvent) event).getNotificationMessage();
            int dest = notificationMessage.getDst();
            int src = notificationMessage.getSrc();
            int opcode = notificationMessage.getOpcode();
            byte[] params = notificationMessage.getParams();

//            MeshLogger.i(String.format("dest=%d, src=%d, opcode=%d, params=%s", dest, src, opcode, Arrays.toString(params)));

            if (opcode == OPCODE_SECURE_RESPONSE){
                boolean isSuccess = checkSuccess(params);
                if(isSuccess){
                    fastProvisionController.onSecureSuccessResponse(src);
                }
            }
        }
    }
    public boolean checkSuccess(byte[] params) {
        // Kiểm tra độ dài của mảng byte
        if (params.length < 8) {
            return false; // Mảng byte không đủ độ dài
        }

        // Kiểm tra 2 byte đầu tiên (header)
        if (params[0] != 0x03 || params[1] != 0x00) {
            return false; // Header không đúng
        }

        // Kiểm tra hai byte tiếp theo
        if (params[2] == (byte)0xff && params[3] == (byte)0xfe) {
            return false;
        }

        return true;
    }
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

    public void checkMeshOtaState() {
        FUCache fuCache = FUCacheService.getInstance().get();
        if (fuCache != null) {
            MeshLogger.d("FU cache: distAdr-" + fuCache.distAddress);
//            showMeshOTATipsDialog(fuCache.distAddress);
        } else {
            MeshLogger.d("FU cache: not found");
        }
    }
}
