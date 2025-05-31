package com.wastech.url_shortener.util;

public class Base62 {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    public static String encode(long num) {
        if (num == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(ALPHABET.charAt((int) (num % BASE)));
            num /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String str) {
        long num = 0;
        for (int i = 0; i < str.length(); i++) {
            num = num * BASE + ALPHABET.indexOf(str.charAt(i));
        }
        return num;
    }

    // You might want to pad short codes to a minimum length, e.g., 6 characters
    public static String encodeWithPadding(long num, int minLength) {
        String encoded = encode(num);
        if (encoded.length() < minLength) {
            return String.format("%" + (minLength - encoded.length()) + "s", "")
                .replace(' ', '0') + encoded;
        }
        return encoded;
    }
}