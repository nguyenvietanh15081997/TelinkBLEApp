package com.example.customtelinkapp.Util;

public class Converter {
    public static String convertToUUID(String input) {

        // Chuyển đổi chuỗi thành chuỗi UUID 8-4-4-12

        return input.substring(0, 8) +
                "-" +
                input.substring(8, 12) +
                "-" +
                input.substring(12, 16) +
                "-" +
                input.substring(16, 20) +
                "-" +
                input.substring(20);
    }
    public static String convertToHexString(String input) {
        return input.replace("-", "").toUpperCase();
    }
    public static byte[] intToByteArray(int num) {
        byte[] byteArray = new byte[2];
        byteArray[0] = (byte) ((num >> 8) & 0xFF);
        byteArray[1] = (byte) (num & 0xFF);
        return byteArray;
    }
}
