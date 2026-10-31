package client.nilore.modules.impl.combat.antikb;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
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

import org.lwjgl.glfw.GLFW;

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
import client.nilore.modules.impl.combat.Velocity;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.movement.NoSlow;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.modules.impl.movement.Stuck;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.render.RenderUtil;

public class ReduceXZMode extends AntiKBMode {
    public static ReduceXZMode INSTANCE;
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
    private long compensateUntilMs = -1L;
    private Entity target;

    // =================================================================
    // === HUD 渲染相关变量 ============================================
    // =================================================================
    private float hudX = 100.0f;
    private float hudY = 100.0f;
    private boolean dragging = false;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private float startHudX = 0;
    private float startHudY = 0;

    // 字号加大到 18
    private final FontRenderer hudFont = FontPresets.axiformaBold(18);
    private static final int HUD_TEXT_COLOR = 0xFFFFFFFF; // 统一纯白

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
        ReduceXZMode mode = INSTANCE;
        if (mode == null || Velocity.INSTANCE == null || !Velocity.INSTANCE.isEnabled()) {
            return null;
        }
        if (!inDelayWindow && counterRemaining <= 0) {
            return null;
        }
        return mode.target != null && mode.target.isAlive() ? mode.target : null;
    }

    public ReduceXZMode() {
        super("ReduceXZ");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.resetAll();
    }

    @Override
    public void onDisable() {
        this.resetAll();
    }

    @Override
    public String getName() {
        return "ReduceXZ";
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

    private boolean canJumpNow(LocalPlayer player) {
        return player.onGround()
                && !player.isInWater()
                && !player.isInLava()
                && !player.onClimbable()
                && !player.isPassenger();
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
        if (!canJumpNow(mc.player)) {
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

        boolean delayEnabled = Velocity.INSTANCE.delayEnable.getValue();

        if (packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.getId() == mc.player.getId()) {
            if (!delayEnabled) {
                // 延迟关闭：纯计数，不拦截、不延迟，空中/地面都生效
                if (counterRemaining <= 0) {
                    counterRemaining = Velocity.INSTANCE.attackAmount.getValue().intValue();
                    if (Velocity.INSTANCE.jumpReset.getValue()) {
                        this.jumpTicks = 1;
                    }
                    // 触发纯计数模式的显示
                    this.debugCounter();
                }
                return;
            }
            // 延迟开启：进入延迟窗口
            if (!inDelayWindow) {
                inDelayWindow = true;
                this.delayTicks = 0;
                handlingVelocity = true;
            }
            receivePacketEvent.setCancelled(true);
            this.packetQueue.add(packet);
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

        this.target = Velocity.INSTANCE.requireKillAura.getValue()
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

        boolean delayEnabled = Velocity.INSTANCE.delayEnable.getValue();

        if (inDelayWindow) {
            compensating = true;
            counterRemaining = 0;
            if (this.delayTicks >= Velocity.INSTANCE.maxDelayTicks.getValue().intValue()) {
                this.resetAll();
                return;
            }
            ++this.delayTicks;
            // 延迟释放条件：必须看目标 + 疾跑 + 落地
            if (this.isAimingAtTarget() && mc.player.isSprinting() && mc.player.onGround()) {
                counterRemaining = Velocity.INSTANCE.attackAmount.getValue().intValue();
                this.noAimTicks = 0;
                inDelayWindow = false;
                handlingVelocity = false;
                this.flushQueue();
                if (Velocity.INSTANCE.jumpReset.getValue()) {
                    this.jumpTicks = 1;
                }
                // 触发延迟释放后的计数显示
                this.debugCounter();
            }
        }

        if (counterRemaining > 0) {
            if (delayEnabled) {
                // Delay 开启时：计数阶段要求严格的 视线 和 疾跑 条件
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
            } else {
                // Delay 关闭时（纯计数）：空中被击退无法保持疾跑和视线，直接发起攻击
                if (this.target == null || !this.target.isAlive()) {
                    --counterRemaining;
                    if (counterRemaining <= 0) {
                        this.resetAll();
                    }
                    return;
                }
            }

            this.noAimTicks = 0;
            this.noSprintTicks = 0;
            this.attackReduce(this.target);
            --counterRemaining;

            if (counterRemaining <= 0) {
                this.resetAll();
            }
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
                && (!inDelayWindow || KillAura.shouldKeepSprintInDelayWindow())
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
    // === HUD 文字渲染 & 拖动逻辑 =====================================
    // =================================================================
    @Override
    public void onRender2D(Render2DEvent event) {
        if (!Velocity.INSTANCE.alinkReader.getValue() || !Velocity.INSTANCE.isEnabled()) {
            return;
        }

        // 如果没开启 Keep Showing，且不在延迟窗口内，就不显示
        if (!Velocity.INSTANCE.keepShowing.getValue() && !inDelayWindow) {
            return;
        }

        String text;
        if (inDelayWindow) {
            int max = Velocity.INSTANCE.maxDelayTicks.getValue().intValue();
            text = String.format("ReduceXZ Delay %d/%d", this.delayTicks, max);
        } else {
            text = "ReduceXZ Ready";
        }

        float textWidth = hudFont.getWidth(text);
        float paddingX = 6.0f;
        float paddingY = 4.0f;
        float width = textWidth + paddingX * 2.0f;
        float height = hudFont.getMetrics().capHeight() + paddingY * 2.0f;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        if (Float.isNaN(hudX) || Float.isNaN(hudY) || hudX < 0.0f || hudX > screenW || hudY < 0.0f || hudY > screenH) {
            hudX = (screenW - width) / 2.0f;
            hudY = screenH / 2.0f + 50.0f;
        }

        // --- 拖动逻辑 ---
        if (mc.screen instanceof ChatScreen) {
            long window = mc.getWindow().getWindow();
            double[] rawX = new double[1];
            double[] rawY = new double[1];
            GLFW.glfwGetCursorPos(window, rawX, rawY);

            double mouseX = rawX[0] * screenW / Math.max(1.0, mc.getWindow().getScreenWidth());
            double mouseY = rawY[0] * screenH / Math.max(1.0, mc.getWindow().getScreenHeight());
            boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            boolean hovered = mouseX >= hudX && mouseX <= hudX + width && mouseY >= hudY && mouseY <= hudY + height;

            if (leftDown && !this.dragging && hovered) {
                this.dragging = true;
                this.dragStartX = mouseX;
                this.dragStartY = mouseY;
                this.startHudX = hudX;
                this.startHudY = hudY;
            }

            if (this.dragging) {
                if (!leftDown) {
                    this.dragging = false;
                } else {
                    hudX = this.startHudX + (float) (mouseX - this.dragStartX);
                    hudY = this.startHudY + (float) (mouseY - this.dragStartY);
                }
            }
        } else {
            this.dragging = false;
        }

        final float finalX = hudX;
        final float finalY = hudY;
        final String finalText = text;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Renderer.render(event.guiGraphics(), drawContext -> {
            try (Paint paint = new Paint()) {
                drawContext.save();

                // 纯白文字
                paint.setColor(HUD_TEXT_COLOR);
                float textY = finalY + (height - hudFont.getMetrics().capHeight()) / 2.0f + 1.0f;
                drawContext.drawString(finalText, finalX + paddingX, textY, hudFont, paint);

                drawContext.restore();
            }
        });

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
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
        if (!KillAura.attackAndLock()) {
            return;
        }
        if (NiloreClient.isReady()) {
            NiloreClient.getInstance().getEventBus().call(new EntityRemoveEvent(false, entity));
        }
        mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(entity, mc.player.isShiftKeyDown()));
        mc.player.swing(InteractionHand.MAIN_HAND);

        // 恢复为固定的 0.6 减速比例
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
        if (!Velocity.INSTANCE.requireKillAura.getValue()) {
            return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == this.target;
        }
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

    /**
     * 计数激活时的汇总消息。
     * 仅当 Jump Reset 开启（生效）时才输出；
     * 地面显示 [地面] (绿色)，空中显示 [空中] (红色)，后接 Count。
     */
    private void debugCounter() {
        if (Velocity.INSTANCE == null || mc.player == null) return;

        // Jump Reset 未开启时，不输出任何消息
        if (!Velocity.INSTANCE.jumpReset.getValue()) return;
        // Chat Alert 开启时才输出
        if (!Velocity.INSTANCE.chatReduceXZ.getValue()) return;

        String location = mc.player.onGround() ? "§a[地面] " : "§c[空中] ";
        String count = "§fCount: " + counterRemaining + "/" + counterRemaining;

        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Velocity] §4[ReduceXZ] " + location + count), false);
    }

    static {
        handlingVelocity = false;
        compensating = false;
        inDelayWindow = false;
        counterRemaining = 0;
    }
}