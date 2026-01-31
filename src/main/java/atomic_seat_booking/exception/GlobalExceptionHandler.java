package atomic_seat_booking.exception;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @Data
    @Builder
    public static class ErrorResponse {
        private String message;
        private int status;
        private String exception;
        private Instant timestamp;
    }

    @ExceptionHandler(SeatAlreadyHeldException.class)
    public ResponseEntity<ErrorResponse> handleSeatAlreadyHeld(SeatAlreadyHeldException e) {
        return buildErrorResponse(e.getMessage(), HttpStatus.CONFLICT, "SeatAlreadyHeldException");
    }

    @ExceptionHandler(SeatAlreadyBookedException.class)
    public ResponseEntity<ErrorResponse> handleSeatAlreadyBooked(SeatAlreadyBookedException e) {
        return buildErrorResponse(e.getMessage(), HttpStatus.CONFLICT, "SeatAlreadyBookedException");
    }

    @ExceptionHandler(SeatHoldExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSeatHoldExpired(SeatHoldExpiredException e) {
        return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST, "SeatHoldExpiredException");
    }

    @ExceptionHandler(UnauthorizedSeatAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedSeatAccess(UnauthorizedSeatAccessException e) {
        return buildErrorResponse(e.getMessage(), HttpStatus.FORBIDDEN, "UnauthorizedSeatAccessException");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException e) {
        return buildErrorResponse(e.getMessage(), HttpStatus.CONFLICT, "IdempotencyConflictException");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException e) {
        return buildErrorResponse(e.getMessage(), HttpStatus.TOO_MANY_REQUESTS, "RateLimitExceededException");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return buildErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND, "IllegalArgumentException");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST, "IllegalStateException");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        return buildErrorResponse("An unexpected error occurred. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR, "UnexpectedException");
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(String message, HttpStatus status, String exceptionName) {
        ErrorResponse error = ErrorResponse.builder()
                .message(message)
                .status(status.value())
                .exception(exceptionName)
                .timestamp(Instant.now())
                .build();
        return new ResponseEntity<>(error, status);
    }
}
