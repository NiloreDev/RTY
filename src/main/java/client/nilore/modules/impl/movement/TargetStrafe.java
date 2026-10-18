package client.nilore.modules.impl.movement;

import java.util.ArrayList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.SneakEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.game.MovementUtil;
import client.nilore.event.EventTarget;
import com.mojang.blaze3d.vertex.PoseStack;

public class TargetStrafe extends Module {
    public static TargetStrafe INSTANCE;
    private final Timer collisionTimer = new Timer();
    private final BooleanSetting smartStrafe = new BooleanSetting("Jump Key Only", true);

    // 物理环绕距离
    private final NumberSetting range = new NumberSetting("Range", 0.5f, 0.1f, 6.0f, 0.1f);
    private final NumberSetting switchDelay = new NumberSetting("Switch Delay", 1000, 100, 5000, 100);

    // === 视觉渲染设置 ===
    private final BooleanSetting drawCircle = new BooleanSetting("Draw Circle", true);
    private final BooleanSetting drawPolygon = new BooleanSetting("Draw Polygon", false);
    private final NumberSetting polygonSides = new NumberSetting("Polygon Sides", 8, 3, 16, 1, this.drawPolygon::getValue);
    // 【新增】独立控制视觉圈的大小
    private final NumberSetting renderRadius = new NumberSetting("Render Radius", 1.0f, 0.5f, 10.0f, 0.1f, () -> this.drawCircle.getValue() || this.drawPolygon.getValue());

    public static int strafeDirectionSign;
    public static Entity strafeTarget;
    private final Timer switchTimer = new Timer();

    public TargetStrafe() {
        super("TargetStrafe", Category.MOVEMENT);
        INSTANCE = this;
        this.registerSetting(smartStrafe, range, switchDelay, drawCircle, drawPolygon, polygonSides, renderRadius);
    }

    public static float getRange() {
        return TargetStrafe.INSTANCE.range.getValue().floatValue();
    }

    public static boolean isSmartStrafe() {
        return TargetStrafe.INSTANCE.smartStrafe.getValue();
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (motionEvent.isPost() && mc.player != null) {
            boolean aboveVoid;
            AABB playerBox;
            if (KillAura.target == null) {
                strafeTarget = null;
            } else if (this.switchTimer.hasPassed(this.switchDelay.getValue().intValue()) || strafeTarget == null) {
                ArrayList<Entity> sortedTargets = new ArrayList<>(KillAura.targetList);
                sortedTargets.sort((a, b) -> {
                    float distA = mc.player.distanceTo(a);
                    float distB = mc.player.distanceTo(b);
                    return Float.compare(distA, distB);
                });
                if (!sortedTargets.isEmpty()) {
                    strafeTarget = sortedTargets.get(0);
                    this.switchTimer.reset();
                }
            }
            playerBox = mc.player.getBoundingBox();
            aboveVoid = MovementUtil.isAboveVoid(playerBox.minX, playerBox.minY, playerBox.minZ) || MovementUtil.isAboveVoid(playerBox.minX, playerBox.minY, playerBox.maxZ) || MovementUtil.isAboveVoid(playerBox.maxX, playerBox.minY, playerBox.minZ) || MovementUtil.isAboveVoid(playerBox.maxX, playerBox.minY, playerBox.maxZ);
            if ((aboveVoid || mc.player.horizontalCollision) && this.collisionTimer.hasPassedDouble(500.0, true)) {
                strafeDirectionSign *= -1;
            }
        }
    }

    @EventTarget
    public void onSneak(SneakEvent sneakEvent) {
        if (!sneakEvent.isCancelled() && !FireballBlink.INSTANCE.isEnabled()) {
            sneakEvent.setCancelled(true);
        }
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null || strafeTarget == null || !strafeTarget.isAlive()) {
            return;
        }

        if (!this.drawCircle.getValue() && !this.drawPolygon.getValue()) {
            return;
        }

        PoseStack poseStack = event.poseStack();
        if (poseStack == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // 目标脚下位置，相对相机位置
        double targetX = strafeTarget.getX() - cameraPos.x;
        double targetY = strafeTarget.getY() + 0.02 - cameraPos.y; // 稍微抬高一点防止和地面重叠闪烁
        double targetZ = strafeTarget.getZ() - cameraPos.z;

        // 【修改】使用单独的渲染半径设置
        float radius = this.renderRadius.getValue().floatValue();

        poseStack.pushPose();

        // 开启透明和忽略深度（穿透方块显示）
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(2.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        // 白色 (255, 255, 255, 255)
        int r = 255;
        int g = 255;
        int b = 255;
        int a = 255;

        // 绘制圆形
        if (this.drawCircle.getValue()) {
            int segments = 64;
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= segments; i++) {
                double angle = Math.PI * 2 * i / segments;
                float dx = (float) (Math.cos(angle) * radius);
                float dz = (float) (Math.sin(angle) * radius);
                buffer.vertex(matrix, (float) targetX + dx, (float) targetY, (float) targetZ + dz).color(r, g, b, a).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }

        // 绘制多边形（如八边形）
        if (this.drawPolygon.getValue()) {
            int sides = this.polygonSides.getValue().intValue();
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= sides; i++) {
                // 减去 Math.PI/2 让多边形的一个角朝向正前方
                double angle = Math.PI * 2 * i / sides - Math.PI / 2;
                float dx = (float) (Math.cos(angle) * radius);
                float dz = (float) (Math.sin(angle) * radius);
                buffer.vertex(matrix, (float) targetX + dx, (float) targetY, (float) targetZ + dz).color(r, g, b, a).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }

        // 恢复渲染状态
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);

        poseStack.popPose();
    }

    static {
        strafeDirectionSign = 1;
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    @Override
    public void onTick() {

    }
}