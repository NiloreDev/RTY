package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * InventoryHud
 *
 * 效果参考图1：
 *   - 深色面板 + 深灰标题栏，标题左上角白色像素字
 *   - 3x9 主背包，可选 Hotbar 一行
 *   - 格子为方形，之间只有 1px 深色分隔，紧密相连
 *   - 可用鼠标在 GUI 打开状态下拖动 HUD
 *   - Size 设置项支持 0.0 ~ 10.0 连续缩放
 */
public class InventoryHud extends Module {

    // =========================
    // 设置
    // =========================
    private final NumberSetting posX =
            new NumberSetting("Pos X", 5.0f, 0.0f, 1920.0f, 1.0f);

    private final NumberSetting posY =
            new NumberSetting("Pos Y", 5.0f, 0.0f, 1080.0f, 1.0f);

    /**
     * 大小：连续缩放，范围 0.0 ~ 10.0。
     *   1.0  = 原始大小
     *   1.3  = 略大
     *   2.0  = 两倍
     *   0.5  = 缩小一半
     */
    private final NumberSetting size =
            new NumberSetting("Size", 1.0f, 0.0f, 10.0f, 0.05f);

    private final BooleanSetting showHotbar =
            new BooleanSetting("Show Hotbar", true);

    private final BooleanSetting background =
            new BooleanSetting("Background", true);

    // 显示 Inventory 标题文字
    private final BooleanSetting showTitle =
            new BooleanSetting("Show Title", true);

    // 显示物品槽黑色/灰色背景框
    private final BooleanSetting showSlotBackground =
            new BooleanSetting("Show Slot Background", true);

    // =========================
    // HUD 参数
    // =========================
    private static final int SLOT_SIZE = 18;
    private static final int GRID_WIDTH = 9;
    private static final int GRID_HEIGHT = 3;
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 16;

    // 颜色
    private static final int PANEL_BG_COLOR    = 0xE0141518; // 整体面板深黑
    private static final int HEADER_BG_COLOR   = 0xFF26272B; // 标题栏深灰
    private static final int SLOT_BG_COLOR     = 0xFF3A3B40; // 单个格子
    private static final int TITLE_TEXT_COLOR  = 0xFFFFFFFF;

    // =========================
    // 拖动状态
    // =========================
    private boolean dragging = false;
    private boolean lastLeftDown = false;
    private float dragOffsetX = 0.0f;
    private float dragOffsetY = 0.0f;

    public InventoryHud() {
        super("InventoryHud", Category.RENDER);

        this.registerSetting(posX);
        this.registerSetting(posY);
        this.registerSetting(size);
        this.registerSetting(showHotbar);
        this.registerSetting(background);
        this.registerSetting(showTitle);
        this.registerSetting(showSlotBackground);
    }

    @Override
    public String getDisplayName() {
        return "InventoryHud";
    }

    @Override
    public String getModuleName() {
        return "InventoryHud";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onEnable() {
        dragging = false;
        lastLeftDown = false;
    }

    @Override
    protected void onDisable() {
        dragging = false;
        lastLeftDown = false;
    }

    // ================================================================
    // Render2D
    // ================================================================
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        if (mc.player == null || mc.level == null) return;

        GuiGraphics guiGraphics = event.guiGraphics();
        if (guiGraphics == null) return;

        // 直接从设置读取缩放，范围 0.0 ~ 10.0
        float scaleValue = size.getValue().floatValue();

        // scale = 0 时矩阵退化，直接跳过渲染，避免奇怪的显示问题。
        if (scaleValue <= 0.001f) return;

        final int totalRows = GRID_HEIGHT + (showHotbar.getValue() ? 1 : 0);

        final float baseWidth  = PADDING * 2.0f + GRID_WIDTH * SLOT_SIZE;
        final float baseHeight = HEADER_HEIGHT + PADDING * 2.0f + totalRows * SLOT_SIZE;

        // 拖动检测
        updateDragging(baseWidth * scaleValue, baseHeight * scaleValue);

        float startX = posX.getValue().floatValue();
        float startY = posY.getValue().floatValue();

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(startX, startY, 0.0f);
        poseStack.scale(scaleValue, scaleValue, 1.0f);

        // ------------------------------------------------------------
        // 1) 面板背景（整体深色板）
        // ------------------------------------------------------------
        if (background.getValue()) {
            try (Paint paint = new Paint()) {
                paint.setColor(PANEL_BG_COLOR);
                drawRoundedRect(
                        guiGraphics,
                        RoundedRectangle.ofXYWHR(0.0f, 0.0f, baseWidth, baseHeight, 2.0f),
                        paint,
                        startX, startY, scaleValue
                );
            }

            // 标题栏（略亮一点的深灰）
            try (Paint paint = new Paint()) {
                paint.setColor(HEADER_BG_COLOR);
                drawRoundedRect(
                        guiGraphics,
                        RoundedRectangle.ofXYWHR(0.0f, 0.0f, baseWidth, HEADER_HEIGHT, 2.0f),
                        paint,
                        startX, startY, scaleValue
                );
            }
        }

        // ------------------------------------------------------------
        // 2) 标题 "Inventory"
        // ------------------------------------------------------------
        if (showTitle.getValue()) {
            guiGraphics.drawString(
                    mc.font,
                    "Inventory",
                    PADDING + 1,
                    4,
                    TITLE_TEXT_COLOR,
                    true
            );
        }

        final int contentTop = HEADER_HEIGHT + PADDING;

        // ------------------------------------------------------------
        // 3) 格子：方形，紧密相连，只留 1px 深色分隔
        // ------------------------------------------------------------
        if (showSlotBackground.getValue()) {
            for (int row = 0; row < totalRows; row++) {
                for (int col = 0; col < GRID_WIDTH; col++) {
                    float slotX = PADDING + col * SLOT_SIZE;
                    float slotY = contentTop + row * SLOT_SIZE;

                    try (Paint paint = new Paint()) {
                        paint.setColor(SLOT_BG_COLOR);
                        drawRoundedRect(
                                guiGraphics,
                                RoundedRectangle.ofXYWHR(
                                        slotX,
                                        slotY,
                                        SLOT_SIZE - 1,
                                        SLOT_SIZE - 1,
                                        0.0f
                                ),
                                paint,
                                startX, startY, scaleValue
                        );
                    }
                }
            }
        }

        // ------------------------------------------------------------
        // 4) 物品
        // ------------------------------------------------------------
        for (int row = 0; row < totalRows; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                int visualIndex = row * GRID_WIDTH + col;
                ItemStack stack = ItemStack.EMPTY;

                if (visualIndex < GRID_HEIGHT * GRID_WIDTH) {
                    // 主背包：原版索引 9 ~ 35
                    int inventoryIndex = 9 + visualIndex;
                    if (inventoryIndex < mc.player.getInventory().getContainerSize()) {
                        stack = mc.player.getInventory().getItem(inventoryIndex);
                    }
                } else if (showHotbar.getValue()) {
                    // Hotbar：索引 0 ~ 8
                    int hotbarIndex = visualIndex - GRID_HEIGHT * GRID_WIDTH;
                    if (hotbarIndex >= 0 && hotbarIndex < 9) {
                        stack = mc.player.getInventory().getItem(hotbarIndex);
                    }
                }

                if (!stack.isEmpty()) {
                    int itemX = PADDING + col * SLOT_SIZE + 1;
                    int itemY = contentTop + row * SLOT_SIZE + 1;

                    guiGraphics.renderItem(stack, itemX, itemY);
                    guiGraphics.renderItemDecorations(mc.font, stack, itemX, itemY);
                }
            }
        }

        poseStack.popPose();
    }

    // ================================================================
    // 鼠标拖动
    // ================================================================
    private void updateDragging(float renderedWidth, float renderedHeight) {
        // 游戏正常锁鼠标时不拖 HUD。
        // 打开 ClickGUI / 聊天 / 背包等 Screen 后鼠标可用，此时才能拖。
        if (mc.screen == null) {
            dragging = false;
            lastLeftDown = false;
            return;
        }

        long window = mc.getWindow().getWindow();

        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;

        double rawMouseX = mc.mouseHandler.xpos();
        double rawMouseY = mc.mouseHandler.ypos();

        // GLFW 鼠标坐标是窗口像素坐标，需要换算成 Minecraft GUI 坐标。
        double screenWidth  = Math.max(1.0, mc.getWindow().getScreenWidth());
        double screenHeight = Math.max(1.0, mc.getWindow().getScreenHeight());

        float mouseX = (float) (rawMouseX
                * mc.getWindow().getGuiScaledWidth()
                / screenWidth);

        float mouseY = (float) (rawMouseY
                * mc.getWindow().getGuiScaledHeight()
                / screenHeight);

        float x = posX.getValue().floatValue();
        float y = posY.getValue().floatValue();

        boolean hovered = mouseX >= x
                && mouseX <= x + renderedWidth
                && mouseY >= y
                && mouseY <= y + renderedHeight;

        // 只在鼠标“刚按下”的那一帧开始拖，防止偏移量反复重算。
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

            float maxX = Math.max(0.0f,
                    mc.getWindow().getGuiScaledWidth() - renderedWidth);
            float maxY = Math.max(0.0f,
                    mc.getWindow().getGuiScaledHeight() - renderedHeight);

            newX = clamp(newX, 0.0f, maxX);
            newY = clamp(newY, 0.0f, maxY);

            posX.setValue((double) newX);
            posY.setValue((double) newY);
        }

        lastLeftDown = leftDown;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 使用项目现有 Renderer / DrawContext 绘制圆角矩形。
     */
    private void drawRoundedRect(GuiGraphics guiGraphics, RoundedRectangle rect, Paint paint,
                                 float translateX, float translateY, float scale) {
        Renderer.render(guiGraphics, drawContext -> {
            try {
                drawContext.getPoseStack().pushPose();
                drawContext.getPoseStack().translate(translateX, translateY, 0.0f);
                drawContext.getPoseStack().scale(scale, scale, 1.0f);
                drawContext.drawRoundedRect(rect, paint);
                drawContext.getPoseStack().popPose();
            } catch (Exception ignored) {
                try {
                    drawContext.getPoseStack().popPose();
                } catch (Exception ignoredToo) {
                }
            }
        });
    }
}