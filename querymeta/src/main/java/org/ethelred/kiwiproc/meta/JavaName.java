/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.meta;

// No need to check if 'name' is a valid Java identifier: it will always come from the compiler.
public record JavaName(String name) {
    @Override
    public String toString() {
        return name;
    }

    public boolean equivalent(SqlName sqlName) {
        return NameUtils.equivalent(sqlName, this);
    }
}
