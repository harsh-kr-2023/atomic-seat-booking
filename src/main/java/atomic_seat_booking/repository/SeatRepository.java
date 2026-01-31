package atomic_seat_booking.repository;

import atomic_seat_booking.entity.Seat;
import atomic_seat_booking.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({ @QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000") })
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    Optional<Seat> findBySeatNumber(String seatNumber);

    List<Seat> findByEventId(String eventId);

    List<Seat> findByStatus(SeatStatus status);

    List<Seat> findByEventIdAndStatus(String eventId, SeatStatus status);
}