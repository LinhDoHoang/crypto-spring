package com.crypto.crypto.feature.users;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.users.dto.CreateUserDto;
import com.crypto.crypto.feature.users.dto.UpdateUserDto;
import com.crypto.crypto.feature.users.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/users")
public class UsersController {
    private final UsersService usersService;

    public UsersController(UsersService usersService) {
        this.usersService = usersService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@RequestBody @Valid CreateUserDto createUserDto) {
        UserResponse user = usersService.create(createUserDto);
        return ResponseEntity.ok(ApiResponse.success("Create user successfully", user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(
            @PathVariable @Positive Long id) {
        UserResponse user = usersService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("User found successfully", user));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAll() {
        List<UserResponse> users = usersService.getAll();
        return ResponseEntity.ok(ApiResponse.success("Users found successfully", users));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable("id") Long id, @RequestBody @Valid UpdateUserDto updateUserDto) {
        UserResponse user = usersService.update(id, updateUserDto);
        return ResponseEntity.ok(ApiResponse.success("Update user successfully", user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") @Positive Long id) {
        this.usersService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Delete user successfully", null));
    }
}
