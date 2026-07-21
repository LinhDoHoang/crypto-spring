package com.crypto.crypto.feature.orders;

import com.crypto.crypto.config.GlobalException;
import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.orders.dto.CreateOrderDto;
import com.crypto.crypto.feature.orders.dto.OrderResponse;
import com.crypto.crypto.feature.orders.exception.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrdersControllerTest {
    @Mock
    private OrdersService ordersService;

    private OrdersController ordersController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ordersController = new OrdersController(ordersService);
        mockMvc = MockMvcBuilders.standaloneSetup(ordersController)
                .setControllerAdvice(new GlobalException())
                .build();
    }

    @Test
    void createReturns201AndDeleteReturns204() {
        when(ordersService.create(any())).thenReturn(null);
        doNothing().when(ordersService).delete(1L);

        ResponseEntity<ApiResponse<OrderResponse>> createResponse =
                ordersController.create(new CreateOrderDto());
        ResponseEntity<Void> deleteResponse = ordersController.delete(1L);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
    }

    @Test
    void invalidBodyReturns422() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void malformedIdReturns400() throws Exception {
        mockMvc.perform(get("/orders/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrderReturns404() throws Exception {
        when(ordersService.getOne(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id 99 was not found"));
    }
}
