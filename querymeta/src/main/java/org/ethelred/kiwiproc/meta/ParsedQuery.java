package org.ethelred.kiwiproc.meta;

import java.util.*;
import java.util.regex.Pattern;

public record ParsedQuery(String rawSql, String parsedSql, List<String> parameterNames) {
    private static final Pattern PARAMETER_REGEX =
            Pattern.compile("([^:\\\\]*)((?<![:]):([a-zA-Z0-9_]+))([^:]*)"); // borrowed from micronaut-data

    public static ParsedQuery parse(String rawSql) {
        var parsedSql = new StringBuilder();
        var indices = new ArrayList<String>();
        var matcher = PARAMETER_REGEX.matcher(rawSql);

        for (var i = 0; matcher.find(); i++) {
            parsedSql.append(matcher.group(1));
            parsedSql.append("?");
            parsedSql.append(matcher.group(4));

            var parameterName = matcher.group(3);
            indices.add(parameterName);
        }
        if (indices.isEmpty()) {
            return new ParsedQuery(rawSql, rawSql, List.of());
        }
        return new ParsedQuery(rawSql, parsedSql.toString(), List.copyOf(indices));
    }
}
