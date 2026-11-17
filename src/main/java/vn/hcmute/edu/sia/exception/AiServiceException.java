package vn.hcmute.edu.sia.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;

    public AiServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status != null ? status : HttpStatus.BAD_GATEWAY;
    }

    public AiServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status != null ? status : HttpStatus.BAD_GATEWAY;
    }
}
