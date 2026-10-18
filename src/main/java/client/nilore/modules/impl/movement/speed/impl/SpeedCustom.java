package client.nilore.modules.impl.movement.speed.impl;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.MotionEvent;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.modules.impl.movement.TargetStrafe;
import client.nilore.modules.impl.movement.speed.SpeedMode;
import client.nilore.settings.impl.NumberSetting;
import net.minecraft.world.entity.Entity;

public class SpeedCustom extends SpeedMode {

    public final NumberSetting speedValue = new NumberSetting("Speed Value", 5.0, 1.0, 100.0, 0.5, this::isActive);

    public SpeedCustom() {
        super("Custom");
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.level == null) return;

        // 只在 Post 阶段改速度，Pre 阶段会被原版摩擦力吃掉
        if (event.isPre()) return;

        // ============================================================
        // 【Scaffold 兼容 #1】AutoContinent 启用时让出全部控制权
        // AutoContinent 会自动旋转玩家绕圈放置，此时若 SpeedCustom
        // 覆盖速度会破坏落地预测，导致放置失败/掉落。
        // ============================================================
        if (isScaffoldAutoContinent()) return;

        float value = speedValue.getValue().floatValue();
        // 1 秒 = 20 tick，value * 0.05 即每 tick 位移
        double speed = value * 0.05;

        // ============================================================
        // 【Scaffold 兼容 #2】bpsLimit 启用时把速度限制到 bps 上限
        // - 普通 WASD 模式下 Scaffold 通过 StrafeEvent 把 forward/strafe
        //   置 0，SpeedCustom 读到的值已经是 0，自然不会覆盖；
        // - 但环绕模式不读 forward/strafe，必须在这里主动限速，
        //   否则会绕过 Scaffold 的 bps 限制，导致放置超距。
        // ============================================================
        double bpsCap = getScaffoldBpsCap();
        if (speed > bpsCap) speed = bpsCap;

        // 模拟原版潜行减速
        if (mc.player.isShiftKeyDown()) {
            speed *= 0.5;
        }

        // 保留原版 Y 轴动量（跳跃 / 重力）
        double yDelta = mc.player.getDeltaMovement().y;

        // ============================================================
        // 【兼容 TargetStrafe】优先使用环绕方向，速度由 speedValue 决定
        // ============================================================
        if (isTargetStrafeActive()) {
            Entity target = TargetStrafe.strafeTarget;

            // 玩家 -> 目标 的水平方向向量
            double dx = target.getX() - mc.player.getX();
            double dz = target.getZ() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.001) dist = 0.001; // 防除零

            float strafeRange = TargetStrafe.getRange();

            // 距离太远先靠近，否则绕圈
            if (dist > strafeRange + 0.5) {
                double nx = dx / dist;
                double nz = dz / dist;
                mc.player.setDeltaMovement(nx * speed, yDelta, nz * speed);
            } else {
                // 环绕方向 = 垂直于 (dx, dz) 的单位向量 * 翻向符号
                double nx = -dz / dist * TargetStrafe.strafeDirectionSign;
                double nz =  dx / dist * TargetStrafe.strafeDirectionSign;
                mc.player.setDeltaMovement(nx * speed, yDelta, nz * speed);
            }
            return;
        }

        // ============================================================
        // 【普通模式】原 WASD 自由移动
        // ============================================================
        float forward = mc.player.input.forwardImpulse;
        float strafe  = mc.player.input.leftImpulse;

        if (forward != 0 || strafe != 0) {
            double yaw = Math.toRadians(mc.player.getYRot());

            double x = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * speed;
            double z = (strafe * Math.sin(yaw) + forward * Math.cos(yaw)) * speed;

            mc.player.setDeltaMovement(x, yDelta, z);
        }
    }

    // ================================================================
    // === 辅助判断 ====================================================
    // ================================================================

    /**
     * TargetStrafe 是否应该接管方向控制：
     * 1. 模块启用
     * 2. 存在有效目标
     * 3. smartStrafe（Jump Key Only）时玩家必须按住跳跃键
     */
    private boolean isTargetStrafeActive() {
        if (TargetStrafe.INSTANCE == null) return false;
        if (!TargetStrafe.INSTANCE.isEnabled()) return false;
        if (TargetStrafe.strafeTarget == null || !TargetStrafe.strafeTarget.isAlive()) return false;
        if (TargetStrafe.isSmartStrafe() && !mc.options.keyJump.isDown()) return false;
        return true;
    }

    /**
     * Scaffold 的 AutoContinent 是否正在接管移动。
     * AutoContinent 会自动旋转玩家绕圈放置，期间 SpeedCustom 应完全让出。
     */
    private boolean isScaffoldAutoContinent() {
        if (Scaffold.INSTANCE == null) return false;
        if (!Scaffold.INSTANCE.isEnabled()) return false;
        return Scaffold.INSTANCE.autoContinent.getValue();
    }

    /**
     * 从 Scaffold 读取速度上限（每 tick 位移）：
     * - Scaffold 未启用 / bpsLimit 未启用 → 返回极大值（无限制）
     * - Scaffold bpsLimit 启用 → 返回 bps * 0.05（每 tick 位移）
     * - bps >= 10.0 视为不限速（与 Scaffold 内部逻辑一致）
     */
    private double getScaffoldBpsCap() {
        if (Scaffold.INSTANCE == null) return Double.MAX_VALUE;
        if (!Scaffold.INSTANCE.isEnabled()) return Double.MAX_VALUE;
        if (!Scaffold.INSTANCE.bpsLimit.getValue()) return Double.MAX_VALUE;

        double bps = Scaffold.INSTANCE.bps.getValue().doubleValue();
        // Scaffold 的 bps 上限 10.0 相当于不限速（与 Scaffold 的 onStrafe 内部逻辑一致）
        if (bps >= 10.0) return Double.MAX_VALUE;
        return bps * 0.05;
    }
}