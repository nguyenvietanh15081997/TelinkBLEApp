package vn.com.rangdong.fastscan.Controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseIntArray;

import vn.com.rangdong.fastscan.MainActivity;
import vn.com.rangdong.fastscan.Service.MqttService;
import vn.com.rangdong.fastscan.TelinkMeshApplication;
import vn.com.rangdong.fastscan.Util.Converter;
import vn.com.rangdong.fastscan.model.NetworkingState;
import vn.com.rangdong.fastscan.Message.SecurityMessage;
import vn.com.rangdong.fastscan.model.MeshInfo;
import vn.com.rangdong.fastscan.model.NetworkingDevice;
import vn.com.rangdong.fastscan.model.NodeInfo;
import vn.com.rangdong.fastscan.model.PrivateDevice;
import vn.com.rangdong.fastscan.model.SecurityDevice;
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

    public void actionStart(SparseIntArray targetDevicePid) {
        meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
        devices.clear();
        securityDeviceList.clear();
        isSendSecurity = true;
        MeshLogger.i(String.valueOf(meshInfo));
        int provisionIndex = meshInfo.getProvisionIndex();

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
            nodeInfo.pid = fastProvisioningDevice.getPid();

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
        Log.i("TAG", "-- number of device ---: " + TelinkMeshApplication.getInstance().getMeshInfo().nodes.size());
//        MainActivity.resetUI();
    }

    public void checkSecure(SecurityDevice securityDevice) {
        sendSecurityMessageByAddress(securityDevice.getNodeInfo().meshAddress, securityDevice.getNodeInfo().macAddress);
    }


    public void startSecureDevice() {
        Log.i("TAG", "startSecureDevice");
        List<NodeInfo> listDeviceFound = new ArrayList<>();

        for (NetworkingDevice networkingDevice : devices) {
            listDeviceFound.add(networkingDevice.nodeInfo);
        }

        List<NodeInfo> commonElements = new ArrayList<>(TelinkMeshApplication.getInstance().getMeshInfo().nodes);

        commonElements.retainAll(listDeviceFound);

        final int DELAY_BETWEEN_DEVICES = 500;
        final Handler handler = new Handler(Looper.getMainLooper());


        for (int i = 0; i < commonElements.size(); i++) {
            int vidDevice = 0x0211;
            if(commonElements.get(i).compositionData != null){
                vidDevice = commonElements.get(i).compositionData.vid;
            }
            SecurityDevice securityDevice = new SecurityDevice(commonElements.get(i), false, 4, vidDevice);
            lock.lock();
            try {
                securityDeviceList.add(securityDevice);
            } finally {
                lock.unlock();
            }
            handler.postDelayed(() -> checkSecure(securityDevice), (long) i * DELAY_BETWEEN_DEVICES);
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
                nodeInfo.pid, nodeInfo.meshAddress);
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
