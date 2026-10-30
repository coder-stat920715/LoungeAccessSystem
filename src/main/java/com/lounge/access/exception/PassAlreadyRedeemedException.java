package com.lounge.access.exception;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

public class PassAlreadyRedeemedException extends LoungeException {

    public PassAlreadyRedeemedException(LocalDateTime redeemedAt) {
        super(HttpStatus.CONFLICT, "Pass was already redeemed at " + redeemedAt);
    }
}
