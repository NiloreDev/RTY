package client.nilore.modules.impl.combat.antikb;

import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.Velocity;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.LinkedBlockingDeque;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.event.EventTarget;

public class MixMode extends AntiKBMode {
    private boolean shouldAttack = false;
    private boolean wasSprinting = false;
    private boolean isSuspending = false;
    private final LinkedBlockingDeque<Packet<ClientGamePacketListener>> packetQueue = new LinkedBlockingDeque();
    private int lastTickCount = 0;
    private int webHitCount = 0;
    private int airTicks = 0;
    private ClientboundSetEntityMotionPacket knockbackPacket;
    private int sprintTick = -1;
    private int movementState = 0;

    // === Debug 状态 ===
    private int kbCount = 0;
    private int flushCount = 0;
    private int attackCount = 0;

    public MixMode() {
        super("Mix");
    }

    @Override
    public boolean isActive() {
        return this.isSuspending;
    }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }

    @Override
    public void onEnable() {
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.resetState();
    }

    @Override
    public String getName() {
        return "";
    }

    @Override public void onRotation(RotationEvent rotationEvent) { }

    @Override
    public void onMotion(MotionEvent motionEvent) {
        if (mc.player == null) return;
        if (this.lastTickCount < mc.player.tickCount) {
            this.webHitCount = 0;
        }

        // 只有真正处于 suspending 时才尝试 flush + 输出
        if (motionEvent.isPre()
                && this.isSuspending
                && (mc.player.onGround() || this.airTicks >= 24)) {
            this.flushPackets();
            this.isSuspending = false;
            this.shouldAttack = false;

            Velocity.INSTANCE.notifyChatIfEnabled(
                    "§b[Velocity/Mix] §aMotion flush §7#" + flushCount
                            + " §7(airTicks=§f" + airTicks + "§7)");
        }
    }

    @EventTarget
    public void onStuckInBlock(StuckInBlockEvent stuckInBlockEvent) {
        if (stuckInBlockEvent.getBlockState().getBlock() == Blocks.COBWEB) {
            ++this.webHitCount;
        }
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent receivePacketEvent) {
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null) return;
        Packet<ClientGamePacketListener> packet = receivePacketEvent.getPacket();

        if (this.webHitCount <= 0 || mc.player.isInWater() || mc.player.isUnderWater()) {
            if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
                if (motionPacket.getId() != localPlayer.getId()) return;
                Velocity.rotation = null;
                this.knockbackPacket = null;
                if (motionPacket.getYa() > 0) {
                    this.sprintTick = 0;
                    if (KillAura.INSTANCE.isEnabled() && KillAura.target != null && Velocity.INSTANCE.tryAttack.getValue()) {
                        this.shouldAttack = true;
                        this.wasSprinting = mc.player.isSprinting();
                    } else if (Velocity.INSTANCE.rotate.getValue()) {
                        float dx = (float)((double)motionPacket.getXa() / 8000.0);
                        float dz = (float)((double)motionPacket.getZa() / 8000.0);
                        float kbYaw = (float)Math.toDegrees(Math.atan2(dx, -dz));
                        Velocity.rotation = new Rotation(kbYaw, localPlayer.getXRot());
                    }
                    receivePacketEvent.setCancelled(true);
                    this.packetQueue.add(receivePacketEvent.getPacket());
                    this.isSuspending = true;
                    this.knockbackPacket = motionPacket;

                    kbCount++;
                    Velocity.INSTANCE.notifyChatIfEnabled(String.format(
                            "§b[Velocity/Mix] §eKB §7#%d §fsuspended §7vec=(§f%.3f, %.3f, %.3f§7)",
                            kbCount,
                            motionPacket.getXa() / 8000.0,
                            motionPacket.getYa() / 8000.0,
                            motionPacket.getZa() / 8000.0));
                }
            }
            if (this.isSuspending) {
                if (packet instanceof ClientboundMoveEntityPacket
                        || packet instanceof ClientboundPingPacket
                        || packet instanceof ClientboundTeleportEntityPacket) {
                    this.packetQueue.add(receivePacketEvent.getPacket());
                    receivePacketEvent.setCancelled(true);
                }
                if (receivePacketEvent.getPacket() instanceof ClientboundPlayerPositionPacket) {
                    ChatUtil.print("?");
                    Velocity.INSTANCE.notifyChatIfEnabled(
                            "§b[Velocity/Mix] §cPosition correction §7-> reset");
                    this.resetState();
                }
            }
        } else {
            Velocity.rotation = null;
            this.knockbackPacket = null;
            this.flushPackets();
            this.isSuspending = false;
            ChatUtil.print("Ignore: Player in web or liquid!");
            Velocity.INSTANCE.notifyChatIfEnabled(
                    "§b[Velocity/Mix] §7Ignore §7(player in web/liquid)");
        }
    }

    @Override
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        this.resetState();
    }

    @Override
    public void reset() {

    }

    @Override public void onPreMotion(PreMotionEvent event) { }

    @Override
    public void onEntityHurt(EntityHurtEvent event) {

    }

    @Override
    public void onSprint(SprintEvent sprintEvent) {
        if (mc.player == null) return;
        if (!this.isSuspending) {
            Velocity.rotation = null;
        }
        if (this.knockbackPacket != null
                && this.knockbackPacket.getYa() > 0
                && mc.player.hurtTime > 0
                && KillAura.INSTANCE.isEnabled()
                && KillAura.target != null
                && this.shouldAttack) {
            this.shouldAttack = false;
            for (int i = 0; i < 2; ++i) {
                mc.player.setSprinting(false);
                KillAura.INSTANCE.doAttack();
            }
            attackCount += 2;
            Velocity.INSTANCE.notifyChatIfEnabled(
                    "§b[Velocity/Mix] §cAttack x2 §7(total=§f" + attackCount + "§7)");
        }
        if (this.sprintTick >= 0) {
            ++this.sprintTick;
        }
        if (Velocity.INSTANCE.movementOverride.getValue() && this.knockbackPacket != null) {
            if (this.sprintTick >= 1) {
                if (this.sprintTick <= 2 && mc.player.onGround()) {
                    mc.options.keyJump.setDown(true);
                }
                if (this.sprintTick <= 3) {
                    this.applyKBDirection();
                    this.movementState = 1;
                }
            }
            if (this.sprintTick >= 4 && this.sprintTick <= 10) {
                mc.options.keyJump.setDown(false);
                if (this.movementState == 1) {
                    this.restoreMovementKeys();
                    this.movementState = 0;
                }
            }
            if (this.sprintTick >= 10) {
                this.sprintTick = -1;
            }
        }
    }

    private void applyKBDirection() {
        float dx = (float)((double)this.knockbackPacket.getXa() / 8000.0);
        float dz = (float)((double)this.knockbackPacket.getZa() / 8000.0);
        float kbYaw = (float)Math.toDegrees(Math.atan2(dx, -dz));
        float yawDelta = Mth.wrapDegrees(kbYaw - mc.player.getYRot());
        this.restoreMovementKeys();
        double yawRad = Math.toRadians(yawDelta);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        if (cosYaw > 0.5) {
            mc.options.keyUp.setDown(true);
        } else if (cosYaw < -0.5) {
            mc.options.keyDown.setDown(true);
        }
        if (sinYaw > 0.5) {
            mc.options.keyRight.setDown(true);
        } else if (sinYaw < -0.5) {
            mc.options.keyLeft.setDown(true);
        }
    }

    private void restoreMovementKeys() {
        mc.options.keyUp.setDown(InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyUp.getKey().getValue()));
        mc.options.keyDown.setDown(InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyDown.getKey().getValue()));
        mc.options.keyLeft.setDown(InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyLeft.getKey().getValue()));
        mc.options.keyRight.setDown(InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyRight.getKey().getValue()));
    }

    @Override
    public void onGameTick(GameTickEvent gameTickEvent) {
        if (this.knockbackPacket != null && !Velocity.INSTANCE.movementOverride.getValue() && mc.player != null) {
            if (mc.player.hurtTime > 6 && !mc.options.keyJump.isDown()) {
                mc.options.keyJump.setDown(true);
            } else if (!Scaffold.INSTANCE.isEnabled()) {
                boolean jumpKeyDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
                mc.options.keyJump.setDown(jumpKeyDown);
            }
        }
    }

    @Override
    public void onAttack(EntityRemoveEvent event) {

    }

    @Override
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null) return;
        this.airTicks = mc.player.onGround() ? 0 : ++this.airTicks;
    }

    @Override public void onStrafe(StrafeEvent event) { }

    private void resetState() {
        this.knockbackPacket = null;
        this.lastTickCount = 0;
        this.webHitCount = 0;
        this.isSuspending = false;
        this.flushPackets();
        Velocity.rotation = null;
        this.restoreMovementKeys();
    }

    private void flushPackets() {
        int count = 0;
        while (!this.packetQueue.isEmpty()) {
            try {
                ((Packet)this.packetQueue.poll()).handle(mc.getConnection());
                count++;
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        if (count > 0) {
            flushCount++;
        }
    }
}