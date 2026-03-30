package ai.llmmonkey.api.exception;

import ai.llmmonkey.api.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LlmMonkeyException.class)
    public ResponseEntity<ErrorResponse> handleLlmMonkeyException(LlmMonkeyException ex) {
        log.warn("LLM Monkey error: {} (status={})", ex.getMessage(), ex.getStatusCode());
        var error = ErrorResponse.of(ex.getMessage(), ex.getErrorType(), ex.getErrorCode());
        var headers = new org.springframework.http.HttpHeaders();
        if (ex instanceof RateLimitException rle) {
            headers.set("Retry-After", String.valueOf(rle.getRetryAfterSeconds()));
        }
        return new ResponseEntity<>(error, headers, HttpStatus.valueOf(ex.getStatusCode()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        var error = ErrorResponse.of(ex.getReason(), "request_error", ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        var error = ErrorResponse.of(ex.getMessage(), "invalid_request_error", "invalid_argument");
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        var error = ErrorResponse.of("Internal server error", "server_error", "internal_error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
