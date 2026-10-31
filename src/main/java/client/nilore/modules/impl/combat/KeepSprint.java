package client.nilore.modules.impl.combat;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.SprintEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.JumpResetMode;
import client.nilore.modules.impl.combat.antikb.ReduceXZMode;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.utils.game.MotionSimulator;
import net.minecraft.world.entity.Entity;

/**
 * 从 KillAura 抽出来的 KeepSprint 逻辑。
 */
public class KeepSprint extends Module {
    public static KeepSprint INSTANCE;

    /** 攻击前释放 sprint 换 crit 的总开关。关掉之后本模块完全静默。 */
    public final BooleanSetting enabled = new BooleanSetting("Enabled", true);

    public KeepSprint() {
        super("KeepSprint", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getModuleName() {
        return "KeepSprint";
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getDisplayName() {
        return "§fKeepSprint §7[GrimAC]";
    }

    @Override
    public String getSuffix() {
        return "GrimAC";
    }

    // ============================================================
    // 核心判定
    // ============================================================

    public static boolean shouldReleaseSprint() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (!INSTANCE.enabled.getValue()) return false;
        if (mc.player == null || mc.level == null) return false;

        Entity target = KillAura.target;
        if (target == null) return false;

        // Antikb 互斥：counter / delay-window / jump-reset 已经接管了 sprint
        if (Velocity.INSTANCE != null && Velocity.INSTANCE.isEnabled()) {
            if (Velocity.isInDelayWindow() && shouldKeepSprintInDelayWindow()) {
                return false;
            }
            if (Velocity.isCountering()) {
                return false;
            }
            if (Velocity.mode.is("JumpReset") && JumpResetMode.isJumping) {
                return false;
            }
        }

        // 3.5 格 crit reach
        if (target.distanceToSqr(mc.player) > 12.25) {
            return false;
        }
        if (mc.player.isDeadOrDying()) {
            return false;
        }
        // 正在使用物品时不释放（会打断）
        return !mc.player.isUsingItem() || !mc.options.keyUse.isDown();
    }

    public static boolean shouldSkipAttack() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (!INSTANCE.enabled.getValue()) return false;
        if (mc.player == null) return false;
        if (!mc.player.isSprinting()) return false;
        if (ReduceXZMode.handlingVelocity) return false;
        return shouldReleaseSprint();
    }

    // ============================================================
    // SprintEvent：真正的释放
    // ============================================================

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (!shouldReleaseSprint()) return;
        mc.options.keySprint.setDown(false);
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    // ============================================================
    // 攻击执行与恢复 Sprint (修复：接收 Entity 参数)
    // ============================================================

    /**
     * 在 KillAura 成功判定命中时调用。
     * 负责：1. 发送攻击包 2. 通知 Critical 3. 攻击后立即恢复 Sprint
     */
    public void attack(Entity entity) {
        if (mc.player == null || mc.gameMode == null) return;

        // 1. 执行真实的攻击发包
        if (entity != null) {
            mc.gameMode.attack(mc.player, entity);
        }

        // 2. 通知 Critical 模块，让它的状态机记录这次攻击
        if (Critical.INSTANCE != null && Critical.INSTANCE.isEnabled()) {
            Critical.INSTANCE.notifyAttack(entity);
        }

        // 3. 攻击后恢复 Sprint
        if (INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.enabled.getValue()) {
            // 如果玩家按着前进键，且没有在使用物品，则尝试恢复疾跑
            if (mc.options.keyUp.isDown() && !mc.player.isUsingItem()) {
                mc.player.setSprinting(true);
                // 确保按键状态同步（如果之前因为 crit 被释放了）
                mc.options.keySprint.setDown(true);
            }
        }
    }

    // ============================================================
    // delay window 判定（NoXZ 的 keepsprint 合约）
    // ============================================================

    public static boolean shouldKeepSprintInDelayWindow() {
        if (mc.player == null || mc.level == null) return false;
        if (mc.player.onGround() || ReduceXZMode.isCountering()) return true;
        if (mc.player.getDeltaMovement().y > 0 || mc.player.fallDistance <= 0.0f) return false;
        return new MotionSimulator(mc.player).findLandingBlock(1) != null;
    }
}