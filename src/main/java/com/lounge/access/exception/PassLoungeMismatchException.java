package com.lounge.access.exception;

import org.springframework.http.HttpStatus;

public class PassLoungeMismatchException extends LoungeException {

    public PassLoungeMismatchException() {
        super(HttpStatus.CONFLICT, "Pass was issued for a different lounge");
    }
}
