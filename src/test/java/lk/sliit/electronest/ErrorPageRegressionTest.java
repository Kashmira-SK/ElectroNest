package lk.sliit.electronest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.level.root=WARN", "debug=false"})
@org.springframework.context.annotation.Import(ErrorPageRegressionTest.ErrorEndpoints.class)
@ActiveProfiles("test")
class ErrorPageRegressionTest {
    @Autowired Environment environment;
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();

    @Test void guestErrorsReachBrandedPagesWithoutLoginRedirects() throws Exception {
        assertPage("/products/not-a-number", 400, "We couldn't complete that request.");
        assertPage("/products/missing/route", 404, "We couldn't find that page.");
        assertPage("/api/products/search", 400, "We couldn't complete that request.");
        assertPage("/api/products/bulk-price", 400, "We couldn't complete that request.");
    }

    @org.springframework.web.bind.annotation.RestController
    static class ErrorEndpoints {
        @org.springframework.web.bind.annotation.GetMapping("/products/test-error/{status}")
        void fail(@org.springframework.web.bind.annotation.PathVariable int status,
                  jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            if (status == 500) throw new IllegalStateException("Internal detail must never be displayed");
            response.sendError(status, "Internal detail must never be displayed");
        }
    }

    @Test void uploadAndServerErrorsUseDedicatedPages() throws Exception {
        assertPage("/products/test-error/413", 413, "That file is too large.");
        assertPage("/products/test-error/500", 500, "Something went wrong.");
        assertPage("/products/test-error/405", 405, "We couldn't complete that request.");
    }

    @Test void oversizedMultipartRendersWithoutReparsingTheFailedUpload() throws Exception {
        var login = request("/login", "text/html");
        var token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(login.body());
        assertTrue(token.find());
        String boundary = "RegressionUploadBoundary";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"large.png\""
                + "\r\nContent-Type: image/png\r\n\r\n" + "x".repeat(16 * 1024 * 1024)
                + "\r\n--" + boundary + "--\r\n";
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:"
                        + environment.getProperty("local.server.port") + "/products/test-upload"))
                .header("Accept", "text/html")
                .header("X-CSRF-TOKEN", token.group(1))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(413, response.statusCode());
        assertTrue(response.body().contains("That file is too large."), response.body());
    }

    @Test void apiErrorsDoNotExposeInternalDetails() throws Exception {
        var response = request("/api/products/not-a-number", "application/json");
        assertEquals(400, response.statusCode());
        assertFalse(response.body().contains("NumberFormatException"));
        assertFalse(response.body().contains("\"trace\""));
        var failure = request("/products/test-error/500", "application/json");
        assertEquals(500, failure.statusCode());
        assertFalse(failure.body().contains("Internal detail"));
        assertFalse(failure.body().contains("\"trace\""));
    }

    private void assertPage(String path, int status, String text) throws Exception {
        var response = request(path, "text/html");
        assertEquals(status, response.statusCode(), path);
        assertTrue(response.body().contains(text), response.body());
        assertFalse(response.body().contains("Whitelabel"));
        assertFalse(response.body().contains("Internal detail"));
    }

    private HttpResponse<String> request(String path, String accept) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"
                        + environment.getProperty("local.server.port") + path))
                .header("Accept", accept).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
