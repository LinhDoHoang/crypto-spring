package com.crypto.crypto.feature.orders;

import com.crypto.crypto.annotation.currentuser.CurrentUser;
import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.orders.dto.OrderResponse;
import com.crypto.crypto.feature.orders.dto.PlaceOrderRequest;
import com.crypto.crypto.feature.orders.dto.PositionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @RequestBody @Valid PlaceOrderRequest request,
            @CurrentUser UsersEntity user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success(
                        "Create order successfully",
                        ordersService.create(user, request, idempotencyKey)
                ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAll(
            @CurrentUser UsersEntity user,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Get all orders successfully",
                ordersService.getAll(user, status, symbol, limit)
        ));
    }

    @GetMapping("/positions")
    public ResponseEntity<List<PositionResponse>> getPositions(
            @CurrentUser UsersEntity user,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(ordersService.getPositions(user, status, symbol, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOne(
            @PathVariable @Positive Long id,
            @CurrentUser UsersEntity user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Find order successfully",
                ordersService.getOne(user, id)
        ));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<OrderResponse>> close(
            @PathVariable @Positive Long id,
            @CurrentUser UsersEntity user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Close order successfully",
                ordersService.close(user, id)
        ));
    }

}
