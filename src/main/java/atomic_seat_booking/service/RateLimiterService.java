package atomic_seat_booking.service;

import atomic_seat_booking.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bandwidth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RateLimiterService {

    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> seatBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> eventBuckets = new ConcurrentHashMap<>();

    @Value("${ratelimit.user.capacity:5}")
    private int userCapacity;
    @Value("${ratelimit.user.refill:5}")
    private int userRefill;

    @Value("${ratelimit.seat.capacity:10}")
    private int seatCapacity;
    @Value("${ratelimit.seat.refill:10}")
    private int seatRefill;

    @Value("${ratelimit.event.capacity:100}")
    private int eventCapacity;
    @Value("${ratelimit.event.refill:100}")
    private int eventRefill;

    public void checkUserLimit(String userId) {
        Bucket bucket = userBuckets.computeIfAbsent(userId, k -> createNewBucket(userCapacity, userRefill));
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for user: {}", userId);
            throw new RateLimitExceededException("Too many requests for user: " + userId);
        }
    }

    public void checkSeatLimit(Long seatId) {
        String key = String.valueOf(seatId);
        Bucket bucket = seatBuckets.computeIfAbsent(key, k -> createNewBucket(seatCapacity, seatRefill));
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for seat: {}", seatId);
            throw new RateLimitExceededException("Too many requests for seat: " + seatId);
        }
    }

    public void checkEventLimit(String eventId) {
        Bucket bucket = eventBuckets.computeIfAbsent(eventId, k -> createNewBucket(eventCapacity, eventRefill));
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for event: {}", eventId);
            throw new RateLimitExceededException("Too many requests for event: " + eventId);
        }
    }

    private Bucket createNewBucket(int capacity, int refillAmount) {
        // Updated to avoid deprecated methods if possible, but 8.x still supports
        // classic for now.
        // Using builder explicitly for a "premium" feel.
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillAmount, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
