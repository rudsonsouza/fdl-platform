package com.fdl.platform.order.domain;

import static com.fdl.platform.order.domain.OrderStatus.CANCELADO;
import static com.fdl.platform.order.domain.OrderStatus.EM_PREPARO;
import static com.fdl.platform.order.domain.OrderStatus.ENTREGUE;
import static com.fdl.platform.order.domain.OrderStatus.RECEBIDO;
import static com.fdl.platform.order.domain.OrderStatus.SAIU_PARA_ENTREGA;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class OrderStatusTest {

    private static final Set<List<OrderStatus>> ALLOWED = Set.of(
            List.of(RECEBIDO, EM_PREPARO),
            List.of(RECEBIDO, CANCELADO),
            List.of(EM_PREPARO, SAIU_PARA_ENTREGA),
            List.of(EM_PREPARO, CANCELADO),
            List.of(SAIU_PARA_ENTREGA, ENTREGUE));

    static Stream<Arguments> allCombinations() {
        return Stream.of(OrderStatus.values())
                .flatMap(from -> Stream.of(OrderStatus.values())
                        .map(to -> Arguments.of(from, to, ALLOWED.contains(List.of(from, to)))));
    }

    @ParameterizedTest(name = "{0} -> {1} permitido: {2}")
    @MethodSource("allCombinations")
    void transitionMatrixCoversAll25Combinations(OrderStatus from, OrderStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    void thereAreExactly25Combinations() {
        assertThat(allCombinations()).hasSize(25);
    }

    @Test
    void allowedTransitionsAreListedInTheDocumentedOrder() {
        assertThat(RECEBIDO.allowedTransitions()).containsExactly(EM_PREPARO, CANCELADO);
        assertThat(EM_PREPARO.allowedTransitions()).containsExactly(SAIU_PARA_ENTREGA, CANCELADO);
        assertThat(SAIU_PARA_ENTREGA.allowedTransitions()).containsExactly(ENTREGUE);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"ENTREGUE", "CANCELADO"})
    void finalStatesHaveNoTransitions(OrderStatus status) {
        assertThat(status.isFinal()).isTrue();
        assertThat(status.allowedTransitions()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"RECEBIDO", "EM_PREPARO", "SAIU_PARA_ENTREGA"})
    void activeStatesAreNotFinal(OrderStatus status) {
        assertThat(status.isFinal()).isFalse();
    }
}
