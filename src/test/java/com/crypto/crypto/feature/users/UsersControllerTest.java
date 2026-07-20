package com.crypto.crypto.feature.users;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.users.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsersControllerTest {
    @Mock
    private UsersService usersService;

    @InjectMocks
    private UsersController usersController;

    @Test
    void getByIdReturnsOkResponse() {
        UserResponse user = new UserResponse(1L, "user@example.com", true);
        when(usersService.getById(1L)).thenReturn(user);

        ResponseEntity<ApiResponse<UserResponse>> response = usersController.getById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().success());
        assertEquals("User found successfully", response.getBody().message());
        assertEquals(user, response.getBody().data());
        verify(usersService).getById(1L);
    }
}
