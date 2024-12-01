/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

/**
 * Wrapper to identify that a name is from a SQL column or parameter.
 * @param name
 */
public record SqlName(String name) {
    public static final SqlName PARAMETER = new SqlName("parameter");

    @Override
    public String toString() {
        return name;
    }

    public boolean equivalent(JavaName javaName) {
        return NameUtils.equivalent(this, javaName);
    }
}
