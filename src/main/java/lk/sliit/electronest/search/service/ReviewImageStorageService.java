package lk.sliit.electronest.search.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ReviewImageStorageService {
    public static final String URL_PREFIX = "/images/reviews-upload/";
    private static final long MAX_SIZE = 8L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            ".jpg", "image/jpeg", ".jpeg", "image/jpeg", ".png", "image/png", ".webp", "image/webp");
    private final Path directory;

    public ReviewImageStorageService() {
        this(Path.of("uploads", "review-images"));
    }

    ReviewImageStorageService(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize review image storage", ex);
        }
    }

    public String store(MultipartFile image) {
        String extension = validate(image);
        String filename = UUID.randomUUID() + extension;
        Path destination = directory.resolve(filename);
        boolean created = false;
        try {
            if (!directory.toRealPath().equals(directory)) {
                throw new IOException("Review image directory must not be a symbolic link");
            }
            // CREATE_NEW avoids overwriting an existing file or following a destination symlink.
            try (var input = image.getInputStream();
                 var output = Files.newOutputStream(destination, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                created = true;
                byte[] bytes = input.readNBytes((int) MAX_SIZE + 1);
                if (bytes.length > MAX_SIZE) throw new IllegalArgumentException("Review photo must be 8 MB or smaller");
                output.write(bytes);
            }
            return URL_PREFIX + filename;
        } catch (IOException | RuntimeException ex) {
            if (created) deleteUrlQuietly(URL_PREFIX + filename);
            if (ex instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Could not store the review photo. Select the file again.", ex);
        }
    }

    private String validate(MultipartFile image) {
        if (image == null || image.isEmpty()) throw new IllegalArgumentException("Review photo is empty");
        if (image.getSize() > MAX_SIZE) throw new IllegalArgumentException("Review photo must be 8 MB or smaller");
        String original = image.getOriginalFilename();
        String extension = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (!TYPES.containsKey(extension) || !TYPES.get(extension).equals(image.getContentType())) {
            throw new IllegalArgumentException("Review photos must be JPG, JPEG, PNG or WEBP with a matching content type");
        }
        try (var input = image.getInputStream()) {
            byte[] header = input.readNBytes(12);
            boolean valid = switch (extension) {
                case ".png" -> startsWith(header, new byte[]{(byte)137,80,78,71,13,10,26,10});
                case ".jpg", ".jpeg" -> startsWith(header, new byte[]{(byte)255,(byte)216,(byte)255});
                default -> header.length >= 12 && startsWith(header, new byte[]{'R','I','F','F'})
                        && Arrays.equals(Arrays.copyOfRange(header, 8, 12), new byte[]{'W','E','B','P'});
            };
            if (!valid) throw new IllegalArgumentException("Review photo contents do not match the selected image format");
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the review photo. Select the file again.", ex);
        }
        return extension;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }

    private boolean managedFilename(String filename) {
        return filename != null && filename.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|webp)");
    }

    public Resource load(String filename) {
        if (!managedFilename(filename)) throw new java.util.NoSuchElementException("Review image not found");
        try {
            Path file = directory.resolve(filename);
            if (!directory.toRealPath().equals(directory) || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new java.util.NoSuchElementException("Review image not found");
            }
            return new UrlResource(file.toUri());
        } catch (IOException ex) {
            throw new java.util.NoSuchElementException("Review image not found");
        }
    }

    public void deleteUrlQuietly(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return;
        String filename = url.substring(URL_PREFIX.length());
        if (!managedFilename(filename)) return;
        try {
            Path file = directory.resolve(filename);
            if (directory.toRealPath().equals(directory) && !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
            // A cleanup failure must not undo a successfully persisted review.
        }
    }
}
