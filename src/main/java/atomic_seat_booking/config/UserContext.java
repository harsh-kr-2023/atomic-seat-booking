package atomic_seat_booking.config;

public class UserContext {
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

    public static void setUserId(String userId) {
        currentUser.set(userId);
    }

    public static String getUserId() {
        return currentUser.get();
    }

    public static void clear() {
        currentUser.remove();
    }
}
