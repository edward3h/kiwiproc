package org.ethelred.kiwiproc.processor;

import java.time.LocalDate;
import org.mapstruct.Mapper;

@Mapper
public interface TestMapper {
    record PrimitiveIntRecord(int value) {}

    record StringRecord(String value) {}

    record LocalDateRecord(LocalDate value) {}

    StringRecord toString(PrimitiveIntRecord x);

    LocalDateRecord toLocalDate(StringRecord x);
}
