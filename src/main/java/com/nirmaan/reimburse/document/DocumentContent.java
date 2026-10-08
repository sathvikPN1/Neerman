package com.nirmaan.reimburse.document;

import java.io.InputStream;

public record DocumentContent(String filename, String contentType, long size, InputStream stream) {
}
