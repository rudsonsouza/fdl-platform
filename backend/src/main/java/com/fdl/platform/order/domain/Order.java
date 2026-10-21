package com.fdl.platform.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Agregado do pedido: dados do cliente, itens, endereço, status atual e histórico de status.
 * As regras de negócio (total, transições) vivem aqui e nos enums, sem depender de Spring.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Embedded
    private DeliveryAddress deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_cents", nullable = false, columnDefinition = "INTEGER")
    private long totalCents;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "INTEGER")
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected Order() {
    }

    public static Order create(String customerName, String customerPhone, DeliveryAddress deliveryAddress,
            List<NewItem> newItems, UUID createdBy, Clock clock) {
        if (newItems == null || newItems.isEmpty()) {
            throw new IllegalArgumentException("O pedido precisa de ao menos um item");
        }
        var now = clock.instant();
        var order = new Order();
        order.id = UUID.randomUUID();
        order.customerName = customerName;
        order.customerPhone = customerPhone;
        order.deliveryAddress = deliveryAddress;
        order.status = OrderStatus.RECEBIDO;
        order.createdBy = createdBy;
        order.createdAt = now;
        order.updatedAt = now;
        for (var newItem : newItems) {
            order.items.add(new OrderItem(order, order.items.size(), newItem));
        }
        order.totalCents = order.items.stream().mapToLong(OrderItem::subtotalCents).reduce(0L, Math::addExact);
        order.recordHistory(null, OrderStatus.RECEBIDO, createdBy, now);
        return order;
    }

    /**
     * Move o pedido para {@code newStatus} se a transição for permitida; caso contrário lança
     * {@link InvalidStatusTransitionException} sem alterar o pedido nem o histórico.
     */
    public void changeStatus(OrderStatus newStatus, UUID changedBy, Clock clock) {
        if (!status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(status, newStatus);
        }
        var now = clock.instant();
        recordHistory(status, newStatus, changedBy, now);
        this.status = newStatus;
        this.updatedAt = now;
    }

    public List<OrderStatus> allowedTransitions() {
        return status.allowedTransitions();
    }

    private void recordHistory(OrderStatus from, OrderStatus to, UUID changedBy, Instant at) {
        history.add(new OrderStatusHistory(this, history.size(), from, to, changedBy, at));
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public DeliveryAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<OrderStatusHistory> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
