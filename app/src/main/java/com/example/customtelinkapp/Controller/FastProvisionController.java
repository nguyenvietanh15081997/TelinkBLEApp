package com.example.customtelinkapp.Controller;

import android.os.Handler;
import android.util.SparseIntArray;

import androidx.core.app.ComponentActivity;

import com.example.customtelinkapp.MainActivity;
import com.example.customtelinkapp.TelinkMeshApplication;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NetworkingDevice;
import com.example.customtelinkapp.model.NetworkingState;
import com.example.customtelinkapp.model.NodeInfo;
import com.example.customtelinkapp.model.PrivateDevice;
import com.telink.ble.mesh.entity.CompositionData;
import com.telink.ble.mesh.entity.FastProvisioningConfiguration;
import com.telink.ble.mesh.entity.FastProvisioningDevice;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.parameter.FastProvisioningParameters;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.MeshLogger;

import java.util.ArrayList;
import java.util.List;

public class FastProvisionController {
    public MeshInfo meshInfo;

    /**
     * ui devices
     */
    public List<NetworkingDevice> devices = new ArrayList<>();

    public Handler delayHandler = new Handler();

    public PrivateDevice[] targetDevices = PrivateDevice.values();
    public void actionStart() {
        MeshLogger.i("In action start");
        meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
        MeshLogger.i(String.valueOf(meshInfo));
        int provisionIndex = meshInfo.getProvisionIndex();
        SparseIntArray targetDevicePid = new SparseIntArray(targetDevices.length);
        MeshLogger.i(String.valueOf(provisionIndex));

        CompositionData compositionData;
        for (PrivateDevice privateDevice : targetDevices) {
            compositionData = CompositionData.from(privateDevice.getCpsData());
            targetDevicePid.put(privateDevice.getPid(), compositionData.elements.size());
        }
        MeshService.getInstance().startFastProvision(new FastProvisioningParameters(FastProvisioningConfiguration.getDefault(
                provisionIndex,
                targetDevicePid
        )));

    }
    public void onDeviceFound(FastProvisioningDevice fastProvisioningDevice) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.meshAddress = fastProvisioningDevice.getNewAddress();

        nodeInfo.deviceUUID = new byte[16];
        System.arraycopy(fastProvisioningDevice.getMac(), 0, nodeInfo.deviceUUID, 0, 6);
        nodeInfo.macAddress = Arrays.bytesToHexString(fastProvisioningDevice.getMac(), ":");
        nodeInfo.deviceKey = fastProvisioningDevice.getDeviceKey();
        nodeInfo.elementCnt = fastProvisioningDevice.getElementCount();
        nodeInfo.compositionData = getCompositionData(fastProvisioningDevice.getPid());

        NetworkingDevice device = new NetworkingDevice(nodeInfo);
        device.state = NetworkingState.PROVISIONING;
        devices.add(device);

        meshInfo.increaseProvisionIndex(fastProvisioningDevice.getElementCount());
    }
    public CompositionData getCompositionData(int pid) {
        for (PrivateDevice privateDevice : targetDevices) {
            if (pid == privateDevice.getPid())
                return CompositionData.from(privateDevice.getCpsData());

        }
        return null;
    }

    public void onFastProvisionComplete(boolean success) {
        for (NetworkingDevice networkingDevice : devices) {
            if (success) {
                networkingDevice.state = NetworkingState.BIND_SUCCESS;
                networkingDevice.nodeInfo.bound = true;
                meshInfo.insertDevice(networkingDevice.nodeInfo);
            } else {
                networkingDevice.state = NetworkingState.PROVISION_FAIL;
            }
        }
        if (success) {
//            meshInfo.saveOrUpdate(this);
        }
    }
}
