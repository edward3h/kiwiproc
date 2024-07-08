package org.ethelred.kiwiproc.processor;

import java.util.Objects;

public record TypeMapping(String source, String target) {
    public static final TypeMapping VOID = new TypeMapping("void", "void");

    public boolean isIdentity() {
        return Objects.equals(source, target);
    }

    public String methodName() {
        var builder = new StringBuilder("to");
        boolean up = true;
        for(var c: target.toCharArray()) {
            if (!Character.isJavaIdentifierPart(c)) {
                up = true;
            } else if (up) {
                builder.append(Character.toUpperCase(c));
                up = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
