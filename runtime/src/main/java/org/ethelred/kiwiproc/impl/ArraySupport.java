/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.impl;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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

    public static int[] toIntArray(List<Integer> items) {
        var result = new int[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
    }

    public static long[] toLongArray(List<Long> items) {
        var result = new long[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
    }

    public static double[] toDoubleArray(List<Double> items) {
        var result = new double[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
    }

    public static float[] toFloatArray(List<Float> items) {
        var result = new float[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
    }

    public static short[] toShortArray(List<Short> items) {
        var result = new short[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
    }

    public static byte[] toByteArray(List<Byte> items) {
        var result = new byte[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
    }

    public static char[] toCharArray(List<Character> items) {
        var result = new char[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
    }

    public static boolean[] toBooleanArray(List<Boolean> items) {
        var result = new boolean[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
    }
}
