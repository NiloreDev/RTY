package client.nilore.modules.impl.combat.antikb;

import java.util.concurrent.LinkedBlockingDeque;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.event.impl.EntityHurtEvent;
import client.nilore.event.impl.EntityRemoveEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RotationEvent;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.impl.combat.Velocity;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.movement.NoSlow;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.modules.impl.movement.Stuck;
import client.nilore.utils.game.FightManager;
import client.nilore.utils.game.RotationUtil;

/**
 * ReduceXZ
 *
 * 两套效果，由 Velocity 面板的 Delay 开关切换：
 *
 *   Delay 开启（延迟生效）：
 *     拦截自身击退包 → 进入延迟窗口，满足 看向目标 + 疾跑 + 在 Buffer Range 内
 *     （Release Mode = Sprint 时不要求落地）→ 释放最后一条击退包并开始计数；
 *     计数阶段对着目标连打 Attack Counter 下（x0.6 自减速）。
 *
 *   Delay 关闭（纯计数）：
 *     收到自身击退包 → 直接开始计数，不收包、不取消、不延迟，地面/空中都生效。
 *
 * 两种模式都有一道 Reach(1~3) 距离上限，超出绝不发包，避免 Grim Reach flag。
 *
 * Jump Reset：
 *     计数开始时强制按一次跳跃键，用于重置击退方向 / 减少被推距离。
 *     Delay ON 与 Delay OFF 两条路径都会触发。
 *     由 Velocity 面板的 "Jump Reset" 开关控制。
 *
 * Debug 消息：
 *     只有 Jump Reset 开启（生效）时才输出计数消息；
 *     关闭（未生效）时完全不输出。
 *     开启时显示当前是 [地面]（绿色）还是 [空中]（红色），并附带 Count 计数。
 *
 * Need KillAura：
 *     开启时，只有 KillAura 处于启用状态才生效；
 *     关闭时，任何时候都生效，不受 KillAura 开关影响。
 *
 * 读取的开关：Delay / Max Delay Ticks / Buffer Range / Release Mode / SideStrafe /
 *            Attack Counter / Attack Range / Reach / Jump Reset / Need KillAura。
 */
public class ReduceXZMode
        extends AntiKBMode {
    public static ReduceXZMode INSTANCE;
    public static boolean handlingVelocity;
    public static boolean velocityHandled;

    private final LinkedBlockingDeque<Packet<ClientGamePacketListener>> packetQueue = new LinkedBlockingDeque<>();

    public boolean delaying;
    private boolean velocityPending;
    private boolean positionCorrection;
    private int delayTicks;
    private int velocityPendingTicks;
    private int attacksRemaining;
    private Entity target;
    private float jumpResetYaw;
    private float jumpResetDifference;
    private boolean forceForward;

    /** Jump Reset：剩余需要强制按跳跃键的 tick 数 */
    private int jumpTicks;
    /** Jump Reset：标记当前是否由我们强制按下了跳跃键 */
    private boolean jumpKeyForced;

    @Override
    public boolean isActive() {
        return this.delaying || this.attacksRemaining > 0;
    }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {
    }

    public static boolean isCountering() {
        return INSTANCE != null && INSTANCE.attacksRemaining > 0;
    }

    public static boolean isInAttackWindow() {
        return isCountering();
    }

    public static boolean isInDelayWindow() {
        return INSTANCE != null && INSTANCE.delaying;
    }

    public ReduceXZMode() {
        super("ReduceXZ");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.resetGrimState();
    }

    @Override
    public void onDisable() {
        this.releaseQueue();
        this.clearGrimState();
    }

    @Override
    public String getName() {
        return "ReduceXZ";
    }

    @Override
    public void onRotation(RotationEvent rotationEvent) {
    }

    @Override
    public void onMotion(MotionEvent motionEvent) {
    }

    // =================================================================
    // === Jump Reset ==================================================
    // =================================================================
    @Override
    public void onGameTick(GameTickEvent gameTickEvent) {
        if (mc.player == null) {
            return;
        }
        if (!Velocity.INSTANCE.jumpReset.getValue() || this.jumpTicks <= 0) {
            this.restoreJumpKey();
            return;
        }
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            return;
        }
        if (mc.player.isOnFire()) {
            this.jumpTicks = 0;
            this.restoreJumpKey();
            return;
        }
        this.jumpKeyForced = true;
        mc.options.keyJump.setDown(true);
        --this.jumpTicks;
    }

    @Override
    public void onPreMotion(PreMotionEvent preMotionEvent) {
    }

    @Override
    public void onSprint(SprintEvent sprintEvent) {
    }

    @Override
    public void onAttack(EntityRemoveEvent entityRemoveEvent) {
    }

    @Override
    public void onEntityHurt(EntityHurtEvent event) {
    }

    @Override
    public void onRender2D(Render2DEvent render2DEvent) {
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent receivePacketEvent) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        Packet<ClientGamePacketListener> packet = receivePacketEvent.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket) {
            this.positionCorrection = true;
            return;
        }

        if (this.shouldAbort()) {
            return;
        }

        boolean delayEnabled = Velocity.INSTANCE.isNoXZAltDelayEnable.getValue();

        if (packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.getId() == mc.player.getId()) {
            if (!delayEnabled) {
                // 延迟关闭：纯计数，不收包/不取消/不延迟
                if (this.attacksRemaining <= 0) {
                    this.attacksRemaining = Velocity.INSTANCE.isNoXZAltAttackAmount.getValue().intValue();

                    // 【新增】Delay OFF 时也触发 Jump Reset
                    if (Velocity.INSTANCE.jumpReset.getValue()) {
                        this.jumpTicks = 1;
                    }

                    this.debugCounter("Attack Count (Delay OFF)");
                }
                return;
            }
            // 延迟开启：更新 jump reset 数据并打开延迟窗口，拦截该包
            this.updateJumpReset(motion);
            if (!this.delaying) {
                this.delaying = true;
                this.delayTicks = 0;
                handlingVelocity = true;
            }
            receivePacketEvent.setCancelled(true);
            this.packetQueue.add(packet);
            return;
        }

        if (!delayEnabled || !this.delaying) {
            return;
        }
        if (this.isImmediatePacket(packet)) {
            return;
        }
        // 实体位置同步永不拦截，否则本地命中箱过期会引发 Grim Hitboxes / Reach
        if (packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket) {
            return;
        }
        receivePacketEvent.setCancelled(true);
        this.packetQueue.add(packet);
    }

    @Override
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        this.releaseQueue();
        this.clearGrimState();
    }

    @Override
    public void reset() {
    }

    @Override
    public void onStrafe(StrafeEvent strafeEvent) {
        if (mc.player == null) {
            return;
        }

        if (this.forceForward) {
            strafeEvent.setForward(1.0f);
            strafeEvent.setStrafe(0.0f);
            if (!mc.player.isSprinting()) {
                mc.player.setSprinting(true);
            }
        }

        if (this.velocityPending && mc.player.isSprinting()) {
            strafeEvent.setSprinting(true);

            if (Math.abs(this.jumpResetDifference) <= 45.0f
                    && Velocity.INSTANCE.isNoXZAltSideStrafe.getValue() && this.velocityPendingTicks < 4) {
                float[] movement = this.jumpResetMovement();
                if (movement[0] != 0.0f) {
                    strafeEvent.setForward(movement[0]);
                }
                if (movement[1] != 0.0f) {
                    strafeEvent.setStrafe(movement[1]);
                }
            } else {
                this.velocityPending = false;
            }
        }
    }

    @Override
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        this.target = KillAura.target;

        if (this.velocityPending) {
            ++this.velocityPendingTicks;
            if (this.velocityPendingTicks >= 4) {
                this.velocityPending = false;
                this.velocityPendingTicks = 0;
            }
        }

        if (mc.player.isDeadOrDying() || this.shouldAbort()) {
            this.resetGrimState();
            return;
        }

        boolean delayEnabled = Velocity.INSTANCE.isNoXZAltDelayEnable.getValue();

        // Delay 被关闭时，立即释放被拦截的包并取消延迟状态
        if (this.delaying && !delayEnabled) {
            this.delaying = false;
            this.forceForward = false;
            handlingVelocity = false;
            this.releaseQueue();
        }

        if (delayEnabled && this.delaying) {
            this.forceForward = true;
            this.attacksRemaining = 0;
            ++this.delayTicks;
            // 超过 Max Delay Ticks 强制释放所有排队包并清空状态
            if (this.delayTicks >= Velocity.INSTANCE.isNoXZAltMaxDelayTicks.getValue().intValue()) {
                this.resetGrimState();
                return;
            }
            boolean insideBufferRange = this.target != null
                    && this.target.distanceTo(mc.player) <= Velocity.INSTANCE.isNoXZAltBufferRange.getValue().doubleValue();
            if (this.isLookingAtTarget() && insideBufferRange && mc.player.isSprinting()
                    && (mc.player.onGround() || Velocity.INSTANCE.isNoXZAltReduceMode.is("Sprint"))) {
                this.attacksRemaining = Velocity.INSTANCE.isNoXZAltAttackAmount.getValue().intValue();
                this.delaying = false;
                handlingVelocity = false;
                this.releaseLastMotionOnly();
                this.velocityPending = true;
                this.velocityPendingTicks = 0;

                // Jump Reset：计数开始时强制按一次跳跃键
                if (Velocity.INSTANCE.jumpReset.getValue()) {
                    this.jumpTicks = 1;
                }

                this.debugCounter("Attack Count (Delay ON)");
            }
        }

        if (this.attacksRemaining > 0) {
            // Need KillAura 关闭时若没有 KillAura 目标，也允许计数走完，避免状态卡死
            if (this.target == null || !this.target.isAlive()) {
                --this.attacksRemaining;
                if (this.attacksRemaining == 0) {
                    this.forceForward = false;
                }
                return;
            }
            if (!this.isLookingAtTarget() || !mc.player.isSprinting() || !this.isTargetInReach()) {
                return;
            }
            this.performReduceAttack(this.target);
            --this.attacksRemaining;
            if (this.attacksRemaining == 0) {
                this.forceForward = false;
            }
        }
    }

    private void performReduceAttack(Entity entity) {
        if (entity == null || mc.player == null || mc.getConnection() == null) {
            return;
        }
        // 兜底：绝不在 Reach 之外发包，避免 Grim Reach flag
        if (!this.isTargetInReach()) {
            return;
        }
        if (!FightManager.attackAndLock()) {
            return;
        }
        FightManager.attackByPacket(entity, false);
        mc.player.swing(InteractionHand.MAIN_HAND);
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
        mc.player.setSprinting(false);
    }

    private boolean isLookingAtTarget() {
        if (this.target == null) {
            return false;
        }
        return RotationUtil.isLookingAt(this.target,
                Velocity.INSTANCE.isNoXZAltAttackRange.getValue().doubleValue(), 0.1f);
    }

    /**
     * 是否在攻击距离内。
     * 用与 Grim 相同的口径：眼睛位置 → 目标碰撞箱最近点 的距离 <= Reach(面板可调，1~3)，
     * 超过就绝不攻击，避免 "failed Reach" flag。
     */
    private boolean isTargetInReach() {
        if (this.target == null || mc.player == null) {
            return false;
        }
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        double distance = RotationUtil.closestPoint(eyePos, this.target.getBoundingBox()).distanceTo(eyePos);
        return distance <= Velocity.INSTANCE.isNoXZAltReach.getValue().doubleValue();
    }

    private boolean shouldAbort() {
        if (mc.player == null || mc.getConnection() == null) {
            return true;
        }
        if (mc.player.isUsingItem() || mc.player.isInLava() || mc.player.isInWater()) {
            return true;
        }
        if (mc.level != null && mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB)) {
            return true;
        }
        if (this.positionCorrection) {
            return true;
        }
        if (mc.screen instanceof InventoryScreen) {
            return true;
        }
        // Need KillAura：开启时，只有 KillAura 启用才允许生效；关闭时不受限制
        if (Velocity.INSTANCE.isReduceModeNeedKillAura != null
                && Velocity.INSTANCE.isReduceModeNeedKillAura.getValue()
                && (KillAura.INSTANCE == null || !KillAura.INSTANCE.isEnabled())) {
            return true;
        }
        // 延迟窗口内必须有有效目标，否则释放队列并取消
        if (this.delaying && (this.target == null || !this.target.isAlive())) {
            return true;
        }
        if (mc.player.isDeadOrDying() || mc.player.isSpectator()) {
            return true;
        }
        if (NoSlow.isHandling()) {
            return true;
        }
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            return true;
        }
        Stuck stuck = Stuck.INSTANCE;
        return stuck != null && stuck.isEnabled();
    }

    private boolean isImmediatePacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundContainerClosePacket
                || packet instanceof ClientboundHurtAnimationPacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetPlayerTeamPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundAnimatePacket animation
                && animation.getId() != mc.player.getId();
    }

    private void releaseQueue() {
        if (mc.getConnection() == null) {
            this.packetQueue.clear();
            return;
        }
        Packet<ClientGamePacketListener> packet;
        while ((packet = this.packetQueue.poll()) != null) {
            try {
                packet.handle(mc.getConnection());
            } catch (Exception exception) {
                this.packetQueue.clear();
                break;
            }
        }
    }

    private void releaseLastMotionOnly() {
        if (mc.getConnection() == null) {
            this.packetQueue.clear();
            return;
        }
        ClientboundSetEntityMotionPacket lastMotion = null;
        for (Packet<ClientGamePacketListener> queued : this.packetQueue) {
            if (queued instanceof ClientboundSetEntityMotionPacket motion) {
                lastMotion = motion;
            }
        }
        Packet<ClientGamePacketListener> packet;
        while ((packet = this.packetQueue.poll()) != null) {
            if (packet instanceof ClientboundSetEntityMotionPacket && packet != lastMotion) {
                continue;
            }
            try {
                packet.handle(mc.getConnection());
            } catch (Exception exception) {
                this.packetQueue.clear();
                break;
            }
        }
    }

    private void resetGrimState() {
        this.releaseQueue();
        this.clearGrimState();
    }

    private void clearGrimState() {
        this.delaying = false;
        this.forceForward = false;
        this.velocityPending = false;
        this.positionCorrection = false;
        this.delayTicks = 0;
        this.velocityPendingTicks = 0;
        this.attacksRemaining = 0;
        this.target = null;
        handlingVelocity = false;
        velocityHandled = false;

        // Jump Reset 状态清理
        this.jumpTicks = 0;
        this.restoreJumpKey();
    }

    private void updateJumpReset(ClientboundSetEntityMotionPacket motion) {
        if (mc.player == null) {
            return;
        }
        double x = -motion.getXa();
        double z = -motion.getZa();
        if (Math.abs(x) < 1.0E-6 && Math.abs(z) < 1.0E-6) {
            this.jumpResetYaw = mc.player.getYRot();
            this.jumpResetDifference = 0.0f;
            return;
        }
        this.jumpResetYaw = (float) Math.toDegrees(Math.atan2(-x, z));
        this.jumpResetDifference = Mth.wrapDegrees(this.jumpResetYaw - mc.player.getYRot());
    }

    private float[] jumpResetMovement() {
        float deltaYaw = Mth.wrapDegrees(this.jumpResetYaw - mc.player.getYRot());
        if (this.jumpResetDifference > 22.5f) {
            deltaYaw -= 45.0f;
        } else if (this.jumpResetDifference < -22.5f) {
            deltaYaw += 45.0f;
        }
        double radians = Math.toRadians(deltaYaw);
        double x = Math.sin(radians);
        double z = Math.cos(radians);
        float forward = z > 0.707 ? 1.0f : z < -0.707 ? -1.0f : 0.0f;
        float strafe = x > 0.707 ? -1.0f : x < -0.707 ? 1.0f : 0.0f;
        return new float[]{forward, strafe};
    }

    /**
     * 恢复原跳跃键状态。
     * 若本地玩家当前没有物理按下跳跃键，就把 keyJump 重置为 false。
     * 若 Scaffold 开启则不操作（避免与 Scaffold 的跳跃控制冲突）。
     */
    private void restoreJumpKey() {
        if (!this.jumpKeyForced) {
            return;
        }
        this.jumpKeyForced = false;
        if (mc.player == null || (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled())) {
            return;
        }
        boolean down = InputConstants.isKeyDown(
                mc.getWindow().getWindow(),
                mc.options.keyJump.getKey().getValue());
        mc.options.keyJump.setDown(down);
    }

    // =================================================================
    // === Debug =======================================================
    // =================================================================

    /**
     * 计数激活时的汇总消息。
     *
     * 仅当 Jump Reset 开启（生效）时才输出；
     * 地面显示 [地面] (绿色)，空中显示 [空中] (红色)，后接 Count。
     */
    private void debugCounter(String prefix) {
        if (Velocity.INSTANCE == null || mc.player == null) return;

        // Jump Reset 未开启时，不输出任何消息
        if (!Velocity.INSTANCE.jumpReset.getValue()) return;

        String location = mc.player.onGround() ? "§a[地面] " : "§c[空中] ";
        String count = "§fCount: " + this.attacksRemaining + "/" + this.attacksRemaining;

        Velocity.INSTANCE.debugChat("§c[Velocity] §4[ReduceXZ] " + location + count);
    }
}