package client.nilore.modules.impl.combat.antikb;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.Velocity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public final class EpsilonDelayVelocityMode extends AntiKBMode {
    private final Object packetLock = new Object();
    private final Queue<Packet<ClientGamePacketListener>> delayedPackets = new ArrayDeque<>();

    private long lag;
    private int delayTicksRemaining;
    private boolean jumpKeyForced;

    private int triggerCount = 0;
    private int flushCount = 0;
    private int totalTicksDelayed = 0;

    public EpsilonDelayVelocityMode() {
        super("EpsilonDelay");
    }

    @Override
    public boolean isActive() {
        synchronized (packetLock) {
            return delayTicksRemaining > 0;
        }
    }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }

    @Override
    public void onEnable() {
        resetState(false);
        triggerCount = 0;
        flushCount = 0;
        totalTicksDelayed = 0;
    }

    @Override
    public void onDisable() {
        flushPackets();
        resetState(true);
    }

    @Override
    public String getName() {
        synchronized (packetLock) {
            return delayTicksRemaining > 0 ? "Epsilon Delay " + delayTicksRemaining + "t" : "Epsilon Delay";
        }
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null || Velocity.INSTANCE == null) return;

        synchronized (packetLock) {
            Packet<ClientGamePacketListener> packet = event.getPacket();

            if (packet instanceof ClientboundDisconnectPacket) {
                delayedPackets.clear();
                delayTicksRemaining = 0;
                lag = 0L;
                return;
            }

            if (packet instanceof ClientboundPlayerPositionPacket
                    || packet instanceof ClientboundExplodePacket) {
                lag = System.currentTimeMillis();
            }

            if (delayTicksRemaining > 0) {
                event.setCancelled(true);
                delayedPackets.offer(packet);
                return;
            }

            if (packet instanceof ClientboundSetEntityMotionPacket motion
                    && motion.getId() == mc.player.getId()
                    && System.currentTimeMillis() - lag >= 100L) {
                boolean knockback = motion.getYa() > 0 && (motion.getXa() != 0 || motion.getZa() != 0);
                if (knockback) {
                    delayTicksRemaining = Velocity.INSTANCE.epsilonDelayTicks.getValue().intValue();
                    totalTicksDelayed += delayTicksRemaining;
                    triggerCount++;

                    if (Velocity.INSTANCE.chatEpsilonDelay.getValue()) {
                        double x = motion.getXa() / 8000.0D;
                        double y = motion.getYa() / 8000.0D;
                        double z = motion.getZa() / 8000.0D;
                        Velocity.INSTANCE.notifyChat(
                                "§b[Velocity/EpsilonDelay] §eDelay §7#" + triggerCount
                                        + " §7-> §f" + delayTicksRemaining + "t"
                                        + String.format(" §7(x=%.3f y=%.3f z=%.3f)", x, y, z));
                    }
                }
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || Velocity.INSTANCE == null) return;

        List<Packet<ClientGamePacketListener>> pending = List.of();
        synchronized (packetLock) {
            if (delayTicksRemaining > 0 && --delayTicksRemaining == 0) {
                pending = drain(delayedPackets);
                flushCount++;

                if (Velocity.INSTANCE.chatEpsilonDelay.getValue()) {
                    Velocity.INSTANCE.notifyChat(
                            "§b[Velocity/EpsilonDelay] §aFlush §7#" + flushCount
                                    + " §7-> released §f" + pending.size() + " §7packets");
                }
            }
        }
        handlePackets(pending);

        if (Velocity.INSTANCE.epsilonJumpReset.getValue()
                && mc.player.onGround()
                && mc.player.hurtTime == 9) {
            mc.options.keyJump.setDown(true);
            jumpKeyForced = true;

            if (Velocity.INSTANCE.chatEpsilonDelay.getValue()) {
                Velocity.INSTANCE.notifyChat("§b[Velocity/EpsilonDelay] §aJump Reset §7triggered");
            }
        } else if (jumpKeyForced && (mc.player.hurtTime != 9 || !mc.player.onGround())) {
            restoreJumpKey();
        }
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
        resetState(false);
    }

    @Override
    public void reset() {

    }

    private void flushPackets() {
        List<Packet<ClientGamePacketListener>> pending;
        synchronized (packetLock) {
            delayTicksRemaining = 0;
            pending = drain(delayedPackets);
        }
        handlePackets(pending);
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

    private void resetState(boolean restoreJump) {
        synchronized (packetLock) {
            delayTicksRemaining = 0;
            delayedPackets.clear();
            lag = 0L;
        }
        if (restoreJump) restoreJumpKey();
    }

    private void restoreJumpKey() {
        if (mc.getWindow() != null) {
            boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
            mc.options.keyJump.setDown(down);
        }
        jumpKeyForced = false;
    }

    @Override public void onRotation(RotationEvent event) { }
    @Override public void onPreMotion(PreMotionEvent event) { }

    @Override
    public void onEntityHurt(EntityHurtEvent event) {

    }

    @Override public void onGameTick(GameTickEvent event) { }

    @Override
    public void onAttack(EntityRemoveEvent event) {

    }

    @Override public void onSprint(SprintEvent event) { }
    @Override public void onStrafe(StrafeEvent event) { }
    @Override public void onMotion(MotionEvent event) { }

    public void onRender3D(Render3DEvent event) { }
}