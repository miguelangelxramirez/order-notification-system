package es.tirea.orderservice.adapters.outbound.persistence;

import es.tirea.orderservice.domain.model.Order;
import es.tirea.orderservice.domain.port.out.OrderRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostgresOrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderJpaRepository repository;

    public PostgresOrderRepositoryAdapter(SpringDataOrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = toEntity(order);
        OrderJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<Order> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setId(order.id());
        entity.setProduct(order.product());
        entity.setQuantity(order.quantity());
        entity.setPrice(order.price());
        entity.setCreatedAt(order.createdAt());
        return entity;
    }

    private Order toDomain(OrderJpaEntity entity) {
        return new Order(
                entity.getId(),
                entity.getProduct(),
                entity.getQuantity(),
                entity.getPrice(),
                entity.getCreatedAt()
        );
    }
}
