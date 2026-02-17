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

    public static Iterator<Long> iterator(long... items) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < items.length;
            }

            @Override
            public Long next() {
                return items[index++];
            }
        };
    }

    public static Iterator<Double> iterator(double... items) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < items.length;
            }

            @Override
            public Double next() {
                return items[index++];
            }
        };
    }

    public static Iterator<Float> iterator(float... items) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < items.length;
            }

            @Override
            public Float next() {
                return items[index++];
            }
        };
    }

    public static Iterator<Short> iterator(short... items) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < items.length;
            }

            @Override
            public Short next() {
                return items[index++];
            }
        };
    }

    public static Iterator<Byte> iterator(byte... items) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < items.length;
            }

            @Override
            public Byte next() {
                return items[index++];
            }
        };
    }

    public static Iterator<Character> iterator(char... items) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < items.length;
            }

            @Override
            public Character next() {
                return items[index++];
            }
        };
    }

    public static Iterator<Boolean> iterator(boolean... items) {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < items.length;
            }

            @Override
            public Boolean next() {
                return items[index++];
            }
        };
    }

    public static <T> Iterator<T> iterator(T... items) {
        return Arrays.asList(items).iterator();
    }
}
