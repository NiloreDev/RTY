package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;

public class Wings extends Module {

    public static Wings INSTANCE;

    public final ModeSetting mode = new ModeSetting("Mode", "天使",
            "天使", "恶魔", "蝴蝶", "龙翼", "光翼", "冰晶", "火焰", "机械"
    ).withDefault("天使");

    public final NumberSetting size = new NumberSetting("Size", 1.2, 0.5, 3.0, 0.05);
    public final NumberSetting flapSpeed = new NumberSetting("Flap Speed", 1.0, 0.2, 3.0, 0.1);
    public final NumberSetting flapAmount = new NumberSetting("Flap Amount", 1.0, 0.0, 2.0, 0.05);

    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);
    public final BooleanSetting followPitch = new BooleanSetting("Follow Pitch", true);

    public Wings() {
        super("Wings", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public String getModuleName() {
        return "Wings";
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getDisplayName() {
        return "§bWings";
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) return;

        PoseStack poseStack = event.poseStack();
        if (poseStack == null) return;
        float partialTick = event.partialTick();

        double px = Mth.lerp(partialTick, mc.player.xOld, mc.player.getX());
        double py = Mth.lerp(partialTick, mc.player.yOld, mc.player.getY());
        double pz = Mth.lerp(partialTick, mc.player.zOld, mc.player.getZ());

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();

        float yaw = Mth.lerp(partialTick, mc.player.yRotO, mc.player.getYRot());
        float pitch = Mth.lerp(partialTick, mc.player.xRotO, mc.player.getXRot());

        float eyeHeight = mc.player.getEyeHeight();
        double shoulderY = py + eyeHeight * 0.88;

        poseStack.pushPose();
        poseStack.translate(px - cam.x, shoulderY - cam.y, pz - cam.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        if (followPitch.getValue()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch * 0.5f));
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (throughWalls.getValue()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        try {
            switch (mode.getValue()) {
                case "天使" -> renderAngel(poseStack);
                case "恶魔" -> renderDemon(poseStack);
                case "蝴蝶" -> renderButterfly(poseStack);
                case "龙翼" -> renderDragon(poseStack);
                case "光翼" -> renderLight(poseStack);
                case "冰晶" -> renderIce(poseStack);
                case "火焰" -> renderFlame(poseStack);
                case "机械" -> renderMech(poseStack);
            }
        } finally {
            RenderSystem.enableCull();
            if (throughWalls.getValue()) {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    // 获取统一的时间（秒），确保动作自然不抽搐
    private float getTime() {
        return (System.currentTimeMillis() % 1000000L) / 1000.0f;
    }

    // ==========================================================
    // === 1. 天使 (多层羽毛、根部窄尖端宽、波浪递进) ===========
    // ==========================================================
    private void renderAngel(PoseStack poseStack) {
        float t = getTime();
        float s = size.getValue().floatValue();
        float spd = flapSpeed.getValue().floatValue();
        float amt = flapAmount.getValue().floatValue();

        // 基础挥动 + 呼吸效果
        float baseFlap = Mth.sin(t * spd * 2.5f) * amt * 25f;
        float breathe = Mth.sin(t * 1.5f) * 2f;

        int feathers = 18;
        float maxLen = 1.6f * s;
        float backZ = -0.1f * s;
        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < feathers; i++) {
                float fi = i / (float) (feathers - 1);

                // 每一根羽毛有轻微的延迟，形成波浪感
                float wave = Mth.sin(t * spd * 3f + i * 0.4f) * amt * 8f;
                float angleRad = (float) Math.toRadians(-30f + fi * 100f + baseFlap + wave + breathe);

                float len = maxLen * (0.4f + 0.6f * Mth.sin(fi * Mth.PI));

                float rootX = side * 0.05f * s;
                float rootY = -0.2f * s + fi * 0.25f * s;

                // 羽毛根部宽度小，中段宽，尖端尖 (模拟真实羽毛)
                float w = (0.05f + fi * 0.1f) * s;
                float perpX = -Mth.sin(angleRad) * w;
                float perpY = Mth.cos(angleRad) * w;

                float tipX = rootX + side * Mth.cos(angleRad) * len;
                float tipY = rootY + Mth.sin(angleRad) * len;
                float tipZ = backZ - (0.15f + fi * 0.1f) * s;

                // 颜色渐变：根部纯白，尖端带一点微金
                int cr = 255, cg = 255, cb = 255;
                int tr = 255, tg = 230, tb = 200;

                // 底部 (根部)
                buffer.vertex(mat, rootX + perpX, rootY + perpY, backZ).color(cr, cg, cb, 240).endVertex();
                buffer.vertex(mat, rootX - perpX, rootY - perpY, backZ).color(cr, cg, cb, 240).endVertex();
                // 顶部 (尖端)
                buffer.vertex(mat, tipX, tipY, tipZ).color(tr, tg, tb, 100).endVertex();
                buffer.vertex(mat, tipX, tipY, tipZ).color(tr, tg, tb, 100).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // ==========================================================
    // === 2. 恶魔 (骨架联动、膜面凹陷、黑红渐变) ===============
    // ==========================================================
    private void renderDemon(PoseStack poseStack) {
        float t = getTime();
        float s = size.getValue().floatValue();
        float spd = flapSpeed.getValue().floatValue();
        float amt = flapAmount.getValue().floatValue();

        float baseFlap = Mth.sin(t * spd * 2.0f) * amt * 30f;
        float backZ = -0.1f * s;
        int bones = 4;
        float maxLen = 1.6f * s;

        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        float[] angles = new float[bones];
        float[] lengths = new float[bones];
        for (int i = 0; i < bones; i++) {
            float fi = i / (float) (bones - 1);
            float wave = Mth.sin(t * spd * 3.0f + i) * amt * 10f;
            angles[i] = (float) Math.toRadians(-10f + fi * 80f + baseFlap + wave);
            lengths[i] = maxLen * (0.6f + 0.4f * Mth.sin(fi * Mth.PI));
        }

        // 绘制膜 (半透明暗红 + 边缘亮红)
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < bones - 1; i++) {
                float a1 = angles[i], a2 = angles[i + 1];
                float l1 = lengths[i], l2 = lengths[i + 1];

                float t1x = side * Mth.cos(a1) * l1, t1y = Mth.sin(a1) * l1;
                float t2x = side * Mth.cos(a2) * l2, t2y = Mth.sin(a2) * l2;

                // 膜中间向内凹陷
                float mx = (t1x + t2x) * 0.5f * 0.7f;
                float my = (t1y + t2y) * 0.5f * 0.7f;

                // 填充两个三角形构成凹陷效果
                buffer.vertex(mat, 0, 0, backZ).color(80, 0, 10, 220).endVertex();
                buffer.vertex(mat, t1x, t1y, backZ - 0.1f).color(150, 20, 30, 180).endVertex();
                buffer.vertex(mat, mx, my, backZ + 0.05f).color(40, 0, 0, 200).endVertex();

                buffer.vertex(mat, 0, 0, backZ).color(80, 0, 10, 220).endVertex();
                buffer.vertex(mat, mx, my, backZ + 0.05f).color(40, 0, 0, 200).endVertex();
                buffer.vertex(mat, t2x, t2y, backZ - 0.1f).color(150, 20, 30, 180).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());

        // 绘制骨架
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < bones; i++) {
                float a = angles[i], l = lengths[i];
                float tipX = side * Mth.cos(a) * l, tipY = Mth.sin(a) * l;
                buffer.vertex(mat, 0, 0, backZ).color(30, 0, 0, 255).endVertex();
                buffer.vertex(mat, tipX, tipY, backZ - 0.1f).color(200, 50, 50, 255).endVertex();

                // 尖刺
                buffer.vertex(mat, tipX, tipY, backZ - 0.1f).color(200, 50, 50, 255).endVertex();
                buffer.vertex(mat, tipX + side * 0.1f, tipY + 0.1f, backZ - 0.2f).color(100, 0, 0, 0).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 其余模式优化色彩与动态表现（代码结构相同，直接替换即可）
    private void renderButterfly(PoseStack poseStack) {
        float t = getTime();
        float s = size.getValue().floatValue();
        float spd = flapSpeed.getValue().floatValue();
        float amt = flapAmount.getValue().floatValue();

        float flap = Mth.sin(t * spd * 3f) * amt;
        float backZ = -0.15f * s;
        float hue = (t * 0.1f) % 1.0f;
        Color c1 = Color.getHSBColor(hue, 0.7f, 1.0f);
        Color c2 = Color.getHSBColor((hue + 0.15f) % 1.0f, 0.85f, 0.9f);

        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int side : new int[]{-1, 1}) {
            drawFanWing(buffer, mat, side, 0.15f * s, backZ, 1.1f * s, (float) Math.toRadians(30f) + flap * 0.2f, (float) Math.toRadians(100f + flap * 30f), c1.getRGB(), c2.getRGB(), 180);
            drawFanWing(buffer, mat, side, -0.25f * s, backZ, 0.7f * s, (float) Math.toRadians(-110f) - flap * 0.2f, (float) Math.toRadians(70f + flap * 20f), c2.getRGB(), c1.getRGB(), 160);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void drawFanWing(BufferBuilder buffer, Matrix4f mat, int side, float cy, float cz, float radius, float startAngle, float span, int innerRGB, int outerRGB, int alpha) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            float a1 = startAngle + span * i / segments;
            float a2 = startAngle + span * (i + 1) / segments;
            float x1 = (float) 0 + side * Mth.cos(a1) * radius, y1 = cy + Mth.sin(a1) * radius;
            float x2 = (float) 0 + side * Mth.cos(a2) * radius, y2 = cy + Mth.sin(a2) * radius;

            buffer.vertex(mat, (float) 0, cy, cz).color((innerRGB >> 16) & 0xFF, (innerRGB >> 8) & 0xFF, innerRGB & 0xFF, alpha).endVertex();
            buffer.vertex(mat, x1, y1, cz).color((outerRGB >> 16) & 0xFF, (outerRGB >> 8) & 0xFF, outerRGB & 0xFF, alpha / 2).endVertex();
            buffer.vertex(mat, x2, y2, cz).color((outerRGB >> 16) & 0xFF, (outerRGB >> 8) & 0xFF, outerRGB & 0xFF, alpha / 2).endVertex();
        }
    }

    private void renderDragon(PoseStack poseStack) {
        float t = getTime();
        float s = size.getValue().floatValue();
        float spd = flapSpeed.getValue().floatValue();
        float amt = flapAmount.getValue().floatValue();
        float flap = Mth.sin(t * spd * 2.5f) * amt;
        float backZ = -0.15f * s;
        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        int spines = 5;
        float maxLen = 1.6f * s;
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < spines - 1; i++) {
                float fi = i / (float) (spines - 1);
                float a1 = (float) Math.toRadians(-5f + fi * 80f + flap * 25f);
                float a2 = (float) Math.toRadians(-5f + (fi+0.25f) * 80f + flap * 25f);
                float l1 = maxLen * (0.7f + 0.3f * Mth.sin(fi * Mth.PI));
                float l2 = maxLen * (0.7f + 0.3f * Mth.sin((fi+0.25f) * Mth.PI));

                buffer.vertex(mat, 0, 0, backZ).color(20, 80, 90, 180).endVertex();
                buffer.vertex(mat, side * Mth.cos(a1) * l1, Mth.sin(a1) * l1, backZ - 0.1f).color(40, 120, 140, 150).endVertex();
                buffer.vertex(mat, side * Mth.cos(a2) * l2, Mth.sin(a2) * l2, backZ - 0.1f).color(40, 120, 140, 150).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void renderLight(PoseStack poseStack) {
        float t = getTime();
        float s = size.getValue().floatValue();
        float spd = flapSpeed.getValue().floatValue();
        float amt = flapAmount.getValue().floatValue();
        float flap = Mth.sin(t * spd * 4f) * amt;
        float backZ = -0.15f * s;
        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 16; i++) {
                float fi = i / 15f;
                float angleRad = (float) Math.toRadians(-30f + fi * 95f + flap * 30f + Mth.sin(t * 5f + i) * 5f);
                float len = 1.5f * s * (0.5f + 0.5f * Mth.sin(fi * Mth.PI));
                float rootX = side * 0.03f * s, rootY = -0.15f * s + fi * 0.2f * s;
                float tipX = rootX + side * Mth.cos(angleRad) * len, tipY = rootY + Mth.sin(angleRad) * len;
                float w = 0.04f * s;
                float perpX = -Mth.sin(angleRad) * w, perpY = Mth.cos(angleRad) * w;

                Color c = Color.getHSBColor((fi + t * 0.2f) % 1.0f, 0.75f, 1.0f);
                buffer.vertex(mat, rootX + perpX, rootY + perpY, backZ).color(c.getRed(), c.getGreen(), c.getBlue(), 200).endVertex();
                buffer.vertex(mat, rootX - perpX, rootY - perpY, backZ).color(c.getRed(), c.getGreen(), c.getBlue(), 200).endVertex();
                buffer.vertex(mat, tipX - perpX * 0.2f, tipY - perpY * 0.2f, backZ - 0.1f).color(c.getRed(), c.getGreen(), c.getBlue(), 50).endVertex();
                buffer.vertex(mat, tipX + perpX * 0.2f, tipY + perpY * 0.2f, backZ - 0.1f).color(c.getRed(), c.getGreen(), c.getBlue(), 50).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void renderIce(PoseStack poseStack) {
        float t = getTime();
        float s = size.getValue().floatValue();
        float spd = flapSpeed.getValue().floatValue();
        float amt = flapAmount.getValue().floatValue();
        float flap = Mth.sin(t * spd * 2f) * amt;
        float backZ = -0.15f * s;
        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 8; i++) {
                float fi = i / 7f;
                float angleRad = (float) Math.toRadians(-20f + fi * 85f + flap * 20f);
                float len = 1.4f * s * (0.6f + 0.4f * Mth.sin(fi * Mth.PI));
                float rootX = side * 0.05f * s, rootY = -0.1f * s + fi * 0.2f * s;
                float tipX = rootX + side * Mth.cos(angleRad) * len, tipY = rootY + Mth.sin(angleRad) * len;
                float w = 0.15f * s;
                float perpX = -Mth.sin(angleRad) * w, perpY = Mth.cos(angleRad) * w;

                buffer.vertex(mat, rootX + perpX, rootY + perpY, backZ).color(150, 200, 255, 200).endVertex();
                buffer.vertex(mat, rootX - perpX, rootY - perpY, backZ).color(150, 200, 255, 200).endVertex();
                buffer.vertex(mat, tipX, tipY, backZ - 0.15f).color(255, 255, 255, 100).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void renderFlame(PoseStack poseStack) {
        float t = getTime();
        float s = size.getValue().floatValue();
        float spd = flapSpeed.getValue().floatValue();
        float amt = flapAmount.getValue().floatValue();
        float flap = Mth.sin(t * spd * 3.5f) * amt;
        float backZ = -0.15f * s;
        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 14; i++) {
                float fi = i / 13f;
                float angleRad = (float) Math.toRadians(-15f + fi * 85f + flap * 25f + Mth.sin(t * 10f + i) * 8f);
                float len = 1.5f * s * (0.55f + 0.45f * Mth.sin(fi * Mth.PI));
                float rootX = side * 0.04f * s, rootY = -0.15f * s + fi * 0.22f * s;
                float tipX = rootX + side * Mth.cos(angleRad) * len, tipY = rootY + Mth.sin(angleRad) * len;
                float w = 0.1f * s;
                float perpX = -Mth.sin(angleRad) * w, perpY = Mth.cos(angleRad) * w;

                buffer.vertex(mat, rootX + perpX, rootY + perpY, backZ).color(255, 150, 0, 220).endVertex();
                buffer.vertex(mat, rootX - perpX, rootY - perpY, backZ).color(255, 150, 0, 220).endVertex();
                buffer.vertex(mat, tipX, tipY, backZ - 0.2f).color(255, 20, 0, 50).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void renderMech(PoseStack poseStack) {
        float t = getTime();
        float s = size.getValue().floatValue();
        float spd = flapSpeed.getValue().floatValue();
        float amt = flapAmount.getValue().floatValue();
        float flap = Mth.sin(t * spd * 2f) * amt;
        float backZ = -0.15f * s;
        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 6; i++) {
                float fi = i / 5f;
                float angleRad = (float) Math.toRadians(-10f + fi * 80f + flap * 20f);
                float segLen = 1.5f * s * (0.55f + 0.45f * Mth.sin(fi * Mth.PI));
                float rootX = side * 0.03f * s, rootY = -0.1f * s + fi * 0.22f * s;
                float ex = rootX + side * Mth.cos(angleRad) * segLen, ey = rootY + Mth.sin(angleRad) * segLen;
                float w = 0.15f * s * (1 - fi * 0.5f);
                float perpX = -Mth.sin(angleRad) * w, perpY = Mth.cos(angleRad) * w;

                buffer.vertex(mat, rootX + perpX, rootY + perpY, backZ).color(0, 150, 255, 200).endVertex();
                buffer.vertex(mat, rootX - perpX, rootY - perpY, backZ).color(0, 150, 255, 200).endVertex();
                buffer.vertex(mat, ex - perpX * 0.5f, ey - perpY * 0.5f, backZ - 0.15f).color(0, 200, 255, 100).endVertex();
                buffer.vertex(mat, ex + perpX * 0.5f, ey + perpY * 0.5f, backZ - 0.15f).color(0, 200, 255, 100).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }
}