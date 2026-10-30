package com.lounge.access.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class LoungeException extends RuntimeException {

    private final HttpStatus status;

    protected LoungeException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
