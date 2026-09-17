package lk.sliit.electronest.vendor.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class VendorDocumentStorageService {

    private static final long MAX_SIZE = 15L * 1024L * 1024L;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf",
            ".jpg",
            ".jpeg",
            ".png",
            ".zip"
    );

    private final Path storageDirectory =
            Paths.get("uploads", "vendor-documents")
                    .toAbsolutePath()
                    .normalize();

    public VendorDocumentStorageService() {
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not initialize vendor document storage",
                    ex
            );
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Verification document is required"
            );
        }

        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Document must not exceed 15 MB"
            );
        }

        String originalName = file.getOriginalFilename();
        String extension = "";

        if (originalName != null && originalName.contains(".")) {
            extension = originalName
                    .substring(originalName.lastIndexOf('.'))
                    .toLowerCase();
        }

        if (!ALLOWED_TYPES.contains(file.getContentType())
                || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Only PDF, JPG, PNG and ZIP documents are allowed"
            );
        }

        String storedName = UUID.randomUUID() + extension;

        Path destination =
                storageDirectory.resolve(storedName).normalize();

        if (!destination.getParent().equals(storageDirectory)) {
            throw new IllegalArgumentException(
                    "Invalid document filename"
            );
        }

        try {
            file.transferTo(destination);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not store verification document",
                    ex
            );
        }

        return storedName;
    }

    public Resource load(String storedName) {
        try {
            Path file =
                    storageDirectory.resolve(storedName).normalize();

            if (!file.getParent().equals(storageDirectory)) {
                throw new IllegalArgumentException(
                        "Invalid document path"
                );
            }

            Resource resource = new UrlResource(file.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException(
                        "Verification document not found"
                );
            }

            return resource;
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(
                    "Verification document not found",
                    ex
            );
        }
    }

    public void deleteQuietly(String storedName) {
        if (storedName == null) {
            return;
        }

        try {
            Files.deleteIfExists(
                    storageDirectory
                            .resolve(storedName)
                            .normalize()
            );
        } catch (IOException ignored) {
        }
    }
}
