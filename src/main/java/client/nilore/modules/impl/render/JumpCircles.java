package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.NumberSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class JumpCircles extends Module {

    private final Queue<JumpCircle> circles = new ConcurrentLinkedQueue<>();

    private final NumberSetting maxRadius =
            new NumberSetting("Max Radius", 2.0, 0.5, 4.0, 0.1);

    private final NumberSetting growSpeed =
            new NumberSetting("Grow Speed", 0.06, 0.01, 0.20, 0.01);

    private final NumberSetting lineWidth =
            new NumberSetting("Line Width", 2.0, 1.0, 5.0, 0.5);

    private boolean wasAirborne;

    public JumpCircles() {
        super("JumpCircles", Category.RENDER);
    }

    @Override
    public String getDisplayName() {
        return "JumpCircles";
    }

    @Override
    public String getModuleName() {
        return "JumpCircles";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onEnable() {
        circles.clear();
        wasAirborne = false;
    }

    @Override
    protected void onDisable() {
        circles.clear();
        wasAirborne = false;
    }

    @EventTarget
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) {
            circles.clear();
            wasAirborne = false;
            return;
        }

        if (!mc.player.onGround()) {
            wasAirborne = true;
        } else if (wasAirborne) {
            circles.add(
                    new JumpCircle(
                            new Vec3(
                                    mc.player.getX(),
                                    mc.player.getY() + 0.02,
                                    mc.player.getZ()
                            )
                    )
            );

            wasAirborne = false;
        }
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null || circles.isEmpty()) {
            return;
        }

        float frame = Math.max(0.25F, mc.getFrameTime());

        double radiusStep =
                growSpeed.getValue().doubleValue() * frame;

        double radiusLimit =
                maxRadius.getValue().doubleValue();

        Iterator<JumpCircle> iterator = circles.iterator();

        while (iterator.hasNext()) {
            JumpCircle circle = iterator.next();

            circle.radius += radiusStep;

            circle.alpha = (float) Math.max(
                    0.0,
                    1.0 - circle.radius / radiusLimit
            );

            if (circle.radius >= radiusLimit || circle.alpha <= 0.01F) {
                iterator.remove();
                continue;
            }

            drawCircle(event.poseStack(), circle);
        }
    }

    private void drawCircle(PoseStack poseStack, JumpCircle circle) {
        Vec3 camera =
                mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();

        poseStack.translate(
                circle.position.x - camera.x,
                circle.position.y - camera.y,
                circle.position.z - camera.z
        );

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.disableCull();

        RenderSystem.setShader(
                GameRenderer::getPositionColorShader
        );

        RenderSystem.lineWidth(
                lineWidth.getValue().floatValue()
        );

        Matrix4f matrix =
                poseStack.last().pose();

        BufferBuilder buffer =
                Tesselator.getInstance().getBuilder();

        buffer.begin(
                VertexFormat.Mode.DEBUG_LINE_STRIP,
                DefaultVertexFormat.POSITION_COLOR
        );

        final int segments = 120;

        for (int i = 0; i <= segments; i++) {
            double angle =
                    Math.PI * 2.0 * i / segments;

            float x =
                    (float) (Math.cos(angle) * circle.radius);

            float z =
                    (float) (Math.sin(angle) * circle.radius);

            int rgb =
                    Color.HSBtoRGB(
                            (float) i / segments,
                            0.75F,
                            1.0F
                    );

            int r = rgb >> 16 & 255;
            int g = rgb >> 8 & 255;
            int b = rgb & 255;

            buffer.vertex(
                            matrix,
                            x,
                            0.0F,
                            z
                    )
                    .color(
                            r,
                            g,
                            b,
                            (int) (circle.alpha * 255.0F)
                    )
                    .endVertex();
        }

        BufferUploader.drawWithShader(
                buffer.end()
        );

        RenderSystem.lineWidth(1.0F);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    private static final class JumpCircle {

        private final Vec3 position;

        private double radius;

        private float alpha = 1.0F;

        private JumpCircle(Vec3 position) {
            this.position = position;
        }
    }
}