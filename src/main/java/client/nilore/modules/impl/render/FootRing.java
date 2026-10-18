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
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;

public class FootRing extends Module {

    public static FootRing INSTANCE;

    // 27 种模式（原有 5 种 + 新增 22 种）
    public final ModeSetting mode = new ModeSetting("Mode", "魔法阵",
            "圆环", "魔法阵", "六芒星", "雷达波纹", "齿轮",
            "发光十字", "多边形护盾", "旋转方块", "极光", "星轨",
            "双层多边形", "代码矩阵", "能量护盾", "准星", "破碎圆环",
            "太极", "音符", "雪花", "八角星", "靶心",
            "闪烁光环", "心电图", "六重魔阵", "流动光环", "元素符文",
            "虚空之眼", "星形轨迹"
    ).withDefault("魔法阵");

    // 大小 (半径)
    public final NumberSetting size = new NumberSetting("Size", 1.5, 0.5, 5.0, 0.1);
    // 旋转速度 (0 = 不转)
    public final NumberSetting rotationSpeed = new NumberSetting("Rot Speed", 45.0, 0.0, 360.0, 5.0);
    // 颜色模式 (彩虹或固定)
    public final ModeSetting colorMode = new ModeSetting("Color", "彩虹", "彩虹", "青色", "紫色", "红色", "白色").withDefault("彩虹");
    // 是否穿透方块
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);

    // =========================================================
    // 【新增】XYZ 轴位置偏移开关
    // =========================================================
    public final NumberSetting offsetX = new NumberSetting("Offset X", 0.0, -3.0, 3.0, 0.1);
    public final NumberSetting offsetY = new NumberSetting("Offset Y", 0.0, -3.0, 3.0, 0.1);
    public final NumberSetting offsetZ = new NumberSetting("Offset Z", 0.0, -3.0, 3.0, 0.1);

    public FootRing() {
        super("FootRing", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public String getModuleName() {
        return "FootRing";
    }

    @Override
    public void onTick() {}

    @Override
    public String getDisplayName() {
        return "§dFootRing";
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) return;

        PoseStack poseStack = event.poseStack();
        if (poseStack == null) return;
        float partialTick = event.partialTick();

        // 获取玩家插值后的脚下坐标
        double px = Mth.lerp(partialTick, mc.player.xOld, mc.player.getX());
        double py = Mth.lerp(partialTick, mc.player.yOld, mc.player.getY());
        double pz = Mth.lerp(partialTick, mc.player.zOld, mc.player.getZ());

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();

        // 【新增】应用 XYZ 偏移
        float ox = offsetX.getValue().floatValue();
        float oy = offsetY.getValue().floatValue();
        float oz = offsetZ.getValue().floatValue();

        poseStack.pushPose();
        // 移动到脚下，应用偏移，并抬高 0.05 防止穿模
        poseStack.translate(px - cam.x + ox, py + 0.05 - cam.y + oy, pz - cam.z + oz);

        // 获取时间
        float time = (System.currentTimeMillis() % 1000000L) / 1000.0f;
        float rotSpeed = rotationSpeed.getValue().floatValue();
        // 计算当前角度，如果速度为0，角度固定为0
        float currentRotation = rotSpeed == 0 ? 0 : (time * rotSpeed) % 360f;

        // 渲染状态设置
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (throughWalls.getValue()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);

        try {
            Matrix4f mat = poseStack.last().pose();
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();

            // 获取当前主题颜色
            int r = 255, g = 255, b = 255;
            String cm = colorMode.getValue();
            if (cm.equals("彩虹")) {
                Color c = Color.getHSBColor((time * 0.2f) % 1.0f, 0.8f, 1.0f);
                r = c.getRed(); g = c.getGreen(); b = c.getBlue();
            } else if (cm.equals("青色")) {
                r = 0; g = 255; b = 255;
            } else if (cm.equals("紫色")) {
                r = 180; g = 0; b = 255;
            } else if (cm.equals("红色")) {
                r = 255; g = 50; b = 50;
            }

            float sizeVal = size.getValue().floatValue();
            // 呼吸脉冲效果 (0.95 - 1.05 之间缩放)
            float pulse = 1.0f + Mth.sin(time * 3.0f) * 0.05f;
            float finalSize = sizeVal * pulse;

            // 旋转画布
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(currentRotation));

            switch (mode.getValue()) {
                // --- 原有 5 种 ---
                case "圆环" -> drawRing(buffer, mat, finalSize, r, g, b);
                case "魔法阵" -> drawMagicCircle(buffer, mat, finalSize, r, g, b, time);
                case "六芒星" -> drawHexagram(buffer, mat, finalSize, r, g, b);
                case "雷达波纹" -> drawRadar(buffer, mat, finalSize, r, g, b, time);
                case "齿轮" -> drawGear(buffer, mat, finalSize, r, g, b);

                // --- 全新增加的 22 种特效 ---
                case "发光十字" -> drawCross(buffer, mat, finalSize, r, g, b, time);
                case "多边形护盾" -> drawPolygonShield(buffer, mat, finalSize, r, g, b, time);
                case "旋转方块" -> drawSpinningSquares(buffer, mat, finalSize, r, g, b, time);
                case "极光" -> drawAurora(buffer, mat, finalSize, r, g, b, time);
                case "星轨" -> drawStarTrail(buffer, mat, finalSize, r, g, b, time);
                case "双层多边形" -> drawDoublePolygon(buffer, mat, finalSize, r, g, b, time);
                case "代码矩阵" -> drawMatrix(buffer, mat, finalSize, r, g, b, time);
                case "能量护盾" -> drawEnergyShield(buffer, mat, finalSize, r, g, b, time);
                case "准星" -> drawCrosshair(buffer, mat, finalSize, r, g, b, time);
                case "破碎圆环" -> drawBrokenRing(buffer, mat, finalSize, r, g, b, time);
                case "太极" -> drawYinYang(buffer, mat, finalSize, r, g, b, time);
                case "音符" -> drawMusicNotes(buffer, mat, finalSize, r, g, b, time);
                case "雪花" -> drawSnowflake(buffer, mat, finalSize, r, g, b, time);
                case "八角星" -> drawOctagram(buffer, mat, finalSize, r, g, b, time);
                case "靶心" -> drawBullseye(buffer, mat, finalSize, r, g, b, time);
                case "闪烁光环" -> drawTwinkleHalo(buffer, mat, finalSize, r, g, b, time);
                case "心电图" -> drawECG(buffer, mat, finalSize, r, g, b, time);
                case "六重魔阵" -> drawHexagramPlus(buffer, mat, finalSize, r, g, b, time);
                case "流动光环" -> drawFlowingHalo(buffer, mat, finalSize, r, g, b, time);
                case "元素符文" -> drawElementalRunes(buffer, mat, finalSize, r, g, b, time);
                case "虚空之眼" -> drawVoidEye(buffer, mat, finalSize, r, g, b, time);
                case "星形轨迹" -> drawStarOrbit(buffer, mat, finalSize, r, g, b, time);
            }

        } finally {
            RenderSystem.lineWidth(1.0f);
            RenderSystem.enableCull();
            if (throughWalls.getValue()) {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    // ==========================================
    // === 原有 5 种画法 ========================
    // ==========================================

    private void drawRing(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int seg = 60;
        for (int i = 0; i <= seg; i++) {
            float angle = (float) (i * Math.PI * 2 / seg);
            buffer.vertex(mat, Mth.cos(angle) * size, 0, Mth.sin(angle) * size).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = (float) (i * Math.PI * 2 / seg);
            buffer.vertex(mat, Mth.cos(angle) * size * 0.8f, 0, Mth.sin(angle) * size * 0.8f).color(r, g, b, 100).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void drawMagicCircle(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int seg = 64;
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = (float) (i * Math.PI * 2 / seg);
            buffer.vertex(mat, Mth.cos(angle) * size, 0, Mth.sin(angle) * size).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = (float) (i * Math.PI * 2 / seg);
            buffer.vertex(mat, Mth.cos(angle) * size * 0.7f, 0, Mth.sin(angle) * size * 0.7f).color(r, g, b, 150).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 24; i++) {
            float angle = (float) (i * Math.PI * 2 / 24);
            float len = (i % 3 == 0) ? 0.9f : 0.8f;
            buffer.vertex(mat, Mth.cos(angle) * size * 0.7f, 0, Mth.sin(angle) * size * 0.7f).color(r, g, b, 180).endVertex();
            buffer.vertex(mat, Mth.cos(angle) * size * len, 0, Mth.sin(angle) * size * len).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void drawHexagram(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int offset = 0; offset < 2; offset++) {
            float startAngle = (float) (offset * Math.PI / 3);
            for (int i = 0; i <= 3; i++) {
                float angle = startAngle + (float) (i * Math.PI * 2 / 3);
                buffer.vertex(mat, Mth.cos(angle) * size, 0, Mth.sin(angle) * size).color(r, g, b, 255).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 60; i++) {
            float angle = (float) (i * Math.PI * 2 / 60);
            buffer.vertex(mat, Mth.cos(angle) * size * 1.1f, 0, Mth.sin(angle) * size * 1.1f).color(r, g, b, 100).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private void drawRadar(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int sweepSeg = 10;
        float sweepLength = (float) Math.PI / 3;
        float startAngle = time * 2.0f;

        for (int i = 0; i < sweepSeg; i++) {
            float a1 = startAngle + sweepLength * i / sweepSeg;
            float a2 = startAngle + sweepLength * (i + 1) / sweepSeg;
            int alpha = (int)(150 * (1.0f - (float)i / sweepSeg));

            buffer.vertex(mat, 0, 0, 0).color(r, g, b, alpha).endVertex();
            buffer.vertex(mat, Mth.cos(a1) * size, 0, Mth.sin(a1) * size).color(r, g, b, alpha).endVertex();
            buffer.vertex(mat, Mth.cos(a2) * size, 0, Mth.sin(a2) * size).color(r, g, b, alpha).endVertex();
            buffer.vertex(mat, 0, 0, 0).color(r, g, b, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        drawRing(buffer, mat, size, r, g, b);
    }

    private void drawGear(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b) {
        int teeth = 12;
        float innerSize = size * 0.8f;
        float outerSize = size;

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < teeth * 2; i++) {
            float angle = (float) (i * Math.PI / teeth);
            float currentRadius = (i % 2 == 0) ? outerSize : innerSize;
            buffer.vertex(mat, Mth.cos(angle) * currentRadius, 0, Mth.sin(angle) * currentRadius).color(r, g, b, 255).endVertex();
        }
        buffer.vertex(mat, outerSize, 0, 0).color(r, g, b, 255).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 40; i++) {
            float angle = (float) (i * Math.PI * 2 / 40);
            buffer.vertex(mat, Mth.cos(angle) * innerSize * 0.5f, 0, Mth.sin(angle) * innerSize * 0.5f).color(r, g, b, 200).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // ==========================================
    // === 全新增加的 22 种特效 =================
    // ==========================================

    // 1. 发光十字
    private void drawCross(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        float t = Mth.sin(time * 2f) * 0.1f + 0.9f;
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(mat, -size * t, 0, 0).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, size * t, 0, 0).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, 0, 0, -size * t).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, 0, 0, size * t).color(r, g, b, 255).endVertex();
        // 对角线
        buffer.vertex(mat, -size * 0.5f * t, 0, -size * 0.5f * t).color(r, g, b, 100).endVertex();
        buffer.vertex(mat, size * 0.5f * t, 0, size * 0.5f * t).color(r, g, b, 100).endVertex();
        buffer.vertex(mat, -size * 0.5f * t, 0, size * 0.5f * t).color(r, g, b, 100).endVertex();
        buffer.vertex(mat, size * 0.5f * t, 0, -size * 0.5f * t).color(r, g, b, 100).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    // 2. 多边形护盾 (动态边数)
    private void drawPolygonShield(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int sides = (int) ((time * 5) % 6) + 3; // 3 到 8 边形
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= sides; i++) {
            float angle = (float) (i * Math.PI * 2 / sides);
            buffer.vertex(mat, Mth.cos(angle) * size, 0, Mth.sin(angle) * size).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        // 内缩的副多边形
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= sides; i++) {
            float angle = (float) (i * Math.PI * 2 / sides + Math.PI);
            buffer.vertex(mat, Mth.cos(angle) * size * 0.7f, 0, Mth.sin(angle) * size * 0.7f).color(r, g, b, 150).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 3. 旋转方块
    private void drawSpinningSquares(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int sq = 0; sq < 3; sq++) {
            float angleOffset = time * (1.0f + sq * 0.5f);
            float rad = size * (1.0f - sq * 0.2f);
            for (int i = 0; i <= 4; i++) {
                float angle = (float) (i * Math.PI / 2 + angleOffset);
                buffer.vertex(mat, Mth.cos(angle) * rad, 0, Mth.sin(angle) * rad).color(r, g, b, 255 - sq * 60).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 4. 极光 (波浪形)
    private void drawAurora(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int seg = 60;
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = (float) (i * Math.PI * 2 / seg);
            float wave = Mth.sin(angle * 4 + time * 3) * 0.2f + 1.0f;
            float wave2 = Mth.sin(angle * 4 + time * 3 + 0.5f) * 0.2f + 0.8f;

            buffer.vertex(mat, Mth.cos(angle) * size * wave, 0, Mth.sin(angle) * size * wave).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, Mth.cos(angle) * size * wave2, 0, Mth.sin(angle) * size * wave2).color(r, g, b, 60).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 5. 星轨 (拖尾光点)
    private void drawStarTrail(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int stars = 4;
        for (int s = 0; s < stars; s++) {
            float baseAngle = (float) (s * Math.PI * 2 / stars + time);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 10; i++) {
                float angle = baseAngle - i * 0.1f;
                int alpha = 255 - i * 25;
                buffer.vertex(mat, Mth.cos(angle) * size, 0, Mth.sin(angle) * size).color(r, g, b, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
    }

    // 6. 双层多边形
    private void drawDoublePolygon(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        // 外三角
        for (int i = 0; i <= 3; i++) {
            float angle = (float) (i * Math.PI * 2 / 3 + time);
            buffer.vertex(mat, Mth.cos(angle) * size, 0, Mth.sin(angle) * size).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        // 内正方
        for (int i = 0; i <= 4; i++) {
            float angle = (float) (i * Math.PI * 2 / 4 - time);
            buffer.vertex(mat, Mth.cos(angle) * size * 0.6f, 0, Mth.sin(angle) * size * 0.6f).color(r, g, b, 180).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 7. 代码矩阵
    private void drawMatrix(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int lines = 36;
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < lines; i++) {
            float angle = (float) (i * Math.PI * 2 / lines);
            // 模拟代码流，长度随机抖动
            float length = size * (0.6f + (float)Math.sin(time * 5 + i * 10) * 0.4f);
            buffer.vertex(mat, Mth.cos(angle) * size * 0.8f, 0, Mth.sin(angle) * size * 0.8f).color(r, g, b, 100).endVertex();
            buffer.vertex(mat, Mth.cos(angle) * length, 0, Mth.sin(angle) * length).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 8. 能量护盾 (多重呼吸圈)
    private void drawEnergyShield(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int rings = 4;
        for (int i = 0; i < rings; i++) {
            float scale = 1.0f - i * 0.15f;
            float alpha = 100 + (i * 50) % 155;
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int j = 0; j <= 60; j++) {
                float angle = (float) (j * Math.PI * 2 / 60);
                float wobble = Mth.sin(angle * 8 + time * 5) * 0.05f * scale;
                buffer.vertex(mat, Mth.cos(angle) * size * (scale + wobble), 0, Mth.sin(angle) * size * (scale + wobble)).color(r, g, b, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
    }

    // 9. 准星
    private void drawCrosshair(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        float inner = size * 0.3f;
        float outer = size * 0.9f;
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        // 上下左右
        buffer.vertex(mat, inner, 0, 0).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, outer, 0, 0).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, -inner, 0, 0).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, -outer, 0, 0).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, 0, 0, inner).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, 0, 0, outer).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, 0, 0, -inner).color(r, g, b, 255).endVertex();
        buffer.vertex(mat, 0, 0, -outer).color(r, g, b, 255).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        // 外圈虚线
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int seg = 24;
        for (int i = 0; i < seg; i += 2) {
            float a1 = (float) (i * Math.PI * 2 / seg);
            float a2 = (float) ((i + 1) * Math.PI * 2 / seg);
            buffer.vertex(mat, Mth.cos(a1) * size, 0, Mth.sin(a1) * size).color(r, g, b, 150).endVertex();
            buffer.vertex(mat, Mth.cos(a2) * size, 0, Mth.sin(a2) * size).color(r, g, b, 150).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 10. 破碎圆环
    private void drawBrokenRing(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int seg = 60;
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        boolean draw = true;
        for (int i = 0; i <= seg; i++) {
            if (i % 6 == 0) draw = !draw; // 每6段断开一次
            if (draw) {
                float angle = (float) (i * Math.PI * 2 / seg);
                // 断开的边缘有轻微抖动
                float jitter = i % 6 == 1 || i % 6 == 5 ? (float)Math.random() * 0.1f : 0;
                buffer.vertex(mat, Mth.cos(angle) * (size + jitter), 0, Mth.sin(angle) * (size + jitter)).color(r, g, b, 255).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 11. 太极
    private void drawYinYang(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        // 外圈
        for (int i = 0; i <= 60; i++) {
            float angle = (float) (i * Math.PI * 2 / 60);
            buffer.vertex(mat, Mth.cos(angle) * size, 0, Mth.sin(angle) * size).color(r, g, b, 255).endVertex();
        }
        // S型曲线
        for (int i = 0; i <= 30; i++) {
            float t = (float) i / 30;
            float angle = (float) (t * Math.PI);
            buffer.vertex(mat, Mth.sin(angle) * (size * 0.5f), 0, Mth.cos(angle) * (size * 0.5f) - size * 0.5f).color(r, g, b, 255).endVertex();
        }
        for (int i = 0; i <= 30; i++) {
            float t = (float) i / 30;
            float angle = (float) (t * Math.PI);
            buffer.vertex(mat, -Mth.sin(angle) * (size * 0.5f), 0, Mth.cos(angle) * (size * 0.5f) + size * 0.5f).color(r, g, b, 255).endVertex();
        }
        // 两个眼睛
        for (int eye = -1; eye <= 1; eye += 2) {
            float cx = 0;
            float cz = eye * size * 0.5f;
            for (int i = 0; i <= 20; i++) {
                float angle = (float) (i * Math.PI * 2 / 20);
                buffer.vertex(mat, cx + Mth.cos(angle) * size * 0.15f, 0, cz + Mth.sin(angle) * size * 0.15f).color(r, g, b, 255).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 12. 音符
    private void drawMusicNotes(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 6; i++) {
            float angle = (float) (i * Math.PI * 2 / 6 + time);
            float x = Mth.cos(angle) * size * 0.8f;
            float z = Mth.sin(angle) * size * 0.8f;
            // 画个简单的八分音符
            // 符头
            buffer.vertex(mat, x, 0, z).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, x + 0.1f, 0, z + 0.1f).color(r, g, b, 255).endVertex();
            // 符干
            buffer.vertex(mat, x + 0.1f, 0, z + 0.1f).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, x + 0.1f, 0, z + 0.4f).color(r, g, b, 255).endVertex();
            // 符尾
            buffer.vertex(mat, x + 0.1f, 0, z + 0.4f).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, x + 0.25f, 0, z + 0.3f).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 13. 雪花
    private void drawSnowflake(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 6; i++) {
            float angle = (float) (i * Math.PI / 3);
            // 主轴
            buffer.vertex(mat, 0, 0, 0).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, Mth.cos(angle) * size, 0, Mth.sin(angle) * size).color(r, g, b, 255).endVertex();

            // 分叉 (在 0.6 和 0.8 位置)
            for (float t : new float[]{0.5f, 0.75f}) {
                float cx = Mth.cos(angle) * size * t;
                float cz = Mth.sin(angle) * size * t;
                float branchLen = size * 0.2f * (1.0f - t);
                buffer.vertex(mat, cx, 0, cz).color(r, g, b, 200).endVertex();
                buffer.vertex(mat, cx + Mth.cos(angle + 0.8f) * branchLen, 0, cz + Mth.sin(angle + 0.8f) * branchLen).color(r, g, b, 200).endVertex();
                buffer.vertex(mat, cx, 0, cz).color(r, g, b, 200).endVertex();
                buffer.vertex(mat, cx + Mth.cos(angle - 0.8f) * branchLen, 0, cz + Mth.sin(angle - 0.8f) * branchLen).color(r, g, b, 200).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 14. 八角星
    private void drawOctagram(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int points = 8;
        for (int i = 0; i <= points * 2; i++) {
            // 交替内外半径形成星形
            float rad = (i % 2 == 0) ? size : size * 0.4f;
            float angle = (float) (i * Math.PI / points);
            buffer.vertex(mat, Mth.cos(angle) * rad, 0, Mth.sin(angle) * rad).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 15. 靶心
    private void drawBullseye(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        for (int i = 1; i <= 4; i++) {
            float rad = size * (i / 4.0f);
            // 最外圈和最内圈实线，中间虚线
            if (i == 4 || i == 1) {
                buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                for (int j = 0; j <= 40; j++) {
                    float angle = (float) (j * Math.PI * 2 / 40);
                    buffer.vertex(mat, Mth.cos(angle) * rad, 0, Mth.sin(angle) * rad).color(r, g, b, 255).endVertex();
                }
            } else {
                buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
                for (int j = 0; j < 40; j += 2) {
                    float a1 = (float) (j * Math.PI * 2 / 40);
                    float a2 = (float) ((j + 1) * Math.PI * 2 / 40);
                    buffer.vertex(mat, Mth.cos(a1) * rad, 0, Mth.sin(a1) * rad).color(r, g, b, 150).endVertex();
                    buffer.vertex(mat, Mth.cos(a2) * rad, 0, Mth.sin(a2) * rad).color(r, g, b, 150).endVertex();
                }
            }
            BufferUploader.drawWithShader(buffer.end());
        }
    }

    // 16. 闪烁光环 (粒子点阵)
    private void drawTwinkleHalo(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int points = 40;
        for (int i = 0; i < points; i++) {
            float angle = (float) (i * Math.PI * 2 / points);
            // 用 sin 控制点的长度形成闪烁感
            float twinkle = (Mth.sin(time * 5 + i) + 1) * 0.5f * 0.2f + 0.8f;
            float rad = size * twinkle;

            // 画一个个小十字代表粒子
            float x = Mth.cos(angle) * rad;
            float z = Mth.sin(angle) * rad;
            float sz = 0.05f;
            buffer.vertex(mat, x - sz, 0, z).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, x + sz, 0, z).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, x, 0, z - sz).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, x, 0, z + sz).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 17. 心电图 (在脚下铺一条带波形的线)
    private void drawECG(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int seg = 60;
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float x = (i / (float)seg - 0.5f) * 2 * size;
            // 默认是直线
            float z = 0;
            // 每隔一段出现一个心跳尖峰
            float phase = (x + time * 2) % 2.0f;
            if (phase > 0.9f && phase < 1.1f) {
                z = Mth.sin((float) ((phase - 0.9f) * Math.PI * 10)) * size * 0.3f;
            }
            buffer.vertex(mat, x, 0, z).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 18. 六重魔阵 (更复杂的六芒星)
    private void drawHexagramPlus(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        drawHexagram(buffer, mat, size, r, g, b);

        // 增加一圈内缩反向旋转的六边形
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 6; i++) {
            float angle = (float) (i * Math.PI / 3 - time);
            buffer.vertex(mat, Mth.cos(angle) * size * 0.6f, 0, Mth.sin(angle) * size * 0.6f).color(r, g, b, 150).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 19. 流动光环 (光带粗细变化)
    private void drawFlowingHalo(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        int seg = 60;
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = (float) (i * Math.PI * 2 / seg);
            // 粗细随角度和时间波动
            float thickness = 0.1f + (Mth.sin(angle * 3 + time * 2) + 1) * 0.1f;
            float innerRad = size - thickness;
            float outerRad = size + thickness;

            buffer.vertex(mat, Mth.cos(angle) * innerRad, 0, Mth.sin(angle) * innerRad).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, Mth.cos(angle) * outerRad, 0, Mth.sin(angle) * outerRad).color(r, g, b, 100).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 20. 元素符文 (内部散落几何图形)
    private void drawElementalRunes(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        drawRing(buffer, mat, size, r, g, b);

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        // 内部三角形
        for (int i = 0; i <= 3; i++) {
            float angle = (float) (i * Math.PI * 2 / 3 + time * 0.5f);
            buffer.vertex(mat, Mth.cos(angle) * size * 0.4f, 0, Mth.sin(angle) * size * 0.4f).color(r, g, b, 200).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        // 内部正方形
        for (int i = 0; i <= 4; i++) {
            float angle = (float) (i * Math.PI * 2 / 4 - time * 0.5f);
            buffer.vertex(mat, Mth.cos(angle) * size * 0.25f, 0, Mth.sin(angle) * size * 0.25f).color(r, g, b, 150).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 21. 虚空之眼
    private void drawVoidEye(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        // 眼眶 (橄榄形)
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 40; i++) {
            float t = i / 40f;
            float angle = (float) (t * Math.PI * 2);
            // 用极坐标画椭圆，并稍微修改使它两端变尖
            float x = Mth.cos(angle) * size;
            float z = Mth.sin(angle) * size * 0.4f * Mth.sin((float) (t * Math.PI)); // 两头收缩
            buffer.vertex(mat, x, 0, z).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        // 瞳孔 (不停旋转的小圆)
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        float pupilSize = size * 0.25f * (1.0f + Mth.sin(time * 4) * 0.1f);
        for (int i = 0; i <= 20; i++) {
            float angle = (float) (i * Math.PI * 2 / 20 + time * 2);
            buffer.vertex(mat, Mth.cos(angle) * pupilSize, 0, Mth.sin(angle) * pupilSize).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    // 22. 星形轨迹 (双向虚线环绕)
    private void drawStarOrbit(BufferBuilder buffer, Matrix4f mat, float size, int r, int g, int b, float time) {
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int points = 24;
        for (int i = 0; i < points; i++) {
            // 正向轨迹
            float a1 = (float) (i * Math.PI * 2 / points + time);
            float a2 = (float) ((i + 0.5) * Math.PI * 2 / points + time);
            buffer.vertex(mat, Mth.cos(a1) * size, 0, Mth.sin(a1) * size).color(r, g, b, 255).endVertex();
            buffer.vertex(mat, Mth.cos(a2) * size, 0, Mth.sin(a2) * size).color(r, g, b, 255).endVertex();

            // 反向小轨迹
            float b1 = (float) (i * Math.PI * 2 / points - time * 1.5);
            float b2 = (float) ((i + 0.5) * Math.PI * 2 / points - time * 1.5);
            buffer.vertex(mat, Mth.cos(b1) * size * 0.6f, 0, Mth.sin(b1) * size * 0.6f).color(r, g, b, 120).endVertex();
            buffer.vertex(mat, Mth.cos(b2) * size * 0.6f, 0, Mth.sin(b2) * size * 0.6f).color(r, g, b, 120).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }
}