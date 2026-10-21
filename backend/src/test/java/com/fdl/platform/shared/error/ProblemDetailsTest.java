package com.fdl.platform.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fdl.platform.shared.config.JacksonConfig;
import com.fdl.platform.shared.web.TraceIdFilter;
import com.fdl.platform.support.ProbeController;
import com.fdl.platform.support.WebSliceSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ProbeController.class)
@Import({WebSliceSecurityConfig.class, JacksonConfig.class})
class ProblemDetailsTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    MockMvc mvc;

    private ResultActions postItems(String json) throws Exception {
        return mvc.perform(post("/probe/items").with(user("tester")).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions getAs(String path) throws Exception {
        return mvc.perform(get(path).with(user("tester")));
    }

    // ---- formato comum ----

    @Test
    void everyErrorCarriesTypeTitleStatusDetailInstanceCodeAndTraceId() throws Exception {
        getAs("/probe/throw/order-not-found")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:fdl:problem:order-not-found"))
                .andExpect(jsonPath("$.title").value("Pedido não encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Pedido 00000000-0000-0000-0000-000000000001 não encontrado"))
                .andExpect(jsonPath("$.instance").value("/probe/throw/order-not-found"))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void traceIdInTheBodyMatchesTheResponseHeader() throws Exception {
        var result = getAs("/probe/throw/email").andReturn().getResponse();

        var headerValue = result.getHeader(TraceIdFilter.HEADER);
        assertThat(headerValue).isNotBlank();
        assertThat(result.getContentAsString()).contains("\"traceId\":\"" + headerValue + "\"");
    }

    @Test
    void aClientProvidedTraceIdIsEchoedInHeaderAndBody() throws Exception {
        mvc.perform(get("/probe/throw/email").with(user("tester")).header(TraceIdFilter.HEADER, "client-trace-0001"))
                .andExpect(header().string(TraceIdFilter.HEADER, "client-trace-0001"))
                .andExpect(jsonPath("$.traceId").value("client-trace-0001"));
    }

    // ---- 400: corpo ----

    @Test
    void emptyBodyListsTheRequiredFields() throws Exception {
        postItems("{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("items"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void validationErrorsPointToTheNestedField() throws Exception {
        postItems("{\"items\":[{\"name\":\"\",\"quantity\":100,\"kind\":null}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field=='items[0].name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='items[0].quantity')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='items[0].kind')]").exists());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        postItems("{\"items\": [")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void unknownFieldIsRejectedAndNamed() throws Exception {
        postItems("{\"items\":[{\"name\":\"Pizza\",\"quantity\":1,\"kind\":\"A\"}],\"totalCents\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("totalCents"));
    }

    @ParameterizedTest(name = "quantity = {0}")
    @CsvSource(delimiter = '|', value = {"1.5", "\"dois\"", "\"2\"", "true", "[1]", "\"\""})
    void nonIntegerQuantityIsRejectedAndTheItemFieldIsNamed(String quantity) throws Exception {
        postItems("{\"items\":[{\"name\":\"Pizza\",\"quantity\":" + quantity + ",\"kind\":\"A\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @ParameterizedTest(name = "kind = {0}")
    @CsvSource(delimiter = '|', value = {"\"a\"", "\"C\"", "1", "\"\""})
    void invalidEnumIsRejectedAndTheFieldIsNamed(String kind) throws Exception {
        postItems("{\"items\":[{\"name\":\"Pizza\",\"quantity\":1,\"kind\":" + kind + "}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].kind"));
    }

    @Test
    void nullForAPrimitiveIsRejected() throws Exception {
        postItems("{\"items\":[{\"name\":\"Pizza\",\"quantity\":null,\"kind\":\"A\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @Test
    void validPayloadIsAccepted() throws Exception {
        postItems("{\"items\":[{\"name\":\"Pizza\",\"quantity\":2,\"kind\":\"B\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    // ---- 400: parâmetros ----

    @ParameterizedTest(name = "size = {0}")
    @CsvSource({"0", "-1", "501"})
    void outOfRangeParameterIsAValidationError(String size) throws Exception {
        getAs("/probe/page?size=" + size)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void parameterOfTheWrongTypeIsInvalidParameter() throws Exception {
        getAs("/probe/page?size=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void missingRequiredParameterIsInvalidParameter() throws Exception {
        getAs("/probe/required")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void pathVariableThatIsNotAUuidIsInvalidParameter() throws Exception {
        getAs("/probe/uuid/abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void constraintViolationExceptionIsAValidationError() throws Exception {
        getAs("/probe/throw/constraint")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
    }

    // ---- protocolo HTTP ----

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        mvc.perform(post("/probe/items").with(user("tester")).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void wrongHttpMethodIs405() throws Exception {
        mvc.perform(put("/probe/page").with(user("tester")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownPathIs404InTheStandardFormat() throws Exception {
        getAs("/probe/does-not-exist")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ---- autenticação ----

    @Test
    void badCredentialsAre401WithAGenericMessage() throws Exception {
        getAs("/probe/throw/credentials")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void otherAuthenticationFailuresAre401Unauthenticated() throws Exception {
        getAs("/probe/throw/unauthenticated")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void accessDeniedIs403() throws Exception {
        getAs("/probe/throw/denied")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- regras de negócio ----

    @Test
    void duplicatedEmailIs409() throws Exception {
        getAs("/probe/throw/email")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void invalidStatusTransitionIs409AndExplainsTheRule() throws Exception {
        getAs("/probe/throw/transition")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("Não é possível ir de SAIU_PARA_ENTREGA para CANCELADO"))
                .andExpect(jsonPath("$.currentStatus").value("SAIU_PARA_ENTREGA"))
                .andExpect(jsonPath("$.requestedStatus").value("CANCELADO"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("ENTREGUE"))
                .andExpect(jsonPath("$.allowedTransitions.length()").value(1));
    }

    @Test
    void optimisticLockFailureIs409ConcurrentModification() throws Exception {
        getAs("/probe/throw/concurrent")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // ---- 500 ----

    @Test
    void unexpectedFailureIs500WithoutLeakingMessageOrStackTrace() throws Exception {
        var body = getAs("/probe/throw/unexpected")
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("segredo interno").doesNotContain("jdbc:sqlite")
                .doesNotContain("IllegalStateException").doesNotContain("\tat ").doesNotContain("stackTrace");
    }
}
