package es.tirea.orderservice.adapters.inbound.rest;

import es.tirea.orderservice.adapters.inbound.rest.dto.OrderRequest;
import es.tirea.orderservice.application.command.CreateOrderCommand;
import es.tirea.orderservice.application.usecase.CreateOrderUseCase;
import es.tirea.orderservice.application.usecase.ListOrdersUseCase;
import es.tirea.orderservice.domain.model.Order;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final ListOrdersUseCase listOrdersUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           ListOrdersUseCase listOrdersUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.listOrdersUseCase = listOrdersUseCase;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        Order order = createOrderUseCase.createOrder(new CreateOrderCommand(
                request.product(),
                request.quantity(),
                request.price()
        ));
        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Order>> listOrders() {
        return ResponseEntity.ok(listOrdersUseCase.listOrders());
    }
}
