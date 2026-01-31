package atomic_seat_booking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PaymentService {

    private final Random random = new Random();

    /**
     * Mocks a payment process.
     * Randomly succeeds (70%), fails (20%), or delays (10%).
     */
    public void processPayment(String userId, Long amount, String idempotencyKey) {
        log.info("Starting payment processing in PaymentService. userId={}, amount={}, idempotencyKey={}",
                userId, amount, idempotencyKey);

        int outcome = random.nextInt(100);

        if (outcome < 10) { // 10% chance of delay
            log.info("Simulating payment delay. idempotencyKey={}", idempotencyKey);
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                log.error("Payment delay interrupted", e);
                Thread.currentThread().interrupt();
            }
        }

        if (outcome < 30) { // 20% + 10% (cumulative) chance of failure
            log.warn("Payment failed simulation. userId={}, idempotencyKey={}", userId, idempotencyKey);
            throw new RuntimeException("Payment gateway error or insufficient funds");
        }

        log.info("Payment success simulation. userId={}, idempotencyKey={}", userId, idempotencyKey);
    }
}
