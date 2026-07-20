package com.crypto.crypto.feature.users.dto;

import com.crypto.crypto.constant.request.ModifiedDto;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;

@Getter
public class UpdateUserDto extends ModifiedDto {
    @Length(min = 8, message = "Min length of email must be greater or equal to 8")
    private String email;

    @Length(min = 5, message = "Password must be greater than 4 characters")
    private String passwordHash;

    private Boolean enabled;
    private Long version;
}
