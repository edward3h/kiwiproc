/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor.types;

import com.karuslabs.utilitary.Logger;
import com.karuslabs.utilitary.type.TypeMirrors;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

public class TypeUtils extends TypeMirrors {
    private final Logger logger;

    /**
     * Creates a {@code TypeMirrors} with the given arguments.
     *
     * @param elements the {@code Elements}
     * @param types    the {@code Types}
     * @param logger
     */
    public TypeUtils(Elements elements, Types types, Logger logger) {
        super(elements, types);
        this.logger = logger;
    }

    public boolean isRecord(TypeMirror type) {
        return isSubtype(type, type(Record.class));
    }

    public String packageName(TypeElement typeElement) {
        for (Element el = typeElement; el != null; el = el.getEnclosingElement()) {
            if (el.getKind() == ElementKind.PACKAGE) {
                return ((PackageElement) el).getQualifiedName().toString();
            }
        }
        throw new IllegalArgumentException();
    }

    public @Nullable ValidCollection containerType(DeclaredType t) {
        for (var ct : ValidCollection.values()) {
            if (is(t, ct.javaType())) {
                return ct;
            }
        }
        return null;
    }

    public List<? extends RecordComponentElement> recordComponents(DeclaredType t) {
        return Objects.requireNonNull(asTypeElement(t)).getRecordComponents();
    }

    public boolean isBoxed(DeclaredType t) {
        try {
            unboxedType(t);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isNullable(DeclaredType t) {
        var annotations = t.getAnnotationMirrors();
        for (var annotationMirror : annotations) {
            if (isSameType(annotationMirror.getAnnotationType(), type(Nullable.class))) {
                return true;
            }
        }
        return false;
    }

    public KiwiType kiwiType(TypeMirror type) {
        return type.accept(new KiwiTypeVisitor(this), null);
    }

    public String fqcn(DeclaredType t) {
        var te = (TypeElement) t.asElement();
        return te.getQualifiedName().toString();
    }

    public String packageName(DeclaredType t) {
        return packageName((TypeElement) t.asElement());
    }

    public String className(DeclaredType t) {
        var te = (TypeElement) t.asElement();
        return te.getSimpleName().toString();
    }
}
