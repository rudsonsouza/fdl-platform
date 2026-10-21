package com.fdl.platform.order.repository;

import static com.fdl.platform.order.domain.OrderStatus.CANCELADO;
import static com.fdl.platform.order.domain.OrderStatus.EM_PREPARO;
import static com.fdl.platform.order.domain.OrderStatus.RECEBIDO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fdl.platform.order.domain.DeliveryAddress;
import com.fdl.platform.order.domain.NewItem;
import com.fdl.platform.order.domain.Order;
import com.fdl.platform.order.domain.OrderItem;
import com.fdl.platform.order.domain.OrderStatus;
import com.fdl.platform.order.domain.OrderStatusHistory;
import com.fdl.platform.support.AbstractSqliteDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderRepositoryTest extends AbstractSqliteDataJpaTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    OrderRepository repository;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    UUID userId;

    @BeforeEach
    void createUser() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, name, email, password_hash, created_at, updated_at) "
                + "VALUES (?, 'Operador', ?, 'hash', '2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.000Z')",
                userId.toString(), userId + "@example.com");
    }

    private Order order(Instant createdAt) {
        return Order.create("Maria Silva", "11987654321",
                new DeliveryAddress("Av. Paulista", "1000", "Apto 12", "Bela Vista", "São Paulo", "SP", "01310100"),
                List.of(new NewItem("Pizza", 1, 5490), new NewItem("Refrigerante", 2, 1200)),
                userId, Clock.fixed(createdAt, ZoneOffset.UTC));
    }

    private Order saved(Instant createdAt, OrderStatus... transitions) {
        var order = order(createdAt);
        for (var status : transitions) {
            order.changeStatus(status, userId, Clock.fixed(createdAt, ZoneOffset.UTC));
        }
        return repository.saveAndFlush(order);
    }

    private void detach() {
        em.flush();
        em.clear();
    }

    @Test
    void persistsAndReloadsACompleteOrder() {
        var id = saved(T0, EM_PREPARO).getId();
        detach();

        var loaded = repository.findWithItemsById(id).orElseThrow();

        assertThat(loaded.getCustomerName()).isEqualTo("Maria Silva");
        assertThat(loaded.getCustomerPhone()).isEqualTo("11987654321");
        assertThat(loaded.getStatus()).isEqualTo(EM_PREPARO);
        assertThat(loaded.getTotalCents()).isEqualTo(7890);
        assertThat(loaded.getCreatedBy()).isEqualTo(userId);
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(loaded.getDeliveryAddress()).usingRecursiveComparison().isEqualTo(
                new DeliveryAddress("Av. Paulista", "1000", "Apto 12", "Bela Vista", "São Paulo", "SP", "01310100"));
        assertThat(loaded.getItems()).extracting(OrderItem::getName, OrderItem::getQuantity,
                OrderItem::getUnitPriceCents)
                .containsExactly(tuple("Pizza", 1, 5490L),
                        tuple("Refrigerante", 2, 1200L));
        assertThat(loaded.getHistory()).extracting(OrderStatusHistory::getFromStatus,
                OrderStatusHistory::getToStatus)
                .containsExactly(tuple(null, RECEBIDO),
                        tuple(RECEBIDO, EM_PREPARO));
    }

    @Test
    void optionalComplementIsStoredAsNull() {
        var order = Order.create("Maria Silva", "11987654321",
                new DeliveryAddress("Rua A", "1", null, "Centro", "São Paulo", "SP", "01001000"),
                List.of(new NewItem("Pizza", 1, 100)), userId, Clock.fixed(T0, ZoneOffset.UTC));
        repository.saveAndFlush(order);
        detach();

        assertThat(repository.findById(order.getId()).orElseThrow().getDeliveryAddress().getComplement()).isNull();
    }

    @Test
    void datesAreStoredAsFixedWidthIsoTextAndIdsAsText() {
        var order = saved(Instant.parse("2026-01-01T10:00:00.5Z"));

        var row = jdbc.queryForMap("SELECT id, created_at FROM orders WHERE id = ?", order.getId().toString());

        assertThat(row.get("id")).isEqualTo(order.getId().toString());
        assertThat(row.get("created_at")).isEqualTo("2026-01-01T10:00:00.500Z");
    }

    @Test
    void versionIncrementsOnEachUpdate() {
        var order = saved(T0);
        assertThat(order.getVersion()).isZero();

        order.changeStatus(EM_PREPARO, userId, Clock.fixed(T0.plusSeconds(1), ZoneOffset.UTC));
        repository.saveAndFlush(order);

        assertThat(order.getVersion()).isEqualTo(1);
    }

    @Test
    void filtersByStatusAndPaginatesNewestFirst() {
        saved(T0);
        saved(T0.plusSeconds(10), EM_PREPARO);
        saved(T0.plusSeconds(20));
        saved(T0.plusSeconds(30), CANCELADO);
        saved(T0.plusSeconds(40));
        detach();
        var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");

        var received = repository.findByStatus(RECEBIDO, PageRequest.of(0, 2, newestFirst));
        var secondPage = repository.findByStatus(RECEBIDO, PageRequest.of(1, 2, newestFirst));

        assertThat(received.getTotalElements()).isEqualTo(3);
        assertThat(received.getTotalPages()).isEqualTo(2);
        assertThat(received.getContent()).extracting(Order::getCreatedAt)
                .containsExactly(T0.plusSeconds(40), T0.plusSeconds(20));
        assertThat(secondPage.getContent()).extracting(Order::getCreatedAt).containsExactly(T0);
        assertThat(repository.findByStatus(EM_PREPARO, PageRequest.of(0, 10)).getContent()).hasSize(1);
    }

    @Test
    void findAllReturnsEveryStatusSortedByCreationDate() {
        saved(T0);
        saved(T0.plusSeconds(10), CANCELADO);
        detach();

        var page = repository.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent()).extracting(Order::getStatus).containsExactly(CANCELADO, RECEBIDO);
    }

    @Test
    void unknownIdReturnsEmpty() {
        assertThat(repository.findWithItemsById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deletingAnOrderCascadesToItemsAndHistory() {
        var order = saved(T0, EM_PREPARO);
        detach();

        repository.deleteById(order.getId());
        em.flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history", Integer.class)).isZero();
    }

    @Test
    void databaseRejectsAnUnknownStatusEvenBypassingTheDomain() {
        var order = saved(T0);
        detach();

        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'PRONTO' WHERE id = ?",
                order.getId().toString())).hasMessageContaining("SQLITE_CONSTRAINT");
    }
}
