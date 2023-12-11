package vn.com.rangdong.fastscan.Message;

import com.telink.ble.mesh.core.message.MeshMessage;

public class SecurityMessage extends MeshMessage {
    private final int SECURE_OPCODE = 0x0211e0;

    public SecurityMessage() {
    }

    public SecurityMessage(int destinationAddress, byte[] encryptedData) {
        this.destinationAddress = destinationAddress;
        this.setOpcode(SECURE_OPCODE);
        this.setParams(encryptedData);
    }
}
