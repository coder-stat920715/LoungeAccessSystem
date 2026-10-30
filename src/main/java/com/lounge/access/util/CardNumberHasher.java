package com.lounge.access.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keyed HMAC-SHA256 of the PAN. A plain hash of a 16-digit PAN is trivially brute-forceable, hence the pepper. */
@Component
public class CardNumberHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] pepper;

    public CardNumberHasher(@Value("${lounge.security.card-hash-pepper}") String pepper) {
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String normalizedPan) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(normalizedPan.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to compute card hash", e);
        }
    }
}
