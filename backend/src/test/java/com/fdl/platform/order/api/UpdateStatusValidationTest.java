package com.fdl.platform.order.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fdl.platform.order.service.OrderService;
import com.fdl.platform.shared.config.JacksonConfig;
import com.fdl.platform.support.WebSliceSecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(OrderController.class)
@Import({WebSliceSecurityConfig.class, JacksonConfig.class})
class UpdateStatusValidationTest {

    private static final String PATH = "/api/v1/orders/22222222-2222-2222-2222-222222222222/status";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orders;

    private ResultActions send(String path, String json) throws Exception {
        return mvc.perform(patch(path).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}"})
    void missingOrNullStatusNamesTheStatusField(String body) throws Exception {
        send(PATH, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field=='status')]").exists());
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"PRONTO\"", "\"recebido\"", "\"Recebido\"", "1", "true", "\"\"", "[]", "{}"})
    void unknownOrNonTextualStatusIsMalformedRequest(String value) throws Exception {
        send(PATH, "{\"status\":" + value + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "", "[]", "{\"status\":"})
    void malformedJsonIsMalformedRequest(String body) throws Exception {
        send(PATH, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(orders);
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        send(PATH, "{\"status\":\"EM_PREPARO\",\"version\":3}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123", "not-a-uuid-at-all"})
    void idThatIsNotAUuidIsInvalidParameter(String id) throws Exception {
        send("/api/v1/orders/" + id + "/status", "{\"status\":\"EM_PREPARO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(orders);
    }

    @Test
    void nonJsonContentTypeIs415() throws Exception {
        mvc.perform(patch(PATH).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.TEXT_PLAIN).content("EM_PREPARO"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(orders);
    }
}
