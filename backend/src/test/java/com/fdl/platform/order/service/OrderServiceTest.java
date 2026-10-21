package com.fdl.platform.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fdl.platform.order.api.AddressRequest;
import com.fdl.platform.order.api.CreateOrderRequest;
import com.fdl.platform.order.api.CustomerRequest;
import com.fdl.platform.order.api.OrderItemRequest;
import com.fdl.platform.order.domain.InvalidStatusTransitionException;
import com.fdl.platform.order.domain.Order;
import com.fdl.platform.order.domain.OrderNotFoundException;
import com.fdl.platform.order.domain.OrderStatus;
import com.fdl.platform.order.repository.OrderRepository;
import com.fdl.platform.support.Users;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class OrderServiceTest {

    private static final UUID USER = UUID.randomUUID();

    OrderRepository orders = mock(OrderRepository.class);
    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, Users.CLOCK);
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static CreateOrderRequest request() {
        return new CreateOrderRequest(new CustomerRequest(" Maria Silva ", "11987654321"),
                List.of(new OrderItemRequest("Pizza", 1, 5490L), new OrderItemRequest("Refrigerante", 2, 1200L)),
                new AddressRequest("Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310100"));
    }

    private Order existing(OrderStatus target) {
        var order = service.create(request(), USER);
        var captured = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captured.capture());
        var entity = captured.getValue();
        if (target != OrderStatus.RECEBIDO) {
            entity.changeStatus(OrderStatus.EM_PREPARO, USER, Users.CLOCK);
        }
        assertThat(order.id()).isEqualTo(entity.getId());
        return entity;
    }

    @Test
    void createBuildsAReceivedOrderWithServerSideTotalAndCreator() {
        var response = service.create(request(), USER);

        assertThat(response.status()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(response.totalCents()).isEqualTo(7890);
        assertThat(response.createdBy()).isEqualTo(USER);
        assertThat(response.customer().name()).isEqualTo("Maria Silva");
        assertThat(response.createdAt()).isEqualTo(Users.CLOCK.instant());
        assertThat(response.history()).hasSize(1);
        assertThat(response.history().getFirst().from()).isNull();
        assertThat(response.history().getFirst().changedBy()).isEqualTo(USER);
    }

    @Test
    void findAllWithoutStatusUsesTheUnfilteredQuery() {
        var pageable = PageRequest.of(0, 100);
        var order = Order.create("Maria", "11987654321",
                com.fdl.platform.order.api.OrderMapper.toDomain(request().deliveryAddress()),
                com.fdl.platform.order.api.OrderMapper.toDomain(request().items()), USER, Users.CLOCK);
        when(orders.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        var page = service.findAll(null, pageable);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting("customerName").containsExactly("Maria");
        verify(orders, never()).findByStatus(any(), any());
    }

    @Test
    void findAllWithStatusFilters() {
        var pageable = PageRequest.of(0, 100);
        when(orders.findByStatus(OrderStatus.EM_PREPARO, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(service.findAll(OrderStatus.EM_PREPARO, pageable).content()).isEmpty();
        verify(orders, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void findByIdReturnsTheOrderOrThrowsNotFound() {
        var order = existing(OrderStatus.RECEBIDO);
        when(orders.findWithItemsById(order.getId())).thenReturn(Optional.of(order));
        var missing = UUID.randomUUID();
        when(orders.findWithItemsById(missing)).thenReturn(Optional.empty());

        assertThat(service.findById(order.getId()).id()).isEqualTo(order.getId());
        assertThatThrownBy(() -> service.findById(missing)).isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void updateStatusAppliesTheTransitionAndRecordsWhoDidIt() {
        var order = existing(OrderStatus.RECEBIDO);
        when(orders.findWithItemsById(order.getId())).thenReturn(Optional.of(order));
        var operator = UUID.randomUUID();

        var response = service.updateStatus(order.getId(), OrderStatus.EM_PREPARO, operator);

        assertThat(response.status()).isEqualTo(OrderStatus.EM_PREPARO);
        assertThat(response.history()).hasSize(2);
        assertThat(response.history().getLast().changedBy()).isEqualTo(operator);
        verify(orders).saveAndFlush(order);
    }

    @Test
    void invalidTransitionIsRejectedWithoutSaving() {
        var order = existing(OrderStatus.RECEBIDO);
        when(orders.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(order.getId(), OrderStatus.ENTREGUE, USER))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(order.getHistory()).hasSize(1);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void updateStatusOfAnUnknownOrderIsNotFound() {
        var id = UUID.randomUUID();
        when(orders.findWithItemsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(id, OrderStatus.EM_PREPARO, USER))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void optimisticLockFailureIsPropagatedForTheApiToTurnInto409() {
        var order = existing(OrderStatus.RECEBIDO);
        when(orders.findWithItemsById(order.getId())).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any(Order.class))).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> service.updateStatus(order.getId(), OrderStatus.EM_PREPARO, USER))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
