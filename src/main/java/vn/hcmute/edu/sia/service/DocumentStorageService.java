package vn.hcmute.edu.sia.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import vn.hcmute.edu.sia.dto.internal.StoredDocumentFile;

public interface DocumentStorageService {

    StoredDocumentFile store(MultipartFile file, String mimeType) throws IOException;

    byte[] loadBytes(String storagePath) throws IOException;

    String loadUtf8Text(String storagePath) throws IOException;

    void delete(String storagePath) throws IOException;
}
