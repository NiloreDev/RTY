package client.nilore.modules.impl.combat.antikb;

import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.Velocity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public final class EpsilonCancelVelocityMode extends AntiKBMode {

    private int motionCancelCount = 0;
    private int explosionCancelCount = 0;

    public EpsilonCancelVelocityMode() {
        super("EpsilonCancel");
    }

    @Override
    public void onEnable() {
        motionCancelCount = 0;
        explosionCancelCount = 0;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public String getName() {
        return "Epsilon Cancel";
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null || Velocity.INSTANCE == null) return;

        Packet<ClientGamePacketListener> packet = event.getPacket();

        if (Velocity.INSTANCE.epsilonCancelMotion.getValue()
                && packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.getId() == mc.player.getId()) {
            event.setCancelled(true);
            motionCancelCount++;

            if (Velocity.INSTANCE.chatEpsilonCancel.getValue()) {
                double x = motion.getXa() / 8000.0D;
                double y = motion.getYa() / 8000.0D;
                double z = motion.getZa() / 8000.0D;
                Velocity.INSTANCE.notifyChat(
                        "§b[Velocity/EpsilonCancel] §fMotion §7cancelled §f#" + motionCancelCount
                                + String.format(" §7(x=%.3f y=%.3f z=%.3f)", x, y, z));
            }
            return;
        }

        if (Velocity.INSTANCE.epsilonCancelExplosion.getValue()
                && packet instanceof ClientboundExplodePacket) {
            event.setCancelled(true);
            explosionCancelCount++;

            if (Velocity.INSTANCE.chatEpsilonCancel.getValue()) {
                Velocity.INSTANCE.notifyChat(
                        "§b[Velocity/EpsilonCancel] §fExplosion §7cancelled §f#" + explosionCancelCount);
            }
        }
    }

    @Override public void onDisconnect(DisconnectEvent event) { }

    @Override
    public void reset() {

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
    @Override public void onTick(TickEvent event) { }
    @Override public void onStrafe(StrafeEvent event) { }
    @Override public void onMotion(MotionEvent event) { }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }

    public void onRender3D(Render3DEvent event) { }
}