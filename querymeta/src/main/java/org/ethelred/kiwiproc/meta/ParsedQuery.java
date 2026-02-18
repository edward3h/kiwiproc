/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

import java.util.*;

public record ParsedQuery(String rawSql, String parsedSql, List<String> parameterNames) {

    public static ParsedQuery parse(String rawSql) {
        var parsedSql = new StringBuilder();
        var indices = new ArrayList<String>();
        var paramName = new StringBuilder();

        // States: 0=normal, 1=inParam, 2=inSingleQuote, 3=inLineComment, 4=inBlockComment
        int state = 0;
        int len = rawSql.length();

        for (int i = 0; i < len; i++) {
            char c = rawSql.charAt(i);
            char peek = (i + 1 < len) ? rawSql.charAt(i + 1) : '\0';

            if (state == 0) { // NORMAL
                if (c == '\'') {
                    parsedSql.append(c);
                    state = 2;
                } else if (c == '-' && peek == '-') {
                    parsedSql.append(c);
                    state = 3;
                } else if (c == '/' && peek == '*') {
                    parsedSql.append(c);
                    state = 4;
                } else if (c == ':' && peek == ':') {
                    // PostgreSQL ::cast syntax — output both colons and advance past the second
                    parsedSql.append(c);
                    parsedSql.append(peek);
                    i++;
                } else if (c == ':' && (Character.isLetter(peek) || peek == '_')) {
                    // start of a named parameter; don't output the ':'
                    state = 1;
                } else {
                    parsedSql.append(c);
                }
            } else if (state == 1) { // IN_PARAM
                if (Character.isLetterOrDigit(c) || c == '_') {
                    paramName.append(c);
                } else {
                    // end of parameter name; flush and reprocess current char in NORMAL state
                    indices.add(paramName.toString());
                    paramName.setLength(0);
                    parsedSql.append('?');
                    state = 0;
                    i--; // reprocess this character
                }
            } else if (state == 2) { // IN_SINGLE_QUOTE
                parsedSql.append(c);
                if (c == '\'') {
                    if (peek == '\'') {
                        // escaped single quote: include it and skip next
                        parsedSql.append(peek);
                        i++;
                    } else {
                        state = 0;
                    }
                }
            } else if (state == 3) { // IN_LINE_COMMENT
                parsedSql.append(c);
                if (c == '\n') {
                    state = 0;
                }
            } else if (state == 4) { // IN_BLOCK_COMMENT
                parsedSql.append(c);
                if (c == '*' && peek == '/') {
                    parsedSql.append(peek);
                    i++;
                    state = 0;
                }
            }
        }

        // Handle parameter name at end of input
        if (state == 1 && paramName.length() > 0) {
            indices.add(paramName.toString());
            parsedSql.append('?');
        }

        if (indices.isEmpty()) {
            return new ParsedQuery(rawSql, rawSql, List.of());
        }
        return new ParsedQuery(rawSql, parsedSql.toString(), List.copyOf(indices));
    }
}
