package com.example.customtelinkapp.Message;

import com.telink.ble.mesh.core.message.MeshMessage;
import com.telink.ble.mesh.util.Arrays;

public class GroupMessage extends MeshMessage {
    private final int GROUP_OPCODE = 0x0211e2;
    private final String PARAMS = "0A0C00C00100";
    public GroupMessage(int destinationAddress) {
        this.destinationAddress = destinationAddress;
        this.setOpcode(GROUP_OPCODE);
        this.setParams(Arrays.hexToBytes(PARAMS));
    }
}
