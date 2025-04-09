/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

public enum ResultPart {
    SIMPLE {
        @Override
        public String prefix() {
            return "";
        }
    },
    KEY,
    VALUE;

    public String prefix() {
        return name().toLowerCase();
    }
}
