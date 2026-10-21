package com.fdl.platform.order.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fdl.platform.order.service.OrderService;
import com.fdl.platform.shared.config.JacksonConfig;
import com.fdl.platform.support.WebSliceSecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(OrderController.class)
@Import({WebSliceSecurityConfig.class, JacksonConfig.class})
class ListAndGetOrderValidationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orders;

    private ResultActions call(String url) throws Exception {
        return mvc.perform(get(url).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALIDO", "recebido", "Recebido", "1"})
    void invalidStatusFilterIsInvalidParameter(String status) throws Exception {
        call("/api/v1/orders?status=" + status).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=-5", "size=501", "size=1000"})
    void outOfRangePagingIsAValidationError(String query) throws Exception {
        call("/api/v1/orders?" + query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=abc", "size=dez", "page=1.5"})
    void nonNumericPagingIsInvalidParameter(String query) throws Exception {
        call("/api/v1/orders?" + query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"senha,asc", "passwordHash", "id,asc", "createdAt,sideways", "createdAt,asc,extra",
            "createdBy", "'; drop table orders"})
    void sortOutsideTheAllowlistIsAValidationError(String sort) throws Exception {
        call("/api/v1/orders?sort=" + java.net.URLEncoder.encode(sort, java.nio.charset.StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdAt", "createdAt,desc", "updatedAt,asc", "status,asc", "totalCents,desc"})
    void allowedSortsPass(String sort) throws Exception {
        org.mockito.Mockito.when(orders.findAll(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        call("/api/v1/orders?sort=" + sort).andExpect(status().isOk());
    }

    @Test
    void sizeLimitsAreInclusive() throws Exception {
        org.mockito.Mockito.when(orders.findAll(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 500, 0, 0));

        call("/api/v1/orders?size=1").andExpect(status().isOk());
        call("/api/v1/orders?size=500").andExpect(status().isOk());
        call("/api/v1/orders?page=0").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123", "not-a-uuid-at-all-not-a-uuid-at-all!"})
    void getWithAnIdThatIsNotAUuidIsInvalidParameter(String id) throws Exception {
        call("/api/v1/orders/" + id).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(orders);
    }
}
