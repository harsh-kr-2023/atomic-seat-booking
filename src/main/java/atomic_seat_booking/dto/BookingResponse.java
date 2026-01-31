package atomic_seat_booking.dto;

import atomic_seat_booking.entity.SeatStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookingResponse {
    private Long bookingId;
    private Long seatId;
    private SeatStatus status;
}
