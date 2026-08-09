package com.crypto.crypto.feature.auth;

import com.crypto.crypto.annotation.currentuser.CurrentUser;
import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.users.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verify")
public class VerifyController {
    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> verify(
            @CurrentUser UsersEntity user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Token verified successfully",
                UserResponse.from(user)
        ));
    }
}
