package client.nilore.utils.math;

import java.util.concurrent.ThreadLocalRandom;

public final class RandomUtil {
    private RandomUtil() {}

    public static int between(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
