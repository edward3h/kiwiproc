/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import java.util.List;

public record RecordType(String packageName, String className, List<RecordTypeComponent> components)
        implements KiwiType {
    @Override
    public boolean isSimple() {
        return false;
    }
}
