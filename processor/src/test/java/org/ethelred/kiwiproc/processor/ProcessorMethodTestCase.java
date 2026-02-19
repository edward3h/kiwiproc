/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import com.google.testing.compile.JavaFileObjects;
import java.util.*;
import javax.tools.JavaFileObject;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;

class ProcessorMethodTestCase {
    static String MyDAO = "com.example.MyDAO";

    @Language(value = "JAVA", prefix = "class X {", suffix = "}")
    String method;

    Map<String, String> additionalSources = new HashMap<>();
    int expectedErrorCount = -1;
    Set<String> expectedErrorMessages = new HashSet<>();

    @Nullable String displayName;

    private ProcessorMethodTestCase(@Language(value = "JAVA", prefix = "class X {", suffix = "}") String method) {
        this.method = method;
    }

    static ProcessorMethodTestCase method(@Language(value = "JAVA", prefix = "class X {", suffix = "}") String method) {
        return new ProcessorMethodTestCase(method);
    }

    ProcessorMethodTestCase withAdditionalSource(String fullyQualifiedName, @Language("JAVA") String source) {
        if (MyDAO.equals(fullyQualifiedName) || additionalSources.containsKey(fullyQualifiedName)) {
            throw new IllegalArgumentException("Already defined " + fullyQualifiedName);
        }
        additionalSources.put(fullyQualifiedName, source);
        return this;
    }

    ProcessorMethodTestCase withExpectedErrorCount(int count) {
        expectedErrorCount = count;
        return this;
    }

    ProcessorMethodTestCase withExpectedErrorMessage(String message) {
        expectedErrorMessages.add(message);
        return this;
    }

    ProcessorMethodTestCase withDisplayName(String name) {
        this.displayName = name;
        return this;
    }

    ProcessorMethodTestCase succeeds() {
        expectedErrorCount = 0;
        return this;
    }

    @Override
    public String toString() {
        return displayName == null ? method : displayName;
    }

    List<JavaFileObject> toFileObjects() {
        validateSelf();
        List<JavaFileObject> result = new ArrayList<>();
        result.add(methodToFileObject());
        additionalSources.forEach((fullyQualifiedName, source) ->
                result.add(JavaFileObjects.forSourceString(fullyQualifiedName, source)));
        return result;
    }

    private void validateSelf() {
        if (expectedErrorCount < 0) {
            throw new AssertionError("Expected Error Count was not set on test case.");
        }
    }

    private JavaFileObject methodToFileObject() {
        return JavaFileObjects.forSourceString(MyDAO, """
                package com.example;

                import java.util.*;
                import org.ethelred.kiwiproc.annotation.*;

                @DAO
                public interface MyDAO {
                    %s
                }""".formatted(method));
    }
}
