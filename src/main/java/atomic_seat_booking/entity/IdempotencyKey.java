package atomic_seat_booking.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_idempotency_key", columnNames = { "userId", "key" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public IdempotencyKey(String userId, String key, String responsePayload) {
        this.userId = userId;
        this.key = key;
        this.responsePayload = responsePayload;
        this.createdAt = Instant.now();
    }
}
