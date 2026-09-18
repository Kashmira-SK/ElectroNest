package lk.sliit.electronest.catalog.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductImageStorageService {

    public static final String URL_PREFIX =
            "/images/products-upload/";

    private static final long MAX_SIZE =
            8L * 1024L * 1024L;

    private static final Set<String> EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".webp");

    private static final Set<String> TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final Path directory =
            Paths.get("uploads", "product-images")
                    .toAbsolutePath()
                    .normalize();

    public ProductImageStorageService() {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not initialize product image storage",
                    ex
            );
        }
    }

    public String store(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException(
                    "Image file is empty"
            );
        }

        if (image.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Each product image must be 8 MB or smaller"
            );
        }

        String original = image.getOriginalFilename();
        String extension = "";

        if (original != null && original.contains(".")) {
            extension = original
                    .substring(original.lastIndexOf('.'))
                    .toLowerCase(Locale.ROOT);
        }

        if (image.getContentType() == null || !EXTENSIONS.contains(extension)
                || !TYPES.contains(image.getContentType())) {
            throw new IllegalArgumentException(
                    "Product images must be JPG, PNG or WEBP"
            );
        }

        String stored =
                UUID.randomUUID() + extension;

        Path destination =
                directory.resolve(stored).normalize();

        if (!destination.getParent().equals(directory)) {
            throw new IllegalArgumentException(
                    "Invalid image filename"
            );
        }

        try {
            image.transferTo(destination);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not store product image",
                    ex
            );
        }

        return URL_PREFIX + stored;
    }

    public Resource load(String filename) {
        try {
            Path file =
                    directory.resolve(filename).normalize();

            if (!file.getParent().equals(directory)) {
                throw new IllegalArgumentException(
                        "Invalid image path"
                );
            }

            Resource resource =
                    new UrlResource(file.toUri());

            if (!resource.exists()
                    || !resource.isReadable()) {
                throw new IllegalArgumentException(
                        "Product image not found"
                );
            }

            return resource;

        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(
                    "Product image not found",
                    ex
            );
        }
    }

    public void deleteUrlQuietly(String url) {
        if (url == null
                || !url.startsWith(URL_PREFIX)) {
            return;
        }

        String filename =
                url.substring(URL_PREFIX.length());

        try {
            Path file =
                    directory.resolve(filename).normalize();

            if (file.getParent().equals(directory)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
        }
    }
}
