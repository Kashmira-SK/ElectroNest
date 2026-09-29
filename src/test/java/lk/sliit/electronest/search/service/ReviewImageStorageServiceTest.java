package lk.sliit.electronest.search.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.*;

class ReviewImageStorageServiceTest {
    @TempDir Path root;
    static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10};

    @Test void allowedFormatsGetSafeNamesAndCanBeLoaded() throws Exception {
        var storage = new ReviewImageStorageService(root);
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("photo", "../../photo.PNG", "image/png", PNG),
                new MockMultipartFile("photo", "photo.jpg", "image/jpeg", new byte[]{(byte)255,(byte)216,(byte)255}),
                new MockMultipartFile("photo", "photo.JPEG", "image/jpeg", new byte[]{(byte)255,(byte)216,(byte)255}),
                new MockMultipartFile("photo", "photo.webp", "image/webp", new byte[]{'R','I','F','F',0,0,0,0,'W','E','B','P'})}) {
            String url = storage.store(file);
            String filename = url.substring(ReviewImageStorageService.URL_PREFIX.length());
            assertTrue(filename.matches("[0-9a-f-]{36}\\.(png|jpg|jpeg|webp)"));
            assertArrayEquals(file.getBytes(), storage.load(filename).getContentAsByteArray());
            storage.deleteUrlQuietly(url);
            assertFalse(Files.exists(root.resolve(filename)));
        }
    }

    @Test void rejectsInvalidTypeMismatchesContentsAndSizeWithoutFiles() throws Exception {
        var storage = new ReviewImageStorageService(root);
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("photo", "photo.svg", "image/svg+xml", "<svg/>".getBytes()),
                new MockMultipartFile("photo", "photo.png", "image/jpeg", PNG),
                new MockMultipartFile("photo", "photo.png", null, PNG),
                new MockMultipartFile("photo", "photo.png", "image/png", "<script>bad</script>".getBytes()),
                new MockMultipartFile("photo", "photo.png", "image/png", new byte[8 * 1024 * 1024 + 1])}) {
            assertThrows(IllegalArgumentException.class, () -> storage.store(file));
        }
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @Test void interruptedUploadLeavesNoPartialFile() throws Exception {
        var storage = new ReviewImageStorageService(root);
        var image = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        org.mockito.Mockito.when(image.getSize()).thenReturn(12L);
        org.mockito.Mockito.when(image.getOriginalFilename()).thenReturn("photo.png");
        org.mockito.Mockito.when(image.getContentType()).thenReturn("image/png");
        org.mockito.Mockito.when(image.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(PNG),
                new java.io.InputStream() {
                    @Override public int read() throws java.io.IOException { throw new java.io.IOException("Interrupted"); }
                });
        assertThrows(IllegalArgumentException.class, () -> storage.store(image));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @Test void traversalExternalUrlsAndSymlinksCannotAffectOutsideFiles() throws Exception {
        Path directory = root.resolve("managed");
        var storage = new ReviewImageStorageService(directory);
        Path outside = Files.write(root.resolve("outside.png"), PNG);
        String name = "12345678-1234-1234-1234-123456789abc.png";
        for (String value : new String[]{outside.toString(), "https://example.com/" + name,
                ReviewImageStorageService.URL_PREFIX + "../outside.png", ReviewImageStorageService.URL_PREFIX + "\0"}) {
            storage.deleteUrlQuietly(value);
            assertTrue(Files.exists(outside));
        }
        Files.createSymbolicLink(directory.resolve(name), outside);
        assertThrows(NoSuchElementException.class, () -> storage.load(name));
        assertThrows(NoSuchElementException.class, () -> storage.load("../outside.png"));
        storage.deleteUrlQuietly(ReviewImageStorageService.URL_PREFIX + name);
        assertTrue(Files.exists(outside));
    }
}
