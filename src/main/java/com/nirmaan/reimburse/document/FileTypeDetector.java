package com.nirmaan.reimburse.document;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * Detects the real file type from its leading bytes ("magic numbers"), ignoring the file name and the
 * browser-supplied content type. Only PDF, JPEG, PNG and HEIC/HEIF are accepted.
 */
public final class FileTypeDetector {

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String HEIC = "image/heic";

    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    private FileTypeDetector() {
    }

    public static Optional<String> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return Optional.empty();
        }
        if (startsWith(bytes, new byte[]{'%', 'P', 'D', 'F', '-'})) {
            return Optional.of(PDF);
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of(PNG);
        }
        // ISO base media file: bytes 4..7 = "ftyp", 8..11 = major brand
        if (bytes.length >= 12 && "ftyp".equals(new String(bytes, 4, 4, StandardCharsets.US_ASCII))
                && HEIC_BRANDS.contains(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
            return Optional.of(HEIC);
        }
        return Optional.empty();
    }

    public static boolean isImage(String contentType) {
        return JPEG.equals(contentType) || PNG.equals(contentType);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
