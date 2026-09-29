package lk.sliit.electronest.catalog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProductImageStorageServiceTest {
    @TempDir Path root;

    @Test void deletesManagedUploadButIgnoresExternalUrlsAndTraversal() throws Exception {
        Path directory = root.resolve("images");
        var storage = new ProductImageStorageService(directory);
        String url = storage.store(new MockMultipartFile("images", "photo.png", "image/png", new byte[]{1}));
        Path stored = directory.resolve(url.substring(ProductImageStorageService.URL_PREFIX.length()));
        assertTrue(Files.exists(stored));
        Path outside = Files.writeString(root.resolve("outside.png"), "keep");
        for (String invalid : new String[]{"https://example.com" + url, outside.toString(),
                ProductImageStorageService.URL_PREFIX + "../outside.png",
                ProductImageStorageService.URL_PREFIX + "sub/../" + stored.getFileName(),
                ProductImageStorageService.URL_PREFIX + "\0", ProductImageStorageService.URL_PREFIX}) {
            assertDoesNotThrow(() -> storage.deleteUrlQuietly(invalid));
        }
        assertTrue(Files.exists(outside));
        assertTrue(Files.exists(stored));
        storage.deleteUrlQuietly(url);
        assertFalse(Files.exists(stored));
        assertTrue(Files.exists(outside));
    }

    @Test void symlinkCannotDeleteItsTarget() throws Exception {
        Path directory = root.resolve("images");
        var storage = new ProductImageStorageService(directory);
        Path outside = Files.writeString(root.resolve("outside.png"), "keep");
        Files.createSymbolicLink(directory.resolve("link.png"), outside);
        storage.deleteUrlQuietly(ProductImageStorageService.URL_PREFIX + "link.png");
        assertTrue(Files.exists(outside));
    }
}
