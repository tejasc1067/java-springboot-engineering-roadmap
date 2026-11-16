package com.example.shortener;

public final class Base62 {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private Base62() { }

    public static String encode(long value) {
        if (value < 0) throw new IllegalArgumentException("negative: " + value);
        if (value == 0) return "0";
        StringBuilder code = new StringBuilder();
        while (value > 0) {
            code.append(ALPHABET.charAt((int) (value % 62)));
            value /= 62;
        }
        return code.reverse().toString();
    }

    public static long decode(String code) {
        long value = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("not base62: " + code);
            value = value * 62 + digit;
        }
        return value;
    }

    /** How many different codes of exactly this length exist: 62^length. */
    public static long capacity(int length) {
        long n = 1;
        for (int i = 0; i < length; i++) n = Math.multiplyExact(n, 62);
        return n;
    }

    static String padded(long value, int length) {
        String code = encode(value);
        return "0".repeat(length - code.length()) + code;
    }
}
