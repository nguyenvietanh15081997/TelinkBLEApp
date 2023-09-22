package com.example.customtelinkapp;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.customtelinkapp.model.AppSettings;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NodeInfo;
import com.example.customtelinkapp.model.NodeStatusChangedEvent;
import com.example.customtelinkapp.model.OnlineState;
import com.example.customtelinkapp.model.SharedPreferenceHelper;
import com.example.customtelinkapp.model.UnitConvert;
import com.telink.ble.mesh.core.message.MeshSigModel;
import com.telink.ble.mesh.core.message.NotificationMessage;
import com.telink.ble.mesh.core.message.StatusMessage;
import com.telink.ble.mesh.core.message.generic.LevelStatusMessage;
import com.telink.ble.mesh.core.message.generic.OnOffStatusMessage;
import com.telink.ble.mesh.core.message.lighting.CtlStatusMessage;
import com.telink.ble.mesh.core.message.lighting.CtlTemperatureStatusMessage;
import com.telink.ble.mesh.core.message.lighting.LightnessStatusMessage;
import com.telink.ble.mesh.entity.OnlineStatusInfo;
import com.telink.ble.mesh.foundation.MeshApplication;
import com.telink.ble.mesh.foundation.event.MeshEvent;
import com.telink.ble.mesh.foundation.event.NetworkInfoUpdateEvent;
import com.telink.ble.mesh.foundation.event.OnlineStatusEvent;
import com.telink.ble.mesh.foundation.event.StatusNotificationEvent;
import com.telink.ble.mesh.util.FileSystem;
import com.telink.ble.mesh.util.MeshLogger;

import java.util.List;

public class TelinkMeshApplication extends MeshApplication {
    private final String TAG = "Telink-APP";
    private static TelinkMeshApplication mThis;
    private MeshInfo meshInfo;

    private Handler mOfflineCheckHandler;
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate: ");
        mThis = this;
        HandlerThread offlineCheckThread = new HandlerThread("offline check thread");
        offlineCheckThread.start();
        mOfflineCheckHandler = new Handler(offlineCheckThread.getLooper());
        initMesh();
        MeshLogger.enableRecord(SharedPreferenceHelper.isLogEnable(this));
        MeshLogger.d(meshInfo.toString());
//        AppCrashHandler.init(this);
//        closePErrorDialog();
    }

    @Override
    protected void onOnlineStatusEvent(OnlineStatusEvent onlineStatusEvent) {
        List<OnlineStatusInfo> infoList = onlineStatusEvent.getOnlineStatusInfoList();
        if (infoList != null && meshInfo != null) {
            NodeInfo statusChangedNode = null;
            for (OnlineStatusInfo onlineStatusInfo : infoList) {
                if (onlineStatusInfo.status == null || onlineStatusInfo.status.length < 3) break;
                NodeInfo deviceInfo = meshInfo.getDeviceByMeshAddress(onlineStatusInfo.address);
                if (deviceInfo == null) continue;
                int onOff;
                if (onlineStatusInfo.sn == 0) {
                    onOff = -1;
                } else {
                    if (onlineStatusInfo.status[0] == 0) {
                        onOff = 0;
                    } else {
                        onOff = 1;
                    }


                }
                /*if (deviceInfo.getOnOff() != onOff){

                }*/
                if (deviceInfo.getOnlineState().st != onOff) {
                    statusChangedNode = deviceInfo;
                }
                deviceInfo.setOnlineState(OnlineState.getBySt(onOff));
                if (deviceInfo.lum != onlineStatusInfo.status[0]) {
                    statusChangedNode = deviceInfo;
                    deviceInfo.lum = onlineStatusInfo.status[0];
                }

                if (deviceInfo.temp != onlineStatusInfo.status[1]) {
                    statusChangedNode = deviceInfo;
                    deviceInfo.temp = onlineStatusInfo.status[1];
                }
            }
            if (statusChangedNode != null) {
                onNodeInfoStatusChanged(statusChangedNode);
            }
        }
    }

    @Override
    protected void onMeshEvent(MeshEvent autoConnectEvent) {
        String eventType = autoConnectEvent.getType();
        if (MeshEvent.EVENT_TYPE_DISCONNECTED.equals(eventType)) {
            AppSettings.ONLINE_STATUS_ENABLE = false;
            for (NodeInfo nodeInfo : meshInfo.nodes) {
                nodeInfo.setOnlineState(OnlineState.OFFLINE);
            }
        }
    }

    @Override
    protected void onStatusNotificationEvent(StatusNotificationEvent statusNotificationEvent) {
        NotificationMessage message = statusNotificationEvent.getNotificationMessage();

        StatusMessage statusMessage = message.getStatusMessage();
        if (statusMessage != null) {
            NodeInfo statusChangedNode = null;
            if (message.getStatusMessage() instanceof OnOffStatusMessage) {
                OnOffStatusMessage onOffStatusMessage = (OnOffStatusMessage) statusMessage;
                int onOff = onOffStatusMessage.isComplete() ? onOffStatusMessage.getTargetOnOff() : onOffStatusMessage.getPresentOnOff();
                for (NodeInfo nodeInfo : meshInfo.nodes) {
                    if (nodeInfo.meshAddress == message.getSrc()) {
                        if (nodeInfo.getOnlineState().st != onOff) {
                            statusChangedNode = nodeInfo;
                        }
                        nodeInfo.setOnlineState(OnlineState.getBySt(onOff));
                        break;
                    }
                }
            } else if (message.getStatusMessage() instanceof LevelStatusMessage) {
                LevelStatusMessage levelStatusMessage = (LevelStatusMessage) statusMessage;
                int srcAdr = message.getSrc();
                int level = levelStatusMessage.isComplete() ? levelStatusMessage.getTargetLevel() : levelStatusMessage.getPresentLevel();
                int tarVal = UnitConvert.level2lum((short) level);
                for (NodeInfo onlineDevice : meshInfo.nodes) {
                    if (onlineDevice.compositionData == null) {
                        continue;
                    }
                    int lightnessEleAdr = onlineDevice.getTargetEleAdr(MeshSigModel.SIG_MD_LIGHTNESS_S.modelId);
                    if (lightnessEleAdr == srcAdr) {
//                        if (onLumStatus(onlineDevice, tarVal)) {
//                            statusChangedNode = onlineDevice;
//                        }

                    } else {
                        int tempEleAdr = onlineDevice.getTargetEleAdr(MeshSigModel.SIG_MD_LIGHT_CTL_TEMP_S.modelId);
                        if (tempEleAdr == srcAdr) {
                            if (onlineDevice.temp != tarVal) {
                                statusChangedNode = onlineDevice;
                                onlineDevice.temp = tarVal;
                            }
                        }
                    }
                }
            } else if (message.getStatusMessage() instanceof CtlStatusMessage) {
                CtlStatusMessage ctlStatusMessage = (CtlStatusMessage) statusMessage;
                MeshLogger.d("ctl : " + ctlStatusMessage.toString());
                int srcAdr = message.getSrc();
                for (NodeInfo onlineDevice : meshInfo.nodes) {
                    if (onlineDevice.meshAddress == srcAdr) {
                        int lum = ctlStatusMessage.isComplete() ? ctlStatusMessage.getTargetLightness() : ctlStatusMessage.getPresentLightness();
//                        if (onLumStatus(onlineDevice, UnitConvert.lightness2lum(lum))) {
//                            statusChangedNode = onlineDevice;
//                        }

                        int temp = ctlStatusMessage.isComplete() ? ctlStatusMessage.getTargetTemperature() : ctlStatusMessage.getPresentTemperature();
//                        if (onTempStatus(onlineDevice, UnitConvert.tempToTemp100(temp))) {
//                            statusChangedNode = onlineDevice;
//                        }
                        break;
                    }
                }
            } else if (message.getStatusMessage() instanceof LightnessStatusMessage) {
                LightnessStatusMessage lightnessStatusMessage = (LightnessStatusMessage) statusMessage;
                int srcAdr = message.getSrc();
                for (NodeInfo onlineDevice : meshInfo.nodes) {
                    if (onlineDevice.meshAddress == srcAdr) {
                        int lum = lightnessStatusMessage.isComplete() ? lightnessStatusMessage.getTargetLightness() : lightnessStatusMessage.getPresentLightness();
//                        if (onLumStatus(onlineDevice, UnitConvert.lightness2lum(lum))) {
//                            statusChangedNode = onlineDevice;
//                        }
                        break;
                    }
                }
            } else if (message.getStatusMessage() instanceof CtlTemperatureStatusMessage) {
                CtlTemperatureStatusMessage ctlTemp = (CtlTemperatureStatusMessage) statusMessage;
                int srcAdr = message.getSrc();
                for (NodeInfo onlineDevice : meshInfo.nodes) {
                    if (onlineDevice.meshAddress == srcAdr) {
                        int temp = ctlTemp.isComplete() ? ctlTemp.getTargetTemperature() : ctlTemp.getPresentTemperature();
//                        if (onTempStatus(onlineDevice, UnitConvert.lightness2lum(temp))) {
//                            statusChangedNode = onlineDevice;
//                        }
                        break;
                    }
                }
            }

            if (statusChangedNode != null) {
                onNodeInfoStatusChanged(statusChangedNode);
            }
        }
    }

    private void initMesh() {
        Object configObj = FileSystem.readAsObject(this, MeshInfo.FILE_NAME);
        if (configObj == null) {
            meshInfo = MeshInfo.createNewMesh(this);
            meshInfo.saveOrUpdate(this);
        } else {
            meshInfo = (MeshInfo) configObj;
        }
//        meshInfo = createTestMesh();
    }
    public void setupMesh(MeshInfo mesh) {
        MeshLogger.d("setup mesh info: " + meshInfo.toString());
        this.meshInfo = mesh;
        dispatchEvent(new MeshEvent(this, MeshEvent.EVENT_TYPE_MESH_RESET, "mesh reset"));
    }

    public MeshInfo getMeshInfo() {
        return meshInfo;
    }

    public static TelinkMeshApplication getInstance() {
        return mThis;
    }
    public Handler getOfflineCheckHandler() {
        return mOfflineCheckHandler;
    }
    private void onNodeInfoStatusChanged(NodeInfo nodeInfo) {
        dispatchEvent(new NodeStatusChangedEvent(this, NodeStatusChangedEvent.EVENT_TYPE_NODE_STATUS_CHANGED, nodeInfo));
    }
    protected void onNetworkInfoUpdate(NetworkInfoUpdateEvent networkInfoUpdateEvent) {
        MeshLogger.d(String.format("mesh info update from local sequenceNumber-%06X ivIndex-%08X to sequenceNumber-%06X ivIndex-%08X",
                meshInfo.sequenceNumber, meshInfo.ivIndex,
                networkInfoUpdateEvent.getSequenceNumber(), networkInfoUpdateEvent.getIvIndex()));
        this.meshInfo.ivIndex = networkInfoUpdateEvent.getIvIndex();
        this.meshInfo.sequenceNumber = networkInfoUpdateEvent.getSequenceNumber();
        this.meshInfo.saveOrUpdate(this);
    }
}
