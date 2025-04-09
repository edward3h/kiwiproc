/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqlName sqlName = (SqlName) o;
        return name.equalsIgnoreCase(sqlName.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name.toLowerCase());
    }
}
