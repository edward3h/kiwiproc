/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import java.util.List;

@FunctionalInterface
public interface NativeEnumLookup {
    List<String> getConstants(String typeName);
}
