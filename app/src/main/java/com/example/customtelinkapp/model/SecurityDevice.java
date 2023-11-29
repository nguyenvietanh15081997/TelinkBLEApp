package com.example.customtelinkapp.model;

public class SecurityDevice {
    private NodeInfo nodeInfo;
    private Boolean isSecured;

    public boolean isGrouped() {
        return isGrouped;
    }

    public void setGrouped(boolean grouped) {
        isGrouped = grouped;
    }

    private Boolean isGrouped;
    private int timeout;
    private int vidDevice;

    public int getVidDevice() {
        return vidDevice;
    }

    public void setVidDevice(int vidDevice) {
        this.vidDevice = vidDevice;
    }

    public SecurityDevice(NodeInfo nodeInfo, Boolean isSecured, int timeout,int vidDevice, Boolean isGrouped) {
        this.nodeInfo = nodeInfo;
        this.isSecured = isSecured;
        this.timeout = timeout;
        this.vidDevice = vidDevice;
        this.isGrouped = isGrouped;
    }
    public NodeInfo getNodeInfo() {
        return nodeInfo;
    }

    public void setNodeInfo(NodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    public Boolean getSecured() {
        return isSecured;
    }

    public void setSecured(Boolean secured) {
        isSecured = secured;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
