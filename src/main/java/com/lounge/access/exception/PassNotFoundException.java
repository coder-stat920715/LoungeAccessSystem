package com.lounge.access.exception;

import org.springframework.http.HttpStatus;

public class PassNotFoundException extends LoungeException {

    public PassNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No lounge pass found for the supplied token");
    }
}
