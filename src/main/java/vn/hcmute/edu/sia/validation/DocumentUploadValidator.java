package vn.hcmute.edu.sia.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentUploadValidator {

    private static final long MAX_FILE_SIZE_BYTES = 20_971_520L;
    private static final long MAX_DOCX_EXPANDED_SIZE_BYTES = 134_217_728L;
    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public String validateAndGetMimeType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty document file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Document size must not exceed 20 MiB");
        }

        String filename = getSafeFilename(file.getOriginalFilename());
        if (filename.length() > 255) {
            throw new IllegalArgumentException("Filename must not exceed 255 characters");
        }

        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        try {
            if (normalizedFilename.endsWith(".pdf") && hasPdfSignature(file)) {
                return PDF_MIME_TYPE;
            }
            if (normalizedFilename.endsWith(".docx") && isDocxArchive(file)) {
                return DOCX_MIME_TYPE;
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the uploaded document", exception);
        }

        throw new IllegalArgumentException("Only valid PDF and DOCX files are allowed");
    }

    public String getSafeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Uploaded filename must not be blank");
        }

        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim();
        if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
            throw new IllegalArgumentException("Uploaded filename is invalid");
        }
        return filename;
    }

    private boolean hasPdfSignature(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(1024);
            byte[] signature = "%PDF-".getBytes(StandardCharsets.US_ASCII);
            for (int start = 0; start <= header.length - signature.length; start++) {
                boolean matches = true;
                for (int offset = 0; offset < signature.length; offset++) {
                    if (header[start + offset] != signature[offset]) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isDocxArchive(MultipartFile file) throws IOException {
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        long expandedBytes = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if ("[Content_Types].xml".equals(entry.getName())) {
                    hasContentTypes = true;
                } else if ("word/document.xml".equals(entry.getName())) {
                    hasWordDocument = true;
                }

                int bytesRead;
                while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                    expandedBytes += bytesRead;
                    if (expandedBytes > MAX_DOCX_EXPANDED_SIZE_BYTES) {
                        return false;
                    }
                }
                zipInputStream.closeEntry();
            }
        } catch (ZipException exception) {
            return false;
        }

        return hasContentTypes && hasWordDocument;
    }
}
