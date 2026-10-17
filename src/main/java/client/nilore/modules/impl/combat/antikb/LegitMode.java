package client.nilore.modules.impl.combat.antikb;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import client.nilore.modules.impl.combat.Velocity;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import client.nilore.NiloreClient;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.event.impl.EntityHurtEvent;
import client.nilore.event.impl.EntityRemoveEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.RotationEvent;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.impl.combat.KeepSprint;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.movement.NoSlow;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.modules.impl.movement.Stuck;
import client.nilore.utils.game.FightManager;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.utils.render.RenderUtil;

/**
 * Legit（原 NoXZ）
 *
 * 两套效果，由 Velocity 面板的 Delay 开关切换：
 *
 *   Delay 开启（延迟生效）：
 *     拦截自身击退包 → 进入延迟窗口；当 看向目标 + 疾跑 + 落地时，
 *     释放全部排队包并开始计数；计数阶段对目标连打 Attack Amount 下。
 *
 *   Delay 关闭（纯计数）：
 *     收到自身击退包 → 不拦截、不延迟，直接开始计数，地面/空中都生效。
 *
 * Need KillAura：
 *     开启时，只有 KillAura 启用（且锁定目标）才生效；
 *     关闭时，任何时候都生效，不受 KillAura 开关影响。
 *
 * Chat 输出：只在"计数激活"那一刻输出一条，包含 target / hits / range / reach；
 *            一次启用周期内最多一条；其余流程全静默。
 *            由 Velocity 面板的 "Chat Legit" 开关控制。
 */
public class LegitMode
        extends AntiKBMode {
    public static LegitMode INSTANCE;
    public static boolean handlingVelocity;

    private final ConcurrentLinkedQueue<Packet<ClientGamePacketListener>> packetQueue = new ConcurrentLinkedQueue<>();
    private final Map<Entity, Vec3> entityPositions = new HashMap<>();

    public static boolean compensating;
    public static boolean inDelayWindow;
    public static int counterRemaining;

    private boolean positionCorrection;
    private int delayTicks;
    private int noAimTicks;
    private int noSprintTicks;
    private int jumpTicks;
    private boolean jumpKeyForced;
    private float barProgress;
    private float barAlpha;
    private long lastFrameNanos;
    private long compensateUntilMs = -1L;
    private Entity target;

    /** 本次启用周期是否已经输出过那条唯一的 "计数激活" 消息 */
    private boolean counterMessageSent;

    private static final int WINDUP_TICKS = 2;

    @Override
    public boolean isActive() {
        return inDelayWindow || counterRemaining > 0;
    }

    @Override
    public void onStrafe(StrafeEvent event, int unused) {
    }

    public static boolean isCountering() {
        return counterRemaining > 0;
    }

    public static boolean isInAttackWindow() {
        return isCountering();
    }

    public static boolean isInDelayWindow() {
        return inDelayWindow;
    }

    public static Entity getLockedTarget() {
        LegitMode mode = INSTANCE;
        if (mode == null || Velocity.INSTANCE == null || !Velocity.INSTANCE.isEnabled()) {
            return null;
        }
        if (!inDelayWindow && counterRemaining <= 0) {
            return null;
        }
        return mode.target != null && mode.target.isAlive() ? mode.target : null;
    }

    public LegitMode() {
        super("Legit");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.counterMessageSent = false;
        this.resetAll();
    }

    @Override
    public void onDisable() {
        this.resetAll();
    }

    @Override
    public String getName() {
        return "Legit";
    }

    @Override
    public void onRotation(RotationEvent rotationEvent) {
    }

    @Override
    public void onMotion(MotionEvent motionEvent) {
    }

    @Override
    public void onPreMotion(PreMotionEvent preMotionEvent) {
    }

    @Override
    public void onSprint(SprintEvent sprintEvent) {
    }

    @Override
    public void onAttack(EntityRemoveEvent entityRemoveEvent) {
    }

    @Override
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null || event.entity() != mc.player) {
            return;
        }
        Entity attacker = event.damageSource().getDirectEntity();
        if (attacker == null) {
            attacker = event.damageSource().getEntity();
        }
        if (attacker != null && attacker != mc.player && attacker.isAlive() && this.target == null) {
            this.target = attacker;
        }
    }

    @Override
    public void onGameTick(GameTickEvent gameTickEvent) {
        if (mc.player == null) {
            return;
        }
        if (!Velocity.INSTANCE.jumpReset.getValue() || this.jumpTicks <= 0) {
            this.restoreJumpKey();
            return;
        }
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            return;
        }
        if (mc.player.isOnFire()) {
            this.jumpTicks = 0;
            this.restoreJumpKey();
            return;
        }
        this.jumpKeyForced = true;
        mc.options.keyJump.setDown(true);
        --this.jumpTicks;
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent receivePacketEvent) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        Packet<ClientGamePacketListener> packet = receivePacketEvent.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket) {
            this.positionCorrection = true;
            return;
        }

        if (this.shouldNotEngage()) {
            return;
        }

        boolean delayEnabled = Velocity.INSTANCE.legitDelayEnable.getValue();

        if (packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.getId() == mc.player.getId()) {
            if (!delayEnabled) {
                if (counterRemaining <= 0) {
                    counterRemaining = Velocity.INSTANCE.attackAmount.getValue().intValue();
                    this.notifyCounterActive();
                }
                return;
            }
            if (!inDelayWindow) {
                inDelayWindow = true;
                this.delayTicks = 0;
                handlingVelocity = true;
            }
            receivePacketEvent.setCancelled(true);
            this.packetQueue.add(packet);
            return;
        }

        if (!delayEnabled || !inDelayWindow) {
            return;
        }
        if (this.isAllowedPacket(packet)) {
            return;
        }

        if (packet instanceof ClientboundMoveEntityPacket move) {
            Entity entity = move.getEntity(mc.level);
            if (entity != null) {
                Vec3 base = this.entityPositions.getOrDefault(entity,
                        new Vec3(entity.getX(), entity.getY(), entity.getZ()));
                if (move.hasPosition()) {
                    this.entityPositions.put(entity, base.add(
                            move.getXa() / 4096.0,
                            move.getYa() / 4096.0,
                            move.getZa() / 4096.0
                    ));
                }
            }
        }
        if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            Entity entity = mc.level.getEntity(teleport.getId());
            if (entity != null) {
                this.entityPositions.put(entity,
                        new Vec3(teleport.getX(), teleport.getY(), teleport.getZ()));
            }
        }

        receivePacketEvent.setCancelled(true);
        this.packetQueue.add(packet);
    }

    @Override
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        // Need KillAura 开启时使用 KillAura 的锁定目标，关闭时使用准星目标
        this.target = Velocity.INSTANCE.isReduceModeNeedKillAura.getValue()
                ? KillAura.target
                : this.crosshairTarget();

        if (this.compensateUntilMs != -1L
                && (System.currentTimeMillis() >= this.compensateUntilMs || mc.options.keyUp.isDown())) {
            compensating = false;
            this.compensateUntilMs = -1L;
        }

        if (this.shouldNotEngage() || this.noAimTicks >= 3) {
            this.resetAll();
            return;
        }

        boolean delayEnabled = Velocity.INSTANCE.legitDelayEnable.getValue();

        if (this.inDelayWindow && !delayEnabled) {
            inDelayWindow = false;
            handlingVelocity = false;
            this.releaseQueue();
        }

        if (delayEnabled && inDelayWindow) {
            compensating = true;
            counterRemaining = 0;
            if (this.delayTicks >= Velocity.INSTANCE.maxDelayTicks.getValue().intValue()) {
                this.resetAll();
                return;
            }
            ++this.delayTicks;
            if (this.isAimingAtTarget() && mc.player.isSprinting() && mc.player.onGround()) {
                counterRemaining = Velocity.INSTANCE.attackAmount.getValue().intValue();
                this.noAimTicks = 0;
                inDelayWindow = false;
                handlingVelocity = false;
                this.flushQueue();
                this.notifyCounterActive();
                if (Velocity.INSTANCE.jumpReset.getValue()) {
                    this.jumpTicks = 1;
                }
            }
        }

        if (counterRemaining > 0) {
            if (!this.isAimingAtTarget()) {
                ++this.noAimTicks;
                return;
            }
            if (!mc.player.isSprinting()) {
                ++this.noSprintTicks;
                if (this.noSprintTicks >= 3) {
                    this.resetAll();
                }
                return;
            }
            this.noSprintTicks = 0;
            this.attackReduce(this.target);
            --counterRemaining;
        } else if (compensating) {
            this.compensateUntilMs = System.currentTimeMillis() + 10L;
        }
    }

    @Override
    public void onStrafe(StrafeEvent strafeEvent) {
        if (mc.player == null) {
            return;
        }
        if (compensating
                && (!inDelayWindow || KeepSprint.shouldKeepSprintInDelayWindow())
                && Velocity.INSTANCE.autoForwards.getValue()) {
            strafeEvent.setForward(1.0f);
        }
    }

    @Override
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        this.resetAll();
    }

    @Override
    public void reset() {
    }

    // =================================================================
    // === 渲染：带文字进度面板 =======================================
    // =================================================================
    @Override
    public void onRender2D(Render2DEvent event) {
        if (!Velocity.INSTANCE.renderBar.getValue() || !Velocity.INSTANCE.isEnabled()) {
            return;
        }
        if (mc.font == null || mc.getWindow() == null) {
            return;
        }

        boolean countering = this.counterRemaining > 0;
        float maxTicks  = Math.max(1.0f, Velocity.INSTANCE.maxDelayTicks.getValue().intValue());
        float maxAttack = Math.max(1.0f, Velocity.INSTANCE.attackAmount.getValue().intValue());

        // 进度目标：延迟阶段 = delayTicks / maxTicks，计数阶段 = (maxAttack - 剩余) / maxAttack
        float target;
        if (this.inDelayWindow) {
            target = Math.min(1.0f, this.delayTicks / maxTicks);
        } else if (countering) {
            target = Math.min(1.0f, (maxAttack - this.counterRemaining) / maxAttack);
        } else {
            target = 0.0f;
        }

        float targetAlpha = (this.inDelayWindow || countering || this.barProgress > 0.01f) ? 1.0f : 0.0f;

        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L
                ? 0.016666668f
                : Math.min((now - this.lastFrameNanos) / 1.0E9f, 0.1f);
        this.lastFrameNanos = now;

        if (target >= 1.0f) {
            this.barProgress = 1.0f;
        } else {
            float speed = target > this.barProgress ? 9.75f : 21.4f;
            this.barProgress += (target - this.barProgress) * (1.0f - (float) Math.exp(-speed * dt));
        }
        this.barAlpha += (targetAlpha - this.barAlpha) * (1.0f - (float) Math.exp(-9.75f * dt));
        if (Math.abs(target - this.barProgress) < 0.001f) {
            this.barProgress = target;
        }
        if (this.barAlpha <= 0.01f) {
            return;
        }

        int width  = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // 面板几何
        float panelWidth  = 210.0f;
        float panelHeight = 46.0f;
        float panelX      = width / 2.0f - panelWidth / 2.0f;
        float panelY      = height / 2.0f + height * 0.10f;

        PoseStack poseStack = event.poseStack();

        // 外发光
        int glowColor = new Color(0, 180, 255, (int) (55 * this.barAlpha)).getRGB();
        RenderUtil.drawFilledRect(poseStack,
                panelX - 2.0f, panelY - 2.0f,
                panelWidth + 4.0f, panelHeight + 4.0f,
                glowColor);

        // 面板底
        int bgColor = new Color(14, 14, 22, (int) (225 * this.barAlpha)).getRGB();
        RenderUtil.drawFilledRect(poseStack, panelX, panelY, panelWidth, panelHeight, bgColor);

        // 状态文字
        String statusText;
        int statusColor;
        if (this.inDelayWindow) {
            statusText  = "DELAYING  " + this.delayTicks + " / " + (int) maxTicks;
            statusColor = new Color(120, 200, 255, (int) (255 * this.barAlpha)).getRGB();
        } else if (countering) {
            int done = (int) (maxAttack - this.counterRemaining);
            statusText  = "COUNTERING  " + done + " / " + (int) maxAttack;
            statusColor = new Color(255, 130, 200, (int) (255 * this.barAlpha)).getRGB();
        } else {
            statusText  = "READY";
            statusColor = new Color(180, 180, 180, (int) (255 * this.barAlpha)).getRGB();
        }
        mc.font.hashCode();

        // 信息行：Range / Reach
        float rangeVal = (float) Math.max(3.0, RotationUtil.getReachLimit());
        float reachVal = (float) RotationUtil.getReachLimit();
        String infoText = String.format("Range: %.1f   Reach: %.1f", rangeVal, reachVal);
        int infoColor = new Color(150, 200, 230, (int) (220 * this.barAlpha)).getRGB();
        mc.font.hashCode();

        // 进度条底槽
        float barX      = panelX + 10.0f;
        float barY      = panelY + panelHeight - 10.0f;
        float barWidth  = panelWidth - 20.0f;
        float barHeight = 4.0f;

        RenderUtil.drawFilledRect(poseStack, barX, barY, barWidth, barHeight,
                new Color(28, 28, 36, (int) (200 * this.barAlpha)).getRGB());

        // 进度填充（延迟蓝 / 计数粉）
        int fillColor = this.inDelayWindow
                ? new Color(0, 180, 255, (int) (240 * this.barAlpha)).getRGB()
                : new Color(255, 100, 180, (int) (240 * this.barAlpha)).getRGB();
        RenderUtil.drawFilledRect(poseStack, barX, barY,
                barWidth * this.barProgress, barHeight, fillColor);

        // 高亮头
        float headX = barX + barWidth * this.barProgress;
        RenderUtil.drawFilledRect(poseStack,
                headX - 1.0f, barY - 1.5f,
                2.0f, barHeight + 3.0f,
                new Color(255, 255, 255, (int) (235 * this.barAlpha)).getRGB());
    }

    @Override
    public void onRender(RenderEvent renderEvent) {
        if (!Velocity.INSTANCE.targetEsp.getValue() || this.target == null || !inDelayWindow) {
            return;
        }
        if (mc.gameRenderer == null) {
            return;
        }
        Vec3 position = this.entityPositions.get(this.target);
        if (position == null) {
            return;
        }
        double halfWidth = this.target.getBbWidth() / 2.0;
        double height = this.target.getBbHeight();
        PoseStack poseStack = renderEvent.poseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
        AABB box = new AABB(-halfWidth, 0.0, -halfWidth, halfWidth, height, halfWidth);
        Color fill = new Color(255, 255, 255, 64);
        RenderUtil.drawFilledColoredBox(box, poseStack, fill, fill);
        Color outline = new Color(255, 255, 255, 204);
        RenderUtil.drawColoredBox(box, poseStack, outline, outline);
        poseStack.popPose();
    }

    private void attackReduce(Entity entity) {
        if (entity == null || mc.player == null || mc.getConnection() == null) {
            return;
        }
        if (!FightManager.attackAndLock()) {
            return;
        }
        if (NiloreClient.isReady()) {
            NiloreClient.getInstance().getEventBus().call(new EntityRemoveEvent(false, entity));
        }
        mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(entity, mc.player.isShiftKeyDown()));
        mc.player.swing(InteractionHand.MAIN_HAND);
        Vec3 movement = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(movement.x * 0.6, movement.y, movement.z * 0.6);
    }

    private Entity crosshairTarget() {
        return mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

    private boolean isAimingAtTarget() {
        if (this.target == null || mc.player == null) {
            return false;
        }
        if (Velocity.INSTANCE.isReduceModeNeedKillAura.getValue()) {
            // Need KillAura 开启时，目标由 KillAura 提供，用准星验证
            return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == this.target;
        }
        // Need KillAura 关闭时，直接检查视线角度
        return RotationUtil.isLookingAt(this.target,
                Math.max(3.0, RotationUtil.getReachLimit()), 0.1f);
    }

    private boolean shouldNotEngage() {
        if (mc.player == null || mc.getConnection() == null) {
            return true;
        }
        if (mc.player.isUsingItem() || mc.player.isInLava() || mc.player.isInWater()) {
            return true;
        }
        if (mc.level != null && mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB)) {
            return true;
        }
        if (this.positionCorrection) {
            return true;
        }
        if (mc.screen instanceof InventoryScreen) {
            return true;
        }
        if (this.target == null || !this.target.isAlive()) {
            return true;
        }
        if (mc.player.isDeadOrDying() || mc.player.isSpectator()) {
            return true;
        }
        if (NoSlow.isHandling()) {
            return true;
        }
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            return true;
        }
        Stuck stuck = Stuck.INSTANCE;
        return stuck != null && stuck.isEnabled();
    }

    private boolean isAllowedPacket(Packet<?> packet) {
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
                || packet instanceof ClientboundAnimatePacket animation
                && animation.getId() != mc.player.getId();
    }

    private void flushQueue() {
        mc.execute(() -> {
            if (mc.getConnection() == null) {
                this.packetQueue.clear();
                return;
            }
            Packet<ClientGamePacketListener> packet;
            while ((packet = this.packetQueue.poll()) != null) {
                try {
                    packet.handle(mc.getConnection());
                } catch (Exception exception) {
                    this.packetQueue.clear();
                    break;
                }
            }
        });
    }

    private void releaseQueue() {
        mc.execute(() -> {
            if (mc.getConnection() == null) {
                this.packetQueue.clear();
                return;
            }
            Packet<ClientGamePacketListener> packet;
            while ((packet = this.packetQueue.poll()) != null) {
                try {
                    packet.handle(mc.getConnection());
                } catch (Exception exception) {
                    this.packetQueue.clear();
                    break;
                }
            }
        });
    }

    private void restoreJumpKey() {
        if (!this.jumpKeyForced) {
            return;
        }
        this.jumpKeyForced = false;
        if (mc.player == null || Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            return;
        }
        boolean down = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
        mc.options.keyJump.setDown(down);
    }

    private void resetAll() {
        this.flushQueue();
        inDelayWindow = false;
        compensating = false;
        this.positionCorrection = false;
        this.delayTicks = 0;
        counterRemaining = 0;
        this.noAimTicks = 0;
        this.noSprintTicks = 0;
        this.compensateUntilMs = -1L;
        this.jumpTicks = 0;
        this.restoreJumpKey();
        this.entityPositions.clear();
        this.target = null;
        handlingVelocity = false;
    }

    private String targetName() {
        if (this.target == null) return "null";
        String name = this.target.getName().getString();
        if (name == null || name.isEmpty()) name = this.target.getType().toString();
        return name;
    }

    /**
     * 唯一输出点：只在"计数激活"那一刻调用（counterRemaining 刚被设置时）。
     * 一次启用周期内只允许成功输出一次；后续调用直接返回。
     * 由 Velocity 面板的 "Chat Legit" 开关控制。
     *
     * 消息格式：target | hits | range | reach
     */
    private void notifyCounterActive() {
        if (this.counterMessageSent) return;
        if (Velocity.INSTANCE == null) return;
        if (!Velocity.INSTANCE.chatLegit.getValue()) return;
        this.counterMessageSent = true;

        float rangeVal = (float) Math.max(3.0, RotationUtil.getReachLimit());
        float reachVal = (float) RotationUtil.getReachLimit();
        String message = String.format(
                "§c[Velocity] §4[Legit] §fCountering §7| target=%s §7| hits=%d §7| range=%.1f §7| reach=%.1f",
                targetName(), this.counterRemaining, rangeVal, reachVal);
        ChatUtil.print(message);
    }

    static {
        handlingVelocity = false;
        compensating = false;
        inDelayWindow = false;
        counterRemaining = 0;
    }
}