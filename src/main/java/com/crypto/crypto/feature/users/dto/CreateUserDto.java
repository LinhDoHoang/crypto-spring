package com.crypto.crypto.feature.users.dto;

import com.crypto.crypto.constant.request.ModifiedDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;

@Getter
public class CreateUserDto extends ModifiedDto {
    @Email
    @NotNull
    @Length(min = 8, message = "Min length of email must be greater or equal to 8")
    private String email;

    @NotNull
    @Length(min = 5, message = "Password must be greater than 4 characters")
    private String passwordHash;

    private Boolean enabled;

    private Long version;
}
