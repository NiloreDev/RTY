package client.nilore.modules.impl.combat;

import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.ReduceXZMode;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.utils.game.MotionSimulator;
import client.nilore.utils.misc.ChatUtil;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;

public class KeepSprint extends Module {
    public static KeepSprint INSTANCE;

    // 【新增】模式选择，用于统一后缀显示逻辑
    public final ModeSetting mode = new ModeSetting("Mode", "GrimAC", "GrimAC").withDefault("GrimAC");

    // Debug 开关
    public final BooleanSetting debug = new BooleanSetting("Debug", false);

    public KeepSprint() {
        super("KeepSprint", Category.COMBAT);
        INSTANCE = this;
        this.registerSetting(mode, debug);
    }

    @Override
    public String getModuleName() {
        return "KeepSprint";
    }

    // 【修改】后缀逻辑与 Critical 完全一致
    @Override
    public String getSuffix() {
        return this.mode.getValue();
    }

    // 【修改】显示逻辑与 Critical 完全一致
    @Override
    public String getDisplayName() {
        String m = this.mode.getValue();
        if (m == null || m.isEmpty()) {
            return "§bKeepSprint";
        }
        return "§bKeepSprint §7[" + m + "]";
    }

    @Override
    public void onTick() {

    }

    /**
     * 提供给 KillAura 判断是否要跳过攻击
     */
    public static boolean shouldSkipAttack() {
        if (mc.player == null) return true;
        // 可以在这里自定义跳过条件，如物品使用、某些特殊状态等
        return false;
    }

    /**
     * 提供给 KillAura 判断在 Velocity (ReduceXZ) 的延迟窗口内是否应该保持疾跑
     */
    public static boolean shouldKeepSprintInDelayWindow() {
        if (mc.player == null || mc.level == null) return false;
        if (mc.player.onGround() || ReduceXZMode.isCountering()) return true;
        if (mc.player.getDeltaMovement().y > 0 || mc.player.fallDistance <= 0.0f) return false;
        return new MotionSimulator(mc.player).findLandingBlock(1) != null;
    }

    /**
     * 执行保持疾跑的攻击
     */
    public void attack(Entity entity) {
        if (mc.player == null || mc.getConnection() == null || entity == null) return;

        boolean wasSprinting = mc.player.isSprinting();

        // 发送攻击包
        mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(entity, mc.player.isShiftKeyDown()));

        // 客户端执行攻击逻辑
        mc.player.attack(entity);

        // 恢复疾跑状态，防止被原版攻击逻辑打断
        if (wasSprinting) {
            mc.player.setSprinting(true);
            mc.options.keySprint.setDown(true);

            // Debug 信息输出
            if (this.debug.getValue()) {
                ChatUtil.print(true, "§b[KeepSprint] §fRestored sprint for target: §e" + entity.getName().getString());
            }
        } else {
            if (this.debug.getValue()) {
                ChatUtil.print(true, "§b[KeepSprint] §fAttack sent, but player was not sprinting.");
            }
        }
    }
}