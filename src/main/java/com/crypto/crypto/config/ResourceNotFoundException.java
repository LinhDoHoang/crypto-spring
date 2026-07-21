package com.crypto.crypto.config;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " with id " + id + " was not found");
    }
}
