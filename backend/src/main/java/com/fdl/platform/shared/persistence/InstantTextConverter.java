package com.fdl.platform.shared.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;

/**
 * Datas como texto ISO-8601 UTC com exatamente 3 casas decimais. O tamanho fixo mantém a
 * ordenação lexicográfica da coluna igual à ordem cronológica.
 */
@Converter(autoApply = true)
public class InstantTextConverter implements AttributeConverter<Instant, String> {

    private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    @Override
    public String convertToDatabaseColumn(Instant value) {
        return value == null ? null : FORMAT.format(value.truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    public Instant convertToEntityAttribute(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
