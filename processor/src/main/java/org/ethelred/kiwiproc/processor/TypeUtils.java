package org.ethelred.kiwiproc.processor;

import com.karuslabs.utilitary.Logger;
import com.karuslabs.utilitary.type.TypeMirrors;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor14;
import javax.lang.model.util.Types;
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public String toString(TypeMirror type) {
        return new ToStringVisitor().visit(type);
    }

    Optional<TypeMirror> sqlType(ColumnMetaData target) {
        TypeMirror targetType = null;
        var baseClass = baseSqlType(target.sqlType());
        if (baseClass == null) {
if (target.sqlType() == JDBCType.ARRAY) {
    baseClass = arraySqlType(target);
    targetType = type(baseClass);
}
        } else if (baseClass.isPrimitive() && target.nullable()) {
            targetType = box(type(baseClass));
        } else {
            targetType = type(baseClass);
        }

        return Optional.ofNullable(targetType);
    }

    private Class<?> arraySqlType(ColumnMetaData target) {
        return null;
    }

    @Nullable
    Class<?> baseSqlType(JDBCType jdbcType) {
        return switch (jdbcType) {
            case BIT, BOOLEAN -> boolean.class;
            case BINARY, LONGVARBINARY, VARBINARY -> byte[].class;
            case CHAR, LONGNVARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, VARCHAR -> String.class;
            case DATE -> java.sql.Date.class;
            case DECIMAL -> BigDecimal.class;
            case DOUBLE -> double.class;
            case FLOAT, REAL -> float.class;
            case INTEGER -> int.class;
            case BIGINT -> long.class;
            case SMALLINT -> short.class;
            case TINYINT -> byte.class;
            case TIME, TIME_WITH_TIMEZONE -> Time.class;
            case TIMESTAMP, TIMESTAMP_WITH_TIMEZONE -> Timestamp.class;
            case ARRAY, OTHER -> null; // null means additional checks are required
            default -> throw new IllegalArgumentException("Unsupported type " + jdbcType);
        };
    }

    public ReturnType returnType(TypeMirror returnType) {
        return new ReturnTypeVisitor(this).visit(returnType).orElseThrow();
    }

    public @Nullable ContainerType containerType(DeclaredType t) {
        for (var ct: ContainerType.values()) {
            if (is(t, ct.javaType())) {
                return ct;
            }
        }
        return null;
    }

    @Override
    public boolean isSameType(TypeMirror t1, TypeMirror t2) {
        var result = super.isSameType(t1, t2);
        logger.note(null, "isSameType(%s, %s) = %s".formatted(t1, t2, result));
        return result;
    }

    public List<? extends RecordComponentElement> recordComponents(DeclaredType t) {
        return Objects.requireNonNull(asTypeElement(t)).getRecordComponents();
    }

    class ToStringVisitor extends SimpleTypeVisitor14<String,Void> {
        @Override
        protected String defaultAction(TypeMirror e, Void unused) {
            throw new UnsupportedOperationException(String.valueOf(e));
        }

        @Override
        public String visitPrimitive(PrimitiveType t, Void unused) {
            return t.getKind().name().toLowerCase();
        }

        @Override
        public String visitArray(ArrayType t, Void unused) {
            return visit(t.getComponentType()) + "[]";
        }

        @Override
        public String visitDeclared(DeclaredType t, Void unused) {
            var te = (TypeElement) t.asElement();
            if ("java.lang".equals(packageName(te))) {
                return te.getSimpleName().toString();
            }
            return String.valueOf(t);//TODO
        }

        @Override
        public String visitNoType(NoType t, Void unused) {
            if (t.getKind() == TypeKind.VOID) {
                return "void";
            }
            throw new UnsupportedOperationException(String.valueOf(t));
        }
    }
}
