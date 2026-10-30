package com.lounge.access.exception;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

public class PassExpiredException extends LoungeException {

    public PassExpiredException(LocalDateTime expiredAt) {
        super(HttpStatus.GONE, "Pass expired at " + expiredAt + "; please re-check eligibility");
    }
}
