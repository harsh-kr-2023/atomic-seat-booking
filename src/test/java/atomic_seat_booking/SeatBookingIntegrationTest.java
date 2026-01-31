package atomic_seat_booking;

import atomic_seat_booking.config.UserContext;
import atomic_seat_booking.entity.Seat;
import atomic_seat_booking.entity.SeatStatus;
import atomic_seat_booking.entity.User;
import atomic_seat_booking.exception.SeatHoldExpiredException;
import atomic_seat_booking.repository.SeatRepository;
import atomic_seat_booking.repository.UserRepository;
import atomic_seat_booking.service.BookingService;
import atomic_seat_booking.service.SeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
public class SeatBookingIntegrationTest {

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    private Long testSeatId;

    @BeforeEach
    void setUp() {
        seatRepository.deleteAll();
        userRepository.deleteAll();

        // Setup a baseline user
        userRepository.save(new User("user-1", "Alice", "alice@test.com"));

        Seat seat = new Seat();
        seat.setEventId("event-1");
        seat.setSeatNumber("A1-" + UUID.randomUUID().toString().substring(0, 8));
        seat.setStatus(SeatStatus.AVAILABLE);
        seat = seatRepository.save(seat);
        testSeatId = seat.getId();
        UserContext.clear();
    }

    @Test
    void testConcurrentHoldSucceedsForOnlyOneUser() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            String userId = "user-" + i;
            // Pre-create user to pass interceptor check (if mocked HTTP)
            // But here we call service directly
            userRepository.save(new User(userId, "User " + i, userId + "@test.com"));

            futures.add(executorService.submit(() -> {
                UserContext.setUserId(userId);
                try {
                    latch.await(); // Wait for signal to start all at once
                    seatHoldService.holdSeat(testSeatId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("Expected failure: " + e.getMessage());
                } finally {
                    UserContext.clear();
                }
            }));
        }

        latch.countDown(); // Start all threads
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        Seat finalizedSeat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(finalizedSeat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void testBookingFailsAfterHoldExpires() {
        String userId = "user-1";
        UserContext.setUserId(userId);

        // 1. Hold the seat
        seatHoldService.holdSeat(testSeatId);

        // 2. Manually expire the hold in DB (since default is 15 mins)
        Seat seat = seatRepository.findById(testSeatId).orElseThrow();
        seat.setHoldExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        seatRepository.saveAndFlush(seat);

        // 3. Try to confirm booking
        String idempotencyKey = UUID.randomUUID().toString();

        assertThrows(SeatHoldExpiredException.class, () -> {
            bookingService.confirmSeat(testSeatId, idempotencyKey);
        });

        // 4. Verify no booking was created
        Seat afterFailedBooking = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(afterFailedBooking.getStatus()).isEqualTo(SeatStatus.HELD); // Remains HELD (until released)
        UserContext.clear();
    }
}
