package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.catalog.service.ProductImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class UploadBoundaryTest {
    @Test void missingContentTypeIsAnExpectedValidationError() {
        var file = new MockMultipartFile("file", "image.png", null, new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> new VendorDocumentStorageService().store(file));
        assertThrows(IllegalArgumentException.class, () -> new ProductImageStorageService().store(file));
    }

    @Test void renamedAndMismatchedDocumentsAreRejected() {
        var storage = new VendorDocumentStorageService();
        assertThrows(IllegalArgumentException.class, () -> storage.validate(new MockMultipartFile("document", "proof.pdf", "application/pdf", "<script>bad</script>".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> storage.validate(new MockMultipartFile("document", "proof.png", "application/pdf", "%PDF-1.7".getBytes())));
        assertDoesNotThrow(() -> storage.validate(new MockMultipartFile("document", "proof.PDF", "application/octet-stream", "%PDF-1.7".getBytes())));
        assertDoesNotThrow(() -> storage.validate(new MockMultipartFile("document", "proof.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10})));
        assertDoesNotThrow(() -> storage.validate(new MockMultipartFile("document", "proof.jpg", "image/jpeg", new byte[]{(byte)255,(byte)216,(byte)255})));
        assertDoesNotThrow(() -> storage.validate(new MockMultipartFile("document", "proof.zip", "application/zip", new byte[]{80,75,3,4})));
    }

    @Test void documentCleanupCannotDeleteOutsideItsDirectory() throws Exception {
        var outside = Files.createTempFile("electronest-upload-boundary-", ".txt");
        try {
            new VendorDocumentStorageService().deleteQuietly(outside.toAbsolutePath().toString());
            assertTrue(Files.exists(outside));
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
