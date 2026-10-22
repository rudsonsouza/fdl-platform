package com.fdl.platform.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fdl.platform.order.domain.InvalidStatusTransitionException;
import com.fdl.platform.order.domain.OrderNotFoundException;
import com.fdl.platform.order.domain.OrderStatus;
import com.fdl.platform.order.service.OrderService;
import com.fdl.platform.shared.config.JacksonConfig;
import com.fdl.platform.support.OrderJson;
import com.fdl.platform.support.WebSliceSecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(OrderController.class)
@Import({WebSliceSecurityConfig.class, JacksonConfig.class})
class OrderControllerTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orders;

    private static RequestPostProcessor operator() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    private static OrderResponse response(OrderStatus status, List<OrderStatus> allowed) {
        return new OrderResponse(ORDER, new OrderResponse.Customer("Maria Silva", "11987654321"),
                List.of(new OrderResponse.Item(UUID.randomUUID(), "Pizza", 1, 5490, 5490)),
                new OrderResponse.Address("Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP",
                        "01310100"),
                status, 5490, allowed,
                List.of(new OrderResponse.HistoryEntry(null, OrderStatus.RECEBIDO, USER, NOW)), USER, NOW, NOW);
    }

    @Test
    void createReturns201WithLocationAndTheCreatedOrder() throws Exception {
        when(orders.create(any(CreateOrderRequest.class), eq(USER)))
                .thenReturn(response(OrderStatus.RECEBIDO, List.of(OrderStatus.EM_PREPARO, OrderStatus.CANCELADO)));

        mvc.perform(post("/api/v1/orders").with(operator()).contentType(MediaType.APPLICATION_JSON)
                .content(OrderJson.valid()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + ORDER))
                .andExpect(jsonPath("$.id").value(ORDER.toString()))
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.totalCents").value(5490))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("EM_PREPARO"))
                .andExpect(jsonPath("$.history[0].from").doesNotExist());
    }

    @Test
    void createPassesTheTokenUserAsCreator() throws Exception {
        when(orders.create(any(), any())).thenReturn(response(OrderStatus.RECEBIDO, List.of()));

        mvc.perform(post("/api/v1/orders").with(operator()).contentType(MediaType.APPLICATION_JSON)
                .content(OrderJson.valid())).andExpect(status().isCreated());

        var request = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orders).create(request.capture(), eq(USER));
        org.assertj.core.api.Assertions.assertThat(request.getValue().items()).hasSize(2);
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(OrderJson.valid()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/" + ORDER)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/orders/" + ORDER + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"EM_PREPARO\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void listWithoutParametersUsesTheDefaults() throws Exception {
        when(orders.findAll(eq(null), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(new OrderSummaryResponse(ORDER, "Maria Silva",
                        OrderStatus.RECEBIDO, 3, 5490, NOW, NOW)), 0, 100, 1, 1));

        mvc.perform(get("/api/v1/orders").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ORDER.toString()))
                .andExpect(jsonPath("$.content[0].customerName").value("Maria Silva"))
                .andExpect(jsonPath("$.content[0].totalItems").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findAll(eq(null), pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getSort().getOrderFor("createdAt"))
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listForwardsStatusPageSizeAndSort() throws Exception {
        when(orders.findAll(any(), any(Pageable.class))).thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));

        mvc.perform(get("/api/v1/orders?status=EM_PREPARO&page=1&size=10&sort=totalCents,asc").with(operator()))
                .andExpect(status().isOk());

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findAll(eq(OrderStatus.EM_PREPARO), pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getSort().getOrderFor("totalCents"))
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void getReturnsTheFullOrderWithHistoryAndAllowedTransitions() throws Exception {
        when(orders.findById(ORDER))
                .thenReturn(response(OrderStatus.EM_PREPARO, List.of(OrderStatus.SAIU_PARA_ENTREGA, OrderStatus.CANCELADO)));

        mvc.perform(get("/api/v1/orders/" + ORDER).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.phone").value("11987654321"))
                .andExpect(jsonPath("$.items[0].subtotalCents").value(5490))
                .andExpect(jsonPath("$.deliveryAddress.zipCode").value("01310100"))
                .andExpect(jsonPath("$.history[0].to").value("RECEBIDO"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("SAIU_PARA_ENTREGA"))
                .andExpect(jsonPath("$.allowedTransitions[1]").value("CANCELADO"));
    }

    @Test
    void getOfAnUnknownOrderIs404() throws Exception {
        when(orders.findById(ORDER)).thenThrow(new OrderNotFoundException(ORDER));

        mvc.perform(get("/api/v1/orders/" + ORDER).with(operator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void patchStatusReturnsTheUpdatedOrder() throws Exception {
        when(orders.updateStatus(ORDER, OrderStatus.EM_PREPARO, USER))
                .thenReturn(response(OrderStatus.EM_PREPARO, List.of(OrderStatus.SAIU_PARA_ENTREGA)));

        mvc.perform(patch("/api/v1/orders/" + ORDER + "/status").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PREPARO"));
    }

    @Test
    void patchStatusOfAnUnknownOrderIs404() throws Exception {
        when(orders.updateStatus(any(), any(), any())).thenThrow(new OrderNotFoundException(ORDER));

        mvc.perform(patch("/api/v1/orders/" + ORDER + "/status").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void invalidTransitionIs409AndExplainsTheRule() throws Exception {
        when(orders.updateStatus(any(), any(), any()))
                .thenThrow(new InvalidStatusTransitionException(OrderStatus.SAIU_PARA_ENTREGA, OrderStatus.CANCELADO));

        mvc.perform(patch("/api/v1/orders/" + ORDER + "/status").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("Não é possível ir de SAIU_PARA_ENTREGA para CANCELADO"))
                .andExpect(jsonPath("$.currentStatus").value("SAIU_PARA_ENTREGA"))
                .andExpect(jsonPath("$.requestedStatus").value("CANCELADO"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("ENTREGUE"));
    }

    @Test
    void staleVersionIs409ConcurrentModification() throws Exception {
        when(orders.updateStatus(any(), any(), any())).thenThrow(new OptimisticLockingFailureException("stale"));

        mvc.perform(patch("/api/v1/orders/" + ORDER + "/status").with(operator())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }
}
