package com.fdl.platform.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price_cents", nullable = false, columnDefinition = "INTEGER")
    private long unitPriceCents;

    @Column(name = "position", nullable = false)
    private int position;

    protected OrderItem() {
    }

    OrderItem(Order order, int position, NewItem item) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.position = position;
        this.name = item.name();
        this.quantity = item.quantity();
        this.unitPriceCents = item.unitPriceCents();
    }

    long subtotalCents() {
        return Math.multiplyExact(unitPriceCents, (long) quantity);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    public int getPosition() {
        return position;
    }
}
