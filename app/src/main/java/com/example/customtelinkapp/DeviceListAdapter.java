package com.example.customtelinkapp;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.customtelinkapp.model.NodeInfo;
import com.telink.ble.mesh.core.message.config.NodeResetMessage;
import com.telink.ble.mesh.foundation.MeshService;

import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder>{
    private final String TAG = getClass().getSimpleName();
    private static NodeInfo deviceInfo;
    private static Handler delayHandler = new Handler();
    private Context mContext;
    private List<NodeInfo> listDevice;

    public DeviceListAdapter(Context mContext, List<NodeInfo> listDevice) {
        this.mContext = mContext;
        this.listDevice = listDevice;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View deviceView = inflater.inflate(R.layout.fast_provisioning_device, parent, false);
        return new ViewHolder(deviceView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NodeInfo device = listDevice.get(position);

        holder.tvMacDevice.setText(device.macAddress);
        holder.tvMeshAdr.setText(String.valueOf(device.meshAddress));

        holder.btn_kick_out.setOnClickListener(v -> {
            kickOut(device.meshAddress);
                listDevice.remove(position);
                notifyDataSetChanged();
        });
    }

    @Override
    public int getItemCount() {
        return listDevice.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView tvMacDevice, tvMeshAdr;
        private Button btn_kick_out;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMacDevice = itemView.findViewById(R.id.tvMacDevice);
            tvMeshAdr = itemView.findViewById(R.id.tvMeshAdr);

            btn_kick_out = itemView.findViewById(R.id.btn_kick_out);
        }
    }

    public static void kickOut(int meshAdr) {
        // send reset message
        MeshService.getInstance().sendMeshMessage(new NodeResetMessage(meshAdr));
        deviceInfo = TelinkMeshApplication.getInstance().getMeshInfo().getDeviceByMeshAddress(meshAdr);
        // device info == null -> crash app
        Log.i("TAG", " device infor kickOut: " + deviceInfo.meshAddress);
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
        MeshService.getInstance().removeDevice(deviceInfo.meshAddress);
    }
    public static void onKickOutFinish() {
        delayHandler.removeCallbacksAndMessages(null);
        MeshService.getInstance().removeDevice(deviceInfo.meshAddress);
        TelinkMeshApplication.getInstance().getMeshInfo().removeDeviceByMeshAddress(deviceInfo.meshAddress);
        TelinkMeshApplication.getInstance().getMeshInfo().saveOrUpdate(TelinkMeshApplication.getInstance().getApplicationContext());
        MainActivity.autoConnect();
        Log.i("[afterkick]", (String.valueOf(TelinkMeshApplication.getInstance().getMeshInfo().nodes.size())));
    }
}
