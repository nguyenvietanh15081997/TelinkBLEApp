package com.example.customtelinkapp.Service.Mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class CustomMqttCallback implements MqttCallbackExtended {
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        // TODO: Handle connect complete event
    }

    @Override
    public void connectionLost(Throwable cause) {
        // TODO: Handle connection lost event
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // TODO: Handle message arrived event
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // TODO: Handle delivery complete event
    }
}