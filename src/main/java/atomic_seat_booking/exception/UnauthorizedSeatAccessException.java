package atomic_seat_booking.exception;

public class UnauthorizedSeatAccessException extends RuntimeException {
    public UnauthorizedSeatAccessException(String message) {
        super(message);
    }
}
