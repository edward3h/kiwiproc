package org.ethelred.kiwiproc.processor;

import java.util.List;

public record RecordType(String packageName, String className, List<RecordTypeComponent> components)
        implements KiwiType {
    public record RecordTypeComponent(String name, KiwiType type) {}
}
