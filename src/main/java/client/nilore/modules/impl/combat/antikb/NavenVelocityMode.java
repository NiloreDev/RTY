package client.nilore.modules.impl.combat.antikb;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.Critical;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.combat.Velocity;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.render.RenderUtil;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * High-fidelity reconstruction of EdNaven 3.3 Velocity, mounted as Nilore Mode "Naven".
 */
public final class NavenVelocityMode extends AntiKBMode {

    public static boolean handlingVelocity;
    private final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private final Map<Entity, Vec3> entityPositions = new LinkedHashMap<>();

    private boolean delayingPackets;
    private boolean forwardState;
    private boolean jumpPending;
    private boolean correctionSeen;
    private boolean replaying;

    private int attackCounter;
    private int delayTicks;
    private int jumpPendingTicks;
    private int failedRaycasts;

    private long forwardResetAt = -1L;
    private Entity target;

    private float progress = 0.0F;
    private float previousProgress = 0.0F;

    /** 合并自 Critical：当前是否处于松疾跑状态。 */
    private boolean criticalReleased = false;

    private enum AlinkState { IDLE, CHARGING, SUCCESS }

    private AlinkState alinkState = AlinkState.IDLE;
    private int alinkSuccessTicks;

    private static final int ALINK_SUCCESS_DURATION_TICKS = 20;

    // === Debug 状态 ===
    private int kbCount = 0;
    private int flushCount = 0;
    private int attackCount = 0;

    private enum EspColorPreset {
        WHITE(1.00F, 1.00F, 1.00F),
        CYAN(0.20F, 0.95F, 1.00F),
        RED(1.00F, 0.25F, 0.25F),
        GREEN(0.25F, 1.00F, 0.35F),
        PURPLE(0.75F, 0.35F, 1.00F);

        final float r;
        final float g;
        final float b;

        EspColorPreset(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private int alinkHudY = 18;
    private EspColorPreset targetEspColor = EspColorPreset.WHITE;

    public NavenVelocityMode() {
        super("Naven");
    }

    @Override
    public String getName() {
        return "Naven";
    }

    @Override
    public boolean isActive() {
        return delayingPackets || attackCounter > 0 || jumpPending || forwardState;
    }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {

    }

    @Override
    public void onEnable() {
        hardReset(false);
    }

    @Override
    public void onDisable() {
        resetOriginalStyle();
    }

    private void resetOriginalStyle() {
        if (!packetQueue.isEmpty()) {
            scheduleFlush();
        }

        delayingPackets = false;
        forwardState = false;
        jumpPending = false;
        entityPositions.clear();
        forwardResetAt = -1L;
        failedRaycasts = 0;
        jumpPendingTicks = 0;
        attackCounter = 0;
        delayTicks = 0;
        correctionSeen = false;
        alinkState = AlinkState.IDLE;
        alinkSuccessTicks = 0;
        progress = 0.0F;
        previousProgress = 0.0F;
        criticalReleased = false;
    }

    private void hardReset(boolean replayQueue) {
        if (replayQueue && !packetQueue.isEmpty()) {
            flushQueue();
        } else {
            packetQueue.clear();
        }

        delayingPackets = false;
        forwardState = false;
        jumpPending = false;
        correctionSeen = false;
        replaying = false;
        attackCounter = 0;
        delayTicks = 0;
        jumpPendingTicks = 0;
        failedRaycasts = 0;
        forwardResetAt = -1L;
        target = null;
        progress = 0.0F;
        previousProgress = 0.0F;
        alinkState = AlinkState.IDLE;
        alinkSuccessTicks = 0;
        entityPositions.clear();
        criticalReleased = false;
    }

    private void scheduleFlush() {
        mc.execute(this::flushQueue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void flushQueue() {
        if (mc.getConnection() == null) {
            packetQueue.clear();
            return;
        }

        replaying = true;
        try {
            Packet packet;
            while ((packet = packetQueue.poll()) != null) {
                packet.handle(mc.getConnection());

                if (Velocity.INSTANCE.navenMode.is("Both")
                        && packet instanceof ClientboundSetEntityMotionPacket velocityPacket) {
                    handleVelocityPacket(velocityPacket);
                }
            }
            flushCount++;
            Velocity.INSTANCE.notifyChatIfEnabled(
                    "§b[Velocity/Naven] §aFlush §7#" + flushCount
                            + " §7attackCounter=§f" + attackCounter);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        } finally {
            replaying = false;
        }
    }

    private boolean isRaycastingTarget() {
        if (mc.player == null || target == null) return false;
        return mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() == target;
    }

    private boolean isBlocked() {
        return mc.player == null
                || mc.player.isUsingItem()
                || correctionSeen
                || mc.getConnection() == null
                || target == null
                || unresolvedGlobalBlocker();
    }

    private boolean unresolvedGlobalBlocker() {
        return false;
    }

    private boolean isPassThroughPacket(Packet<?> packet) {
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
                || packet instanceof ClientboundAnimatePacket animate
                && mc.player != null
                && animate.getId() != mc.player.getId();
    }

    private void handleVelocityPacket(ClientboundSetEntityMotionPacket packet) {
        if (mc.player == null || packet.getId() != mc.player.getId()) {
            return;
        }

        kbCount++;
        Velocity.INSTANCE.notifyChatIfEnabled(String.format(
                "§b[Velocity/Naven] §eKB §7#%d §fvec=(%.3f, %.3f, %.3f)",
                kbCount,
                packet.getXa() / 8000.0, packet.getYa() / 8000.0, packet.getZa() / 8000.0));

        if (Velocity.INSTANCE.navenMode.is("JumpReset") && Velocity.INSTANCE.navenAutoRotation.getValue()) {
            if (target == null) {
                float velocityYaw = (float) Math.toDegrees(
                        Math.atan2(packet.getXa() / 8000.0D,
                                -(packet.getZa() / 8000.0D)));

                setNiloreRotationCompat(velocityYaw, mc.player.getYRot());
            }

            forwardState = true;
        }

        jumpPending = true;
        jumpPendingTicks = 0;
    }

    private void setNiloreRotationCompat(float yaw, float secondAngle) {
        try {
            for (Constructor<?> constructor : Rotation.class.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length != 2) continue;

                Object a = convertNumber(yaw, p[0]);
                Object b = convertNumber(secondAngle, p[1]);
                if (a == null || b == null) continue;

                constructor.setAccessible(true);
                Velocity.rotation = (Rotation) constructor.newInstance(a, b);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private Object convertNumber(float value, Class<?> type) {
        if (type == float.class || type == Float.class) return value;
        if (type == double.class || type == Double.class) return (double) value;
        return null;
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null || mc.level == null || replaying) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket) {
            correctionSeen = true;
            Velocity.INSTANCE.notifyChatIfEnabled("§b[Velocity/Naven] §cPosition correction §7-> reset");
            return;
        }

        if (Velocity.INSTANCE.navenMode.is("JumpReset")) {
            boolean killAuraRequirementFailed =
                    Velocity.INSTANCE.navenRequiresKillAura.getValue()
                            && (KillAura.INSTANCE == null || !KillAura.INSTANCE.isEnabled());

            if (correctionSeen || !mc.player.onGround() || killAuraRequirementFailed) {
                resetOriginalStyle();
                return;
            }

            if (packet instanceof ClientboundSetEntityMotionPacket velocityPacket
                    && velocityPacket.getId() == mc.player.getId()) {
                handleVelocityPacket(velocityPacket);
            }
            return;
        }

        if (isBlocked()) {
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket velocityPacket
                && velocityPacket.getId() == mc.player.getId()) {
            if (!delayingPackets) {
                delayingPackets = true;
                delayTicks = 0;
                previousProgress = 0.0F;
                progress = 0.0F;
                alinkState = AlinkState.CHARGING;
                alinkSuccessTicks = 0;

                if (target != null) {
                    entityPositions.putIfAbsent(target, target.position());
                }

                Velocity.INSTANCE.notifyChatIfEnabled(
                        "§b[Velocity/Naven] §eDelay started §7(target=§f"
                                + (target != null ? target.getName().getString() : "null") + "§7)");
            }

            event.setCancelled(true);
            packetQueue.add(packet);
            return;
        }

        if (isPassThroughPacket(packet) || !delayingPackets) {
            return;
        }

        updatePredictedEntityPosition(packet);
        event.setCancelled(true);
        packetQueue.add(packet);
    }

    private void updatePredictedEntityPosition(Packet<?> packet) {
        if (mc.level == null) return;

        if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()) {
            Entity entity = move.getEntity(mc.level);
            if (entity != null) {
                Vec3 base = entityPositions.getOrDefault(entity, entity.position());
                entityPositions.put(entity, base.add(
                        move.getXa() / 4096.0D,
                        move.getYa() / 4096.0D,
                        move.getZa() / 4096.0D));
            }
            return;
        }

        if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            Entity entity = mc.level.getEntity(teleport.getId());
            if (entity != null) {
                entityPositions.put(entity,
                        new Vec3(teleport.getX(), teleport.getY(), teleport.getZ()));
            }
        }
    }

    private boolean isCriticalReleaseWindow() {
        if (Critical.INSTANCE != null
                && Critical.INSTANCE.isEnabled()
                && Critical.INSTANCE.mode.is("GrimAC")
                && Critical.INSTANCE.isReleaseWindow()) {
            return true;
        }

        if (mc.player == null) return false;
        if (target == null) return false;
        if (!(target instanceof LivingEntity living)) return false;

        if (mc.player.onGround()) return false;
        if (mc.player.isInWater() || mc.player.isInLava()) return false;
        if (mc.player.isUsingItem()) return false;
        if (mc.player.isShiftKeyDown()) return false;
        if (mc.player.isFallFlying()) return false;
        if (mc.player.isPassenger()) return false;
        if (mc.player.onClimbable()) return false;
        if (mc.player.hasEffect(MobEffects.BLINDNESS)) return false;
        if (mc.player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) return false;
        if (mc.player.hasEffect(MobEffects.LEVITATION)) return false;

        int hurtTime = living.hurtTime;
        return hurtTime >= 7 || hurtTime <= 3;
    }

    @Override
    public void onTick(TickEvent event) {
        Entity auraTarget = KillAura.target;
        if (!delayingPackets || auraTarget != null) {
            target = auraTarget;
        }

        if (jumpPending) {
            ++jumpPendingTicks;
        }

        if (jumpPendingTicks >= 5) {
            jumpPending = false;
        }

        if (alinkState == AlinkState.SUCCESS) {
            if (++alinkSuccessTicks >= ALINK_SUCCESS_DURATION_TICKS) {
                alinkState = AlinkState.IDLE;
                alinkSuccessTicks = 0;
                progress = 0.0F;
                previousProgress = 0.0F;
            }
        }

        if (Velocity.INSTANCE.navenMode.is("JumpReset")) {
            updateForwardResetTimer();
            return;
        }

        if (mc.player == null || isBlocked() || failedRaycasts >= 3) {
            resetOriginalStyle();
            return;
        }

        if (delayingPackets) {
            forwardState = true;
            attackCounter = 0;

            if (target != null) {
                entityPositions.putIfAbsent(target, target.position());
            }

            ++delayTicks;
            previousProgress = progress;
            float maxDelay = Math.max(1.0F,
                    Velocity.INSTANCE.navenMaxDelayTicks.getValue().floatValue());
            progress = Math.max(0.0F, Math.min(1.0F, delayTicks / maxDelay));
            alinkState = AlinkState.CHARGING;

            if (mc.player.onGround()) {

                attackCounter = Velocity.INSTANCE.navenMaxCounter.getValue().intValue();
                failedRaycasts = 0;
                delayingPackets = false;
                previousProgress = progress;

                alinkState = AlinkState.SUCCESS;
                alinkSuccessTicks = 0;
                scheduleFlush();
            }
        }

        if (attackCounter > 0) {
            if (!isRaycastingTarget()) {
                ++failedRaycasts;
                debug("[N] Failed (RayCast) - Counter: " + attackCounter);
                return;
            }

            if (isCriticalReleaseWindow()) {
                mc.options.keySprint.setDown(false);
                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }
                criticalReleased = true;
                return;
            }

            if (criticalReleased) {
                mc.player.setSprinting(true);
                criticalReleased = false;
            }

            if (!mc.player.isSprinting()) {
                debug("[N] Failed (Sprint) - Counter: " + attackCounter);
                return;
            }

            attackTarget(target);
            --attackCounter;
        } else if (forwardState) {
            forwardResetAt = System.currentTimeMillis() + 10L;
        } else {
            criticalReleased = false;
        }

        updateForwardResetTimer();

        if (alinkState == AlinkState.IDLE) {
            updateProgress();
        }
    }

    private void attackTarget(Entity entity) {
        if (mc.player == null || mc.gameMode == null || entity == null) {
            return;
        }

        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);

        attackCount++;
        Velocity.INSTANCE.notifyChatIfEnabled(
                "§b[Velocity/Naven] §cAttack §7#" + attackCount
                        + " §7remaining=§f" + attackCounter);
    }

    @Override
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null) return;

        boolean jumpFamily = Velocity.INSTANCE.navenMode.is("JumpReset") || Velocity.INSTANCE.navenMode.is("Both");

        if (jumpFamily) {
            if (forwardState
                    && Velocity.INSTANCE.navenAutoForwards.getValue()
                    && (!delayingPackets || movementGate())) {
                event.setForward(1.0F);
                event.setStrafe(0.0F);
            }

            if (jumpPending && mc.player.isSprinting()) {
                if (mc.player.hurtTime > 9 || Velocity.INSTANCE.navenMode.is("Both")) {
                    mc.player.input.jumping = true;
                    jumpPending = false;
                }

                if (Velocity.INSTANCE.navenMode.is("JumpReset")) {
                    forwardResetAt = System.currentTimeMillis() + 10L;
                }
            }

            return;
        }

        if (forwardState
                && (!delayingPackets || movementGate())
                && Velocity.INSTANCE.navenAutoForwards.getValue()) {
            event.setForward(1.0F);
        }
    }

    private boolean movementGate() {
        if (mc.player == null || mc.level == null) return false;
        if (mc.player.onGround() || attackCounter != 0) return true;
        if (mc.player.getDeltaMovement().y > 0.0D || mc.player.fallDistance > 0.0F) {
            return false;
        }

        return false;
    }

    @Override
    public void onSprint(SprintEvent event) {
        if (attackCounter == 0) return;

        try {
            Method m = event.getClass().getMethod("setCancelled", boolean.class);
            m.invoke(event, true);
        } catch (Throwable ignored) {
        }
    }

    private void updateForwardResetTimer() {
        if (forwardResetAt == -1L) return;

        boolean timeReached = System.currentTimeMillis() >= forwardResetAt;
        boolean forwardKeyDown = mc.options != null && mc.options.keyUp.isDown();

        if (timeReached || forwardKeyDown) {
            forwardState = false;
            forwardResetAt = -1L;
        }
    }

    private void updateProgress() {
        previousProgress = progress;
        if (delayingPackets) {
            float max = Math.max(1.0F,
                    Velocity.INSTANCE.navenMaxDelayTicks.getValue().floatValue());
            progress = Math.max(0.0F, Math.min(1.0F, delayTicks / max));
        } else {
            progress *= 0.85F;
            if (progress < 0.001F) progress = 0.0F;
        }
    }

    /**
     * 原有的 debug 输出（navenDebug 控制），现在同时受 Chat Naven 控制。
     */
    private void debug(String message) {
        if (mc.player == null) return;

        if (Velocity.INSTANCE.navenDebug.getValue()) {
            mc.player.sendSystemMessage(Component.literal(message));
        }

        Velocity.INSTANCE.notifyChatIfEnabled("§b[Velocity/Naven] §7" + message);
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
        hardReset(false);
    }

    @Override
    public void reset() {

    }

    @Override
    public void onGameTick(GameTickEvent event) {
        updateForwardResetTimer();
    }

    @Override
    public void onAttack(EntityRemoveEvent event) {

    }

    @Override public void onPreMotion(PreMotionEvent event) { }

    @Override
    public void onEntityHurt(EntityHurtEvent event) {

    }

    @Override public void onRotation(RotationEvent event) { }
    @Override public void onMotion(MotionEvent event) { }

    @Override
    public void onRender(RenderEvent event) {
        if (!Velocity.INSTANCE.navenTargetESP.getValue()
                || !delayingPackets
                || target == null
                || mc.player == null
                || mc.level == null
                || mc.gameRenderer == null) {
            return;
        }

        PoseStack poseStack = event.poseStack();
        if (poseStack == null) return;

        Vec3 predicted = entityPositions.getOrDefault(target, target.position());

        Vec3 currentPos = target.position();
        AABB box = target.getBoundingBox().move(
                predicted.x - currentPos.x,
                predicted.y - currentPos.y,
                predicted.z - currentPos.z
        ).inflate(0.025D);

        EspColorPreset preset = resolveTargetEspColor();
        Color color = new Color(
                Math.round(preset.r * 255.0F),
                Math.round(preset.g * 255.0F),
                Math.round(preset.b * 255.0F),
                255
        );

        poseStack.pushPose();
        try {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 cameraPos = camera.getPosition();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            RenderUtil.drawColoredBox(box, poseStack, color, color);
        } finally {
            poseStack.popPose();
        }
    }

    public void onRender3D(Render3DEvent event) {
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (alinkState == AlinkState.IDLE) {
            return;
        }

        Object graphics = extractGuiGraphics(event);
        if (graphics == null || mc.font == null) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int x = screenWidth / 2 - 55;
        int y = resolveAlinkHudY();
        int width = 110;
        int height = 18;

        guiFill(graphics, x, y, x + width, y + height, 0xB8181818);

        if (alinkState == AlinkState.CHARGING) {
            int percent = Math.max(0, Math.min(100, Math.round(progress * 100.0F)));
            String text = "Alink Charging " + percent + "%";

            int barX = x + 3;
            int barY = y + height - 3;
            int barWidth = width - 6;
            int filled = Math.round(barWidth * progress);
            guiFill(graphics, barX, barY, barX + barWidth, barY + 1, 0x70404040);
            if (filled > 0) {
                guiFill(graphics, barX, barY, barX + filled, barY + 1, 0xFFFFFFFF);
            }

            guiDrawCenteredComfortableString(graphics, text, screenWidth / 2, y + 5, 0xFFFFFFFF);
        } else {
            guiDrawCenteredComfortableString(graphics, "Alink Success", screenWidth / 2, y + 5, 0xFFFFFFFF);
        }
    }

    private int resolveAlinkHudY() {
        Object value = readOptionalVelocitySettingValue("navenAlinkY");
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return Math.max(0, alinkHudY);
    }

    private EspColorPreset resolveTargetEspColor() {
        Object value = readOptionalVelocitySettingValue("navenTargetESPColor");
        if (value != null) {
            String name = String.valueOf(value).trim().toUpperCase();
            if (name.contains("WHITE")) return EspColorPreset.WHITE;
            if (name.contains("CYAN")) return EspColorPreset.CYAN;
            if (name.contains("RED")) return EspColorPreset.RED;
            if (name.contains("GREEN")) return EspColorPreset.GREEN;
            if (name.contains("PURPLE")) return EspColorPreset.PURPLE;
        }
        return targetEspColor;
    }

    private Object readOptionalVelocitySettingValue(String fieldName) {
        try {
            Field field = Velocity.INSTANCE.getClass().getField(fieldName);
            field.setAccessible(true);
            Object setting = field.get(Velocity.INSTANCE);
            if (setting == null) return null;

            for (String getter : new String[]{"getValue", "getMode", "getSelected", "get"}) {
                try {
                    Method method = setting.getClass().getMethod(getter);
                    method.setAccessible(true);
                    Object value = method.invoke(setting);
                    if (value != null) return value;
                } catch (Throwable ignored) {
                }
            }

            return setting;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void setAlinkHudY(int y) {
        this.alinkHudY = Math.max(0, y);
    }

    public int getAlinkHudY() {
        return resolveAlinkHudY();
    }

    public void setTargetEspColor(String colorName) {
        if (colorName == null) return;
        String name = colorName.trim().toUpperCase();
        if (name.equals("WHITE")) targetEspColor = EspColorPreset.WHITE;
        else if (name.equals("CYAN")) targetEspColor = EspColorPreset.CYAN;
        else if (name.equals("RED")) targetEspColor = EspColorPreset.RED;
        else if (name.equals("GREEN")) targetEspColor = EspColorPreset.GREEN;
        else if (name.equals("PURPLE")) targetEspColor = EspColorPreset.PURPLE;
    }

    public String getTargetEspColor() {
        return resolveTargetEspColor().name();
    }

    private PoseStack extractPoseStack(Object event) {
        Object value = readMember(event,
                "getPoseStack", "getMatrixStack", "getStack", "getMatrices",
                "poseStack", "matrixStack", "stack", "matrices");
        return value instanceof PoseStack stack ? stack : null;
    }

    private Object extractGuiGraphics(Object event) {
        return readMember(event,
                "getGuiGraphics", "getGraphics", "getContext", "getGuiContext",
                "guiGraphics", "graphics", "context", "guiContext");
    }

    private Object readMember(Object owner, String... names) {
        if (owner == null) return null;

        for (String name : names) {
            try {
                Method method = owner.getClass().getMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(owner);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }

            try {
                Field field = owner.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private void guiFill(Object graphics, int x1, int y1, int x2, int y2, int color) {
        try {
            Method fill = graphics.getClass().getMethod(
                    "fill", int.class, int.class, int.class, int.class, int.class);
            fill.invoke(graphics, x1, y1, x2, y2, color);
        } catch (Throwable ignored) {
        }
    }

    private void guiDrawCenteredComfortableString(Object graphics, String text, int centerX, int y, int color) {
        final float scale = 0.90F;
        PoseStack pose = null;

        Object poseValue = readMember(graphics, "pose", "getPose", "getPoseStack", "poseStack");
        if (poseValue instanceof PoseStack stack) {
            pose = stack;
        }

        if (pose != null) {
            pose.pushPose();
            try {
                pose.scale(scale, scale, 1.0F);

                int width = mc.font.width(text);
                int scaledCenterX = Math.round(centerX / scale);
                int scaledY = Math.round(y / scale);
                int x = scaledCenterX - width / 2;

                if (invokeGuiDrawString(graphics, text, x, scaledY, color, false)) {
                    return;
                }
            } finally {
                pose.popPose();
            }
        }

        int width = mc.font.width(text);
        int x = centerX - width / 2;
        invokeGuiDrawString(graphics, text, x, y, color, false);
    }

    private boolean invokeGuiDrawString(Object graphics, String text, int x, int y, int color, boolean shadow) {
        for (Method method : graphics.getClass().getMethods()) {
            if (!method.getName().equals("drawString") || method.getParameterCount() != 6) {
                continue;
            }

            try {
                method.invoke(graphics, mc.font, text, x, y, color, shadow);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public Vec3 getPredictedTargetPosition() {
        return target == null ? null : entityPositions.get(target);
    }

    public float getProgress() {
        return progress;
    }

    public float getPreviousProgress() {
        return previousProgress;
    }
}