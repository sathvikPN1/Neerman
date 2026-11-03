package com.nirmaan.reimburse.mcp;

/** An OAuth error response (RFC 6749 §5.2): machine-readable code plus a human description. */
public class OAuthException extends RuntimeException {

    private final String error;
    private final int status;

    public OAuthException(String error, String description, int status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description, 400);
    }

    public static OAuthException invalidGrant(String description) {
        return new OAuthException("invalid_grant", description, 400);
    }

    public static OAuthException invalidClient(String description) {
        return new OAuthException("invalid_client", description, 401);
    }

    public String error() {
        return error;
    }

    public int status() {
        return status;
    }
}
