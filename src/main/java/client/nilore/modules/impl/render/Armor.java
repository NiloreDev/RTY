package client.nilore.modules.impl.render;

import client.nilore.NiloreClient;
import client.nilore.utils.render.ColorUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.hud.HudElement;
import client.nilore.hud.ModuleListHud;
import client.nilore.render.DrawContext;
import client.nilore.render.Paint;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.List;

public class Armor extends HudElement {
    private static final float MIN_VISIBLE_EDGE = 4.0f;

    private final BooleanSetting backgroundSetting = new BooleanSetting("Background", true);
    private final NumberSetting bgAlphaSetting = new NumberSetting("Bg Alpha", 70, 0, 255, 1);
    private final BooleanSetting glowSetting = new BooleanSetting("Glow", false);
    private final NumberSetting glowRadiusSetting = new NumberSetting("Glow Radius", 12, 4, 40, 1);
    private final NumberSetting glowAlphaSetting = new NumberSetting("Glow Alpha", 120, 0, 255, 1);

    // ==================== 耐久显示开关 ====================
    private final BooleanSetting showDurabilitySetting = new BooleanSetting("Show Durability", true);
    // 显示手中物品开关
    private final BooleanSetting showHeldItemSetting = new BooleanSetting("Show Held Item", true);
    // 耐久显示为百分比开关
    private final BooleanSetting showDurabilityPercentSetting = new BooleanSetting("Durability Percent", false, () -> showDurabilitySetting.getValue());
    // 跟随主题颜色开关
    private final BooleanSetting followThemeSetting = new BooleanSetting("Follow Theme Color", false, () -> showDurabilitySetting.getValue());

    // 字体大小、阴影
    private final NumberSetting durabilityTextScale = new NumberSetting("Text Scale", 1.0f, 0.5f, 1.5f, 0.05f, () -> showDurabilitySetting.getValue());
    private final BooleanSetting durabilityTextShadow = new BooleanSetting("Text Shadow", true, () -> showDurabilitySetting.getValue());

    // 全局 Y 轴偏移（控制整个 HUD）
    private final NumberSetting hudYOffset = new NumberSetting("Hud Y Offset", 0.0f, -200.0f, 200.0f, 1.0f);

    // 文字精细微调（解决不适中问题）
    private final NumberSetting durabilityTextXOffset = new NumberSetting("Text X Offset", 0.0f, -50.0f, 50.0f, 1.0f, () -> showDurabilitySetting.getValue());
    private final NumberSetting durabilityTextYOffset = new NumberSetting("Text Y Offset", 2.0f, -50.0f, 50.0f, 1.0f, () -> showDurabilitySetting.getValue());

    private final Paint bgPaint = new Paint();

    public Armor() {
        super("Armor");
        this.setX(10.0f);
        this.setY(120.0f);
        this.bgPaint.setAntialias(true);
    }

    @Override
    public void registerSettings() {
        this.registerSetting(backgroundSetting, bgAlphaSetting, glowSetting, glowRadiusSetting, glowAlphaSetting,
                showDurabilitySetting, showHeldItemSetting, showDurabilityPercentSetting, followThemeSetting,
                durabilityTextScale, durabilityTextShadow, hudYOffset, durabilityTextXOffset, durabilityTextYOffset);
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

    @Override
    public void onRender2D(Render2DEvent render2DEvent, float x, float y) {
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
        if (mc.player == null) {
            return;
        }

        // 1. 收集要显示的物品列表
        List<ItemStack> itemsToRender = new ArrayList<>();

        // 优先添加主手和副手，使其显示在头盔左侧
        if (this.showHeldItemSetting.getValue()) {
            itemsToRender.add(mc.player.getMainHandItem());
            itemsToRender.add(mc.player.getOffhandItem());
        }

        // 添加盔甲
        itemsToRender.add(mc.player.getItemBySlot(EquipmentSlot.HEAD));
        itemsToRender.add(mc.player.getItemBySlot(EquipmentSlot.CHEST));
        itemsToRender.add(mc.player.getItemBySlot(EquipmentSlot.LEGS));
        itemsToRender.add(mc.player.getItemBySlot(EquipmentSlot.FEET));

        // 移除空的物品槽位，使显示更紧凑
        itemsToRender.removeIf(ItemStack::isEmpty);

        // 没有任何物品时直接跳过渲染
        if (itemsToRender.isEmpty()) {
            this.setWidth(0.0f);
            this.setHeight(0.0f);
            return;
        }

        DrawContext drawContext = glRenderEvent.drawContext();
        if (drawContext == null) {
            return;
        }

        // 2. 应用全局 Y 轴偏移
        float currentY = y + this.hudYOffset.getValue().floatValue();

        // 槽位宽度设为 26.0f，留足空间让 100% 也能原大小显示，解决模糊问题
        float slotSize = 26.0f;
        float gap = 2.0f;

        // 根据实际物品数量计算总宽度
        int itemCount = itemsToRender.size();
        float totalWidth = slotSize * itemCount + gap * (itemCount - 1);

        boolean showDurability = this.showDurabilitySetting.getValue();
        boolean showPercent = this.showDurabilityPercentSetting.getValue();
        float durabilityTextHeight = showDurability ? 14.0f : 0.0f;
        float totalHeight = slotSize + durabilityTextHeight;

        float padding = 3.0f;
        float bgX = x - padding;
        float bgY = currentY - padding; // 使用偏移后的 Y
        float bgW = totalWidth + padding * 2.0f;
        float bgH = totalHeight + padding * 2.0f;
        float bgRadius = 4.5f;

        if (this.backgroundSetting.getValue()) {
            if (this.glowSetting.getValue()) {
                float gRadius = this.glowRadiusSetting.getValue().floatValue();
                int gAlpha = this.glowAlphaSetting.getValue().intValue();
                if (gAlpha > 0 && gRadius > 0.0f) {
                    RenderUtil.drawShadow(drawContext.getPoseStack(),
                            bgX, bgY, bgW, bgH,
                            (int) gRadius, (gAlpha << 24) | 0x000000);
                    RenderUtil.enableBlend();
                }
            }

            int alpha = this.bgAlphaSetting.getValue().intValue();
            this.bgPaint.setColor(ColorUtil.fromARGB(0, 0, 0, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(bgX, bgY, bgW, bgH, bgRadius), this.bgPaint);
        }

        float xOffset = this.durabilityTextXOffset.getValue().floatValue();
        float yOffset = this.durabilityTextYOffset.getValue().floatValue();

        // 3. 遍历渲染物品
        for (int i = 0; i < itemsToRender.size(); i++) {
            float itemX = x + (float)i * (slotSize + gap);
            ItemStack stack = itemsToRender.get(i);

            // 渲染物品图标（物品通常占用16x16像素，在26的槽位里居中偏置）
            int iconX = (int)itemX + Math.round((slotSize - 16.0f) / 2.0f);
            glRenderEvent.guiGraphics().renderItem(stack, iconX, (int)currentY);
            glRenderEvent.guiGraphics().renderItemDecorations(mc.font, stack, iconX, (int)currentY);

            // 渲染耐久度
            if (showDurability && stack.isDamageableItem()) {
                int currentDurability = stack.getMaxDamage() - stack.getDamageValue();
                int maxDurability = stack.getMaxDamage();

                String durabilityText;
                if (showPercent) {
                    float percent = ((float) currentDurability / maxDurability) * 100.0f;
                    durabilityText = (int) percent + "%";
                } else {
                    durabilityText = String.valueOf(currentDurability);
                }

                // 颜色逻辑
                int color;
                if (this.followThemeSetting.getValue()) {
                    ModuleListHud moduleList = NiloreClient.getInstance().getHudManager() != null
                            ? NiloreClient.getInstance().getHudManager().getHudElement(ModuleListHud.class)
                            : null;
                    if (moduleList != null) {
                        color = moduleList.getThemeColor(0, 0.0f, 1);
                    } else {
                        color = 0xFFFFFFFF;
                    }
                } else {
                    float durabilityPercent = (float) currentDurability / maxDurability;
                    if (durabilityPercent > 0.6f) {
                        color = 0xFF00FF00; // 绿色
                    } else if (durabilityPercent > 0.3f) {
                        color = 0xFFFFFF00; // 黄色
                    } else {
                        color = 0xFFFF0000; // 红色
                    }
                }

                // 计算文字宽度与缩放
                float textWidth = mc.font.width(durabilityText);
                float textScale = this.durabilityTextScale.getValue().floatValue();

                // 强制取整坐标，保证像素对齐，并加上 X/Y 微调
                int textX = Math.round(itemX + (slotSize - textWidth * textScale) / 2.0f + xOffset);
                int textY = Math.round(currentY + slotSize + yOffset);

                var poseStack = glRenderEvent.guiGraphics().pose();
                poseStack.pushPose();
                poseStack.translate(textX, textY, 0.0f);
                poseStack.scale(textScale, textScale, 1.0f);

                boolean shadow = this.durabilityTextShadow.getValue();
                glRenderEvent.guiGraphics().drawString(mc.font, durabilityText, 0, 0, color, shadow);

                poseStack.popPose();
            }
        }

        this.setWidth(totalWidth);
        this.setHeight(totalHeight);
        this.clampToScreen(totalWidth, totalHeight);
    }

    @Override
    public void onSettings() {
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY) {
        this.setX((float) mouseX - this.getDragOffsetX());
        this.setY((float) mouseY - this.getDragOffsetY());
        this.clampToScreen(Math.max(this.getWidth(), 1.0f), Math.max(this.getHeight(), 1.0f));
    }

    @Override
    public void stopDragging() {
        boolean wasDragging = this.isDragging();
        super.stopDragging();
        if (wasDragging) {
            client.nilore.NiloreClient.getInstance().getConfigManager().saveAll();
        }
    }

    private void clampToScreen(float width, float height) {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float maxX = Math.max(MIN_VISIBLE_EDGE, screenWidth - Math.min(width, screenWidth) - MIN_VISIBLE_EDGE);
        float maxY = Math.max(MIN_VISIBLE_EDGE, screenHeight - Math.min(height, screenHeight) - MIN_VISIBLE_EDGE);
        this.setX(Mth.clamp(this.getX(), MIN_VISIBLE_EDGE, maxX));
        this.setY(Mth.clamp(this.getY(), MIN_VISIBLE_EDGE, maxY));
    }
}