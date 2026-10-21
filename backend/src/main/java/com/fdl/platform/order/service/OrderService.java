package com.fdl.platform.order.service;

import com.fdl.platform.order.api.CreateOrderRequest;
import com.fdl.platform.order.api.OrderMapper;
import com.fdl.platform.order.api.OrderResponse;
import com.fdl.platform.order.api.OrderSummaryResponse;
import com.fdl.platform.order.api.PageResponse;
import com.fdl.platform.order.domain.Order;
import com.fdl.platform.order.domain.OrderNotFoundException;
import com.fdl.platform.order.domain.OrderStatus;
import com.fdl.platform.order.repository.OrderRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de pedidos. Devolve DTOs montados dentro da transação, porque itens e histórico são
 * coleções preguiçosas (a aplicação roda com {@code open-in-view} desligado).
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final Clock clock;

    public OrderService(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, UUID createdBy) {
        var customer = request.customer();
        var order = Order.create(customer.name().strip(), customer.phone(),
                OrderMapper.toDomain(request.deliveryAddress()), OrderMapper.toDomain(request.items()), createdBy,
                clock);
        return OrderMapper.toResponse(orders.save(order));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> findAll(OrderStatus status, Pageable pageable) {
        var page = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        return PageResponse.from(page.map(OrderMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        return OrderMapper.toResponse(load(id));
    }

    /**
     * Aplica a transição e grava na hora ({@code saveAndFlush}): se outra transação alterou o pedido
     * depois da leitura, o {@code @Version} faz o UPDATE falhar e a API responde 409 CONCURRENT_MODIFICATION.
     */
    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus newStatus, UUID changedBy) {
        var order = load(id);
        order.changeStatus(newStatus, changedBy, clock);
        return OrderMapper.toResponse(orders.saveAndFlush(order));
    }

    private Order load(UUID id) {
        return orders.findWithItemsById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
