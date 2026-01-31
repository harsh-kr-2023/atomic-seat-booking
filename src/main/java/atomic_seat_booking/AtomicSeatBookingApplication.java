package atomic_seat_booking;

import atomic_seat_booking.entity.User;
import atomic_seat_booking.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AtomicSeatBookingApplication {

	public static void main(String[] args) {
		SpringApplication.run(AtomicSeatBookingApplication.class, args);
	}

	@Bean
	public CommandLineRunner seedUsers(UserRepository userRepository) {
		return args -> {
			if (userRepository.count() == 0) {
				userRepository.save(new User("user-1", "Alice", "alice@example.com"));
				userRepository.save(new User("user-2", "Bob", "bob@example.com"));
				userRepository.save(new User("user-3", "Charlie", "charlie@example.com"));
				System.out.println("✅ Seeded initial users: Alice (user-1), Bob (user-2), Charlie (user-3)");
			}
		};
	}
}
