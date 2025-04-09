/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor.types;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.DeclaredType;
import org.ethelred.kiwiproc.meta.JavaName;

public class RecordTypeHandler extends DeclaredTypeHandler {
    RecordTypeHandler(KiwiTypeVisitor visitor, TypeUtils utils) {
        super(visitor, utils);
    }

    @Override
    public KiwiType apply(DeclaredType t) {
        var recordTypeComponents = new ArrayList<RecordTypeComponent>();
        for (var component : utils.recordComponents(t)) {
            var componentType = visit(component.asType());
            if (componentType instanceof CollectionType collectionType) {
                componentType = collectionType.asSimple();
            }
            if (componentType.isSimple()) {
                recordTypeComponents.add(new RecordTypeComponent(
                        new JavaName(component.getSimpleName().toString()), componentType));
            } else {
                return KiwiType.unsupported();
            }
        }
        boolean isNullable = utils.isNullable(t);
        return new RecordType(utils.packageName(t), utils.className(t), isNullable, List.copyOf(recordTypeComponents));
    }

    @Override
    public boolean test(DeclaredType declaredType) {
        return utils.isRecord(declaredType);
    }
}
