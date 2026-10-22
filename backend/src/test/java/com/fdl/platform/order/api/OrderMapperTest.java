package com.fdl.platform.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fdl.platform.order.domain.Order;
import com.fdl.platform.order.domain.OrderStatus;
import com.fdl.platform.support.Users;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderMapperTest {

    private static final AddressRequest ADDRESS = new AddressRequest("  Avenida Paulista ", "1000", "   ",
            "Bela Vista", "São Paulo", "SP", "01310100");

    private static Order order() {
        var items = List.of(new OrderItemRequest(" Pizza ", 1, 5490L), new OrderItemRequest("Refri", 2, 1200L));
        return Order.create("Maria Silva", "11987654321", OrderMapper.toDomain(ADDRESS), OrderMapper.toDomain(items),
                UUID.randomUUID(), Users.CLOCK);
    }

    @Test
    void addressIsTrimmedAndABlankComplementBecomesNull() {
        var address = OrderMapper.toDomain(ADDRESS);

        assertThat(address.getStreet()).isEqualTo("Avenida Paulista");
        assertThat(address.getComplement()).isNull();
        assertThat(address.getZipCode()).isEqualTo("01310100");
    }

    @Test
    void itemsKeepTheirOrderAndAreTrimmed() {
        var items = OrderMapper.toDomain(List.of(new OrderItemRequest(" Pizza ", 1, 5490L),
                new OrderItemRequest("Refri", 2, 1200L)));

        assertThat(items).extracting("name").containsExactly("Pizza", "Refri");
        assertThat(items.get(1).quantity()).isEqualTo(2);
    }

    @Test
    void fullResponseCarriesTotalsHistoryAndAllowedTransitions() {
        var order = order();
        order.changeStatus(OrderStatus.EM_PREPARO, order.getCreatedBy(), Users.CLOCK);

        var response = OrderMapper.toResponse(order);

        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.customer()).isEqualTo(new OrderResponse.Customer("Maria Silva", "11987654321"));
        assertThat(response.status()).isEqualTo(OrderStatus.EM_PREPARO);
        assertThat(response.totalCents()).isEqualTo(7890);
        assertThat(response.items()).extracting(OrderResponse.Item::subtotalCents).containsExactly(5490L, 2400L);
        assertThat(response.allowedTransitions())
                .containsExactly(OrderStatus.SAIU_PARA_ENTREGA, OrderStatus.CANCELADO);
        assertThat(response.history()).extracting(OrderResponse.HistoryEntry::from)
                .containsExactly(null, OrderStatus.RECEBIDO);
        assertThat(response.history()).extracting(OrderResponse.HistoryEntry::to)
                .containsExactly(OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO);
        assertThat(response.deliveryAddress().complement()).isNull();
        assertThat(response.createdBy()).isEqualTo(order.getCreatedBy());
    }

    @Test
    void summaryHasOnlyTheListFields() {
        var order = order();

        var summary = OrderMapper.toSummary(order);

        assertThat(summary).isEqualTo(new OrderSummaryResponse(order.getId(), "Maria Silva", OrderStatus.RECEBIDO,
                3, 7890, order.getCreatedAt(), order.getUpdatedAt()));
    }

    @Test
    void pageResponseCopiesThePagingMetadata() {
        var page = new org.springframework.data.domain.PageImpl<>(List.of("a", "b"),
                org.springframework.data.domain.PageRequest.of(1, 2), 5);

        var response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
