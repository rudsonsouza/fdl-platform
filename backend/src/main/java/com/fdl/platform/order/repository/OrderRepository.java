package com.fdl.platform.order.repository;

import com.fdl.platform.order.domain.Order;
import com.fdl.platform.order.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Lista paginada; itens e histórico não são carregados (a lista só usa os dados do pedido). */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Detalhe com os itens já carregados. O histórico é uma segunda coleção em lista e fica
     * preguiçoso (carregar as duas num único join geraria produto cartesiano); o serviço o lê
     * dentro da mesma transação.
     */
    @Query("select o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") UUID id);
}
