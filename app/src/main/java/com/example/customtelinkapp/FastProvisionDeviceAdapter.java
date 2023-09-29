package com.example.customtelinkapp;

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.example.customtelinkapp.Service.MyBleService;
import com.example.customtelinkapp.model.AppSettings;
import com.example.customtelinkapp.model.MeshInfo;
import com.example.customtelinkapp.model.NetworkingDevice;
import com.example.customtelinkapp.model.NodeInfo;
import com.telink.ble.mesh.core.message.config.NodeResetMessage;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.parameter.AutoConnectParameters;
import com.telink.ble.mesh.util.MeshLogger;

import java.util.List;

public class FastProvisionDeviceAdapter extends BaseAdapter {
    private final String TAG = getClass().getSimpleName();
    private NodeInfo deviceInfo;
    private Handler delayHandler = new Handler();
    MainActivity context;
    int layout;
    List<NetworkingDevice> deviceList;

    public FastProvisionDeviceAdapter(MainActivity context, int layout, List<NetworkingDevice> deviceList) {
        this.context = context;
        this.layout = layout;
        this.deviceList = deviceList;
    }

    @Override
    public int getCount() {
        return deviceList.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolderDevice viewHolderDevice;
        if (convertView == null) {
            viewHolderDevice = new ViewHolderDevice();
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(layout, null);

            viewHolderDevice.tvMacDevice = convertView.findViewById(R.id.tvMacDevice);
            viewHolderDevice.tvMeshDevice = convertView.findViewById(R.id.tvMeshAdr);
            viewHolderDevice.btn_kick_out = convertView.findViewById(R.id.btn_kick_out);
            viewHolderDevice.btn_auto_connect = convertView.findViewById(R.id.btn_auto_connect);
            convertView.setTag(viewHolderDevice);
        } else {
            viewHolderDevice = (ViewHolderDevice) convertView.getTag();
        }
        NetworkingDevice device = deviceList.get(position);

        viewHolderDevice.tvMacDevice.setText(device.nodeInfo.macAddress);
        viewHolderDevice.tvMeshDevice.setText(String.valueOf(device.nodeInfo.meshAddress));

        viewHolderDevice.btn_kick_out.setOnClickListener(v -> {
            kickOut(device.nodeInfo.meshAddress);
            deviceList.remove(position);
            notifyDataSetChanged();
            MeshLogger.i(String.valueOf(position));
        });
        viewHolderDevice.btn_auto_connect.setOnClickListener(v -> {
            autoConnect();
        });
        return convertView;
    }
    private class ViewHolderDevice {
        TextView tvMacDevice, tvMeshDevice;
        Button btn_kick_out,btn_auto_connect;
    }
    private void kickOut(int meshAdr) {
        // send reset message
        MeshService.getInstance().sendMeshMessage(new NodeResetMessage(meshAdr));
        deviceInfo = TelinkMeshApplication.getInstance().getMeshInfo().getDeviceByMeshAddress(meshAdr);
        boolean kickDirect = deviceInfo.meshAddress == MeshService.getInstance().getDirectConnectedNodeAddress();
        if (!kickDirect) {
            delayHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onKickOutFinish();
                }
            }, 3 * 1000);
        } else {
            delayHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onKickOutFinish();
                }
            }, 10 * 1000);
        }
    }
    private void onKickOutFinish() {
        delayHandler.removeCallbacksAndMessages(null);
        MeshService.getInstance().removeDevice(deviceInfo.meshAddress);
        TelinkMeshApplication.getInstance().getMeshInfo().removeDeviceByMeshAddress(deviceInfo.meshAddress);
        TelinkMeshApplication.getInstance().getMeshInfo().saveOrUpdate(MyBleService.context);
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
}
