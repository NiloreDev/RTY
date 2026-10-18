package client.nilore.modules.impl.combat.antikb;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.event.impl.EntityHurtEvent;
import client.nilore.event.impl.EntityRemoveEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.RotationEvent;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.impl.combat.Velocity;
import client.nilore.modules.impl.combat.Backtrack;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.modules.impl.player.NoFall;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;

public class JumpResetMode extends AntiKBMode {
    public static volatile boolean isJumping = false;

    private ClientboundSetEntityMotionPacket knockbackPacket;
    private int rotationHeldTicks = 0;
    private int jumpTicks = 0;

    /** Debug 去重：上一次输出的状态，避免重复刷屏 */
    private String lastDebugStatus = "";

    public JumpResetMode() {
        super("JumpReset");
    }

    @Override
    public String getName() {
        return "JumpReset";
    }

    @Override
    public void onEnable() {
        this.knockbackPacket = null;
        Velocity.rotation = null;
        this.rotationHeldTicks = 0;
        this.lastDebugStatus = "";
        this.resetState();
        isJumping = false;
    }

    @Override
    public void onDisable() {
        this.onEnable();
    }

    private void resetState() {
        isJumping = false;
        this.jumpTicks = 0;
    }

    private boolean isNoFallEnabled() {
        return NoFall.INSTANCE != null && NoFall.INSTANCE.isEnabled()
                && (NoFall.INSTANCE.jumpLandingBoost || NoFall.INSTANCE.boostActive);
    }

    private boolean isBacktracking() {
        return Backtrack.INSTANCE != null && Backtrack.INSTANCE.isEnabled()
                && (Backtrack.INSTANCE.isActive() || Backtrack.INSTANCE.isBacktracking());
    }

    private boolean isSuspended() {
        return this.isNoFallEnabled() || this.isBacktracking();
    }

    private void cancelJumpReset() {
        Velocity.rotation = null;
        this.rotationHeldTicks = 0;
        this.resetState();
    }

    // 判断当前是否处于 Velocity 的 JumpReset 模式
    private boolean isJumpResetModeActive() {
        return Velocity.INSTANCE != null
                && Velocity.INSTANCE.isEnabled()
                && Velocity.mode != null
                && Velocity.mode.is("Velocity")
                && Velocity.INSTANCE.velocityMode.is("JumpRest");
    }

    @Override
    public void onRotation(RotationEvent event) {
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }

    @Override
    public void onAttack(EntityRemoveEvent event) {
    }

    @Override
    public void onEntityHurt(EntityHurtEvent event) {
    }

    @Override
    public void onSprint(SprintEvent event) {
    }

    @Override
    public void onPreMotion(PreMotionEvent event) {
    }

    @Override
    public void onStrafe(StrafeEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (this.isSuspended()) {
            this.cancelJumpReset();
            return;
        }
        if (this.isJumpResetModeActive()
                && Velocity.INSTANCE.followDirection.getValue()
                && Velocity.rotation != null) {
            event.setForward(1.0f);
            event.setStrafe(0.0f);
        }
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || !this.isJumpResetModeActive()) return;
        if (this.isSuspended()) {
            this.cancelJumpReset();
            return;
        }
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket motion)) return;
        if (motion.getId() != player.getId()) return;

        // ==============================================================
        // 摔落/水落伤害的特征拦截
        // ==============================================================
        if (Velocity.INSTANCE.jumpResetNoFallCheck != null && Velocity.INSTANCE.jumpResetNoFallCheck.getValue()) {
            double vx = motion.getXa() / 8000.0;
            double vz = motion.getZa() / 8000.0;

            // 1. 如果水平速度极小，直接拦截
            if (Math.abs(vx) < 0.05 && Math.abs(vz) < 0.05) {
                return;
            }
            // 2. 如果玩家正在摔落且速度向下，直接拦截
            if (player.fallDistance > 0 && motion.getYa() < 0) {
                return;
            }
            // 3. 玩家处于水中/岩浆中，直接拦截
            if (player.isInWater() || player.isInLava()) {
                return;
            }
        }

        this.knockbackPacket = motion;

        boolean wantRotate = (Velocity.INSTANCE.rotate.getValue() || Velocity.INSTANCE.followDirection.getValue())
                && KillAura.target == null;
        float xMotion = (float) (motion.getXa() / 8000.0);
        float zMotion = (float) (motion.getZa() / 8000.0);
        if (wantRotate && (Math.abs(xMotion) > 1.0E-6 || Math.abs(zMotion) > 1.0E-6)) {
            float yaw = (float) Math.toDegrees(Math.atan2(xMotion, -zMotion));
            Rotation kbRotation = new Rotation(yaw, player.getXRot());
            Velocity.rotation = kbRotation;
            this.rotationHeldTicks = 0;
            try {
                RotationHandler.setTargetRotation(kbRotation);
                RotationHandler.isRotating = true;
            } catch (Throwable ignored) {
            }
        }

        isJumping = true;
        if (player.onGround()) {
            this.jumpTicks = 1;
        }

        // 美化后的聊天栏输出
        this.notifyKnockback(player);
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
        Velocity.rotation = null;
        this.knockbackPacket = null;
        this.rotationHeldTicks = 0;
        this.resetState();
    }

    @Override
    public void reset() {

    }

    @Override
    public void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || !this.isJumpResetModeActive()) return;
        if (this.isSuspended()) {
            this.cancelJumpReset();
            return;
        }

        // 预判提前跳 (由于 Velocity 可能没这个开关，默认直接开启)
        if (this.shouldPredictJump(player)) {
            isJumping = true;
            this.jumpTicks = 1;
        }

        if (this.jumpTicks > 0 && !Scaffold.INSTANCE.isEnabled()) {
            if (player.isOnFire()) {
                this.resetState();
                return;
            }
            mc.options.keyJump.setDown(true);
            this.jumpTicks--;
            return;
        }
        if (!Scaffold.INSTANCE.isEnabled()) {
            boolean down = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
            mc.options.keyJump.setDown(down);
        }
    }

    /**
     * 预判机制
     */
    private boolean shouldPredictJump(LocalPlayer player) {
        // 如果你在 Velocity 里加了 predict 开关，可以换成 Velocity.INSTANCE.predict.getValue()
        // if (!Velocity.INSTANCE.predict.getValue()) return false;

        if (this.jumpTicks > 0 || Scaffold.INSTANCE.isEnabled()) return false;
        if (!player.onGround()) return false;
        if (!isJumping && player.hurtTime > 0) return true;
        if (player.hurtTime > 0) return false;

        KillAura aura = KillAura.INSTANCE;
        Entity target = aura != null && aura.isEnabled() ? KillAura.target : null;
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return false;
        if (!living.swinging || living.swingTime > 2) return false;

        Vec3 eyePos = living.getEyePosition(1.0f);
        Vec3 reachEnd = eyePos.add(living.getLookAngle().scale(RotationUtil.getReachLimit()));
        return player.getBoundingBox().inflate(0.1).clip(eyePos, reachEnd).isPresent();
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || !this.isJumpResetModeActive()) return;
        if (this.isSuspended()) {
            this.cancelJumpReset();
            return;
        }
        if (Velocity.rotation != null) {
            this.rotationHeldTicks++;
        }
        if (player.hurtTime == 0) {
            isJumping = false;
        }
        boolean shouldClear = player.hurtTime == 0
                || this.rotationHeldTicks > Velocity.INSTANCE.rotateTicks.getValue().intValue()
                || (!Velocity.INSTANCE.rotate.getValue() && !Velocity.INSTANCE.followDirection.getValue());
        if (shouldClear) {
            Velocity.rotation = null;
            this.knockbackPacket = null;
            this.rotationHeldTicks = 0;
        }
    }

    // =================================================================
    // === Debug 提示逻辑 ================================
    // =================================================================

    /**
     * 收到击退包时输出一条：
     *   在地面（生效）→ 状态绿色
     *   在空中（失效）→ 状态红色
     */
    private void notifyKnockback(LocalPlayer player) {
        if (Velocity.INSTANCE == null) return;

        boolean onGround = player.onGround();
        String statusColor = onGround ? "§a" : "§c";
        String statusText  = onGround ? "生效" : "失效";
        String coords = String.format("§7XYZ: §f%.1f %.1f %.1f", player.getX(), player.getY(), player.getZ());
        String posLabel = onGround ? "§aGround" : "§cAir";

        String message = statusColor + statusText + " §7| " + coords + " §7| " + posLabel;
        this.debug(message);
    }

    /**
     * 输出到聊天栏（相同消息只输出一次，避免刷屏）
     */
    private void debug(String message) {
        if (Velocity.INSTANCE == null) return;
        if (message.equals(this.lastDebugStatus)) return;
        this.lastDebugStatus = message;

        Velocity.INSTANCE.debugChat("§c[Velocity] §4[JumpReset] §f" + message);
    }
}