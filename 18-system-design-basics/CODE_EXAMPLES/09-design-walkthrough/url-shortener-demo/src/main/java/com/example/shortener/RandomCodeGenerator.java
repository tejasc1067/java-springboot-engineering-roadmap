package com.example.shortener;

import java.util.Random;

/** Random base62 characters. Unguessable and needs no coordination, but the store must reject duplicates. */
public class RandomCodeGenerator implements CodeGenerator {

    private final Random random;
    private final int length;

    public RandomCodeGenerator(Random random, int length) {
        this.random = random;
        this.length = length;
    }

    @Override
    public String generate(String longUrl) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) code.append(Base62.ALPHABET.charAt(random.nextInt(62)));
        return code.toString();
    }
}
