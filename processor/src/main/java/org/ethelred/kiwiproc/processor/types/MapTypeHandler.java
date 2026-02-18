/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

import java.util.Map;
import java.util.SortedMap;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public class MapTypeHandler extends DeclaredTypeHandler {
    MapTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    @Override
    public KiwiType apply(DeclaredType t) {
        var typeArguments = t.getTypeArguments();
        KiwiType keyType = visit(typeArguments.get(0));
        if (!(keyType.isSimple() || keyType instanceof RecordType)) {
            return KiwiType.unsupported();
        }
        KiwiType valueType = visit(typeArguments.get(1));
        if (!(valueType.isSimple() || valueType instanceof RecordType || valueType instanceof CollectionType)) {
            return KiwiType.unsupported();
        }
        boolean isSortedMap = isSameType(t, SortedMap.class);
        return new MapType(
                keyType.withIsNullable(false),
                valueType.withIsNullable(false),
                isComparable(typeArguments.get(0)),
                isSortedMap);
    }

    private boolean isComparable(TypeMirror typeMirror) {
        return utils.isSubtype(utils.erasure(typeMirror), utils.erasure(utils.type(Comparable.class)));
    }

    @Override
    public boolean test(DeclaredType t) {
        return isSameType(t, Map.class) || isSameType(t, SortedMap.class);
    }
}
