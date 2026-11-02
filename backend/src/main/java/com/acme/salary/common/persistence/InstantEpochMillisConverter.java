package com.acme.salary.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

/** SQLite has no timestamp type; epoch millis are compact, sortable and timezone-free. */
@Converter(autoApply = true)
public class InstantEpochMillisConverter implements AttributeConverter<Instant, Long> {

    @Override
    public Long convertToDatabaseColumn(Instant value) {
        return value == null ? null : value.toEpochMilli();
    }

    @Override
    public Instant convertToEntityAttribute(Long value) {
        return value == null ? null : Instant.ofEpochMilli(value);
    }
}
