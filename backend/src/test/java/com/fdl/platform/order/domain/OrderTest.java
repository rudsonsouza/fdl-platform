package com.fdl.platform.order.domain;

import static com.fdl.platform.order.domain.OrderStatus.CANCELADO;
import static com.fdl.platform.order.domain.OrderStatus.EM_PREPARO;
import static com.fdl.platform.order.domain.OrderStatus.ENTREGUE;
import static com.fdl.platform.order.domain.OrderStatus.RECEBIDO;
import static com.fdl.platform.order.domain.OrderStatus.SAIU_PARA_ENTREGA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderTest {

    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    static final UUID CREATOR = UUID.randomUUID();
    static final UUID OPERATOR = UUID.randomUUID();

    static Order newOrder() {
        return Order.create("Maria Silva", "11987654321",
                new DeliveryAddress("Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "SP", "01310100"),
                List.of(new NewItem("Pizza", 1, 5490), new NewItem("Refrigerante", 2, 1200)),
                CREATOR, Clock.fixed(T0, ZoneOffset.UTC));
    }

    static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    void createStartsReceivedWithServerSideTotal() {
        var order = newOrder();

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(RECEBIDO);
        assertThat(order.getTotalCents()).isEqualTo(7890);
        assertThat(order.getCreatedBy()).isEqualTo(CREATOR);
        assertThat(order.getCreatedAt()).isEqualTo(T0);
        assertThat(order.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void createRecordsInitialHistoryEntryWithNullOrigin() {
        var order = newOrder();

        assertThat(order.getHistory()).singleElement().satisfies(entry -> {
            assertThat(entry.getFromStatus()).isNull();
            assertThat(entry.getToStatus()).isEqualTo(RECEBIDO);
            assertThat(entry.getChangedBy()).isEqualTo(CREATOR);
            assertThat(entry.getChangedAt()).isEqualTo(T0);
        });
    }

    @Test
    void createKeepsItemsInTheOrderTheyWereSent() {
        var order = newOrder();

        assertThat(order.getItems()).extracting(OrderItem::getName).containsExactly("Pizza", "Refrigerante");
        assertThat(order.getItems()).extracting(OrderItem::getPosition).containsExactly(0, 1);
    }

    @Test
    void createRejectsMissingOrEmptyItems() {
        var address = new DeliveryAddress("Rua A", "1", null, "Centro", "São Paulo", "SP", "01001000");
        var clock = clockAt(T0);

        assertThatThrownBy(() -> Order.create("Maria", "11987654321", address, List.of(), CREATOR, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.create("Maria", "11987654321", address, null, CREATOR, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalOverflowIsNotSilentlyWrapped() {
        var address = new DeliveryAddress("Rua A", "1", null, "Centro", "São Paulo", "SP", "01001000");
        var items = List.of(new NewItem("Caro", 99, Long.MAX_VALUE / 2));

        assertThatThrownBy(() -> Order.create("Maria", "11987654321", address, items, CREATOR, clockAt(T0)))
                .isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidItems")
    void newItemRejectsInvalidValues(String name, int quantity, long unitPriceCents) {
        assertThatThrownBy(() -> new NewItem(name, quantity, unitPriceCents))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidItems() {
        return Stream.of(Arguments.of(null, 1, 100L), Arguments.of(" ", 1, 100L), Arguments.of("Pizza", 0, 100L),
                Arguments.of("Pizza", -1, 100L), Arguments.of("Pizza", 1, -1L));
    }

    @Test
    void validTransitionChangesStatusAndAppendsHistory() {
        var order = newOrder();
        var later = T0.plus(Duration.ofMinutes(5));

        order.changeStatus(EM_PREPARO, OPERATOR, clockAt(later));

        assertThat(order.getStatus()).isEqualTo(EM_PREPARO);
        assertThat(order.getUpdatedAt()).isEqualTo(later);
        assertThat(order.getCreatedAt()).isEqualTo(T0);
        assertThat(order.getHistory()).hasSize(2);
        assertThat(order.getHistory().get(1)).satisfies(entry -> {
            assertThat(entry.getFromStatus()).isEqualTo(RECEBIDO);
            assertThat(entry.getToStatus()).isEqualTo(EM_PREPARO);
            assertThat(entry.getChangedBy()).isEqualTo(OPERATOR);
            assertThat(entry.getChangedAt()).isEqualTo(later);
            assertThat(entry.getPosition()).isEqualTo(1);
        });
    }

    @Test
    void fullDeliveryFlowRecordsFourHistoryEntriesInOrder() {
        var order = newOrder();

        order.changeStatus(EM_PREPARO, OPERATOR, clockAt(T0.plusSeconds(60)));
        order.changeStatus(SAIU_PARA_ENTREGA, OPERATOR, clockAt(T0.plusSeconds(120)));
        order.changeStatus(ENTREGUE, OPERATOR, clockAt(T0.plusSeconds(180)));

        assertThat(order.getStatus()).isEqualTo(ENTREGUE);
        assertThat(order.getHistory()).extracting(OrderStatusHistory::getToStatus)
                .containsExactly(RECEBIDO, EM_PREPARO, SAIU_PARA_ENTREGA, ENTREGUE);
        assertThat(order.allowedTransitions()).isEmpty();
    }

    @Test
    void cancelFromReceivedAndFromInPreparationIsAllowed() {
        var received = newOrder();
        received.changeStatus(CANCELADO, OPERATOR, clockAt(T0));

        var inPreparation = newOrder();
        inPreparation.changeStatus(EM_PREPARO, OPERATOR, clockAt(T0));
        inPreparation.changeStatus(CANCELADO, OPERATOR, clockAt(T0));

        assertThat(received.getStatus()).isEqualTo(CANCELADO);
        assertThat(inPreparation.getStatus()).isEqualTo(CANCELADO);
    }

    @Test
    void cancelAfterDepartureIsRejectedWithExplanation() {
        var order = newOrder();
        order.changeStatus(EM_PREPARO, OPERATOR, clockAt(T0));
        order.changeStatus(SAIU_PARA_ENTREGA, OPERATOR, clockAt(T0));

        assertThatThrownBy(() -> order.changeStatus(CANCELADO, OPERATOR, clockAt(T0.plusSeconds(1))))
                .isInstanceOfSatisfying(InvalidStatusTransitionException.class, e -> {
                    assertThat(e.currentStatus()).isEqualTo(SAIU_PARA_ENTREGA);
                    assertThat(e.requestedStatus()).isEqualTo(CANCELADO);
                    assertThat(e.allowedTransitions()).containsExactly(ENTREGUE);
                    assertThat(e).hasMessage("Não é possível ir de SAIU_PARA_ENTREGA para CANCELADO");
                });
    }

    @Test
    void skippingAndGoingBackAreRejected() {
        var order = newOrder();
        assertThatThrownBy(() -> order.changeStatus(ENTREGUE, OPERATOR, clockAt(T0)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        order.changeStatus(EM_PREPARO, OPERATOR, clockAt(T0));
        assertThatThrownBy(() -> order.changeStatus(RECEBIDO, OPERATOR, clockAt(T0)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void sameStatusIsRejected() {
        var order = newOrder();
        order.changeStatus(EM_PREPARO, OPERATOR, clockAt(T0));

        assertThatThrownBy(() -> order.changeStatus(EM_PREPARO, OPERATOR, clockAt(T0)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void finalStatesAreImmutable() {
        var delivered = newOrder();
        delivered.changeStatus(EM_PREPARO, OPERATOR, clockAt(T0));
        delivered.changeStatus(SAIU_PARA_ENTREGA, OPERATOR, clockAt(T0));
        delivered.changeStatus(ENTREGUE, OPERATOR, clockAt(T0));
        var cancelled = newOrder();
        cancelled.changeStatus(CANCELADO, OPERATOR, clockAt(T0));

        for (var target : OrderStatus.values()) {
            assertThatThrownBy(() -> delivered.changeStatus(target, OPERATOR, clockAt(T0)))
                    .isInstanceOf(InvalidStatusTransitionException.class);
            assertThatThrownBy(() -> cancelled.changeStatus(target, OPERATOR, clockAt(T0)))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Test
    void rejectedTransitionLeavesStatusTimestampAndHistoryUntouched() {
        var order = newOrder();

        assertThatThrownBy(() -> order.changeStatus(ENTREGUE, OPERATOR, clockAt(T0.plusSeconds(99))))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(order.getStatus()).isEqualTo(RECEBIDO);
        assertThat(order.getUpdatedAt()).isEqualTo(T0);
        assertThat(order.getHistory()).hasSize(1);
    }

    @Test
    void allowedTransitionsFollowTheCurrentStatus() {
        var order = newOrder();
        assertThat(order.allowedTransitions()).containsExactly(EM_PREPARO, CANCELADO);

        order.changeStatus(EM_PREPARO, OPERATOR, clockAt(T0));
        assertThat(order.allowedTransitions()).containsExactly(SAIU_PARA_ENTREGA, CANCELADO);
    }

    @Test
    void collectionsExposedByTheOrderAreReadOnly() {
        var order = newOrder();

        assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> order.getHistory().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
