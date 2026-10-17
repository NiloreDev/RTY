package client.nilore.utils.epsilon.player;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Epsilon player helper adapted to the Nilore Minecraft version. */
public final class PlayerUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    private PlayerUtils() {}

    public static void swingHand(InteractionHand hand) {
        if (mc.player != null) mc.player.swing(hand);
    }

    public static boolean isEating() {
        if (mc.player == null) return false;
        return mc.player.isUsingItem() && (mc.player.getMainHandItem().isEdible() || mc.player.getOffhandItem().isEdible());
    }

    public static boolean isInWeb() {
        if (mc.player == null || mc.level == null) return false;
        AABB box = mc.player.getBoundingBox().deflate(1.0E-6);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    pos.set(x, y, z);
                    if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) return true;
                }
            }
        }
        return false;
    }

    public static boolean isInBlock() {
        if (mc.player == null || mc.level == null) return false;
        AABB box = mc.player.getBoundingBox().deflate(1.0E-6);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    pos.set(x, y, z);
                    if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) return true;
                }
            }
        }
        return false;
    }
}
