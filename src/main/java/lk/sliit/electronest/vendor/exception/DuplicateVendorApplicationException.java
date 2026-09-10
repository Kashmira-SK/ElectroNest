package lk.sliit.electronest.vendor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateVendorApplicationException extends RuntimeException {

    public DuplicateVendorApplicationException(String message) {
        super(message);
    }
}
