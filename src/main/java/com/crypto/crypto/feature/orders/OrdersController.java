package com.crypto.crypto.feature.orders;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.orders.dto.CreateOrderDto;
import com.crypto.crypto.feature.orders.dto.OrderResponse;
import com.crypto.crypto.feature.orders.dto.UpdateOrderDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/orders")
public class OrdersController {
    private final OrdersService ordersService;

    OrdersController(OrdersService ordersService) {
        this.ordersService = ordersService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@RequestBody @Valid CreateOrderDto dto) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Create order successfully", ordersService.create(dto)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Get all orders successfully", ordersService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOne(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Find order successfully", ordersService.getOne(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> update(
            @PathVariable @Positive Long id,
            @RequestBody @Valid UpdateOrderDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Update order successfully", ordersService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        ordersService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
