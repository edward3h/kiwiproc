package org.ethelred.kiwiproc.impl;

import java.util.Iterator;

public class ArraySupport {
    private ArraySupport(){}

    public static Iterator<Integer> iterator(int... ints) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < ints.length;
            }

            @Override
            public Integer next() {
                return ints[index++];
            }
        };
    }
}
