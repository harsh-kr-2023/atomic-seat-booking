package atomic_seat_booking.service;

import atomic_seat_booking.entity.Booking;
import atomic_seat_booking.entity.IdempotencyKey;
import atomic_seat_booking.entity.Seat;
import atomic_seat_booking.exception.IdempotencyConflictException;
import atomic_seat_booking.repository.BookingRepository;
import atomic_seat_booking.repository.IdempotencyKeyRepository;
import atomic_seat_booking.repository.SeatRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import atomic_seat_booking.config.UserContext;

@Service
@Slf4j
public class BookingService {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public Booking confirmSeat(Long seatId, String idempotencyKey) {
        String userId = UserContext.getUserId();
        log.info("Attempting to confirm booking. seatId={}, userId={}, idempotencyKey={}", seatId, userId,
                idempotencyKey);

        // Rate Limit Checks
        rateLimiterService.checkUserLimit(userId);
        rateLimiterService.checkSeatLimit(seatId);

        // 1. Check idempotency table first
        Optional<IdempotencyKey> existingKey = idempotencyKeyRepository.findByUserIdAndKey(userId, idempotencyKey);
        if (existingKey.isPresent()) {
            log.info("Idempotency hit detected. userId={}, idempotencyKey={}", userId, idempotencyKey);
            try {
                Booking storedBooking = objectMapper.readValue(existingKey.get().getResponsePayload(), Booking.class);
                if (!storedBooking.getSeatId().equals(seatId)) {
                    log.error(
                            "Idempotency conflict: key used for different seat. key={}, existingSeatId={}, requestedSeatId={}",
                            idempotencyKey, storedBooking.getSeatId(), seatId);
                    throw new IdempotencyConflictException("Idempotency key already used for a different seat booking");
                }
                return storedBooking;
            } catch (JsonProcessingException e) {
                log.error("Failed to parse stored idempotency payload. key={}", idempotencyKey, e);
                throw new RuntimeException("Failed to parse stored idempotency response", e);
            }
        }

        // 2. Fetch seat FOR UPDATE
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> {
                    log.warn("Seat not found for booking. seatId={}", seatId);
                    return new IllegalArgumentException("Seat not found with ID: " + seatId);
                });

        log.info("Seat locked for booking. seatId={}, currentStatus={}, heldBy={}",
                seatId, seat.getStatus(), seat.getHeldByUserId());

        // Event level limit check
        rateLimiterService.checkEventLimit(seat.getEventId());

        // 3. Preliminary validation
        Instant now = Instant.now();
        if (!seat.isAvailableForBooking(userId, now)) {
            log.warn("Validation failed for seat booking. seatId={}, userId={}, status={}", seatId, userId,
                    seat.getStatus());
            seat.book(userId, now); // Throws specific domain exception
        }

        // 4. External Side Effect: Payment
        log.info("Processing payment. userId={}, amount=100, idempotencyKey={}", userId, idempotencyKey);
        paymentService.processPayment(userId, 100L, idempotencyKey);
        log.info("Payment successful. userId={}, idempotencyKey={}", userId, idempotencyKey);

        // 5. Atomic State Transition: Mark seat BOOKED
        seat.book(userId, now);

        // 6. Create booking record
        Booking booking = new Booking();
        booking.setSeatId(seatId);
        booking.setUserId(userId);
        booking.setBookedAt(now);
        booking = bookingRepository.save(booking);

        // 7. Explicitly save seat state
        seatRepository.save(seat);

        // 8. Store idempotency response
        try {
            String payload = objectMapper.writeValueAsString(booking);
            idempotencyKeyRepository.save(new IdempotencyKey(userId, idempotencyKey, payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize booking for idempotency. key={}", idempotencyKey, e);
            throw new RuntimeException("Serialization error", e);
        }

        log.info("Booking confirmed successfully. seatId={}, bookingId={}, userId={}", seatId, booking.getId(), userId);
        return booking;
    }
}
