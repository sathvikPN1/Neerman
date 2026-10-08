package com.nirmaan.reimburse.document;

import java.time.Instant;

public record DocumentView(Long id, DocumentType type, String originalFilename, String contentType, long size,
                           String sha256, Instant uploadedAt) {

    static DocumentView of(ClaimDocument d) {
        return new DocumentView(d.getId(), d.getType(), d.getOriginalFilename(), d.getContentType(), d.getSize(),
                d.getSha256(), d.getUploadedAt());
    }

    public boolean isImage() {
        return FileTypeDetector.isImage(contentType);
    }

    public boolean isPdf() {
        return FileTypeDetector.PDF.equals(contentType);
    }

    public String sizeLabel() {
        if (size >= 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        }
        return Math.max(1, size / 1024) + " KB";
    }
}
