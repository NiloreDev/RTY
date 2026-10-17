package client.nilore.modules.impl.combat;

import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.JumpResetMode;
import client.nilore.modules.impl.combat.antikb.ReduceXZMode;
import client.nilore.utils.game.MotionSimulator;
import net.minecraft.world.entity.Entity;

public class KeepSprint extends Module {
    public static KeepSprint INSTANCE;

    public KeepSprint() {
        super("KeepSprint", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getSuffix() {
        return "GrimAC";
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    @Override
    public void onTick() {

    }

    public static boolean shouldStopSprint() {
        KeepSprint module = INSTANCE;
        if (module == null || !module.isEnabled()) return false;
        if (mc.player == null || KillAura.target == null) return false;
        if (mc.player.isUsingItem()) return false;
        if (JumpResetMode.isJumping) return false;
        if (ReduceXZMode.isCountering()) return false;
        if (ReduceXZMode.isInDelayWindow() && shouldKeepSprintInDelayWindow()) return false;
        if (KillAura.target.getBoundingBox().distanceToSqr(mc.player.position()) > 12.25) return false;
        return !mc.player.onGround() || !mc.options.keyJump.isDown();
    }

    /**
     * 【改动】private -> public，供 velocity 包的 ReduceXZMode 跨包调用。
     */
    public static boolean shouldKeepSprintInDelayWindow() {
        if (mc.player == null || mc.level == null) return false;
        if (mc.player.onGround() || ReduceXZMode.isCountering()) return true;
        if (mc.player.getDeltaMovement().y > 0 || mc.player.hurtTime <= 0) return false;
        return new MotionSimulator(mc.player).findLandingBlock(1) != null;
    }

    /**
     * 当 KeepSprint 开启时，检查是否应跳过攻击以保持冲刺。
     * 返回 true 表示应跳过当前攻击。
     */
    public static boolean shouldSkipAttack() {
        KeepSprint module = INSTANCE;
        if (module == null || !module.isEnabled()) return false;
        if (ReduceXZMode.handlingVelocity) return false;
        if (mc.player == null) return false;
        return mc.player.isSprinting();
    }

    /**
     * 执行攻击但保持冲刺状态。
     */
    public void attack(Entity entity) {
        if (entity == null || mc.player == null || mc.gameMode == null) return;
        mc.gameMode.attack(mc.player, entity);
    }
}