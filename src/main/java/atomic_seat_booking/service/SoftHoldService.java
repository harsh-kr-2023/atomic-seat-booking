package atomic_seat_booking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class SoftHoldService {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Value("${softhold.ttl.seconds:15}")
    private int softHoldTtl;

    private static final String KEY_PREFIX = "soft_hold:seat:";

    /**
     * Attempts to create a soft hold in Redis.
     * Soft holds are short-lived and non-blocking at the DB level.
     */
    public boolean createSoftHold(Long seatId, String userId) {
        if (redisTemplate == null) {
            log.warn("Redis not configured. Skipping soft hold check.");
            return true; // Graceful fallback: allow proceeding to hard hold attempt
        }

        String key = KEY_PREFIX + seatId;
        try {
            // SET key userId NX EX ttl
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, userId, Duration.ofSeconds(softHoldTtl));
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.error("Redis error during soft hold creation. seatId={}, userId={}, error={}", seatId, userId,
                    e.getMessage());
            return true; // Graceful fallback: don't break the system if Redis is down
        }
    }

    /**
     * Verifies if a user has a valid soft hold on a seat.
     */
    public boolean hasSoftHold(Long seatId, String userId) {
        if (redisTemplate == null)
            return true;

        String key = KEY_PREFIX + seatId;
        try {
            String currentHolder = redisTemplate.opsForValue().get(key);
            return userId.equals(currentHolder);
        } catch (Exception e) {
            log.error("Redis error during soft hold verification. seatId={}, userId={}", seatId, userId);
            return true; // Graceful fallback
        }
    }

    /**
     * Explicitly removes a soft hold (e.g., after successful hard hold).
     */
    public void removeSoftHold(Long seatId) {
        if (redisTemplate == null)
            return;

        String key = KEY_PREFIX + seatId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to remove soft hold from Redis. seatId={}", seatId);
        }
    }
}
