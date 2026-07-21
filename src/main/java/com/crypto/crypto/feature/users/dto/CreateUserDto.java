package com.crypto.crypto.feature.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserDto {
    @Email
    @NotBlank
    @Size(max = 320)
    private String email;

    @NotBlank
    @Size(min = 5, max = 100)
    private String passwordHash;

    private Boolean enabled;
}
