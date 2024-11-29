package org.ethelred.kiwiproc.example;

public record Country(int id, String name, String code) {
    public Country {
        if (code.length() != 3) {
            throw new IllegalArgumentException("Country code must be three letters.");
        }
    }
}
