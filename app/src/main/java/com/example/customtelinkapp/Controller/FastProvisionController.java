package com.example.customtelinkapp.Controller;

import android.os.Handler;
import android.util.Log;
import android.util.SparseIntArray;

import com.example.customtelinkapp.MainActivity;
import com.example.customtelinkapp.Message.SecurityMessage;
import com.example.customtelinkapp.Service.MqttService;
import com.example.customtelinkapp.TelinkMeshApplication;
import com.example.customtelinkapp.Util.Converter;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NetworkingDevice;
import com.example.customtelinkapp.model.NetworkingState;
import com.example.customtelinkapp.model.NodeInfo;
import com.example.customtelinkapp.model.PrivateDevice;
import com.example.customtelinkapp.model.SecurityDevice;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FastProvisionController {

    public static boolean isSendSecurity = false;
    public static Lock lock = new ReentrantLock();
    public static List<SecurityDevice> securityDeviceList = new ArrayList<>();
    public static MeshInfo meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
    private final String RD_KEY = "4469676974616c403238313132383034";
    private final String UNENCRYPTED_DATA_PREFIXES = "2402280428112020";
    private final String PARAMS_PREFIXES = "0003";
    /**
     * ui devices
     */
    public static List<NetworkingDevice> devices = new ArrayList<>();

    public PrivateDevice[] targetDevices = PrivateDevice.values();

    public void actionStart() {
        devices.clear();
        securityDeviceList.clear();
        MeshLogger.i("In action start");
        isSendSecurity = true;
        MeshLogger.i(String.valueOf(meshInfo));
        int provisionIndex = meshInfo.getProvisionIndex();
        SparseIntArray targetDevicePid = new SparseIntArray(targetDevices.length);

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
        try {
            meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
            MeshLogger.i(fastProvisioningDevice.toString());
            NodeInfo nodeInfo = new NodeInfo();
            nodeInfo.meshAddress = fastProvisioningDevice.getNewAddress();

            nodeInfo.deviceUUID = new byte[16];
            System.arraycopy(fastProvisioningDevice.getMac(), 0, nodeInfo.deviceUUID, 0, 6);
            nodeInfo.macAddress = Arrays.bytesToHexString(fastProvisioningDevice.getMac(), ":");
            nodeInfo.deviceKey = fastProvisioningDevice.getDeviceKey();
            nodeInfo.elementCnt = fastProvisioningDevice.getElementCount();
            nodeInfo.compositionData = getCompositionData(fastProvisioningDevice.getPid());
            nodeInfo.deviceUUID = Encipher.calcUuidByMac(fastProvisioningDevice.getMac());

            NetworkingDevice device = new NetworkingDevice(nodeInfo);
            device.state = NetworkingState.PROVISIONING;
            devices.add(device);
            //for debug
//            MainActivity.fastProvisionDeviceAdapter.notifyDataSetChanged();

            meshInfo.increaseProvisionIndex(fastProvisioningDevice.getElementCount());
            meshInfo.saveOrUpdate(TelinkMeshApplication.getInstance());
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
        meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
        for (NetworkingDevice networkingDevice : devices) {
            if (success) {
                networkingDevice.state = NetworkingState.BIND_SUCCESS;
                networkingDevice.nodeInfo.bound = true;
                meshInfo.insertDevice(networkingDevice.nodeInfo);
                MeshLogger.i(String.format("Mac: %s, Address: %s, Ele: %s, DevKey; %s", networkingDevice.nodeInfo.macAddress, networkingDevice.nodeInfo.meshAddress, networkingDevice.nodeInfo.elementCnt, java.util.Arrays.toString(networkingDevice.nodeInfo.deviceKey)));
            } else {
                networkingDevice.state = NetworkingState.PROVISION_FAIL;
            }
        }
        if (success) {
            MainActivity.autoConnect();
            meshInfo.saveOrUpdate(TelinkMeshApplication.getInstance());
        }
        MqttService.getInstance().callProvisionNormal();
        Log.i("TAG", "-- list size---: " + TelinkMeshApplication.getInstance().getMeshInfo().nodes.size());
        MainActivity.fastProvisionDeviceAdapter.notifyDataSetChanged();
    }

//    public int checkSecure(SecurityDevice securityDevice) {
//        sendSecurityMessageByAddress(securityDevice.getNodeInfo().meshAddress, securityDevice.getNodeInfo().macAddress);
//        final int[] rs = {0};
//        new Handler().postDelayed(() -> {
//            if (!securityDevice.getSecured()) {
//                rs[0] = 1;
//            }
//        }, 400);
//        return rs[0];
//    }
    public void checkSecure(SecurityDevice securityDevice) {
        sendSecurityMessageByAddress(securityDevice.getNodeInfo().meshAddress, securityDevice.getNodeInfo().macAddress);

        new Handler().postDelayed(() -> {
            if (securityDevice.getSecured()) {
                securityDevice.getNodeInfo().setIsSecured(false); // Cập nhật trạng thái bảo mật
                sendNewDeviceToHC(securityDevice); // Gửi thiết bị mới đến trung tâm điều khiển
            } else {
                // Nếu thiết bị không được bảo mật sau 400ms, có thể thực hiện các bước cần thiết

            }
        }, 400);
    }


    public void startSecureDevice() {
        Log.i("TAG", "startSecureDevice");
        List<NodeInfo> listDeviceFound = new ArrayList<>();

        for (NetworkingDevice networkingDevice : devices) {
            listDeviceFound.add(networkingDevice.nodeInfo);
        }

        List<NodeInfo> commonElements = new ArrayList<>(TelinkMeshApplication.getInstance().getMeshInfo().nodes);

        commonElements.retainAll(listDeviceFound);

        for (NodeInfo nodeInfo : commonElements) {
            SecurityDevice securityDevice = new SecurityDevice(nodeInfo, false, 4, nodeInfo.compositionData.vid);
            lock.lock();
            try {
                securityDeviceList.add(securityDevice);
            } finally {
                lock.unlock();
            }
            checkSecure(securityDevice);
        }
        FastProvisionController.isSendSecurity = false;
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

    public void sendNewDeviceToHC(SecurityDevice securityDevice) {
        Log.i("TAG", "vid send: " + securityDevice.getVidDevice());
        NodeInfo nodeInfo = securityDevice.getNodeInfo();
        MqttService.getInstance().sendBindedDeviceInfo(Arrays.bytesToHexString(nodeInfo.deviceUUID), nodeInfo.macAddress,
                Arrays.bytesToHexString(nodeInfo.deviceKey), securityDevice.getVidDevice(),
                nodeInfo.compositionData.pid, nodeInfo.meshAddress);
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

    public static byte[] getArrayElements(byte[] array, int startIndex, int endIndex) {
        int length = endIndex - startIndex + 1;
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = array[startIndex + i];
        }
        return result;
    }
}
