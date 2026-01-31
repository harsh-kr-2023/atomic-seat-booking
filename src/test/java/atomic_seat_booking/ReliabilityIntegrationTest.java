package atomic_seat_booking;

import atomic_seat_booking.config.UserContext;
import atomic_seat_booking.entity.Booking;
import atomic_seat_booking.entity.Seat;
import atomic_seat_booking.entity.SeatStatus;
import atomic_seat_booking.entity.User;
import atomic_seat_booking.exception.SeatHoldExpiredException;
import atomic_seat_booking.exception.UnauthorizedSeatAccessException;
import atomic_seat_booking.repository.BookingRepository;
import atomic_seat_booking.repository.IdempotencyKeyRepository;
import atomic_seat_booking.repository.SeatRepository;
import atomic_seat_booking.repository.UserRepository;
import atomic_seat_booking.service.BookingService;
import atomic_seat_booking.service.PaymentService;
import atomic_seat_booking.service.SeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
public class ReliabilityIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private PaymentService paymentService;

    private Long testSeatId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();

        // Ensure consistent users
        userRepository.save(new User("user-1", "Alice", "alice@test.com"));
        userRepository.save(new User("user-2", "Bob", "bob@test.com"));

        // Ensure reliable payment by default for tests
        Mockito.doNothing().when(paymentService).processPayment(anyString(), anyLong(), anyString());

        Seat seat = new Seat();
        seat.setEventId("event-1");
        seat.setSeatNumber("B1-" + UUID.randomUUID().toString().substring(0, 8));
        seat.setStatus(SeatStatus.AVAILABLE);
        seat = seatRepository.save(seat);
        testSeatId = seat.getId();
    }

    @Test
    void testTransactionRollbackOnPaymentCrash() {
        String userId = "user-1";
        UserContext.setUserId(userId);
        seatHoldService.holdSeat(testSeatId);

        // Simulate a crash/exception during payment
        doThrow(new RuntimeException("SIMULATED_CRASH"))
                .when(paymentService).processPayment(anyString(), anyLong(), anyString());

        String key = UUID.randomUUID().toString();
        assertThrows(RuntimeException.class, () -> {
            bookingService.confirmSeat(testSeatId, key);
        });

        // Verify state: Seat should still be HELD (rolled back)
        Seat seat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(bookingRepository.count()).isEqualTo(0);
        UserContext.clear();
    }

    @Test
    void testDelayedPaymentBeyondExpiry() {
        String userId = "user-1";
        UserContext.setUserId(userId);
        // 1. Hold seat
        seatHoldService.holdSeat(testSeatId);

        // 2. Mock payment to delay for 1 second
        doAnswer(invocation -> {
            Thread.sleep(1000);
            return null;
        }).when(paymentService).processPayment(anyString(), anyLong(), anyString());

        // 3. Make hold expire NOW
        Seat seat = seatRepository.findById(testSeatId).orElseThrow();
        seat.setHoldExpiresAt(Instant.now().minus(500, ChronoUnit.MILLIS));
        seatRepository.saveAndFlush(seat);

        // 4. Try to confirm
        String key = UUID.randomUUID().toString();
        assertThrows(SeatHoldExpiredException.class, () -> {
            bookingService.confirmSeat(testSeatId, key);
        });

        // Verify: Not booked
        Seat finalSeat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(finalSeat.getStatus()).isNotEqualTo(SeatStatus.BOOKED);
        UserContext.clear();
    }

    @Test
    void testIdempotencyReturnsConsistentResult() {
        String userId = "user-1";
        UserContext.setUserId(userId);
        seatHoldService.holdSeat(testSeatId);

        String key = "fixed-key";

        // First call
        Booking firstBooking = bookingService.confirmSeat(testSeatId, key);
        assertThat(firstBooking).isNotNull();

        // Subsequent calls with same key
        for (int i = 0; i < 3; i++) {
            Booking subsequentBooking = bookingService.confirmSeat(testSeatId, key);
            assertThat(subsequentBooking.getId()).isEqualTo(firstBooking.getId());
        }

        // Verify only 1 record in DB
        assertThat(bookingRepository.count()).isEqualTo(1);
        UserContext.clear();
    }

    @Test
    void testConcurrentConfirmsWithDifferentKeys() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        String userId = "user-1";
        UserContext.setUserId(userId);
        seatHoldService.holdSeat(testSeatId);

        for (int i = 0; i < threadCount; i++) {
            String key = "key-" + i;
            executorService.submit(() -> {
                UserContext.setUserId(userId);
                try {
                    latch.await();
                    bookingService.confirmSeat(testSeatId, key);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    UserContext.clear();
                }
            });
        }

        latch.countDown();
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // Only ONE should ever succeed
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        UserContext.clear();
    }

    @Test
    void testUserBCannotConfirmUserASeat() {
        // User A holds the seat
        UserContext.setUserId("user-1");
        seatHoldService.holdSeat(testSeatId);
        UserContext.clear();

        // User B tries to confirm it
        UserContext.setUserId("user-2");
        String key = UUID.randomUUID().toString();

        assertThrows(UnauthorizedSeatAccessException.class, () -> {
            bookingService.confirmSeat(testSeatId, key);
        });

        // Verify seat still belongs to A and is HELD
        Seat seat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(seat.getHeldByUserId()).isEqualTo("user-1");
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        UserContext.clear();
    }

    @Test
    void testIdempotencyIsUserScoped() {
        String key = "shared-key";

        // User A books successfully
        UserContext.setUserId("user-1");
        seatHoldService.holdSeat(testSeatId);
        bookingService.confirmSeat(testSeatId, key);
        UserContext.clear();

        // User B tries to use the SAME key for a DIFFERENT seat
        Seat seatB = new Seat();
        seatB.setEventId("event-1");
        seatB.setSeatNumber("B2");
        seatB = seatRepository.save(seatB);

        UserContext.setUserId("user-2");
        seatHoldService.holdSeat(seatB.getId());

        // This should NOT hit User A's idempotency record and should succeed for User B
        Booking bookingB = bookingService.confirmSeat(seatB.getId(), key);
        assertThat(bookingB).isNotNull();
        assertThat(bookingB.getUserId()).isEqualTo("user-2");
        UserContext.clear();
    }
}
