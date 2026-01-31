package atomic_seat_booking.controller;

import atomic_seat_booking.config.UserContext;
import atomic_seat_booking.dto.*;
import atomic_seat_booking.entity.Booking;
import atomic_seat_booking.entity.Seat;
import atomic_seat_booking.entity.SeatStatus;
import atomic_seat_booking.repository.SeatRepository;
import atomic_seat_booking.service.BookingService;
import atomic_seat_booking.service.SeatHoldService;
import atomic_seat_booking.service.SoftHoldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller for managing seat reservations and bookings.
 * Enforces atomic state transitions and identity-aware access.
 */
@RestController
@RequestMapping("/api/seats")
public class SeatController {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SoftHoldService softHoldService;

    @GetMapping
    public List<Seat> getAllSeats() {
        return seatRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Seat> getSeatById(@PathVariable Long id) {
        Optional<Seat> seat = seatRepository.findById(id);
        return seat.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new seat for an event.
     */
    @PostMapping
    public Seat createSeat(@RequestBody Seat seat) {
        // Ensure new seats start as AVAILABLE
        seat.setStatus(SeatStatus.AVAILABLE);
        return seatRepository.save(seat);
    }

    /**
     * Claims a short-lived (15s) Redis hold to prevent DB stampede.
     * Accessible by any authenticated user.
     */
    @PostMapping("/{seatId}/soft-hold")
    public ResponseEntity<?> softHoldSeat(@PathVariable Long seatId) {
        String userId = UserContext.getUserId();
        boolean success = softHoldService.createSoftHold(seatId, userId);
        if (success) {
            return ResponseEntity.ok().body("Soft hold created for 15 seconds");
        } else {
            return ResponseEntity.status(409).body("Seat is currently being considered by another user");
        }
    }

    /**
     * Upgrades a soft hold to a formal 15-minute DB-level hold.
     * Uses Pessimistic Writing Lock to ensure only one user succeeds.
     */
    @PostMapping("/{seatId}/hold")
    public ResponseEntity<HoldSeatResponse> holdSeat(@PathVariable Long seatId) {
        Seat seat = seatHoldService.holdSeat(seatId);

        HoldSeatResponse response = HoldSeatResponse.builder()
                .seatId(seat.getId())
                .status(seat.getStatus())
                .holdExpiresAt(seat.getHoldExpiresAt())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Finalizes booking by processing payment and updating seat state to BOOKED.
     * Uses X-Idempotency-Key to ensure safe retries without double charging.
     */
    @PostMapping("/{seatId}/confirm")
    public ResponseEntity<BookingResponse> confirmSeat(
            @PathVariable Long seatId,
            @RequestHeader(value = "X-Idempotency-Key") String idempotencyKey) {

        Booking booking = bookingService.confirmSeat(seatId, idempotencyKey);

        BookingResponse response = BookingResponse.builder()
                .bookingId(booking.getId())
                .seatId(booking.getSeatId())
                .status(SeatStatus.BOOKED)
                .build();

        return ResponseEntity.ok(response);
    }
}