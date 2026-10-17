package client.nilore.modules.impl.combat.antikb;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.combat.Velocity;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.utils.rotation.Rotation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

public final class EpsilonReduceVelocityMode extends AntiKBMode {
    private final Object packetLock = new Object();
    private final Queue<Packet<ClientGamePacketListener>> packets = new ArrayDeque<>();

    private long lag;
    private long startDelay;
    private int attackQueue;
    private int sprintQueue;
    private float yaw;
    private boolean delay;
    private boolean jumpAfterFlush;

    private int knockbackDetected = 0;
    private int flushCount = 0;

    public EpsilonReduceVelocityMode() {
        super("EpsilonReduce");
    }

    @Override
    public boolean isActive() {
        synchronized (packetLock) {
            return delay || attackQueue > 0;
        }
    }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }

    @Override
    public void onEnable() {
        resetState(false);
        knockbackDetected = 0;
        flushCount = 0;
    }

    @Override
    public void onDisable() {
        flush(false);
        resetState(true);
    }

    @Override
    public String getName() {
        synchronized (packetLock) {
            return delay ? "Epsilon Reduce " + (System.currentTimeMillis() - startDelay) + "ms" : "Epsilon Reduce";
        }
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null || Velocity.INSTANCE == null) return;

        synchronized (packetLock) {
            Packet<ClientGamePacketListener> packet = event.getPacket();

            if (packet instanceof ClientboundDisconnectPacket) {
                resetState(false);
                return;
            }

            if (delay && (packet instanceof ClientboundSetEntityMotionPacket
                    || packet instanceof ClientboundMoveEntityPacket
                    || packet instanceof ClientboundTeleportEntityPacket
                    || packet instanceof ClientboundPingPacket
                    || packet instanceof ClientboundPlayerLookAtPacket
                    || packet instanceof ClientboundPlayerPositionPacket)) {
                event.setCancelled(true);
                packets.offer(packet);
            }

            if (packet instanceof ClientboundPlayerPositionPacket
                    || packet instanceof ClientboundExplodePacket) {
                lag = System.currentTimeMillis();
            }

            if (packet instanceof ClientboundSetEntityMotionPacket motion
                    && motion.getId() == mc.player.getId()) {
                if (System.currentTimeMillis() - lag >= 100L) {
                    boolean knockback = motion.getYa() > 0 && (motion.getXa() != 0 || motion.getZa() != 0);
                    if (knockback && !delay) {
                        delay = true;
                        event.setCancelled(true);
                        if (!packets.contains(packet)) packets.offer(packet);
                        startDelay = System.currentTimeMillis();
                        knockbackDetected++;

                        if (Velocity.INSTANCE.chatEpsilonReduce.getValue()) {
                            double x = motion.getXa() / 8000.0D;
                            double y = motion.getYa() / 8000.0D;
                            double z = motion.getZa() / 8000.0D;
                            Velocity.INSTANCE.notifyChat(
                                    "§b[Velocity/EpsilonReduce] §eDelay §7started §f#" + knockbackDetected
                                            + String.format(" §7(x=%.3f y=%.3f z=%.3f)", x, y, z));
                        }
                    }
                }

                double x = motion.getXa() / 8000.0D;
                double z = motion.getZa() / 8000.0D;
                yaw = Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(z, x)) + 90.0D));
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || Velocity.INSTANCE == null) return;

        boolean shouldFlush;
        synchronized (packetLock) {
            shouldFlush = delay && (mc.player.onGround()
                    || System.currentTimeMillis() - lag < 100L
                    || System.currentTimeMillis() - startDelay >= Velocity.INSTANCE.epsilonMaxDelay.getValue().longValue());
        }

        if (shouldFlush && flush(true) && System.currentTimeMillis() - lag >= 100L) {
            synchronized (packetLock) {
                attackQueue = Velocity.INSTANCE.epsilonAttackCounts.getValue().intValue();
                sprintQueue = Velocity.INSTANCE.epsilonSprintTicks.getValue().intValue();

                if (Velocity.INSTANCE.chatEpsilonReduce.getValue()) {
                    Velocity.INSTANCE.notifyChat(
                            "§b[Velocity/EpsilonReduce] §aFlush §7#" + flushCount
                                    + " §7-> attackQueue=§f" + attackQueue
                                    + " §7sprintQueue=§f" + sprintQueue);
                }
            }
        }

        if (jumpAfterFlush && mc.player.onGround()) {
            if (System.currentTimeMillis() - lag >= 100L) {
                mc.options.keyJump.setDown(true);
            }
            jumpAfterFlush = false;
        }
    }

    @Override
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null) return;

        synchronized (packetLock) {
            if (delay && mc.player.fallDistance == 0.0F && System.currentTimeMillis() - lag >= 100L) {
                event.setForward(1.0F);
                event.setStrafe(0.0F);
            }

            if (sprintQueue > 0 && System.currentTimeMillis() - lag >= 100L) {
                event.setForward(1.0F);
                event.setStrafe(0.0F);
                sprintQueue--;
            }
        }
    }

    @Override
    public void onSprint(SprintEvent event) {
        synchronized (packetLock) {
            if (sprintQueue > 0) {
                event.setSprint(true);
            }
        }
    }

    @Override
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || Velocity.INSTANCE == null) return;

        int attacks;
        synchronized (packetLock) {
            attacks = attackQueue;
            if (attackQueue > 0) attackQueue--;
        }

        if (attacks > 0
                && KillAura.INSTANCE != null
                && KillAura.INSTANCE.isEnabled()
                && KillAura.target != null) {
            KillAura.INSTANCE.doAttack();
            if (Velocity.INSTANCE.epsilonSwingHand.getValue()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }

            if (Velocity.INSTANCE.chatEpsilonReduce.getValue()) {
                Velocity.INSTANCE.notifyChat(
                        "§b[Velocity/EpsilonReduce] §cAttack §7executed, remaining=§f" + attackQueue);
            }
        }

        synchronized (packetLock) {
            if (sprintQueue > 0
                    && (KillAura.target == null)
                    && (Scaffold.INSTANCE == null || !Scaffold.INSTANCE.isEnabled())) {
                Velocity.rotation = new Rotation(yaw, mc.player.getXRot());
            } else if (!delay) {
                Velocity.rotation = null;
            }
        }
    }

    @Override
    public void onAttack(EntityRemoveEvent event) {

    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
        resetState(false);
    }

    @Override
    public void reset() {

    }

    private boolean flush(boolean armJump) {
        List<Packet<ClientGamePacketListener>> pending;
        synchronized (packetLock) {
            if (!delay) return false;
            delay = false;
            pending = drain(packets);
            flushCount++;
        }

        handlePackets(pending);
        if (armJump) jumpAfterFlush = true;
        return true;
    }

    private void handlePackets(List<Packet<ClientGamePacketListener>> packets) {
        ClientGamePacketListener listener = mc.getConnection();
        if (listener == null) return;
        for (Packet<ClientGamePacketListener> packet : packets) {
            try {
                packet.handle(listener);
            } catch (Exception ignored) {
            }
        }
    }

    private static <T> List<T> drain(Queue<T> queue) {
        List<T> result = new ArrayList<>(queue.size());
        T value;
        while ((value = queue.poll()) != null) result.add(value);
        return result;
    }

    private void resetState(boolean clearRotation) {
        synchronized (packetLock) {
            delay = false;
            packets.clear();
            lag = 0L;
            startDelay = 0L;
            attackQueue = 0;
            sprintQueue = 0;
            yaw = 0.0F;
            jumpAfterFlush = false;
        }
        if (clearRotation) Velocity.rotation = null;
    }

    @Override public void onRotation(RotationEvent event) {
        if (Velocity.rotation != null) {
            event.setYaw(Velocity.rotation.getYaw());
            event.setPitch(Velocity.rotation.getPitch());
        }
    }
    @Override public void onPreMotion(PreMotionEvent event) { }

    @Override
    public void onEntityHurt(EntityHurtEvent event) {

    }

    @Override public void onMotion(MotionEvent event) { }

    public void onRender3D(Render3DEvent event) { }
}