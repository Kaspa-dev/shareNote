package lt.sharenote.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    final HttpStatus status;
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
    public static ApiException notFound(String resource, long id) {
        return new ApiException(HttpStatus.NOT_FOUND, resource + " (id=" + id + ") was not found.");
    }
}
