package atomic_seat_booking.entity;

import atomic_seat_booking.exception.*;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false, unique = true)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column
    private String heldByUserId;

    @Column
    private Instant holdExpiresAt;

    // Helper method to hold a seat
    public void hold(String userId, Instant expiresAt) {
        if (this.status == SeatStatus.HELD) {
            throw new SeatAlreadyHeldException("Seat is already held by another user");
        }
        if (this.status == SeatStatus.BOOKED) {
            throw new SeatAlreadyBookedException("Seat is already booked");
        }
        if (this.status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat must be AVAILABLE to be held. Current status: " + this.status);
        }

        this.status = SeatStatus.HELD;
        this.heldByUserId = userId;
        this.holdExpiresAt = expiresAt;
    }

    // Helper method to release seat if hold has expired
    public boolean releaseIfExpired(Instant now) {
        if (this.status != SeatStatus.HELD) {
            return false; // Not released
        }

        if (this.holdExpiresAt != null && now.isAfter(this.holdExpiresAt)) {
            this.status = SeatStatus.AVAILABLE;
            this.heldByUserId = null;
            this.holdExpiresAt = null;
            return true;
        }
        return false;
    }

    // Helper method to book a seat
    public void book(String userId, Instant now) {
        if (this.status == SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat must be HELD to be booked. Current status: " + this.status);
        }
        if (this.status == SeatStatus.BOOKED) {
            throw new SeatAlreadyBookedException("Seat is already booked");
        }

        if (!userId.equals(this.heldByUserId)) {
            throw new UnauthorizedSeatAccessException("Seat is held by another user");
        }

        if (this.holdExpiresAt != null && now.isAfter(this.holdExpiresAt)) {
            throw new SeatHoldExpiredException("Seat hold has expired");
        }

        this.status = SeatStatus.BOOKED;
        this.holdExpiresAt = null; // Clear expiration since it's now booked
    }

    // Helper method to release a booked or held seat
    public void release() {
        if (this.status == SeatStatus.AVAILABLE) {
            return;
        }
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.holdExpiresAt = null;
    }

    // Helper method to check if seat is available for booking
    public boolean isAvailableForBooking(String userId, Instant now) {
        if (this.status == SeatStatus.AVAILABLE) {
            return false; // Must be held first if 'cannot skip states'
        }

        if (this.status == SeatStatus.HELD) {
            // Available to book if held by same user and not expired
            return userId.equals(this.heldByUserId) &&
                    (this.holdExpiresAt == null || now.isBefore(this.holdExpiresAt));
        }

        return false; // Booked seats are not available
    }

    // Helper method to check if hold has expired
    public boolean isHoldExpired(Instant now) {
        return this.status == SeatStatus.HELD &&
                this.holdExpiresAt != null &&
                now.isAfter(this.holdExpiresAt);
    }

    // Helper method to get the user who currently controls the seat
    public String getControllingUserId() {
        if (this.status == SeatStatus.AVAILABLE) {
            return null;
        }
        return this.heldByUserId;
    }
}
