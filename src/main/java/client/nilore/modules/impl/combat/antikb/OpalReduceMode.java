package client.nilore.modules.impl.combat.antikb;

import java.util.LinkedList;
import java.util.Queue;

import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.Velocity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class OpalReduceMode extends AntiKBMode {

    private final Velocity parent;

    private LivingEntity reduceTarget;
    private int reduceTicks;
    private int reduceAttackDelay;
    private int reduceHitCount;

    // =========================================================
    // Naven 风格 forward state
    // =========================================================
    /** 是否处于"被击退、要向前冲"的状态 */
    private boolean forwardState;
    /** 前向状态的截止时间戳（到点或玩家按前进键就停止） */
    private long forwardResetAt = -1L;
    /** Follow Direction 的目标 yaw */
    private float followYaw = 0f;

    /** forwardState 最长持续时间上限（毫秒），避免任何情况下"一直冲" */
    private static final long MAX_FORWARD_DURATION_MS = 500L;

    private final Queue<Packet<?>> packetQueue = new LinkedList<>();

    public OpalReduceMode(Velocity parent) {
        super("OpalReduce");
        this.parent = parent;
    }

    @Override
    public String getName() {
        return "OpalReduce";
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { reset(); }

    @Override
    public void reset() {
        reduceTarget = null;
        reduceTicks = 0;
        reduceAttackDelay = 0;
        reduceHitCount = 0;

        forwardState = false;
        forwardResetAt = -1L;
        followYaw = 0f;

        packetQueue.clear();
    }

    private LivingEntity getTarget() {
        if (mc.hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le) {
            return le;
        }
        try {
            LivingEntity kaTarget = KillAuraRef.getTarget();
            if (kaTarget != null) return kaTarget;
        } catch (Throwable ignored) {}
        return null;
    }

    /** 红色主题 §c。 */
    private void debug(String msg) {
        if (parent.v2Debug.getValue() && mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§c[Velocity] §4[OpalReduce] §r" + msg), false);
        }
    }

    // =========================================================
    // Yaw 计算
    // =========================================================

    /**
     * Follow Direction 的 yaw：朝向目标（攻击者）。
     * 与 vanilla yaw 定义一致（-Z 为 0°，顺时针增长）。
     */
    private float computeFollowYaw(LivingEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Naven 风格的击退 yaw：根据击退向量反推朝向攻击者的方向。
     * 只在没有目标时使用。
     */
    private float computeVelocityYaw(ClientboundSetEntityMotionPacket motion) {
        return (float) Math.toDegrees(Math.atan2(
                motion.getXa() / 8000.0D,
                -(motion.getZa() / 8000.0D)));
    }

    // =========================================================
    // Naven 风格：forward state 生命周期
    // =========================================================

    /**
     * 激活前冲状态。
     * @param durationMs 持续时间（毫秒），会被截断到 MAX_FORWARD_DURATION_MS 以内
     */
    private void enableForwardState(long durationMs) {
        long clamped = Math.min(Math.max(1L, durationMs), MAX_FORWARD_DURATION_MS);
        forwardState = true;
        forwardResetAt = System.currentTimeMillis() + clamped;
    }

    /**
     * 结束前冲状态并清掉所有相关数据。
     * 注意：这里必须清 forwardState，否则 onStrafe 会一直前进。
     */
    private void disableForwardState() {
        forwardState = false;
        forwardResetAt = -1L;
        followYaw = 0f;
    }

    /**
     * Naven 的 updateForwardResetTimer：
     *   - 时间到 → 结束
     *   - 玩家自己按前进键 → 结束
     *   - 兜底：如果 forwardState 为 true 但没有计时器，强制结束
     * 由 GameTick / Tick 每 tick 调用一次。
     */
    private void updateForwardResetTimer() {
        if (!forwardState) return;

        // 兜底：状态开着但没计时器，直接结束（防止任何残留）
        if (forwardResetAt == -1L) {
            disableForwardState();
            return;
        }

        boolean timeReached = System.currentTimeMillis() >= forwardResetAt;
        boolean forwardKeyDown = mc.options != null && mc.options.keyUp.isDown();

        if (timeReached || forwardKeyDown) {
            debug("§7Forward stopped §7(time=" + timeReached
                    + ", key=" + forwardKeyDown + ")");
            disableForwardState();
        }
    }

    // =========================================================
    // Strafe
    // =========================================================

    @Override
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null) return;
        if (!forwardState) return;

        // 强制检查一下时间戳，避免 Strafe 早于 GameTick 时还继续冲
        if (forwardResetAt != -1L && System.currentTimeMillis() >= forwardResetAt) {
            disableForwardState();
            return;
        }

        // ---------- Follow Direction：旋转朝向目标 ----------
        if (parent.v2FollowDirection != null
                && parent.v2FollowDirection.getValue()
                && reduceTarget != null) {
            followYaw = computeFollowYaw(reduceTarget);
            try {
                mc.player.setYRot(followYaw);
                mc.player.yBodyRot = followYaw;
                mc.player.yHeadRot = followYaw;
            } catch (Throwable ignored) {}
        }

        // ---------- 自动前冲（Naven 风格） ----------
        try {
            event.setForward(1.0f);
        } catch (Throwable ignored) {}
    }

    // =========================================================
    // GameTick
    // =========================================================

    @Override
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null) return;

        // 1) 更新 forward 计时器（Naven 风格，到点就停）
        updateForwardResetTimer();

        // 2) 攻击延迟
        if (reduceAttackDelay > 0) reduceAttackDelay--;

        // 3) 无条件递减 reduceTicks（关键修复：不再依赖是否攻击成功）
        if (reduceTicks > 0) {
            reduceTicks--;
        }

        // 4) reduce 逻辑：有目标时按需攻击
        if (reduceTicks > 0 && reduceTarget != null) {
            if (mc.player.distanceTo(reduceTarget) > 3.0) {
                debug("§7Target out of range, cancelled");
                reduceTarget = null;
            } else if (parent.v2AutoAttack.getValue()
                    && reduceAttackDelay <= 0) {

                boolean wasSprinting = mc.player.isSprinting();
                if (wasSprinting) mc.player.setSprinting(false);

                packetQueue.offer(ServerboundInteractPacket.createAttackPacket(
                        reduceTarget, mc.player.isShiftKeyDown()));
                packetQueue.offer(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

                if (wasSprinting) {
                    Vec3 m = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(m.x * 0.6, m.y, m.z * 0.6);
                    reduceAttackDelay = 2;
                }

                reduceHitCount++;
                debug("§4Attacked §f" + reduceTarget.getName().getString()
                        + " §7#§f" + reduceHitCount
                        + (forwardState
                        ? " §7(followYaw=§f" + String.format("%.1f", followYaw) + "§7)"
                        : ""));
                reduceTarget = null;
            }
        }

        // 5) reduceTicks 归零：确保 forward 也结束
        if (reduceTicks <= 0) {
            if (forwardState) {
                disableForwardState();
            }
            if (reduceTarget != null) {
                reduceTarget = null;
            }
        }

        // 6) 发送队列中的 packet
        while (!packetQueue.isEmpty()) {
            Packet<?> p = packetQueue.poll();
            if (p != null && mc.getConnection() != null) {
                try { mc.getConnection().send(p); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onAttack(EntityRemoveEvent event) {

    }

    // =========================================================
    // ReceivePacket
    // =========================================================

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null) return;
        Packet<?> p = event.getPacket();
        if (!(p instanceof ClientboundSetEntityMotionPacket motion)) return;
        if (motion.getId() != mc.player.getId()) return;

        boolean horizontal = (motion.getXa() != 0 || motion.getZa() != 0);
        if (!horizontal) return;

        int ticks = Math.max(1, parent.v2ReduceTick.getValue().intValue());

        LivingEntity t = getTarget();
        if (t != null) {
            reduceTarget = t;
            reduceTicks = ticks;

            // Naven 风格：forward 时长 = reduceTicks × 50ms（1 tick = 50ms）
            enableForwardState(ticks * 50L);

            // 预计算 Follow Direction yaw
            followYaw = computeFollowYaw(t);

            debug("§eTriggered §7on §f" + t.getName().getString()
                    + " §7ticks=§f" + reduceTicks
                    + " §7duration=§f" + (ticks * 50L) + "ms"
                    + (parent.v2FollowDirection != null
                    && parent.v2FollowDirection.getValue()
                    ? " §7follow=§f" + String.format("%.1f", followYaw)
                    : ""));
        } else {
            // 没有目标：Naven 风格回退到 velocityYaw
            float velocityYaw = computeVelocityYaw(motion);
            debug("§7No target §7(velocityYaw=§f"
                    + String.format("%.1f", velocityYaw) + "§7)");
        }
    }

    // =========================================================
    // Tick / 其他
    // =========================================================

    @Override
    public void onTick(TickEvent event) {
        // 双保险：如果 forwardState 残留但 reduceTicks 已归零，直接清
        if (forwardState && reduceTicks <= 0) {
            disableForwardState();
        }
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
        reset();
    }

    @Override public void onRotation(RotationEvent event) { /* 占位 */ }
    @Override public void onSprint(SprintEvent event) { /* 占位 */ }
    @Override public void onPreMotion(PreMotionEvent event) { /* 占位 */ }

    @Override
    public void onEntityHurt(EntityHurtEvent event) {

    }

    @Override public void onMotion(MotionEvent event) { /* 占位 */ }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }

    private static final class KillAuraRef {
        static LivingEntity getTarget() {
            try {
                Class<?> ka = Class.forName("client.nilore.modules.impl.combat.KillAura");
                Object inst = ka.getField("INSTANCE").get(null);
                if (inst == null) return null;
                Object t = ka.getField("target").get(inst);
                return t instanceof LivingEntity le ? le : null;
            } catch (Throwable e) {
                return null;
            }
        }
    }
}