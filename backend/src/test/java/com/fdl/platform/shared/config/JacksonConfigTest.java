package com.fdl.platform.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fdl.platform.support.WebSliceSecurityConfig;
import com.fdl.platform.support.ProbeController;
import com.fdl.platform.support.ProbeController.Item;
import com.fdl.platform.support.ProbeController.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/** Garante que o JsonMapper da aplicação é estrito onde os cenários 400 dependem disso. */
@WebMvcTest(ProbeController.class)
@Import({WebSliceSecurityConfig.class, JacksonConfig.class})
class JacksonConfigTest {

    @Autowired
    JsonMapper mapper;

    private Item read(String json) {
        return mapper.readValue(json, Item.class);
    }

    @Test
    void validJsonIsReadNormally() {
        assertThat(read("{\"name\":\"Pizza\",\"quantity\":2,\"kind\":\"B\"}"))
                .isEqualTo(new Item("Pizza", 2, Kind.B));
    }

    @Test
    void unknownPropertyFails() {
        assertThatThrownBy(() -> read("{\"name\":\"Pizza\",\"quantity\":2,\"kind\":\"B\",\"totalCents\":1}"))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "2.0", "\"dois\"", "\"2\"", "\"\"", "true"})
    void integerFieldDoesNotCoerceFloatsOrStrings(String quantity) {
        assertThatThrownBy(() -> read("{\"name\":\"Pizza\",\"quantity\":" + quantity + ",\"kind\":\"A\"}"))
                .isInstanceOf(MismatchedInputException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"a\"", "\"b\"", "\"C\"", "0", "1"})
    void enumsAreCaseSensitiveAndNotReadFromNumbers(String kind) {
        assertThatThrownBy(() -> read("{\"name\":\"Pizza\",\"quantity\":1,\"kind\":" + kind + "}"))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void nullForAPrimitiveFails() {
        assertThatThrownBy(() -> read("{\"name\":\"Pizza\",\"quantity\":null,\"kind\":\"A\"}"))
                .isInstanceOf(MismatchedInputException.class)
                .hasMessageContaining("Cannot map `null` into type `int`");
    }

    @Test
    void trailingContentAfterTheJsonValueFails() {
        assertThatThrownBy(() -> read("{\"name\":\"Pizza\",\"quantity\":1,\"kind\":\"A\"} {\"x\":1}"))
                .isInstanceOf(tools.jackson.core.JacksonException.class);
    }
}
