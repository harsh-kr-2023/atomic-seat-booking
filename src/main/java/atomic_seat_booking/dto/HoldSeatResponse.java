package atomic_seat_booking.dto;

import atomic_seat_booking.entity.SeatStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class HoldSeatResponse {
    private Long seatId;
    private SeatStatus status;
    private Instant holdExpiresAt;
}
