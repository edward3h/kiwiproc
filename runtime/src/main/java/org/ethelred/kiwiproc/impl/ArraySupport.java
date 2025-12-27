/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.impl;

import java.util.Arrays;
import java.util.Iterator;

public class ArraySupport {
    private ArraySupport() {}

    public static Iterator<Integer> iterator(int... items) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < items.length;
            }

            @Override
            public Integer next() {
                return items[index++];
            }
        };
    }

    public static <T> Iterator<T> iterator(T... items) {
        return Arrays.asList(items).iterator();
    }
}
