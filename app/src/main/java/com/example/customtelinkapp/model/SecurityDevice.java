package com.example.customtelinkapp.model;

public class SecurityDevice {
    private NodeInfo nodeInfo;
    private Boolean isSecured;

    public SecurityDevice(NodeInfo nodeInfo, Boolean isSecured, int timeout) {
        this.nodeInfo = nodeInfo;
        this.isSecured = isSecured;
        this.timeout = timeout;
    }

    private int timeout;

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
