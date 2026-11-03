package vn.hcmute.edu.sia.exception;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.hcmute.edu.sia.dto.response.LoginLockResponse;
import vn.hcmute.edu.sia.dto.response.ApiErrorResponse;
import vn.hcmute.edu.sia.dto.response.OtpVerificationLockResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
                return buildErrorResponse(
                        HttpStatus.BAD_REQUEST,
                        exception.getMessage()
                );
        }

        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception) {
                return buildErrorResponse(
                        HttpStatus.CONFLICT,
                        exception.getMessage()
                );
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
                MaxUploadSizeExceededException exception
        ) {
                return buildErrorResponse(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Document size must not exceed 20 MiB."
                );
        }

        @ExceptionHandler(DocumentStorageException.class)
        public ResponseEntity<ApiErrorResponse> handleDocumentStorage(
                DocumentStorageException exception
        ) {
                return buildErrorResponse(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Document storage is unavailable."
                );
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
                String message = exception
                        .getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("Request validation failed.");

                return buildErrorResponse(
                        HttpStatus.BAD_REQUEST,
                        message
                );
        }
        @ExceptionHandler(OtpVerificationLockedException.class)
        public ResponseEntity<OtpVerificationLockResponse> handleOtpVerificationLocked(OtpVerificationLockedException exception) {
                OtpVerificationLockResponse response =
                new OtpVerificationLockResponse(
                        exception.getMessage(),
                        exception.getLockRemainingSeconds()
                );

                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(response);
                }

        @ExceptionHandler(LoginLockedException.class)
        public ResponseEntity<LoginLockResponse> handleLoginLocked(LoginLockedException exception) {
                LoginLockResponse response = new LoginLockResponse(
                        exception.getMessage(),
                        exception.getLockRemainingSeconds()
                        );

                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(response);
        }
        private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status, String message) {
                ApiErrorResponse response = new ApiErrorResponse(
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        OffsetDateTime.now()
                );

                return ResponseEntity
                        .status(status)
                        .body(response);
        }
}
