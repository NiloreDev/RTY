package client.nilore.modules.impl.combat.antikb;

import java.util.concurrent.ThreadLocalRandom;

import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.Velocity;
import client.nilore.settings.impl.NumberSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public class OpalJumpMode extends AntiKBMode {

    private final Velocity parent;

    private int ticksSinceHit;
    private boolean tookVerticalKnockback;
    private int targetJumpHits;
    private int targetJumpDelay;
    private boolean shouldJump;
    private int flagCooldownTicks;
    private int jumpCount;

    public OpalJumpMode(Velocity parent) {
        super("OpalJump");
        this.parent = parent;
    }

    @Override
    public String getName() {
        return "OpalJump";
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { reset(); }

    @Override
    public void reset() {
        ticksSinceHit = 0;
        tookVerticalKnockback = false;
        shouldJump = false;
        flagCooldownTicks = 0;
        jumpCount = 0;
        targetJumpHits = rand(parent.v2HitsMin, parent.v2HitsMax);
        targetJumpDelay = rand(parent.v2DelayMin, parent.v2DelayMax);
    }

    private int rand(NumberSetting minS, NumberSetting maxS) {
        int min = minS.getValue().intValue();
        int max = maxS.getValue().intValue();
        if (max < min) { int t = min; min = max; max = t; }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /** 青色主题 §b。 */
    private void debug(String msg) {
        if (parent.v2Debug.getValue() && mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§b[Velocity] §3[OpalJump] §r" + msg), false);
        }
    }

    private void incrementHitCounter() {
        if (parent.v2JumpByHits.getValue()) {
            if (mc.player != null && mc.player.hurtTime > 0) ticksSinceHit++;
            return;
        }
        ticksSinceHit++;
    }

    private boolean checkConditions() {
        if (parent.v2JumpByHits.getValue()) return ticksSinceHit >= targetJumpHits;
        if (parent.v2JumpByDelay.getValue()) return ticksSinceHit >= targetJumpDelay;
        return true;
    }

    private boolean checkChance() {
        double c = parent.v2JumpChance.getValue().doubleValue();
        return c >= 100.0 || ThreadLocalRandom.current().nextInt(100) <= (int) c;
    }

    @Override
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null) return;
        if (flagCooldownTicks != 0) return;

        if (mc.player.hurtTime != 10
                || (parent.v2OnlyGround.getValue() && !mc.player.onGround())
                || !mc.player.isSprinting()
                || tookVerticalKnockback
                || !checkConditions()
                || !checkChance()) {
            incrementHitCounter();
            return;
        }

        shouldJump = true;
        ticksSinceHit = 0;
        targetJumpHits = rand(parent.v2HitsMin, parent.v2HitsMax);
        targetJumpDelay = rand(parent.v2DelayMin, parent.v2DelayMax);
    }

    @Override
    public void onAttack(EntityRemoveEvent event) {

    }

    @Override
    public void onStrafe(StrafeEvent event) {
        if (flagCooldownTicks != 0 || !shouldJump) return;
        if (mc.options != null) mc.options.keyJump.setDown(true);
        shouldJump = false;
        jumpCount++;
        debug("§aJumped §7#§f" + jumpCount
                + " §7(hits=§f" + targetJumpHits
                + "§7, delay=§f" + targetJumpDelay + "§7)");
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null) return;
        Packet<?> p = event.getPacket();
        if (p instanceof ClientboundSetEntityMotionPacket motion) {
            if (motion.getId() != mc.player.getId()) return;

            tookVerticalKnockback = (motion.getXa() == 0
                    && motion.getZa() == 0
                    && motion.getYa() < 0);

            if (tookVerticalKnockback) {
                debug("§7Ignored vertical knockback");
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

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }

    @Override public void onTick(TickEvent event) { /* 占位 */ }
}