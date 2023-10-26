package com.example.customtelinkapp.Controller;

import android.os.Handler;
import android.util.Log;
import android.util.SparseIntArray;

import com.example.customtelinkapp.MainActivity;
import com.example.customtelinkapp.Message.SecurityMessage;
import com.example.customtelinkapp.Service.MqttService;
import com.example.customtelinkapp.Service.MyBleService;
import com.example.customtelinkapp.TelinkMeshApplication;
import com.example.customtelinkapp.Util.Converter;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NetworkingDevice;
import com.example.customtelinkapp.model.NetworkingState;
import com.example.customtelinkapp.model.NodeInfo;
import com.example.customtelinkapp.model.PrivateDevice;
import com.telink.ble.mesh.core.Encipher;
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
    private final String RD_KEY = "4469676974616c403238313132383034";
    private final String UNENCRYPTED_DATA_PREFIXES = "2402280428112020";
    private final String PARAMS_PREFIXES = "0003";
    /**
     * ui devices
     */
    public static List<NetworkingDevice> devices = new ArrayList<>();
    private List<Integer> securityDeviceAddress = new ArrayList<>();
    private List<Integer> responseSecuredDeviceAddress = new ArrayList<>();
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
        MeshLogger.i(fastProvisioningDevice.toString());

        try {
            MeshLogger.i(fastProvisioningDevice.toString());
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
            MainActivity.fastProvisionDeviceAdapter.notifyDataSetChanged();
            meshInfo.increaseProvisionIndex(fastProvisioningDevice.getElementCount());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public CompositionData getCompositionData(int pid) {
        for (PrivateDevice privateDevice : targetDevices) {
            if (pid == privateDevice.getPid())
                return CompositionData.from(privateDevice.getCpsData());

        }
        return null;
    }

    public void onFastProvisionComplete(boolean success) {
        MainActivity.autoConnect();
        for (NetworkingDevice networkingDevice : devices) {
            if (success) {
                networkingDevice.state = NetworkingState.BIND_SUCCESS;
                networkingDevice.nodeInfo.bound = true;
                meshInfo.insertDevice(networkingDevice.nodeInfo);
//                MqttService.getInstance().sendBindedDeviceInfo(Arrays.bytesToHexString(networkingDevice.nodeInfo.deviceUUID), networkingDevice.nodeInfo.macAddress, Arrays.bytesToHexString(networkingDevice.nodeInfo.deviceKey), networkingDevice.nodeInfo.compositionData.vid, networkingDevice.nodeInfo.compositionData.pid,networkingDevice.nodeInfo.meshAddress);
                MeshLogger.i(String.format("Mac: %s, Address: %s, Ele: %s, DevKey; %s", networkingDevice.nodeInfo.macAddress, networkingDevice.nodeInfo.meshAddress, networkingDevice.nodeInfo.elementCnt, java.util.Arrays.toString(networkingDevice.nodeInfo.deviceKey)));
            } else {
                networkingDevice.state = NetworkingState.PROVISION_FAIL;
            }
        }
        if (success) {
            meshInfo.saveOrUpdate(TelinkMeshApplication.getInstance().getApplicationContext());
        }
    }

    public void startSecureDevice() {
        for (NetworkingDevice networkingDevice : devices) {
            networkingDevice.state = NetworkingState.BIND_SUCCESS;
            networkingDevice.nodeInfo.bound = true;
//            meshInfo.insertDevice(networkingDevice.nodeInfo);
            sendSecurityMessageByAddress(networkingDevice.nodeInfo.meshAddress, networkingDevice.nodeInfo.macAddress);
            securityDeviceAddress.add(networkingDevice.nodeInfo.meshAddress);
        }
    }

    public void onSecureSuccessResponse(int addressResponse) {
        for (NetworkingDevice networkingDevice : devices) {
            if (networkingDevice.nodeInfo.meshAddress == addressResponse) {
                MqttService.getInstance().sendBindedDeviceInfo(Arrays.bytesToHexString(networkingDevice.nodeInfo.deviceUUID), networkingDevice.nodeInfo.macAddress, Arrays.bytesToHexString(networkingDevice.nodeInfo.deviceKey), networkingDevice.nodeInfo.compositionData.vid, networkingDevice.nodeInfo.compositionData.pid, networkingDevice.nodeInfo.meshAddress);
                break;
            }
        }
        responseSecuredDeviceAddress.add(addressResponse);
        securityDeviceAddress.removeAll(responseSecuredDeviceAddress);
        responseSecuredDeviceAddress.clear();
    }

    public void secureAgain() {
        for (int adr : securityDeviceAddress) {
            NodeInfo nodeInfo = meshInfo.getDeviceByMeshAddress(adr);
            sendSecurityMessageByAddress(adr, nodeInfo.macAddress);
        }
    }

    public void sendSecurityMessageByDevice(FastProvisioningDevice fastProvisioningDevice) {
        byte[] adr = fastProvisioningDevice.getMac();
        byte[] unicast = Arrays.reverse(Converter.intToByteArray(fastProvisioningDevice.getNewAddress()));
        byte[] dataPrefixes = Arrays.hexToBytes(UNENCRYPTED_DATA_PREFIXES);
        byte[] data = concatenateArrays(dataPrefixes, concatenateArrays(Arrays.reverse(adr), unicast));
        byte[] key = Arrays.hexToBytes(RD_KEY);
        byte[] re = Encipher.aes(data, key);
        byte[] paramPrefixes = Arrays.reverse(Arrays.hexToBytes(PARAMS_PREFIXES));
        SecurityMessage securityMessage = new SecurityMessage(fastProvisioningDevice.getNewAddress(), concatenateArrays(paramPrefixes, getLastElements(re, 6)));
        MeshService.getInstance().sendMeshMessage(securityMessage);

    }

    public void sendSecurityMessageByAddress(int meshAddress, String macAddress) {
        String cleanedMac = macAddress.replaceAll(":", "");
        byte[] adr = Arrays.hexToBytes(cleanedMac);
        byte[] unicast = Arrays.reverse(Converter.intToByteArray(meshAddress));
        byte[] dataPrefixes = Arrays.hexToBytes(UNENCRYPTED_DATA_PREFIXES);
        byte[] data = concatenateArrays(dataPrefixes, concatenateArrays(Arrays.reverse(adr), unicast));
        byte[] key = Arrays.hexToBytes(RD_KEY);
        byte[] re = Encipher.aes(data, key);
        byte[] paramPrefixes = Arrays.reverse(Arrays.hexToBytes(PARAMS_PREFIXES));
        SecurityMessage securityMessage = new SecurityMessage(meshAddress, concatenateArrays(paramPrefixes, getLastElements(re, 6)));
        MeshService.getInstance().sendMeshMessage(securityMessage);

    }

    public static byte[] concatenateArrays(byte[] array1, byte[] array2) {
        int length1 = array1.length;
        int length2 = array2.length;

        byte[] result = new byte[length1 + length2];

        System.arraycopy(array1, 0, result, 0, length1);
        System.arraycopy(array2, 0, result, length1, length2);

        return result;
    }

    public static byte[] getLastElements(byte[] inputArray, int n) {
        int length = inputArray.length;
        byte[] outputArray = new byte[n];

        if (n > length) {
            throw new IllegalArgumentException("n is larger than the length of the input array");
        }

        for (int i = 0; i < n; i++) {
            outputArray[i] = inputArray[length - n + i];
        }

        return outputArray;
    }
}
