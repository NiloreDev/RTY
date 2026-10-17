package client.nilore.modules.impl.render;

import client.nilore.NiloreClient;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.hud.DynamicIsland;
import client.nilore.hud.LogoWatermark;
import client.nilore.hud.NeverloseWatermark;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.render.DrawContext;
import client.nilore.render.FontRenderer;
import client.nilore.render.FontPresets;
import client.nilore.render.Fonts;
import client.nilore.render.Paint;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.event.EventTarget;
import client.nilore.utils.render.ColorUtil;
import client.nilore.utils.render.RenderUtil;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Watermark extends Module {
    // 模式：Neverlose / DynamicIsland / Simple / Pharos / Logo / Naven
    final ModeSetting styleSetting = new ModeSetting("Style", "Neverlose", "DynamicIsland", "Simple", "Pharos", "Logo", "Naven").withDefault("DynamicIsland");
    private final NumberSetting bgAlpha = new NumberSetting("BG Alpha", 160, 0, 255, 1, () -> this.styleSetting.is("Simple"));
    private final BooleanSetting glow = new BooleanSetting("Glow", false, () -> this.styleSetting.is("Simple"));
    private final NumberSetting glowRadius = new NumberSetting("Glow Radius", 12, 4, 40, 1, () -> this.styleSetting.is("Simple") && this.glow.getValue());
    private final NumberSetting glowAlpha = new NumberSetting("Glow Alpha", 120, 0, 255, 1, () -> this.styleSetting.is("Simple") && this.glow.getValue());

    private final NumberSetting watermarkX = new NumberSetting("Watermark X", 0, -2000, 2000, 1);
    private final NumberSetting watermarkY = new NumberSetting("Watermark Y", 0, -2000, 2000, 1);
    private final NumberSetting navenZ = new NumberSetting("Naven Z (Scale)", 1.0f, 0.1f, 5.0f, 0.05f, () -> this.styleSetting.is("Naven"));

    private boolean dragging = false;
    private boolean mouseWasDown = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;

    private final ModeSetting textColorMode = new ModeSetting("Text Color", "White",
            "White", "Red", "Gold", "Yellow", "Green", "DarkGreen",
            "Aqua", "DarkAqua", "Blue", "DarkBlue", "Pink", "Purple",
            "Gray", "DarkGray", "DarkRed", "LightRed", "LightGreen",
            "LightAqua", "LightPink", "Orange", "Lime", "Cyan", "Magenta",
            "DoubleColor", "Rainbow")
            .withDefault("White")
            .withVisibility(() -> this.styleSetting.is("Simple"));

    private final DynamicIsland dynamicIsland = new DynamicIsland();
    private final LogoWatermark logoWatermark = new LogoWatermark();
    private final NeverloseWatermark neverloseWatermark = new NeverloseWatermark();

    // 图标字符定义
    private static final String ICON_CLIENT = "⚡";
    private static final String ICON_USER   = "☠";
    private static final String ICON_FPS    = "⛓";
    private static final String ICON_PING   = "◉";
    private static final String ICON_TIME   = "◷";

    private static final FontRenderer titleFont = Fonts.getRenderer("quicksand.ttf", 36.0f);
    private static final FontRenderer fpsFont = Fonts.getRenderer("quicksand.ttf", 20.0f);
    private static final float MARGIN = 8.0f;

    // Simple 模式使用的字体和常量（来自第二个文件）
    private static final FontRenderer iconFont = FontPresets.niloreIcon(24.0f);
    private static final FontRenderer textFont = FontPresets.pingfang(24.0f);
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INFO_COLOR = 0xCCFFFFFF;
    private static final float ICON_SIZE = 20.0f;
    private static final float PAD = 3.0f;
    private static final int BG_COLOR_BASE = 0x0A0A0A;

    // Naven 模式使用的字体和常量
    private static final FontRenderer navenFont = FontPresets.pingfang(17.0f);
    private static final DateTimeFormatter NAVEN_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final java.util.Map<String, Integer> COLOR_MAP = new java.util.HashMap<>();
    static {
        COLOR_MAP.put("White", 0xFFFFFFFF);
        COLOR_MAP.put("Red", 0xFFFF5555);
        COLOR_MAP.put("Gold", 0xFFFFAA00);
        COLOR_MAP.put("Yellow", 0xFFFFFF55);
        COLOR_MAP.put("Green", 0xFF55FF55);
        COLOR_MAP.put("DarkGreen", 0xFF00AA00);
        COLOR_MAP.put("Aqua", 0xFF55FFFF);
        COLOR_MAP.put("DarkAqua", 0xFF00AAAA);
        COLOR_MAP.put("Blue", 0xFF5555FF);
        COLOR_MAP.put("DarkBlue", 0xFF0000AA);
        COLOR_MAP.put("Pink", 0xFFFF55FF);
        COLOR_MAP.put("Purple", 0xFFAA55AA);
        COLOR_MAP.put("Gray", 0xFFAAAAAA);
        COLOR_MAP.put("DarkGray", 0xFF555555);
        COLOR_MAP.put("DarkRed", 0xFFAA0000);
        COLOR_MAP.put("LightRed", 0xFFFF8888);
        COLOR_MAP.put("LightGreen", 0xFF88FF88);
        COLOR_MAP.put("LightAqua", 0xFF88FFFF);
        COLOR_MAP.put("LightPink", 0xFFFF88FF);
        COLOR_MAP.put("Orange", 0xFFFF8800);
        COLOR_MAP.put("Lime", 0xFF88FF00);
        COLOR_MAP.put("Cyan", 0xFF00FFFF);
        COLOR_MAP.put("Magenta", 0xFFFF00FF);
    }

    private long lastColorUpdate = 0;
    private float colorOffset = 0;

    public Watermark() {
        super("Watermark", Category.RENDER);
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        if (!this.isEnabled()) return;
        float dx = this.watermarkX.getValue().floatValue();
        float dy = this.watermarkY.getValue().floatValue();

        switch (this.styleSetting.getValue()) {
            case "Neverlose":
                render2DEvent.poseStack().pushPose();
                render2DEvent.poseStack().translate(dx, dy, 0.0f);
                this.neverloseWatermark.onRender2D(render2DEvent);
                render2DEvent.poseStack().popPose();
                break;
            case "DynamicIsland":
                render2DEvent.poseStack().pushPose();
                render2DEvent.poseStack().translate(dx, dy, 0.0f);
                this.dynamicIsland.onRender2D(render2DEvent);
                render2DEvent.poseStack().popPose();
                break;
        }
    }

    @EventTarget
    public void onGlRender(GlRenderEvent glRenderEvent) {
        if (!this.isEnabled()) return;
        DrawContext ctx = glRenderEvent.drawContext();
        if (ctx == null) return;

        updateColorAnimation();
        updateDrag();

        switch (this.styleSetting.getValue()) {
            case "Neverlose":
                ctx.getPoseStack().pushPose();
                ctx.getPoseStack().translate(this.watermarkX.getValue().floatValue(), this.watermarkY.getValue().floatValue(), 0.0f);
                this.neverloseWatermark.onGlRender(glRenderEvent);
                ctx.getPoseStack().popPose();
                break;
            case "Pharos":
                this.renderPharos(ctx);
                break;
            case "Simple":
                this.renderSimple(ctx);
                break;
            case "Logo":
                ctx.getPoseStack().pushPose();
                ctx.getPoseStack().translate(this.watermarkX.getValue().floatValue(), this.watermarkY.getValue().floatValue(), 0.0f);
                this.logoWatermark.onGlRender(glRenderEvent);
                ctx.getPoseStack().popPose();
                break;
            case "Naven":
                this.renderNaven(ctx);
                break;
        }
    }

    private void updateColorAnimation() {
        long now = System.currentTimeMillis();
        if (now - lastColorUpdate > 50) {
            lastColorUpdate = now;
            colorOffset += 0.02f;
            if (colorOffset > 1.0f) colorOffset -= 1.0f;
        }
    }

    private int getCurrentColor(float offset, int charIndex) {
        String mode = this.textColorMode.getValue();
        if (mode.equals("Rainbow")) {
            float hue = (colorOffset + charIndex * 0.05f) % 1.0f;
            return Color.HSBtoRGB(hue, 0.9f, 1.0f);
        }
        if (mode.equals("DoubleColor")) {
            float progress = (colorOffset * 3) % 1.0f;
            int color1 = COLOR_MAP.getOrDefault("Red", 0xFFFF5555);
            int color2 = COLOR_MAP.getOrDefault("Blue", 0xFF5555FF);
            return ColorUtil.lerpColor(color1, color2, progress);
        }
        return COLOR_MAP.getOrDefault(mode, 0xFFFFFFFF);
    }

    private void drawGradientText(DrawContext ctx, String text, float x, float y, FontRenderer font, int charOffset) {
        String mode = this.textColorMode.getValue();
        if (mode.equals("Rainbow")) {
            float charX = x;
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                float hue = (colorOffset + (charOffset + i) * 0.05f) % 1.0f;
                int color = Color.HSBtoRGB(hue, 0.9f, 1.0f);
                float w = font.getWidth(ch);
                ctx.drawString(ch, charX, y, font, new Paint().setColor(color));
                charX += w;
            }
            return;
        }
        if (mode.equals("DoubleColor")) {
            int color1 = COLOR_MAP.getOrDefault("Red", 0xFFFF5555);
            int color2 = COLOR_MAP.getOrDefault("Blue", 0xFF5555FF);
            float charX = x;
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                float progress = (colorOffset * 3 + (charOffset + i) * 0.03f) % 1.0f;
                int color = ColorUtil.lerpColor(color1, color2, progress);
                float w = font.getWidth(ch);
                ctx.drawString(ch, charX, y, font, new Paint().setColor(color));
                charX += w;
            }
            return;
        }
        int color = getCurrentColor(0, 0);
        ctx.drawString(text, x, y, font, new Paint().setColor(color));
    }

    // ================================================================
    // Simple 模式（完全替换为第二个文件的逻辑）
    // ================================================================
    private void renderSimple(DrawContext ctx) {
        if (mc.player == null) return;

        String icon = "N";
        String clientName = NiloreClient.CLIENT_NAME;
        String userName = NameProtect.getProtectedName();
        String fpsText = mc.getFps() + "fps";
        String pingText = getPing() + "ms";

        // 完整日期时间
        String timeText = LocalDateTime.now().format(TIME_FORMATTER);

        String infoText = " | " + userName + " | " + fpsText + " | " + pingText + " | " + timeText;

        float iconW = iconFont.getWidth(icon);
        float nameW = textFont.getWidth(clientName);
        float infoW = textFont.getWidth(infoText);

        float contentW = iconW + PAD + nameW + infoW;
        float lineH = Math.max(ICON_SIZE, textFont.getMetrics().height());
        float boxH = lineH + PAD * 2;
        float boxW = contentW + PAD * 2 + 6f;

        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float x = (screenW - boxW) / 2.0f + this.watermarkX.getValue().floatValue();
        float y = screenH / 20.0f + this.watermarkY.getValue().floatValue();

        // Capsule background (full pill shape)
        float radius = boxH / 2;
        int bgColor = BG_COLOR_BASE | (bgAlpha.getValue().intValue() << 24);
        RoundedRectangle capsule = RoundedRectangle.ofXYWHR(x, y, boxW, boxH, radius);
        ctx.drawRoundedRect(capsule, new Paint().setColor(bgColor));

        // Glow behind background
        if (glow.getValue()) {
            int gRadius = this.glowRadius.getValue().intValue();
            int gAlpha = this.glowAlpha.getValue().intValue();
            if (gAlpha > 0 && gRadius > 0) {
                RenderUtil.drawShadow(ctx.getPoseStack(), x, y, boxW, boxH, gRadius, (gAlpha << 24) | 0x000000);
                RenderUtil.enableBlend();
            }
        }

        float cursorX = x + PAD + 3f;
        float textY = y + PAD + (lineH - iconFont.getMetrics().height()) / 2 + 13f;

        // Icon "N" - 使用白色
        ctx.drawString(icon, cursorX, textY, iconFont, new Paint().setColor(TEXT_COLOR));
        cursorX += iconW + PAD;

        // Client name "Nebula" - 使用渐变颜色
        float nameY = y + PAD + (lineH - textFont.getMetrics().height()) / 2 + 10f;
        drawGradientText(ctx, clientName, cursorX, nameY, textFont, 0);
        cursorX += nameW;

        // Info text - 使用渐变颜色（偏移不同，产生不同效果）
        float infoY = y + PAD + (lineH - textFont.getMetrics().height()) / 2 + 10f;
        drawGradientText(ctx, infoText, cursorX, infoY, textFont, clientName.length());
    }

    // ================================================================
    // Naven 风格水印渲染（删除图标）
    // ================================================================
    private void renderNaven(DrawContext ctx) {
        if (mc.player == null) return;
        String text = getNavenText();
        // 不再计算 iconW
        float textW = navenFont.getWidth(text) + 4f;
        float textH = navenFont.getMetrics().height();
        float padX = 7.0f, padTop = 3.0f, padBottom = 3.0f, topLineH = 1.5f;
        float boxW = textW + padX * 2.0f;
        float boxH = textH + padTop + padBottom + topLineH;
        float screenW = mc.getWindow().getGuiScaledWidth();
        float x = (screenW - boxW) / 2.0f + this.watermarkX.getValue().floatValue();
        float y = 4.0f + this.watermarkY.getValue().floatValue();
        float scale = navenZ.getValue().floatValue();
        boolean useScale = Math.abs(scale - 1.0f) > 0.001f;
        if (useScale) {
            ctx.getPoseStack().pushPose();
            ctx.getPoseStack().translate(x + boxW / 2.0f, y + boxH / 2.0f, 0.0f);
            ctx.getPoseStack().scale(scale, scale, 1.0f);
            ctx.getPoseStack().translate(-(x + boxW / 2.0f), -(y + boxH / 2.0f), 0.0f);
        }
        RenderUtil.drawShadow(ctx.getPoseStack(), x, y, boxW, boxH, 8, 0x76000000);
        RenderUtil.enableBlend();
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, boxW, boxH, 3.5f), new Paint().setColor(0xB0181A1B));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x + 1.0f, y, boxW - 2.0f, 2.0f, 2.0f), new Paint().setColor(0xA8A92727));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x + 2.0f, y, boxW - 4.0f, 1.0f, 1.0f), new Paint().setColor(0xEED44646));
        RenderUtil.drawShadow(ctx.getPoseStack(), x + 2.0f, y, boxW - 4.0f, 1.0f, 5, 0x55D43C3C);
        RenderUtil.enableBlend();
        int textColor = 0xFFD2D2D2;
        // 直接绘制文本，去掉图标绘制
        float textX = x + padX;
        float textY = y + topLineH + padTop + navenFont.getMetrics().capHeight();
        ctx.drawString(text, textX, textY, navenFont, new Paint().setColor(textColor));
        if (useScale) ctx.getPoseStack().popPose();
    }

    private String getNavenText() {
        return "Naven | Modern-Beta | " + NameProtect.getProtectedName() + " | " + mc.getFps() + " FPS | " + LocalDateTime.now().format(NAVEN_TIME_FORMATTER);
    }

    private void updateDrag() {
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) return;
        boolean cursorVisible = mc.screen != null || !mc.mouseHandler.isMouseGrabbed();
        boolean leftDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        float guiWidth = mc.getWindow().getGuiScaledWidth();
        float guiHeight = mc.getWindow().getGuiScaledHeight();
        float screenWidth = Math.max(1.0f, mc.getWindow().getScreenWidth());
        float screenHeight = Math.max(1.0f, mc.getWindow().getScreenHeight());
        float mouseX = (float) (mc.mouseHandler.xpos() * guiWidth / screenWidth);
        float mouseY = (float) (mc.mouseHandler.ypos() * guiHeight / screenHeight);
        float[] bounds = getCurrentWatermarkBounds();
        if (bounds == null) { mouseWasDown = leftDown; return; }
        float x = bounds[0], y = bounds[1], w = Math.max(1.0f, bounds[2]), h = Math.max(1.0f, bounds[3]);
        if (!cursorVisible) {
            if (dragging) { dragging = false; savePosition(); }
            mouseWasDown = leftDown; return;
        }
        if (leftDown && !mouseWasDown && !dragging) {
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                dragging = true; dragOffsetX = mouseX - x; dragOffsetY = mouseY - y;
            }
        }
        if (dragging) {
            if (leftDown) {
                float desiredX = Math.max(0.0f, Math.min(mouseX - dragOffsetX, guiWidth - w));
                float desiredY = Math.max(0.0f, Math.min(mouseY - dragOffsetY, guiHeight - h));
                this.watermarkX.setValue(this.watermarkX.getValue().floatValue() + (desiredX - x));
                this.watermarkY.setValue(this.watermarkY.getValue().floatValue() + (desiredY - y));
            } else { dragging = false; savePosition(); }
        }
        mouseWasDown = leftDown;
    }

    private float[] getCurrentWatermarkBounds() {
        if (mc.player == null) return null;
        float dx = this.watermarkX.getValue().floatValue();
        float dy = this.watermarkY.getValue().floatValue();
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();

        switch (this.styleSetting.getValue()) {
            case "Naven": {
                String text = getNavenText();
                // 不再包含 iconW
                float boxW = navenFont.getWidth(text) + 14.0f;
                float boxH = navenFont.getMetrics().height() + 7.5f;
                float scale = navenZ.getValue().floatValue();
                float baseX = (screenW - boxW) / 2.0f + dx;
                float baseY = 4.0f + dy;
                return new float[]{ baseX + (boxW - boxW * scale) / 2.0f, baseY + (boxH - boxH * scale) / 2.0f, boxW * scale, boxH * scale };
            }
            case "Simple": {
                // 同步文件2的边界计算
                String icon = "N";
                String clientName = NiloreClient.CLIENT_NAME;
                String userName = NameProtect.getProtectedName();
                String fpsText = mc.getFps() + "fps";
                String pingText = getPing() + "ms";
                String timeText = LocalDateTime.now().format(TIME_FORMATTER);

                String infoText = " | " + userName + " | " + fpsText + " | " + pingText + " | " + timeText;

                float iconW = iconFont.getWidth(icon);
                float nameW = textFont.getWidth(clientName);
                float infoW = textFont.getWidth(infoText);

                float contentW = iconW + PAD + nameW + infoW;
                float lineH = Math.max(ICON_SIZE, textFont.getMetrics().height());
                float boxH = lineH + PAD * 2;
                float boxW = contentW + PAD * 2 + 6f;
                float x = (screenW - boxW) / 2.0f + dx;
                float y = screenH / 20.0f + dy;
                return new float[]{x, y, boxW, boxH};
            }
            case "Pharos": {
                float iconW = iconFont.getWidth("N") + 4f;
                float titleW = titleFont.getWidth(NiloreClient.CLIENT_NAME);
                float fpsW = fpsFont.getWidth(String.valueOf(mc.getFps()));
                float w = Math.max(titleW, fpsW) + 12.0f + iconW;
                float h = titleFont.getMetrics().height() + fpsFont.getMetrics().height() + 16.0f;
                return new float[]{MARGIN - 5.0f + dx, MARGIN + dy, w, h};
            }
            case "Logo": return new float[]{-2.0f + dx, 18.0f + dy, 96.0f, 96.0f};
            case "Neverlose": {
                float w = Math.min(screenW - 8.0f, 620.0f);
                return new float[]{(screenW - w) / 2.0f + dx, 8.0f + dy, w, 32.0f};
            }
            case "DynamicIsland": {
                float w = Math.min(screenW - 8.0f, 420.0f);
                return new float[]{(screenW - w) / 2.0f + dx, 18.0f + dy, w, 48.0f};
            }
            default: return null;
        }
    }

    private void savePosition() {
        try { NiloreClient.getInstance().getConfigManager().saveAll(); } catch (Throwable ignored) {}
    }

    private void renderPharos(DrawContext ctx) {
        if (mc.player == null) return;
        Paint textPaint = new Paint().setColor(0xFFFFFFFF);
        Paint fpsPaint = new Paint().setColor(0xFFFFFFFB);
        float padX = 6.0f, padY = 4.0f;
        float baseX = MARGIN - 5f + this.watermarkX.getValue().floatValue();
        float baseY = MARGIN + this.watermarkY.getValue().floatValue();
        float iconW = iconFont.getWidth("N");
        float iconY = baseY + padY + iconFont.getMetrics().capHeight() + 5f;
        ctx.drawString("N", baseX + padX, iconY, iconFont, textPaint);
        float titleX = baseX + padX + iconW + 4f;
        float titleY = baseY + padY + titleFont.getMetrics().capHeight();
        ctx.drawString(NiloreClient.CLIENT_NAME, titleX, titleY, titleFont, textPaint);
        String fpsStr = String.valueOf(mc.getFps());
        float fpsX = baseX + padX + iconW + 4f;
        float fpsY = titleY + titleFont.getMetrics().capHeight() - 7.0f + fpsFont.getMetrics().capHeight();
        ctx.drawString(fpsStr, fpsX, fpsY, fpsFont, fpsPaint);
    }

    private int getPing() {
        if (mc.player == null || mc.player.connection == null) return 0;
        PlayerInfo playerInfo = mc.player.connection.getPlayerInfo(mc.player.getUUID());
        return playerInfo != null ? playerInfo.getLatency() : 0;
    }

    @Override public String getDisplayName() { return ""; }
    @Override public String getModuleName() { return ""; }
    @Override public void onTick() { }
}