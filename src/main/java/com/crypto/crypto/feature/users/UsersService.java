package com.crypto.crypto.feature.users;

import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.users.dto.CreateUserDto;
import com.crypto.crypto.feature.users.dto.UpdateUserDto;
import com.crypto.crypto.feature.users.dto.UserResponse;
import com.crypto.crypto.feature.users.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsersService {
    private final UsersRepository usersRepository;

    public UsersService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Transactional
    public UserResponse create(CreateUserDto createUserDto) {
        var builder = UsersEntity.builder()
                .email(createUserDto.getEmail())
                .passwordHash(createUserDto.getPasswordHash());

        if (createUserDto.getEnabled() != null) {
            builder.enabled(createUserDto.getEnabled());
        }

        UsersEntity user = builder
                .build();
        usersRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        UsersEntity user = usersRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        List<UsersEntity> users = usersRepository.findAll();
        return users.stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserDto updateUserDto) {
        UsersEntity existingUser = usersRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (updateUserDto.getEnabled() != null) {
            existingUser.setEnabled(updateUserDto.getEnabled());
        }

        if (updateUserDto.getEmail() != null) {
            existingUser.setEmail(updateUserDto.getEmail());
        }

        if (updateUserDto.getPasswordHash() != null) {
            existingUser.setPasswordHash(updateUserDto.getPasswordHash());
        }

        if (updateUserDto.getUpdatedBy() != null) {
            existingUser.setUpdatedBy(updateUserDto.getUpdatedBy());
        }

        return UserResponse.from(existingUser);
    }

    @Transactional
    public void delete(Long id) {
        UsersEntity existingUser = usersRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        usersRepository.softDelete(id);
        return;
    }
}
