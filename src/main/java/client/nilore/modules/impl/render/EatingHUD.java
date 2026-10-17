package client.nilore.modules.impl.render;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.screens.ChatScreen;

import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.render.RenderUtil;

/**
 * EatingHUD —— 进食/物品使用状态显示
 *
 * 效果：当玩家进食、喝药水或拉弓弩时，在屏幕中央偏下位置显示状态文字以及进度条。
 * 支持平滑动画，支持弓弩蓄力显示（含已装填状态）。
 */
public class EatingHUD extends Module {

    public static EatingHUD INSTANCE;

    // ==================== 设置 ====================
    public final BooleanSetting showProgressText = new BooleanSetting("Show Progress Text", true);
    public final BooleanSetting showBar = new BooleanSetting("Show Bar", true);

    // 开启：未进食/未拉弓时 HUD 也一直显示；关闭：仅在使用物品时显示
    public final BooleanSetting alwaysShow = new BooleanSetting("Always Show", false);

    // 弓弩蓄力支持开关
    public final BooleanSetting showBowCharge = new BooleanSetting("Show Bow Charge", true);

    // 文字颜色模式
    public final ModeSetting textColorMode = new ModeSetting("Text Color Mode",
            "White", "Rainbow", "Gradient 1", "Gradient 2", "Gradient 3").withDefault("White");

    // 拖动位置会写入这两个设置，配置保存后位置可保留
    public final NumberSetting hudX = new NumberSetting("HUD X", 0.0f, -2000.0f, 2000.0f, 1.0f);
    public final NumberSetting hudY = new NumberSetting("HUD Y", 0.0f, -2000.0f, 2000.0f, 1.0f);

    // 进度条颜色设置
    public final NumberSetting barRed = new NumberSetting("Bar Red", 255.0f, 0.0f, 255.0f, 1.0f);
    public final NumberSetting barGreen = new NumberSetting("Bar Green", 200.0f, 0.0f, 255.0f, 1.0f);
    public final NumberSetting barBlue = new NumberSetting("Bar Blue", 0.0f, 0.0f, 255.0f, 1.0f);

    // ==================== 状态 ====================
    private boolean isEating = false;
    private boolean isCharging = false;
    private boolean fullyCharged = false;
    private String itemName = "";

    // 动画状态
    private float renderBarAnimatedProgress = 0.0f;
    private float targetProgress = 0.0f;
    private long renderBarLastFrameMs = 0L;

    // 鼠标拖动状态
    private boolean dragging = false;
    private boolean mouseWasDown = false;
    private double dragStartMouseX = 0.0;
    private double dragStartMouseY = 0.0;
    private float dragStartHudX = 0.0f;
    private float dragStartHudY = 0.0f;

    public EatingHUD() {
        super("EatingHUD", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.resetState();
        super.onDisable();
    }

    private void resetState() {
        this.isEating = false;
        this.isCharging = false;
        this.fullyCharged = false;
        this.itemName = "";
        this.targetProgress = 0.0f;
        this.renderBarAnimatedProgress = 0.0f;
        this.renderBarLastFrameMs = 0L;
        this.dragging = false;
        this.mouseWasDown = false;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        // 1. 获取物品
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        ItemStack activeStack = mc.player.isUsingItem() ? mc.player.getUseItem() : ItemStack.EMPTY;

        boolean currentEating = false;
        boolean currentCharging = false;
        boolean currentFullyCharged = false;
        String currentItemName = "";
        float currentTargetProgress = 0.0f;

        // 2. 检测弓弩
        if (this.showBowCharge.getValue()) {
            ItemStack bowStack = null;
            boolean isUsingBow = false;

            if (mainHand.is(Items.BOW) || mainHand.is(Items.CROSSBOW)) {
                bowStack = mainHand;
                isUsingBow = activeStack == mainHand;
            } else if (offHand.is(Items.BOW) || offHand.is(Items.CROSSBOW)) {
                bowStack = offHand;
                isUsingBow = activeStack == offHand;
            }

            if (bowStack != null) {
                if (bowStack.is(Items.CROSSBOW)) {
                    // 弩：优先检测是否已经装填完毕
                    if (CrossbowItem.isCharged(bowStack)) {
                        currentEating = true;
                        currentCharging = true;
                        currentFullyCharged = true;
                        currentItemName = "Crossbow";
                        currentTargetProgress = 1.0f;
                    } else if (isUsingBow) {
                        // 正在拉栓
                        currentEating = true;
                        currentCharging = true;
                        currentItemName = "Crossbow";

                        int maxUseDuration = bowStack.getUseDuration();
                        int remainingTicks = mc.player.getUseItemRemainingTicks();
                        int chargeTicks = maxUseDuration - remainingTicks;

                        // 动态计算最大蓄力时间，兼容快速装填附魔
                        float maxChargeTicks = Math.max(1.0f, (float) maxUseDuration);
                        currentTargetProgress = Math.max(0.0f, Math.min(1.0f, (float) chargeTicks / maxChargeTicks));

                        if (currentTargetProgress >= 1.0f) {
                            currentFullyCharged = true;
                        }
                    }
                } else if (bowStack.is(Items.BOW) && isUsingBow) {
                    // 弓：只有正在使用才算蓄力
                    currentEating = true;
                    currentCharging = true;
                    currentItemName = "Bow";

                    int maxUseDuration = bowStack.getUseDuration();
                    int remainingTicks = mc.player.getUseItemRemainingTicks();
                    int chargeTicks = maxUseDuration - remainingTicks;

                    // 弓满蓄力固定 20 ticks
                    float maxChargeTicks = 20.0f;
                    currentTargetProgress = Math.max(0.0f, Math.min(1.0f, chargeTicks / maxChargeTicks));

                    if (currentTargetProgress >= 1.0f) {
                        currentFullyCharged = true;
                    }
                }
            }
        }

        // 3. 检测食物 / 药水（只有没有检测到弓弩时才检测）
        if (!currentEating && mc.player.isUsingItem()) {
            ItemStack useItem = mc.player.getUseItem();
            if (useItem.isEdible() || useItem.is(Items.POTION) || useItem.is(Items.GOLDEN_APPLE) || useItem.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                currentEating = true;
                currentItemName = getItemName(useItem);

                int maxUseDuration = useItem.getUseDuration();
                int remainingTicks = mc.player.getUseItemRemainingTicks();

                if (maxUseDuration > 0) {
                    currentTargetProgress = 1.0f - ((float) remainingTicks / (float) maxUseDuration);
                    currentTargetProgress = Math.max(0.0f, Math.min(1.0f, currentTargetProgress));
                } else {
                    currentTargetProgress = 1.0f;
                }
            }
        }

        this.isEating = currentEating;
        this.isCharging = currentCharging;
        this.fullyCharged = currentFullyCharged;
        this.itemName = currentItemName;
        this.targetProgress = currentTargetProgress;

        // 4. 动画平滑处理
        long now = System.currentTimeMillis();
        if (this.renderBarLastFrameMs == 0L) {
            this.renderBarLastFrameMs = now;
        }

        float deltaSeconds = Math.min(0.05f, Math.max(0.0f, (now - this.renderBarLastFrameMs) / 1000.0f));
        this.renderBarLastFrameMs = now;

        float animationSpeed = this.isEating ? 15.0f : 8.0f;
        float blend = 1.0f - (float) Math.exp(-animationSpeed * deltaSeconds);

        this.renderBarAnimatedProgress += (this.targetProgress - this.renderBarAnimatedProgress) * blend;

        if (Math.abs(this.targetProgress - this.renderBarAnimatedProgress) < 0.001f) {
            this.renderBarAnimatedProgress = this.targetProgress;
        }

        this.renderBarAnimatedProgress = Math.max(0.0f, Math.min(1.0f, this.renderBarAnimatedProgress));

        // 5. 显示逻辑
        if (!this.alwaysShow.getValue() && !this.isEating) {
            this.dragging = false;
            this.mouseWasDown = false;
            return;
        }

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        float barWidth = 110.0f;
        float barHeight = 3.0f;
        float textGap = 4.0f;

        float baseBarX = width / 2.0f - barWidth / 2.0f;
        float baseBarY = height / 2.0f + height * 0.10f;

        float barX = baseBarX + this.hudX.getValue().floatValue();
        float barY = baseBarY + this.hudY.getValue().floatValue();

        // 6. 生成本帧文字
        String statusText;
        if (this.isEating) {
            int percent = Math.round(this.renderBarAnimatedProgress * 100.0f);
            if (this.isCharging) {
                if (this.fullyCharged) {
                    // 蓄力满时显示的文字
                    statusText = this.itemName + " Charged!";
                } else {
                    // 蓄力中显示的文字
                    statusText = "Charging " + this.itemName + "... " + percent + "%";
                }
            } else {
                // 吃东西/喝药水
                statusText = "Eating " + this.itemName + "... " + percent + "%";
            }
        } else {
            statusText = "Wait";
        }

        int textWidth = mc.font.width(statusText);
        int fontHeight = mc.font.lineHeight;

        float textXFloat = barX + (barWidth - textWidth) / 2.0f;
        float textYFloat = barY - fontHeight - textGap;

        // 合并文字和进度条的实际范围
        float contentLeft = this.showProgressText.getValue()
                ? Math.min(barX, textXFloat)
                : barX;
        float contentRight = this.showProgressText.getValue()
                ? Math.max(barX + barWidth, textXFloat + textWidth)
                : barX + barWidth;
        float contentTop = this.showProgressText.getValue()
                ? textYFloat
                : barY;
        float contentBottom = this.showBar.getValue()
                ? barY + barHeight
                : textYFloat + fontHeight;

        // 拖动检测
        float hitPadding = 5.0f;
        this.updateDragging(
                contentLeft - hitPadding,
                contentTop - hitPadding,
                (contentRight - contentLeft) + hitPadding * 2.0f,
                (contentBottom - contentTop) + hitPadding * 2.0f
        );

        // 重新计算坐标（拖动可能改变坐标）
        barX = baseBarX + this.hudX.getValue().floatValue();
        barY = baseBarY + this.hudY.getValue().floatValue();
        textXFloat = barX + (barWidth - textWidth) / 2.0f;
        textYFloat = barY - fontHeight - textGap;

        // 渲染文字（使用渐变/彩虹逐字渲染）
        if (this.showProgressText.getValue()) {
            this.drawGradientString(
                    event,
                    statusText,
                    Math.round(textXFloat),
                    Math.round(textYFloat),
                    true
            );
        }

        // 渲染进度条
        if (this.showBar.getValue()) {
            int backgroundColor = new Color(30, 30, 36, 185).getRGB();
            int activeColor = new Color(
                    this.barRed.getValue().intValue(),
                    this.barGreen.getValue().intValue(),
                    this.barBlue.getValue().intValue(),
                    235
            ).getRGB();

            RenderUtil.drawFilledRect(
                    event.poseStack(),
                    barX,
                    barY,
                    barWidth,
                    barHeight,
                    backgroundColor
            );

            float visibleWidth = barWidth * this.renderBarAnimatedProgress;
            if (visibleWidth > 0.05f) {
                RenderUtil.drawFilledRect(
                        event.poseStack(),
                        barX,
                        barY,
                        visibleWidth,
                        barHeight,
                        activeColor
                );
            }
        }
    }

    /**
     * 根据当前设置，绘制带有颜色效果的字符串
     */
    private void drawGradientString(Render2DEvent event, String text, int x, int y, boolean shadow) {
        String mode = this.textColorMode.getValue();

        if ("White".equals(mode)) {
            event.guiGraphics().drawString(mc.font, text, x, y, 0xFFFFFFFF, shadow);
            return;
        }

        int length = text.length();
        int currentX = x;
        long time = System.currentTimeMillis();

        for (int i = 0; i < length; i++) {
            String character = String.valueOf(text.charAt(i));
            int color = getTextColorForIndex(mode, i, length, time);

            event.guiGraphics().drawString(mc.font, character, currentX, y, color, shadow);
            currentX += mc.font.width(character);
        }
    }

    /**
     * 获取指定索引字符的颜色
     */
    private int getTextColorForIndex(String mode, int index, int totalLength, long time) {
        if ("Rainbow".equals(mode)) {
            float hue = ((time % 3000) / 3000.0f + (index / (float) totalLength)) % 1.0f;
            return Color.HSBtoRGB(hue, 0.8f, 1.0f) | 0xFF000000;
        }

        float progress = totalLength <= 1 ? 0.0f : index / (float) (totalLength - 1);
        int r, g, b;

        switch (mode) {
            case "Gradient 1":
                r = lerp(255, 0, progress);
                g = lerp(105, 160, progress);
                b = lerp(180, 255, progress);
                break;
            case "Gradient 2":
                r = lerp(255, 255, progress);
                g = lerp(0, 255, progress);
                b = lerp(0, 0, progress);
                break;
            case "Gradient 3":
                r = lerp(0, 0, progress);
                g = lerp(255, 255, progress);
                b = lerp(0, 255, progress);
                break;
            default:
                return 0xFFFFFFFF;
        }
        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    private int lerp(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    /**
     * EatingHUD 拖动
     */
    private void updateDragging(float hudX, float hudY, float hudWidth, float hudHeight) {
        if (!(mc.screen instanceof ChatScreen)) {
            this.dragging = false;
            this.mouseWasDown = false;
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean leftDown = GLFW.glfwGetMouseButton(
                window,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ) == GLFW.GLFW_PRESS;

        double[] rawX = new double[1];
        double[] rawY = new double[1];
        GLFW.glfwGetCursorPos(window, rawX, rawY);

        double mouseX = rawX[0]
                * mc.getWindow().getGuiScaledWidth()
                / Math.max(1.0, mc.getWindow().getScreenWidth());

        double mouseY = rawY[0]
                * mc.getWindow().getGuiScaledHeight()
                / Math.max(1.0, mc.getWindow().getScreenHeight());

        boolean hovered = mouseX >= hudX
                && mouseX <= hudX + hudWidth
                && mouseY >= hudY
                && mouseY <= hudY + hudHeight;

        if (leftDown && !this.mouseWasDown && hovered) {
            this.dragging = true;
            this.dragStartMouseX = mouseX;
            this.dragStartMouseY = mouseY;
            this.dragStartHudX = this.hudX.getValue().floatValue();
            this.dragStartHudY = this.hudY.getValue().floatValue();
        }

        if (this.dragging) {
            if (!leftDown) {
                this.dragging = false;
            } else {
                float newX = this.dragStartHudX
                        + (float) (mouseX - this.dragStartMouseX);
                float newY = this.dragStartHudY
                        + (float) (mouseY - this.dragStartMouseY);

                newX = Math.max(-2000.0f, Math.min(2000.0f, newX));
                newY = Math.max(-2000.0f, Math.min(2000.0f, newY));

                this.hudX.setValue(newX);
                this.hudY.setValue(newY);
            }
        }

        this.mouseWasDown = leftDown;
    }

    /**
     * 获取物品显示名称
     */
    private String getItemName(ItemStack itemStack) {
        if (itemStack.is(Items.GOLDEN_APPLE)) {
            return "Gapple";
        }
        if (itemStack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return "Enchanted Gapple";
        }
        if (itemStack.is(Items.APPLE)) {
            return "Apple";
        }
        if (itemStack.is(Items.POTION)) {
            return "Potion";
        }
        if (itemStack.is(Items.BOW)) {
            return "Bow";
        }
        if (itemStack.is(Items.CROSSBOW)) {
            return "Crossbow";
        }
        return itemStack.getHoverName().getString();
    }

    @Override
    public String getSuffix() {
        return "HUD";
    }

    @Override
    public String getDisplayName() {
        return "§fEatingHUD";
    }

    @Override
    public String getModuleName() {
        return "EatingHUD";
    }

    @Override
    public void onTick() {

    }
}