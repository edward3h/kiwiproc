/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

public enum RowCount {
    EXACTLY_ONE, // non-null simple type or record
    ZERO_OR_ONE, // nullable simple type or record
    MANY // collection or Map
}
