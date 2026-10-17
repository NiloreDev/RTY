package client.nilore.utils.epsilon.math;

import java.util.concurrent.ThreadLocalRandom;

/** Original Epsilon random helpers. */
public final class MathUtils {
    private MathUtils() {}
    public static int getRandom(int min, int max) {
        return min >= max ? min : (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
    }
    public static float getRandom(float min, float max) {
        return min >= max ? min : ThreadLocalRandom.current().nextFloat(min, Math.nextUp(max));
    }
    public static double getRandom(double min, double max) {
        return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }
}
