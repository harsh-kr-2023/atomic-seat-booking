package atomic_seat_booking.repository;

import atomic_seat_booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findBySeatId(Long seatId);

    boolean existsBySeatId(Long seatId);
}
