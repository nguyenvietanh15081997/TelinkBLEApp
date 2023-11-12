package com.example.customtelinkapp.Util;

import java.util.Random;

public class RandomRequestIdGenerator {
    private static final String characters = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int length = 8;
    private static final Random random = new Random();
    public static String generateRandomRequestId() {
        StringBuilder requestId = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(characters.length());
            requestId.append(characters.charAt(randomIndex));
        }

        return requestId.toString();
    }
}
