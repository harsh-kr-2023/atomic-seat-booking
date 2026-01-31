package atomic_seat_booking.service;

import atomic_seat_booking.entity.Seat;
import atomic_seat_booking.entity.SeatStatus;
import atomic_seat_booking.exception.SeatAlreadyBookedException;
import atomic_seat_booking.exception.SeatAlreadyHeldException;
import atomic_seat_booking.repository.SeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import atomic_seat_booking.config.UserContext;

@Service
@Slf4j
public class SeatHoldService {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private SoftHoldService softHoldService;

    @Transactional
    public Seat holdSeat(Long seatId) {
        String userId = UserContext.getUserId();
        log.info("Attempting to hold seat. seatId={}, userId={}", seatId, userId);

        // 1. Rate Limit Checks
        rateLimiterService.checkUserLimit(userId);
        rateLimiterService.checkSeatLimit(seatId);

        // 2. Soft Hold Check (Stampede Prevention)
        // If current user doesn't have the soft hold AND someone else has it, fail
        // early.
        if (!softHoldService.hasSoftHold(seatId, userId) && !softHoldService.createSoftHold(seatId, "SYSTEM_CHECK")) {
            log.warn("Soft hold exists for another user. seatId={}, userId={}", seatId, userId);
            throw new SeatAlreadyHeldException("Seat is currently being considered by another user");
        }

        // 3. Fetch seat FOR UPDATE (Pessimistic Lock)
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> {
                    log.warn("Seat not found for hold. seatId={}", seatId);
                    return new IllegalArgumentException("Seat not found with ID: " + seatId);
                });

        log.info("Seat fetched for hold. seatId={}, currentStatus={}", seatId, seat.getStatus());

        // 4. Event level limit check
        rateLimiterService.checkEventLimit(seat.getEventId());

        Instant now = Instant.now();

        // 5. If HELD and expired → reset to AVAILABLE so it can be held again
        if (seat.getStatus() == SeatStatus.HELD && seat.isHoldExpired(now)) {
            log.info("Seat hold expired, releasing for reuse. seatId={}, heldByUserId={}", seatId,
                    seat.getHeldByUserId());
            seat.release();
        }

        // 6. Logic for holding
        try {
            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);
                seat.hold(userId, expiresAt);
                Seat savedSeat = seatRepository.save(seat);

                // Finalize: Success! Remove soft hold as it's now a hard hold.
                softHoldService.removeSoftHold(seatId);

                log.info("Seat hold successful. seatId={}, userId={}, expiresAt={}", seatId, userId, expiresAt);
                return savedSeat;
            } else if (seat.getStatus() == SeatStatus.HELD) {
                log.warn("Seat hold failed: already held. seatId={}, userId={}, currentHolder={}", seatId, userId,
                        seat.getHeldByUserId());
                throw new SeatAlreadyHeldException("Seat is already held by another user");
            } else if (seat.getStatus() == SeatStatus.BOOKED) {
                log.warn("Seat hold failed: already booked. seatId={}, userId={}", seatId, userId);
                throw new SeatAlreadyBookedException("Seat is already booked");
            }
        } catch (Exception e) {
            log.error("Error during seat hold. seatId={}, userId={}, error={}", seatId, userId, e.getMessage());
            throw e;
        }

        throw new IllegalStateException("Unreachable state for seat: " + seat.getStatus());
    }
}