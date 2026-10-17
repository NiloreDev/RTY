package client.nilore.modules.impl.combat.antikb;

import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.Velocity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public class OpalGrimMode extends AntiKBMode {

    private final Velocity parent;

    private int suppressTicks;
    private int flagCooldownTicks;
    private boolean active;
    private int triggerCount;

    public OpalGrimMode(Velocity parent) {
        super("OpalGrim");
        this.parent = parent;
    }

    @Override
    public String getName() {
        return "OpalGrim";
    }

    @Override
    public void onEnable() {
        flagCooldownTicks = 0;
        suppressTicks = 0;
        active = false;
        triggerCount = 0;
    }

    @Override
    public void onDisable() { reset(); }

    @Override
    public void reset() {
        flagCooldownTicks = 0;
        suppressTicks = 0;
        active = false;
        triggerCount = 0;
    }

    /** 金色主题 §6。 */
    private void debug(String msg) {
        if (parent.v2GrimDebug.getValue() && mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§6[Velocity] §e[OpalGrim] §r" + msg), false);
        }
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

    @Override
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null) return;

        if (suppressTicks > 0) {
            suppressTicks--;
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
            if (mc.options != null) mc.options.keySprint.setDown(false);
            if (suppressTicks == 0) {
                active = false;
                debug("§7Suppression ended");
            }
        }

        if (parent.v2GrimFlagCheck.getValue() && flagCooldownTicks > 0) {
            flagCooldownTicks--;
        }
    }

    @Override
    public void onAttack(EntityRemoveEvent event) {

    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null) return;
        Packet<?> p = event.getPacket();

        if (p instanceof ClientboundSetEntityMotionPacket motion) {
            if (motion.getId() != mc.player.getId()) return;

            boolean horizontal = (motion.getXa() != 0 || motion.getZa() != 0);
            if (!horizontal) return;

            LivingEntity target = getTarget();
            if (target == null) {
                debug("§7No target, ignored");
                return;
            }

            int ticks = parent.v2GrimFlagTicks.getValue().intValue();
            suppressTicks = ticks;
            active = true;
            triggerCount++;

            mc.player.setSprinting(false);
            if (mc.options != null) mc.options.keySprint.setDown(false);

            debug("§eTriggered §7on §f" + target.getName().getString()
                    + " §7suppress=§f" + ticks + "t §7#§f" + triggerCount);
        }

        if (p instanceof ClientboundPlayerPositionPacket) {
            if (parent.v2GrimFlagCheck.getValue()) {
                flagCooldownTicks = parent.v2GrimFlagTicks.getValue().intValue();
                debug("§cFlag detected §7cooldown=§f" + flagCooldownTicks + "t");
            }
        }
    }

    @Override
    public void onStrafe(StrafeEvent event) {
        if (suppressTicks > 0) {
            try { event.setSprinting(false); } catch (Throwable ignored) {}
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

    public boolean isActive() { return active; }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }
}