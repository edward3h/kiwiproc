/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processor.types;

import java.util.stream.Stream;
import javax.lang.model.type.DeclaredType;

public class StreamTypeHandler extends DeclaredTypeHandler {

    StreamTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    @Override
    public boolean test(DeclaredType t) {
        return isSameType(t, Stream.class);
    }

    @Override
    public KiwiType apply(DeclaredType t) {
        var containedType = t.getTypeArguments().get(0);
        var containedKiwiType = visit(containedType);
        if (containedKiwiType instanceof RecordType || containedKiwiType.isSimple()) {
            return new StreamType(containedKiwiType.withIsNullable(false));
        }
        return KiwiType.unsupported("Stream element type must be a simple type or record, got: " + containedKiwiType);
    }
}
