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
