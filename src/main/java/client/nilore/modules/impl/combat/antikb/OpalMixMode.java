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

public class OpalMixMode extends AntiKBMode {

    private final Velocity parent;

    private LivingEntity reduceTarget;
    private int reduceTicks;
    private int reduceAttackDelay;
    private int reduceHitCount;

    private boolean shouldJump;
    private int jumpCooldown;

    private final Queue<Packet<?>> packetQueue = new LinkedList<>();

    public OpalMixMode(Velocity parent) {
        super("OpalMix");
        this.parent = parent;
    }

    @Override
    public String getName() {
        return "OpalMix";
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
        shouldJump = false;
        jumpCooldown = 0;
        packetQueue.clear();
    }

    private LivingEntity getTarget() {
        if (mc.hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le) {
            return le;
        }
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

    /** 粉色主题 §d。 */
    private void debug(String msg) {
        if (parent.v2MixDebug.getValue() && mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§d[Velocity] §5[OpalMix] §r" + msg), false);
        }
    }

    @Override
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null) return;

        if (reduceAttackDelay > 0) reduceAttackDelay--;
        if (jumpCooldown > 0) jumpCooldown--;

        if (reduceTicks > 0 && reduceTarget != null) {
            if (mc.player.distanceTo(reduceTarget) > 3.0) {
                debug("§7Target out of range, cancelled");
                reduceTarget = null;
                reduceTicks = 0;
            } else if (parent.v2AutoAttack.getValue()) {
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
                debug("§cReduce-hit §f" + reduceTarget.getName().getString()
                        + " §7#§f" + reduceHitCount);
                reduceTarget = null;
                reduceTicks--;
            }
        }

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

    @Override
    public void onStrafe(StrafeEvent event) {
        if (reduceTicks > 0 && reduceTarget != null) {
            try { event.setForward(1.0f); } catch (Throwable ignored) {}
            return;
        }

        if (shouldJump) {
            if (mc.options != null) mc.options.keyJump.setDown(true);
            shouldJump = false;
            debug("§aJump reset §7(no target)");
        }
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null) return;
        Packet<?> p = event.getPacket();
        if (!(p instanceof ClientboundSetEntityMotionPacket motion)) return;
        if (motion.getId() != mc.player.getId()) return;

        boolean horizontal = (motion.getXa() != 0 || motion.getZa() != 0);
        if (!horizontal) return;

        LivingEntity target = getTarget();

        if (target != null) {
            reduceTarget = target;
            reduceTicks = parent.v2ReduceTick.getValue().intValue();
            debug("§cReduce path §7on §f" + target.getName().getString()
                    + " §7ticks=§f" + reduceTicks);
        } else {
            if (jumpCooldown == 0) {
                shouldJump = true;
                jumpCooldown = 3;
                debug("§bJump path §7(no target)");
            }
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
    @Override public void onTick(TickEvent event) { /* 占位 */ }

    public boolean isActive() { return reduceTicks > 0 || shouldJump; }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }
}