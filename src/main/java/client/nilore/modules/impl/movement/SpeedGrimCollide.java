package client.nilore.modules.impl.movement;

/**
 * 碰撞速度增量计算。
 */
public final class SpeedGrimCollide {
    /** 【修改】允许注入的最大水平增量，从 0.08 放宽到 0.3 */
    private static final double MAX_BOOST = 0.3;
    /** 【修改】速度上限的校验上界，从 1.0 放宽到 3.0 */
    private static final double MAX_LIMIT = 3.0;
    /** 服务端碰撞推挤判定同一 tick 内会考虑的实体上限。 */
    private static final int MAX_CONTACTS = 4;

    private SpeedGrimCollide() { }

    public static double increment(SpeedAccess.Collision collision, float radians, double boost, double limit) {
        if (collision == null || collision.contacts() <= 0 || !Float.isFinite(radians)
                || !Double.isFinite(collision.motionX()) || !Double.isFinite(collision.motionZ())
                || !Double.isFinite(boost) || boost <= 0 || boost > MAX_BOOST
                || !Double.isFinite(limit) || limit <= 0 || limit > MAX_LIMIT) return 0;
        double x = collision.motionX(), z = collision.motionZ();
        double squared = x * x + z * z;
        if (squared >= limit * limit) return 0;
        double along = -Math.sin(radians) * x + Math.cos(radians) * z;
        double remaining = -along + Math.sqrt(along * along + limit * limit - squared);
        return Math.max(0, Math.min(boost * Math.min(MAX_CONTACTS, collision.contacts()), remaining));
    }
}