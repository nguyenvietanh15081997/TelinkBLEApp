package com.example.customtelinkapp.Message;

import com.telink.ble.mesh.core.message.MeshMessage;
import com.telink.ble.mesh.util.Arrays;

public class SceneMessage extends MeshMessage {
    private final int SCENE_OPCODE = 0x4282;
    public SceneMessage(int sceneId) {
        this.destinationAddress = 0xC000;
        this.setOpcode(SCENE_OPCODE);
        //this.setParams(Arrays.hexToBytes("0100000000"));
        this.setParams(Arrays.hexToBytes("0" + String.valueOf(sceneId)+"00000000"));
    }
}
