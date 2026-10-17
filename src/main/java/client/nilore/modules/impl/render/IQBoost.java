package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.GlHelper;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.settings.impl.TextSetting;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

public class IQBoost extends Module {
    public static IQBoost INSTANCE;

    public final NumberSetting value = new NumberSetting("IQBoost", 431, 0, 9178, 1);
    public final NumberSetting fontSize = new NumberSetting("Font Size", 13, 6, 100, 1);
    public final TextSetting suffix = new TextSetting("Suffix", " ");

    public final ModeSetting colorMode = new ModeSetting(
            "Color Mode",
            "White", "Red", "Green", "Blue", "Yellow", "Cyan", "Purple",
            "Rainbow", "Gradient", "Color Fusion"
    ).withDefault("White");

    public final NumberSetting gradAR = new NumberSetting("Grad A R", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));
    public final NumberSetting gradAG = new NumberSetting("Grad A G", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));
    public final NumberSetting gradAB = new NumberSetting("Grad A B", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));

    public final NumberSetting gradBR = new NumberSetting("Grad B R", 85, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));
    public final NumberSetting gradBG = new NumberSetting("Grad B G", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));
    public final NumberSetting gradBB = new NumberSetting("Grad B B", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));

    public final NumberSetting fusionSpeed = new NumberSetting("Fusion Speed", 1.0, 0.1, 10.0, 0.1,
            () -> colorMode.is("Color Fusion"));

    public final NumberSetting offsetX = new NumberSetting("X", 10, 0, 5000, 1);
    public final NumberSetting offsetY = new NumberSetting("Y", 10, 0, 5000, 1);

    // 鼠标拖动状态。位置仍然直接保存到 X / Y 设置中。
    private boolean dragging = false;
    private boolean lastLeftDown = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;

    public IQBoost() {
        super("IQBoost", Category.RENDER);
        INSTANCE = this;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        int displayValue = value.getValue().intValue();
        String text = "IQBoost " + displayValue + suffix.getValue();

        int color = resolveColor();

        FontRenderer font = FontPresets.axiformaBold(fontSize.getValue().floatValue());

        // 当鼠标被调出来（例如打开 ClickGUI/聊天等界面）时，
        // 可以直接按住 IQBoost 文字本体拖动。
        handleMouseDrag(text, font);

        float x = offsetX.getValue().floatValue();
        float y = offsetY.getValue().floatValue();

        Renderer.render(event.guiGraphics(), drawContext -> {
            try (Paint paint = new Paint()) {
                paint.setColor(color);
                drawContext.drawString(text, x, y, font, paint);
            }
        });
    }


    /**
     * 处理 HUD 拖动。
     *
     * 使用 GUI 缩放后的鼠标坐标，因此 GUI Scale 不同也能准确拖动。
     * 只有鼠标可见时才允许开始拖动，避免正常游戏攻击时误拖。
     */
    private void handleMouseDrag(String text, FontRenderer font) {
        if (mc.getWindow() == null) return;

        long window = mc.getWindow().getWindow();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // 没有打开任何 Screen 时，Minecraft 通常会锁定鼠标。
        // 这种状态不允许开始拖动，但如果 Screen 在拖动途中被关闭，则立即结束拖动。
        boolean mouseVisible = mc.screen != null;
        if (!mouseVisible) {
            dragging = false;
            lastLeftDown = leftDown;
            return;
        }

        double rawMouseX = mc.mouseHandler.xpos();
        double rawMouseY = mc.mouseHandler.ypos();

        double windowWidth = Math.max(1.0, mc.getWindow().getScreenWidth());
        double windowHeight = Math.max(1.0, mc.getWindow().getScreenHeight());

        float mouseX = (float) (rawMouseX * mc.getWindow().getGuiScaledWidth() / windowWidth);
        float mouseY = (float) (rawMouseY * mc.getWindow().getGuiScaledHeight() / windowHeight);

        float x = offsetX.getValue().floatValue();
        float y = offsetY.getValue().floatValue();
        float textWidth = GlHelper.getStringWidth(text, font);
        float textHeight = Math.max(font.getMetrics().capHeight(), fontSize.getValue().floatValue());

        // 稍微扩大一点点击范围，拖起来更方便。
        float padX = 4.0f;
        float padY = 3.0f;
        boolean hovered = mouseX >= x - padX
                && mouseX <= x + textWidth + padX
                && mouseY >= y - padY
                && mouseY <= y + textHeight + padY;

        // 只在“刚按下”的瞬间开始拖动，避免鼠标已经按住时经过文字突然吸附。
        if (leftDown && !lastLeftDown && hovered) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }

        if (!leftDown) {
            dragging = false;
        }

        if (dragging && leftDown) {
            float newX = mouseX - dragOffsetX;
            float newY = mouseY - dragOffsetY;

            float maxX = Math.max(0.0f, mc.getWindow().getGuiScaledWidth() - textWidth);
            float maxY = Math.max(0.0f, mc.getWindow().getGuiScaledHeight() - textHeight);

            newX = Math.max(0.0f, Math.min(newX, maxX));
            newY = Math.max(0.0f, Math.min(newY, maxY));

            // NumberSetting 使用整数步进，所以这里四舍五入写回。
            offsetX.setValue((double) Math.round(newX));
            offsetY.setValue((double) Math.round(newY));
        }

        lastLeftDown = leftDown;
    }

    /**
     * 根据当前 Color Mode 解析最终颜色（ARGB）。
     */
    private int resolveColor() {
        String mode = colorMode.getValue();
        long now = System.currentTimeMillis();

        switch (mode) {
            case "Red":    return 0xFFFF5555;
            case "Green":  return 0xFF55FF55;
            case "Blue":   return 0xFF5555FF;
            case "Yellow": return 0xFFFFFF55;
            case "Cyan":   return 0xFF55FFFF;
            case "Purple": return 0xFFAA55FF;

            case "Rainbow": {
                float hue = (now % 3000L) / 3000.0f;
                return Color.HSBtoRGB(hue, 0.8f, 1.0f);
            }

            case "Gradient": {
                int ar = gradAR.getValue().intValue();
                int ag = gradAG.getValue().intValue();
                int ab = gradAB.getValue().intValue();
                int br = gradBR.getValue().intValue();
                int bg = gradBG.getValue().intValue();
                int bb = gradBB.getValue().intValue();

                float t = (now % 2000L) / 2000.0f;
                int r = Math.round(ar + (br - ar) * t);
                int g = Math.round(ag + (bg - ag) * t);
                int b = Math.round(ab + (bb - ab) * t);
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }

            case "Color Fusion": {
                int ar = gradAR.getValue().intValue();
                int ag = gradAG.getValue().intValue();
                int ab = gradAB.getValue().intValue();
                int br = gradBR.getValue().intValue();
                int bg = gradBG.getValue().intValue();
                int bb = gradBB.getValue().intValue();

                float speed = fusionSpeed.getValue().floatValue();
                double phase = (now / 1000.0) * speed;

                float t = (float) ((Math.sin(phase) + 1.0) / 2.0);
                int r = Math.round(ar + (br - ar) * t);
                int g = Math.round(ag + (bg - ag) * t);
                int b = Math.round(ab + (bb - ab) * t);
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }

            case "White":
            default:
                return 0xFFFFFFFF;
        }
    }

    @Override
    public String getDisplayName() {
        return "§fIQBoost";
    }

    @Override
    public String getModuleName() {
        return "IQBoost";
    }

    @Override
    public void onTick() {
    }
}