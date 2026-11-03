package com.nirmaan.reimburse.mcp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random tokens, hashing and PKCE verification for the OAuth server. */
public final class Secrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Secrets() {
    }

    /** 256-bit random value, URL-safe. Used for codes, tokens, client ids and upload links. */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Only this hash is stored, so a database leak does not leak usable tokens. */
    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * PKCE S256 (RFC 7636): BASE64URL(SHA256(code_verifier)) must equal the code_challenge sent at authorize time.
     * Verifier must be 43–128 characters of [A-Z a-z 0-9 - . _ ~].
     */
    public static boolean pkceMatches(String codeVerifier, String codeChallenge) {
        if (codeVerifier == null || codeChallenge == null || !codeVerifier.matches("[A-Za-z0-9\\-._~]{43,128}")) {
            return false;
        }
        String computed = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                codeChallenge.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
