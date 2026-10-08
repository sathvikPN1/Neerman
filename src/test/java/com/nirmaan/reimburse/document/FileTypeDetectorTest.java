package com.nirmaan.reimburse.document;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeDetectorTest {

    @Test
    void detectsByMagicBytes() {
        assertThat(FileTypeDetector.detect("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII))).hasValue(FileTypeDetector.PDF);
        assertThat(FileTypeDetector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0})).hasValue(FileTypeDetector.JPEG);
        assertThat(FileTypeDetector.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0})).hasValue(FileTypeDetector.PNG);
        byte[] heic = new byte[16];
        System.arraycopy(new byte[]{0, 0, 0, 0x18}, 0, heic, 0, 4);
        System.arraycopy("ftypheic".getBytes(StandardCharsets.US_ASCII), 0, heic, 4, 8);
        assertThat(FileTypeDetector.detect(heic)).hasValue(FileTypeDetector.HEIC);
    }

    @Test
    void rejectsEverythingElseRegardlessOfName() {
        assertThat(FileTypeDetector.detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[]{'P', 'K', 3, 4, 0, 0})).isEmpty(); // zip / docx
        assertThat(FileTypeDetector.detect(new byte[]{'M', 'Z', 0, 0})).isEmpty();       // exe
        byte[] mp4 = new byte[16];
        System.arraycopy("ftypisom".getBytes(StandardCharsets.US_ASCII), 0, mp4, 4, 8);
        assertThat(FileTypeDetector.detect(mp4)).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[]{1})).isEmpty();
        assertThat(FileTypeDetector.detect(null)).isEmpty();
    }

    @Test
    void sanitisesFilenames() {
        assertThat(DocumentService.sanitise("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.sanitise("C:\\Users\\x\\bill <1>.pdf")).isEqualTo("bill _1_.pdf");
        assertThat(DocumentService.sanitise(null)).isEqualTo("document");
    }

    @Test
    void sha256IsHex() {
        assertThat(DocumentService.sha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
