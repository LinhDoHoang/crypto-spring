package com.crypto.crypto.feature.users.exception;

import com.crypto.crypto.config.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(Long id) {
        super("User", id);
    }
}
