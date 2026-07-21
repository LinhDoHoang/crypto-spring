package com.crypto.crypto.feature.users;

import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.users.dto.CreateUserDto;
import com.crypto.crypto.feature.users.dto.UpdateUserDto;
import com.crypto.crypto.feature.users.dto.UserResponse;
import com.crypto.crypto.feature.users.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsersServiceTest {
    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private UsersService usersService;

    @Test
    void getByIdReturnsSafeResponse() {
        UsersEntity user = UsersEntity.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("sensitive-hash")
                .enabled(true)
                .build();
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = usersService.getById(1L);

        assertEquals(1L, response.id());
        assertEquals("user@example.com", response.email());
        assertEquals(true, response.enabled());
        verify(usersRepository).findById(1L);
    }

    @Test
    void getByIdThrowsWhenUserDoesNotExist() {
        when(usersRepository.findById(99L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> usersService.getById(99L)
        );

        assertEquals("User with id 99 was not found", exception.getMessage());
        verify(usersRepository).findById(99L);
    }

    @Test
    void createNormalizesEmailAndDeleteIsSoft() {
        CreateUserDto dto = new CreateUserDto();
        dto.setEmail("  USER@Example.COM ");
        dto.setPasswordHash("hashed-password");
        when(usersRepository.save(any())).thenAnswer(invocation -> {
            UsersEntity user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse response = usersService.create(dto);
        UsersEntity user = UsersEntity.builder()
                .id(1L)
                .email(response.email())
                .passwordHash("hashed-password")
                .enabled(true)
                .build();
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));
        usersService.delete(1L);

        assertEquals("user@example.com", response.email());
        assertNotNull(user.getDeletedAt());
    }

    @Test
    void nullPatchDoesNotOverwriteUser() {
        UsersEntity user = UsersEntity.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hashed-password")
                .enabled(true)
                .build();
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = usersService.update(1L, new UpdateUserDto());

        assertEquals("user@example.com", response.email());
        assertEquals(true, response.enabled());
    }
}
