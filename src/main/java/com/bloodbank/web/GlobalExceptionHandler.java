package com.bloodbank.web;

import com.bloodbank.service.DonorService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * One place that turns exceptions into the status-code contract documented in
 * {@code docs/API.md} section 5, so that the HTML screens and the JSON API can
 * never disagree about what a failure means.
 *
 * <pre>
 *   400  validation failed            409  legal request, illegal state
 *   403  authenticated, wrong role    422  semantically invalid business input
 *   404  unknown identifier           500  unexpected failure
 * </pre>
 */
@RestControllerAdvice(basePackages = "com.bloodbank.web.api")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(LocalDateTime timestamp, int status, String error, String message,
                           String path, List<Map<String, String>> fieldErrors) { }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message,
                                           HttpServletRequest request,
                                           List<Map<String, String>> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(
                LocalDateTime.now(), status.value(), error, message,
                request.getRequestURI(), fieldErrors == null ? List.of() : fieldErrors));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidationFailure(MethodArgumentNotValidException e,
                                                        HttpServletRequest request) {
        List<Map<String, String>> fields = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", String.valueOf(fieldError.getDefaultMessage())))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "ValidationFailed",
                     "One or more fields are invalid", request, fields);
    }

    @ExceptionHandler(DonorService.DonorNotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(RuntimeException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NotFound", e.getMessage(), request, null);
    }

    @ExceptionHandler(DonorService.DuplicatePhoneException.class)
    public ResponseEntity<ApiError> onDuplicate(RuntimeException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DuplicatePhone", e.getMessage(), request, null);
    }

    /** Unknown blood group text, bad date, and similar input the binder rejects. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException e,
                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BadRequest", e.getMessage(), request, null);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(RuntimeException e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Forbidden",
                     "Your role is not permitted to perform this action", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected failure handling {}", request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError",
                     "Unexpected failure", request, null);
    }
}
