package com.example.customtelinkapp.Controller;

import android.os.Handler;
import android.util.Log;

import com.example.customtelinkapp.MainActivity;
import com.example.customtelinkapp.TelinkMeshApplication;
import com.example.customtelinkapp.model.AppSettings;
import com.example.customtelinkapp.model.CertCacheService;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NetworkingDevice;
import com.example.customtelinkapp.model.NetworkingState;
import com.example.customtelinkapp.model.NodeInfo;
import com.telink.ble.mesh.core.MeshUtils;
import com.telink.ble.mesh.core.access.BindingBearer;
import com.telink.ble.mesh.entity.AdvertisingDevice;
import com.telink.ble.mesh.entity.BindingDevice;
import com.telink.ble.mesh.entity.CompositionData;
import com.telink.ble.mesh.entity.ProvisioningDevice;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.event.BindingEvent;
import com.telink.ble.mesh.foundation.event.ProvisioningEvent;
import com.telink.ble.mesh.foundation.parameter.BindingParameters;
import com.telink.ble.mesh.foundation.parameter.ProvisioningParameters;
import com.telink.ble.mesh.foundation.parameter.ScanParameters;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.MeshLogger;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class DeviceProvisionController {
    /**
     * local mesh info
     */
    public MeshInfo mesh = TelinkMeshApplication.getInstance().getMeshInfo();
    public Handler mHandler = new Handler();
    /**
     * found by bluetooth scan
     */
    public List<NetworkingDevice> devices = new ArrayList<>();

    public void startScan() {
        ScanParameters parameters = ScanParameters.getDefault(false, false);
        MeshLogger.i("START SCAN");
        parameters.setScanTimeout(10 * 1000);
        MeshService.getInstance().startScan(parameters);
    }

    public void onDeviceFound(AdvertisingDevice advertisingDevice) {

//        if (!advertisingDevice.device.getAddress().toUpperCase().contains("00:1B:DC:08:E2:DA"))return; /// for pts test

        // provision service data: 15:16:28:18:[16-uuid]:[2-oobInfo]
        byte[] serviceData = MeshUtils.getMeshServiceData(advertisingDevice.scanRecord, true);
        if (serviceData == null || serviceData.length < 17) {
            MeshLogger.log("serviceData error", MeshLogger.LEVEL_ERROR);
            return;
        }

        final int uuidLen = 16;
        byte[] deviceUUID = new byte[uuidLen];


        System.arraycopy(serviceData, 0, deviceUUID, 0, uuidLen);

        final int oobInfo = MeshUtils.bytes2Integer(serviceData, 16, 2, ByteOrder.LITTLE_ENDIAN);


        if (deviceExists(deviceUUID)) {
            MeshLogger.d("device exists");
            return;
        }

        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.meshAddress = -1;
        nodeInfo.deviceUUID = deviceUUID;
        MeshLogger.d("device found -> device uuid : " + Arrays.bytesToHexString(deviceUUID) + " -- oobInfo: " + oobInfo + " -- certSupported?" + MeshUtils.isCertSupported(oobInfo));
        nodeInfo.macAddress = advertisingDevice.device.getAddress();
        MeshLogger.i("DEVICE ADDR : " + advertisingDevice.device.getAddress());
        NetworkingDevice processingDevice = new NetworkingDevice(nodeInfo);
        processingDevice.bluetoothDevice = advertisingDevice.device;
        if (AppSettings.DRAFT_FEATURES_ENABLE) {
            processingDevice.oobInfo = oobInfo;
        }
        processingDevice.state = NetworkingState.IDLE;
        processingDevice.addLog(NetworkingDevice.TAG_SCAN, "device found");
        devices.add(processingDevice);
    }

    public boolean deviceExists(byte[] deviceUUID) {
        for (NetworkingDevice device : this.devices) {
            if (device.state == NetworkingState.IDLE && Arrays.equals(deviceUUID, device.nodeInfo.deviceUUID)) {
                return true;
            }
        }
        return false;
    }

    public void provisionNext() {
        NetworkingDevice waitingDevice = getNextWaitingDevice();
        if (waitingDevice == null) {
            MeshLogger.d("no waiting device found");
            return;
        }
        startProvision(waitingDevice);
    }
    public boolean addAll() {
        boolean anyValid = false;
        for (NetworkingDevice device : devices) {
            if (device.state == NetworkingState.IDLE) {
                anyValid = true;
                device.state = NetworkingState.WAITING;
            }
        }
        return anyValid;
    }
    private NetworkingDevice getNextWaitingDevice() {
        for (NetworkingDevice device : devices) {
            if (device.state == NetworkingState.WAITING) {
                return device;
            }
        }
        return null;
    }

    private void startProvision(NetworkingDevice processingDevice) {
//        if (isScanning) {
//            isScanning = false;
//            MeshService.getInstance().stopScan();
//        }

        int address = mesh.getProvisionIndex();
        MeshLogger.d("alloc address: " + address);
        if (!MeshUtils.validUnicastAddress(address)) {
            return;
        }

        byte[] deviceUUID = processingDevice.nodeInfo.deviceUUID;
        ProvisioningDevice provisioningDevice = new ProvisioningDevice(processingDevice.bluetoothDevice, processingDevice.nodeInfo.deviceUUID, address);
        provisioningDevice.setRootCert(CertCacheService.getInstance().getRootCert());
        provisioningDevice.setOobInfo(processingDevice.oobInfo);
        processingDevice.state = NetworkingState.PROVISIONING;
        processingDevice.addLog(NetworkingDevice.TAG_PROVISION, "action start -> 0x" + String.format("%04X", address));
        processingDevice.nodeInfo.meshAddress = address;

        // check if oob exists
        byte[] oob = TelinkMeshApplication.getInstance().getMeshInfo().getOOBByDeviceUUID(deviceUUID);
//        oob = new byte[]{(byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
//        if (oob != null) {
//            provisioningDevice.setAuthValue(oob);
//        } else {
//            final boolean autoUseNoOOB = SharedPreferenceHelper.isNoOOBEnable(this);
//            provisioningDevice.setAutoUseNoOOB(autoUseNoOOB);
//        }
        provisioningDevice.setAutoUseNoOOB(true);
        ProvisioningParameters provisioningParameters = new ProvisioningParameters(provisioningDevice);
        MeshService.getInstance().startProvisioning(provisioningParameters);
    }
    public void onProvisionStart(ProvisioningEvent event) {
        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.PROVISIONING);
        if (pvDevice == null) return;
        pvDevice.addLog(NetworkingDevice.TAG_PROVISION, "begin");
    }
    public void onProvisionSuccess(ProvisioningEvent event) {

        ProvisioningDevice remote = event.getProvisioningDevice();


        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.PROVISIONING);
        if (pvDevice == null) {
            MeshLogger.d("pv device not found when provision success");
            return;
        }

        pvDevice.state = NetworkingState.BINDING;
        pvDevice.addLog(NetworkingDevice.TAG_PROVISION, "success");
        NodeInfo nodeInfo = pvDevice.nodeInfo;
        int elementCnt = remote.getDeviceCapability().eleNum;
        nodeInfo.elementCnt = elementCnt;
        nodeInfo.deviceKey = remote.getDeviceKey();
        nodeInfo.netKeyIndexes.add(mesh.getDefaultNetKey().index);
        mesh.insertDevice(nodeInfo);
        mesh.increaseProvisionIndex(elementCnt);


        // check if device support fast bind
        boolean defaultBound = false;

        nodeInfo.setDefaultBind(defaultBound);
        pvDevice.addLog(NetworkingDevice.TAG_BIND, "action start");
        int appKeyIndex = mesh.getDefaultAppKeyIndex();
        BindingDevice bindingDevice = new BindingDevice(nodeInfo.meshAddress, nodeInfo.deviceUUID, appKeyIndex);
        bindingDevice.setDefaultBound(defaultBound);
        bindingDevice.setBearer(BindingBearer.GattOnly);
//        bindingDevice.setDefaultBound(false);
        MeshService.getInstance().startBinding(new BindingParameters(bindingDevice));
    }
    public void onProvisionFail(ProvisioningEvent event) {
//        ProvisioningDevice deviceInfo = event.getProvisioningDevice();

        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.PROVISIONING);
        if (pvDevice == null) {
            MeshLogger.d("pv device not found when failed");
            return;
        }
        pvDevice.state = NetworkingState.PROVISION_FAIL;
        pvDevice.addLog(NetworkingDevice.TAG_PROVISION, event.getDesc());
    }
    public void onKeyBindFail(BindingEvent event) {
        NetworkingDevice deviceInList = getCurrentDevice(NetworkingState.BINDING);
        if (deviceInList == null) return;

        deviceInList.state = NetworkingState.BIND_FAIL;
        deviceInList.addLog(NetworkingDevice.TAG_BIND, "failed - " + event.getDesc());
    }
    public void onKeyBindSuccess(BindingEvent event) {
        BindingDevice remote = event.getBindingDevice();
        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.BINDING);
        if (pvDevice == null) {
            MeshLogger.d("pv device not found when bind success");
            return;
        }
        pvDevice.addLog(NetworkingDevice.TAG_BIND, "success");
        pvDevice.nodeInfo.bound = true;
        // if is default bound, composition data has been valued ahead of binding action
        if (!remote.isDefaultBound()) {
            pvDevice.nodeInfo.compositionData = remote.getCompositionData();
        }


        // no need to set time publish
        pvDevice.state = NetworkingState.BIND_SUCCESS;
        provisionNext();
        MainActivity mainActivity = new MainActivity();

        mainActivity.sendTypeAsk(remote);
    }

    /**
     * @param state target state,
     * @return processing device
     */
    private NetworkingDevice getCurrentDevice(NetworkingState state) {
        for (NetworkingDevice device : devices) {
            if (device.state == state) {
                return device;
            }
        }
        return null;
    }

}
