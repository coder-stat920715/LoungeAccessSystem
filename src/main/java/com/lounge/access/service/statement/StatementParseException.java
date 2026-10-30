package com.lounge.access.service.statement;

/** Problem with an uploaded statement. {@code httpStatus}: 400 = bad upload, 422 = content cannot be understood. */
public class StatementParseException extends RuntimeException {

    private final int httpStatus;

    public StatementParseException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
