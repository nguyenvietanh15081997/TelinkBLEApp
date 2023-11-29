package com.example.customtelinkapp.Message;

import com.telink.ble.mesh.core.message.MeshMessage;
import com.telink.ble.mesh.util.Arrays;

public class ControlGroupMessage extends MeshMessage {
    private final int GROUP_OPCODE = 0x0282;
    public ControlGroupMessage(Boolean status) {
        this.destinationAddress = 0xC000;
        this.setOpcode(GROUP_OPCODE);
        if(status){
            this.setParams(Arrays.hexToBytes("0100000000"));
        }
        else {
            this.setParams(Arrays.hexToBytes("0000000000"));
        }
    }
}
