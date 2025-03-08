/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

class NameUtils {
    private NameUtils() {}

    /*
    The convention (in Postgres at least) is that identifiers are in lower snake case. However, identifiers can be double-quoted, which can accept any string.
     */
    static boolean equivalent(SqlName sqlName, JavaName javaName) {
        // shortcut for already equal
        if (sqlName.name().equals(javaName.name())) {
            return true;
        }

        var camelBuilder = new StringBuilder();
        var uppercaseNext = false;
        for (var c : sqlName.name().toCharArray()) {
            if (c == '_') {
                uppercaseNext = true;
            } else if (Character.isLowerCase(c)) {
                if (uppercaseNext) {
                    camelBuilder.append(Character.toUpperCase(c));
                    uppercaseNext = false;
                } else {
                    camelBuilder.append(c);
                }
            } else {
                // SQL name does not match expected convention. Give up.
                return false;
            }
        }
        return camelBuilder.toString().equals(javaName.name());
    }
}
