package com.nirmaan.reimburse.common.storage;

import java.io.InputStream;

/** Binary object storage for uploaded documents. Keys are opaque, never user-supplied paths. */
public interface StorageService {

    void put(String key, byte[] content, String contentType);

    InputStream get(String key);

    void delete(String key);
}
