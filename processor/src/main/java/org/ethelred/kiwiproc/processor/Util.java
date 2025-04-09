/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Util {
    private Util() {}

    static Pattern delimiter = Pattern.compile("[\\W_]+|(?=\\p{Upper})");

    public static String toTitleCase(String input) {
        return delimiter.splitAsStream(input).map(Util::capitalizeFirst).collect(Collectors.joining());
    }

    static String capitalizeFirst(String input) {
        if (input.length() < 2) {
            return input.toUpperCase();
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
}
