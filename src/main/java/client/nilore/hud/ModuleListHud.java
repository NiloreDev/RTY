package client.nilore.hud;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import client.nilore.NiloreClient;
import client.nilore.utils.render.ColorUtil;
import client.nilore.utils.render.ShaderCompatUtil;
import net.minecraft.util.Mth;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.Module;
import client.nilore.modules.impl.render.Interface;
import client.nilore.render.DrawContext;
import client.nilore.render.FontRenderer;
import client.nilore.render.FontPresets;
import client.nilore.render.GlHelper;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.SmoothAnimationTimer;
import client.nilore.utils.math.Easings;
import client.nilore.utils.render.GradientTheme;
import client.nilore.utils.render.RenderUtil;

public class ModuleListHud extends HudElement {

    public boolean isSuffixBracketsEnabled() { return false; }
    public boolean isSuffixColorEnabled() { return false; }
    public boolean isShowSuffix() { return false; }
    public boolean isSuffixLowercaseEnabled() { return false; }
    public String getSelectedSuffixColorCode() { return ""; }
    public int getSelectedSuffixColorArgb() { return 0; }

    private enum Alignment { LEFT, RIGHT }

    private static final class AnimatedRow {
        private final SmoothAnimationTimer progressAnim = new SmoothAnimationTimer();
        private final String name;
        private String fullDisplayName;
        private float textWidth;
        private float rowWidth;
        private boolean targetVisible;
        private Module module;

        private AnimatedRow(Module module) {
            this.module = module;
            this.name = module.getName();
            this.progressAnim.setCurrentValue(0.0f);
            this.progressAnim.setToValue(0.0f);
        }

        private void updateMetrics(String displayName, float textWidth, float rowWidth) {
            this.fullDisplayName = displayName;
            this.textWidth = textWidth;
            this.rowWidth = rowWidth;
        }

        private void setTargetVisible(boolean visible) {
            if (this.targetVisible == visible) return;
            this.targetVisible = visible;
            double target = visible ? 1.0 : 0.0;
            float current = this.progressAnim.getValueF();
            this.progressAnim.setCurrentValue(current);
            this.progressAnim.setFromValue(current);
            this.progressAnim.setToValue(target);
            this.progressAnim.setStartTime(System.currentTimeMillis());
            this.progressAnim.setDuration(visible ? 240.0 : 180.0);
            this.progressAnim.setEasing(visible ? Easings.EASE_OUT_POW3 : Easings.EASE_IN_POW3);
        }

        private void tick() { this.progressAnim.tick(); }
        private float progress() { return Mth.clamp(this.progressAnim.getValueF(), 0.0f, 1.0f); }
        private boolean isFinishedRemoving() {
            return !this.targetVisible && this.progress() <= 0.01f && this.progressAnim.isDone();
        }
    }

    private static final class RowRenderLayout {
        private final AnimatedRow row;
        private final int rowIndex;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float fullHeight;
        private final float progress;
        private float linkHeight;

        private RowRenderLayout(AnimatedRow row, int rowIndex, float x, float y, float width, float height,
                                float fullHeight, float progress) {
            this.row = row;
            this.rowIndex = rowIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fullHeight = fullHeight;
            this.progress = progress;
        }

        private void linkTo(RowRenderLayout next) {
            this.linkHeight = Math.max(0.0f, next.y - (this.y + this.height));
        }

        private float visualHeight(boolean broken) {
            return this.height + (broken ? 0.0f : this.linkHeight);
        }
    }

    private static final String[] SUFFIX_COLOR_NAMES = {
            "Red", "Gold", "Yellow", "Green", "DarkGreen",
            "Aqua", "DarkAqua", "Blue", "DarkBlue", "Pink",
            "Purple", "Gray", "DarkGray", "White", "DarkRed",
            "Black", "LightRed", "LightGreen", "LightAqua", "LightPink"
    };

    private static final String[] SUFFIX_TEXT = {
            " [Red]", " [Gold]", " [Yellow]", " [Green]", " [DarkGreen]",
            " [Aqua]", " [DarkAqua]", " [Blue]", " [DarkBlue]", " [Pink]",
            " [Purple]", " [Gray]", " [DarkGray]", " [White]", " [DarkRed]",
            " [Black]", " [LightRed]", " [LightGreen]", " [LightAqua]", " [LightPink]"
    };

    private static final int[] SUFFIX_ARGB = {
            0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF00AA00,
            0xFF55FFFF, 0xFF00AAAA, 0xFF5555FF, 0xFF0000AA, 0xFFFF55FF,
            0xFFAA55AA, 0xFFAAAAAA, 0xFF555555, 0xFFFFFFFF, 0xFFAA0000,
            0xFF000000, 0xFFFF8888, 0xFF88FF88, 0xFF88FFFF, 0xFFFF88FF
    };

    private static final int NAVEN_SUFFIX_COLOR = 0xFFAAAAAA;

    private static final float MIN_VISIBLE_EDGE = 4.0f;
    private static final float DEFAULT_ROW_HEIGHT = 15f;
    private static final float DEFAULT_PADDING_X = 3f;
    private static final float DEFAULT_PADDING_Y = 3.5f;
    private static final float DEFAULT_ROW_SPACING = 0.0f;
    private static final float DEFAULT_RADIUS = 2f;
    private static final float SLIDE_DISTANCE = 18.0f;

    private static final long NAVEN_CYCLE_MS = 7000L;
    private static final float NAVEN_BAND_SPAN = 0.62f;
    private static final float NAVEN_SATURATION = 0.95f;
    private static final float NAVEN_BRIGHTNESS = 1.0f;

    private ModeSetting renderStyle;
    private NumberSetting navenScale;

    private ModeSetting sideMode;
    private BooleanSetting breakEnabled;
    private BooleanSetting showSuffix;
    private BooleanSetting suffixColorEnabled;
    private BooleanSetting suffixLowercaseEnabled;
    private BooleanSetting important;
    private NumberSetting paddingX;
    private NumberSetting paddingY;
    private NumberSetting rowHeight;
    private NumberSetting rowSpacing;

    private BooleanSetting suffixBracketsEnabled;
    private ModeSetting suffixColorMode;

    private BooleanSetting backgroundEnabled;
    private NumberSetting backgroundRadius;
    private NumberSetting backgroundAlpha;

    // ========== Rise 风格背景 ==========
    private BooleanSetting riseBackgroundEnabled;
    private NumberSetting riseBackgroundBlur;
    private NumberSetting riseBackgroundAlpha;
    private NumberSetting riseBackgroundRadius;

    private BooleanSetting glowEnabled;
    private NumberSetting glowRadius;
    private NumberSetting glowAlpha;

    private BooleanSetting sideLineEnabled;
    private ModeSetting sideLineMode;
    private NumberSetting sideLineWidth;

    private BooleanSetting useClientColor;
    private ModeSetting textColorMode;
    private ModeSetting gradientTheme;
    private NumberSetting rainbowSpeed;
    private NumberSetting rainbowSaturation;
    private NumberSetting rainbowBrightness;
    private NumberSetting rainbowOffset;
    private BooleanSetting useMinecraftFont;

    private static final boolean FONT_GLOW_ENABLED = false;

    private final SmoothAnimationTimer widthAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer heightAnim = new SmoothAnimationTimer();
    private final Map<Module, AnimatedRow> rowStates = new IdentityHashMap<>();
    private boolean animationReady;

    public ModuleListHud() {
        super("ModuleList");
        this.setX(4.0f);
        this.setY(18.0f);
        this.setWidth(0.0f);
        this.setHeight(0.0f);
        this.setEnabled(true);
    }

    @Override
    public void registerSettings() {
        this.renderStyle = new ModeSetting("Style", "Normal", "Naven").withDefault("Normal");

        this.sideMode = new ModeSetting("Side Mode", "Auto", "Left", "Right")
                .withDefault("Auto");
        this.showSuffix = new BooleanSetting("Show Suffix", true);
        this.suffixColorEnabled = new BooleanSetting("Suffix Color", true,
                () -> this.showSuffix.getValue());
        this.suffixLowercaseEnabled = new BooleanSetting("Suffix Lowercase", false,
                () -> this.showSuffix.getValue());
        this.suffixBracketsEnabled = new BooleanSetting("Suffix Brackets", true,
                () -> this.showSuffix.getValue());

        this.important = new BooleanSetting("Important", false);
        this.paddingX = new NumberSetting("Padding X", DEFAULT_PADDING_X, 0.0f, 12.0f, 0.25f);
        this.paddingY = new NumberSetting("Padding Y", DEFAULT_PADDING_Y, 0.0f, 8.0f, 0.25f);
        this.rowHeight = new NumberSetting("Row Height", DEFAULT_ROW_HEIGHT, 9.0f, 24.0f, 0.25f);
        this.rowSpacing = new NumberSetting("Row Spacing", DEFAULT_ROW_SPACING, 0.0f, 8.0f, 0.25f);
        this.useMinecraftFont = new BooleanSetting("Minecraft Font", false);

        this.suffixColorMode = new ModeSetting("Suffix Color", "Red", "Gold", "Yellow", "Green", "DarkGreen",
                "Aqua", "DarkAqua", "Blue", "DarkBlue", "Pink",
                "Purple", "Gray", "DarkGray", "White", "DarkRed",
                "Black", "LightRed", "LightGreen", "LightAqua", "LightPink")
                .withDefault("White")
                .withVisibility(() -> this.showSuffix.getValue() && this.suffixColorEnabled.getValue());

        this.backgroundEnabled = new BooleanSetting("Background", true);
        this.backgroundRadius = new NumberSetting("Background Radius", DEFAULT_RADIUS, 0.0f, 10.0f, 0.25f,
                () -> this.backgroundEnabled.getValue());
        this.backgroundAlpha = new NumberSetting("Background Alpha", 80.0f, 0.0f, 255.0f, 1.0f,
                () -> this.backgroundEnabled.getValue());

        // Naven 独占大小设置：Normal 模式完全看不到。
        this.navenScale = new NumberSetting("Naven Size", 1.0f, 0.5f, 2.0f, 0.05f,
                this::isNavenStyle);

        // ============================================================
        // 【修改】Rise Background 整组：两种模式都显示，都能使用。
        // 之前的可见性条件 this::isNavenStyle 已经去掉。
        // ============================================================
        this.riseBackgroundEnabled = new BooleanSetting("Rise Background", false);
        this.riseBackgroundBlur = new NumberSetting("Rise Blur", 15.0f, 1.0f, 40.0f, 1.0f,
                () -> this.riseBackgroundEnabled.getValue());
        this.riseBackgroundAlpha = new NumberSetting("Rise Alpha", 0.95f, 0.0f, 1.0f, 0.01f,
                () -> this.riseBackgroundEnabled.getValue());
        this.riseBackgroundRadius = new NumberSetting("Rise Radius", 10.0f, 0.0f, 20.0f, 0.5f,
                () -> this.riseBackgroundEnabled.getValue());
        // ============================================================

        this.breakEnabled = new BooleanSetting("Break", false, this::isNormalStyle);

        this.glowEnabled = new BooleanSetting("Glow", false, this::isNormalStyle);
        this.glowRadius = new NumberSetting("Glow Radius", 15.0f, 4.0f, 40.0f, 1.0f,
                () -> this.isNormalStyle() && this.glowEnabled.getValue());
        this.glowAlpha = new NumberSetting("Glow Alpha", 150.0f, 0.0f, 255.0f, 1.0f,
                () -> this.isNormalStyle() && this.glowEnabled.getValue());

        this.sideLineEnabled = new BooleanSetting("Side Line", false, this::isNormalStyle);
        this.sideLineMode = new ModeSetting("Side Line Mode", "Auto", "Left", "Right")
                .withDefault("Auto")
                .withVisibility(() -> this.isNormalStyle() && this.sideLineEnabled.getValue());
        this.sideLineWidth = new NumberSetting("Side Line Width", 0.8f, 0.5f, 5.0f, 0.25f,
                () -> this.isNormalStyle() && this.sideLineEnabled.getValue());

        this.useClientColor = new BooleanSetting("Use Client Color", false, this::isNormalStyle);
        this.textColorMode = new ModeSetting("Text Color Mode", "Gradient", "Solid")
                .withDefault("Gradient")
                .withVisibility(this::isNormalStyle);
        this.gradientTheme = new ModeSetting("Gradient Theme",
                "Rainbow", "Aurora", "Sunset", "Ocean", "Cotton Candy",
                "Lavender", "Peach", "Mint", "Cyberpunk", "Drift", "Cyan",
                "CyanRainbowVertical",
                "Crimson", "Ruby", "Rose", "Candy", "Hot Pink", "Burgundy",
                "Tangerine", "Pumpkin", "Coral", "Amber", "Mango",
                "Lemon", "Banana", "Honey", "Mustard", "Butter",
                "Forest", "Lime", "Olive", "Teal", "Emerald", "Sage",
                "Sky", "Navy", "Azure", "Turquoise", "Indigo", "Sapphire", "Cerulean",
                "Plum", "Lilac", "Amethyst", "Magenta", "Orchid",
                "Blush", "Bubblegum", "Salmon", "Flamingo", "Rose Gold",
                "Silver", "Champagne", "Pearl", "Onyx", "Platinum",
                "Neon", "Pastel", "Vintage", "Retro", "Galaxy")
                .withDefault("Cotton Candy")
                .withVisibility(() -> this.isNormalStyle() && "Gradient".equals(this.textColorMode.getValue()));

        this.rainbowSpeed = new NumberSetting("Rainbow Speed", 5.0f, 1.0f, 240.0f, 1.0f,
                this::isNormalStyle);
        this.rainbowSaturation = new NumberSetting("Rainbow Saturation", 90.0f, 0.0f, 100.0f, 1.0f,
                this::isNormalStyle);
        this.rainbowBrightness = new NumberSetting("Rainbow Brightness", 100.0f, 10.0f, 100.0f, 1.0f,
                this::isNormalStyle);
        this.rainbowOffset = new NumberSetting("Rainbow Offset", 85.0f, 0.0f, 90.0f, 1.0f,
                this::isNormalStyle);

        this.registerSetting(renderStyle, sideMode, breakEnabled, showSuffix, suffixColorEnabled, suffixBracketsEnabled,
                suffixColorMode, suffixLowercaseEnabled, important,
                paddingX, paddingY, rowHeight, rowSpacing, navenScale,
                backgroundEnabled, backgroundRadius, backgroundAlpha,
                riseBackgroundEnabled, riseBackgroundBlur, riseBackgroundAlpha, riseBackgroundRadius,
                sideLineEnabled, sideLineMode, sideLineWidth,
                glowEnabled, glowRadius, glowAlpha,
                useClientColor, useMinecraftFont, textColorMode, gradientTheme,
                rainbowSpeed, rainbowSaturation, rainbowBrightness, rainbowOffset);
    }

    private int getSelectedSuffixColorIndex() {
        String selected = this.suffixColorMode.getValue();
        for (int i = 0; i < SUFFIX_COLOR_NAMES.length; i++) {
            if (SUFFIX_COLOR_NAMES[i].equals(selected)) return i;
        }
        return 0;
    }

    private int getSelectedSuffixColor() { return SUFFIX_ARGB[getSelectedSuffixColorIndex()]; }
    private String getSelectedSuffixText() { return SUFFIX_TEXT[getSelectedSuffixColorIndex()]; }

    @Override public String getDisplayName() { return ""; }
    @Override public String getModuleName() { return ""; }
    @Override public void onTick() { }

    private List<AnimatedRow> updateRows() {
        float scale = this.isNavenStyle() ? this.getNavenScale() : 1.0f;
        FontRenderer font = FontPresets.pingfang(16.0f * scale);
        boolean importantOnly = this.important.getValue();
        boolean useMcFont = this.useMinecraftFont.getValue();
        for (Module module : NiloreClient.getInstance().getModuleManager().getModules()) {
            if (module == this || module.getName().isEmpty() || module.isHiddenInModuleList()) {
                this.rowStates.remove(module);
                continue;
            }
            if (importantOnly && module.getCategory() == client.nilore.modules.Category.RENDER) {
                this.rowStates.remove(module);
                continue;
            }
            AnimatedRow row = this.rowStates.get(module);
            if (module.isEnabled()) {
                if (row == null) {
                    row = new AnimatedRow(module);
                    this.rowStates.put(module, row);
                }
                String displayName = this.displayName(module);
                float textWidth = useMcFont
                        ? mc.font.width(displayName) * scale
                        : GlHelper.getStringWidth(displayName, font);
                row.updateMetrics(displayName, textWidth, this.rowWidth(textWidth));
                row.setTargetVisible(true);
            } else if (row != null) {
                row.setTargetVisible(false);
            }
        }
        this.rowStates.values().forEach(AnimatedRow::tick);
        this.rowStates.values().removeIf(AnimatedRow::isFinishedRemoving);

        List<AnimatedRow> rows = new ArrayList<>(this.rowStates.values());
        rows.sort((a, b) -> Float.compare(b.textWidth, a.textWidth));
        return rows;
    }

    private String displayName(Module module) {
        String name = module.getName();
        if (!this.showSuffix.getValue()) return name;
        String suffix = module.getSuffix();
        if (suffix == null || suffix.isBlank()) return name;
        suffix = suffix.trim();
        if (!this.suffixBracketsEnabled.getValue()) {
            suffix = suffix.replace("[", "").replace("]", "").trim();
        }
        if (this.suffixLowercaseEnabled.getValue()) {
            suffix = suffix.toLowerCase(Locale.ROOT);
        }
        return name + " " + suffix;
    }

    private int getSuffixColor(Module module) {
        if (!this.suffixColorEnabled.getValue()) return 0xFF888888;
        return getSelectedSuffixColor();
    }

    private boolean isNavenStyle() { return "Naven".equals(this.renderStyle.getValue()); }
    private boolean isNormalStyle() { return !this.isNavenStyle(); }
    private float getNavenScale() {
        return this.navenScale == null ? 1.0f : this.navenScale.getValue().floatValue();
    }

    @Override
    public void onRender2D(Render2DEvent event, float x, float y) {
        ShaderCompatUtil.beginUIRender();
        try {
            if (!this.shouldRender()) return;
            List<AnimatedRow> rows = this.updateRows();
            if (rows.isEmpty()) {
                this.setWidth(0.0f);
                this.setHeight(0.0f);
                return;
            }

            float targetWidth = this.measureWidth(rows);
            float targetHeight = this.measureHeight(rows);
            float previousWidth = this.getWidth();
            Alignment anchorBeforeResize = this.resolveAlignment(x, Math.max(previousWidth, targetWidth));
            this.updateSizeAnimation(targetWidth, targetHeight);

            float width = this.widthAnim.getValueF();
            float height = this.heightAnim.getValueF();
            if (anchorBeforeResize == Alignment.RIGHT && !this.isDragging() && previousWidth > 0.0f) {
                this.setX(this.getX() + previousWidth - width);
            }
            this.clampToScreen(width, height);
            this.setWidth(width);
            this.setHeight(height);

            float drawX = this.getX();
            float drawY = this.getY();
            Alignment alignment = this.resolveAlignment(drawX, width);

            // ============================================================
            // 【修改】Naven 风格
            // Rise Background 现在两种模式都显示，逻辑不变。
            // ============================================================
            if (this.isNavenStyle()) {
                if (this.riseBackgroundEnabled.getValue()) {
                    this.renderRiseBackground(event, rows, drawX, drawY, width, alignment);
                }
                Renderer.render(event.guiGraphics(),
                        dc -> this.renderNavenRows(dc, rows, drawX, drawY, width, alignment));
                return;
            }

            // ============================================================
            // 【修改】Normal 原版风格
            // 也支持 Rise Background（用 1.0 倍缩放，不用 Naven Size）。
            // ============================================================
            if (this.riseBackgroundEnabled.getValue()) {
                this.renderRiseBackground(event, rows, drawX, drawY, width, alignment);
            }
            Renderer.render(event.guiGraphics(),
                    drawContext -> this.renderRows(drawContext, rows, drawX, drawY, width, alignment));
        } finally {
            ShaderCompatUtil.endUIRender();
        }
    }

    private void renderRiseBackground(Render2DEvent event, List<AnimatedRow> rows, float drawX, float drawY,
                                      float width, Alignment alignment) {
        // 【修改】Normal 模式下用 1.0 倍缩放；Naven 模式下用 Naven Size。
        float scale = this.isNavenStyle() ? this.getNavenScale() : 1.0f;
        float radius = this.riseBackgroundRadius.getValue().floatValue() * scale;
        float blur = this.riseBackgroundBlur.getValue().floatValue() * scale;
        float alpha = this.riseBackgroundAlpha.getValue().floatValue();
        List<RowRenderLayout> riseLayouts = this.computeRowLayouts(rows, drawX, drawY, width, alignment);
        var poseStack = event.guiGraphics().pose();
        poseStack.pushPose();
        try {
            final float expandX = 1.5f * scale;
            final float expandY = 1.0f * scale;
            for (RowRenderLayout layout : riseLayouts) {
                if (layout.progress <= 0.01f) continue;
                float rowX = layout.x - expandX;
                float rowY = layout.y - expandY;
                float rowW = layout.width + expandX * 2.0f;
                float rowH = layout.height + expandY * 2.0f;
                if (rowW <= 0.1f || rowH <= 0.1f) continue;
                RenderUtil.drawBlurredRect(poseStack, rowX, rowY, rowW, rowH,
                        radius, blur, alpha * layout.progress, 0);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private boolean shouldRender() {
        if (!this.isEnabled()) return false;
        Interface interfaceModule = NiloreClient.getInstance().getModuleManager().getModule(Interface.class);
        return interfaceModule == null || interfaceModule.isEnabled();
    }

    private float rowWidth(float textWidth) {
        if (isNavenStyle()) {
            float scale = this.getNavenScale();
            return textWidth + this.paddingX.getValue().floatValue() * 2.0f * scale;
        }
        float lineReserve = this.sideLineEnabled.getValue() ? this.sideLineWidth.getValue().floatValue() : 0.0f;
        return textWidth + this.paddingX.getValue().floatValue() * 2.0f + lineReserve;
    }

    private float measureWidth(List<AnimatedRow> rows) {
        float maxWidth = 0.0f;
        for (AnimatedRow row : rows) maxWidth = Math.max(maxWidth, row.rowWidth);
        return maxWidth;
    }

    private float measureHeight(List<AnimatedRow> rows) {
        float scale = this.isNavenStyle() ? this.getNavenScale() : 1.0f;
        float rowHeightValue = this.rowHeight.getValue().floatValue() * scale;
        float spacing = this.rowSpacing.getValue().floatValue() * scale;
        float height = 0.0f;
        boolean hasVisibleRow = false;
        for (AnimatedRow row : rows) {
            float progress = row.progress();
            if (progress <= 0.01f) continue;
            if (hasVisibleRow) height += spacing * progress;
            height += rowHeightValue * progress;
            hasVisibleRow = true;
        }
        return height;
    }

    private void updateSizeAnimation(float targetWidth, float targetHeight) {
        if (!this.animationReady) {
            this.widthAnim.setCurrentValue(targetWidth);
            this.widthAnim.setToValue(targetWidth);
            this.heightAnim.setCurrentValue(targetHeight);
            this.heightAnim.setToValue(targetHeight);
            this.animationReady = true;
            return;
        }
        this.widthAnim.animate(targetWidth, 0.18, Easings.EASE_OUT_SINE);
        this.heightAnim.animate(targetHeight, 0.18, Easings.EASE_OUT_SINE);
        this.widthAnim.tick();
        this.heightAnim.tick();
    }

    private void renderRows(DrawContext drawContext, List<AnimatedRow> rows, float x, float y, float width, Alignment alignment) {
        List<RowRenderLayout> layouts = this.computeRowLayouts(rows, x, y, width, alignment);
        boolean broken = this.breakEnabled.getValue();
        for (int i = 0; i < layouts.size(); i++) {
            this.renderRow(drawContext, rows, layouts.get(i), broken, alignment, i, layouts.size(), layouts);
        }
    }

    private void renderNavenRows(DrawContext drawContext, List<AnimatedRow> rows, float x, float y, float width, Alignment alignment) {
        List<RowRenderLayout> layouts = this.computeRowLayouts(rows, x, y, width, alignment);

        float scale = this.getNavenScale();
        float bgRadius = this.backgroundRadius.getValue().floatValue() * scale;
        int bgAlpha = Math.round(this.backgroundAlpha.getValue().floatValue());
        boolean bgEnabled = this.backgroundEnabled.getValue();

        int totalRows = layouts.size();

        for (int idx = 0; idx < totalRows; idx++) {
            RowRenderLayout layout = layouts.get(idx);
            if (layout.progress <= 0.01f) continue;

            float rowH = layout.height;
            if (rowH <= 0.5f) continue;

            if (bgEnabled && bgAlpha > 0) {
                try (Paint paint = new Paint()) {
                    int alpha = Math.round(bgAlpha * layout.progress);
                    paint.setColor(alpha << 24);
                    drawContext.drawRoundedRect(
                            RoundedRectangle.ofXYWHR(layout.x, layout.y, layout.width, rowH, bgRadius),
                            paint
                    );
                }
            }

            int rowColor = this.getNavenRainbowColor(idx, totalRows);
            int textColor = Argb.withAlpha(rowColor, layout.progress);

            float padding = this.paddingX.getValue().floatValue() * scale;
            float textWidth = layout.row.textWidth;
            float textX = alignment == Alignment.RIGHT
                    ? layout.x + layout.width - padding - textWidth
                    : layout.x + padding;

            drawContext.save();
            drawContext.clipRoundedRect(
                    RoundedRectangle.ofXYWHR(layout.x, layout.y, layout.width, rowH, bgRadius), true);

            boolean useMcFont = this.useMinecraftFont.getValue();
            String text = layout.row.fullDisplayName;
            FontRenderer font = FontPresets.pingfang(16.0f * scale);

            float textY;
            if (useMcFont) {
                textY = layout.y + (rowH + mc.font.lineHeight * scale) / 2.0f - 10.0f * scale
                        + this.paddingY.getValue().floatValue() * 0.25f * scale;
            } else {
                textY = layout.y + (rowH - (float) GlHelper.getFontAscent(font)) / 2.0f
                        + this.paddingY.getValue().floatValue() * 0.25f * scale;
            }

            String namePart = text;
            String suffixPart = "";
            int suffixColor = textColor;

            if (this.showSuffix.getValue() && this.suffixColorEnabled.getValue() && text.contains(" ")) {
                int lastSpace = text.lastIndexOf(' ');
                namePart = text.substring(0, lastSpace);
                suffixPart = text.substring(lastSpace + 1);
                suffixColor = Argb.withAlpha(NAVEN_SUFFIX_COLOR, layout.progress);
            }

            if (useMcFont) {
                var poseStack = drawContext.getPoseStack();
                poseStack.pushPose();
                poseStack.translate(textX, textY, 0.0f);
                poseStack.scale(scale, scale, 1.0f);
                try {
                    var bufferSource = drawContext.getGuiGraphics().bufferSource();
                    if (suffixPart.length() > 0) {
                        mc.font.drawInBatch(namePart, 0.0f, 0.0f, textColor, false,
                                poseStack.last().pose(),
                                bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
                        float suffixX = mc.font.width(namePart) + 2.0f;
                        mc.font.drawInBatch(suffixPart, suffixX, 0.0f, suffixColor, false,
                                poseStack.last().pose(),
                                bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
                    } else {
                        mc.font.drawInBatch(text, 0.0f, 0.0f, textColor, false,
                                poseStack.last().pose(),
                                bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
                    }
                    bufferSource.endBatch();
                } finally {
                    poseStack.popPose();
                }
            } else {
                if (suffixPart.length() > 0) {
                    GlHelper.drawText(namePart, textX, textY, font, textColor);
                    float suffixX = textX + GlHelper.getStringWidth(namePart, font) + 2.0f * scale;
                    GlHelper.drawText(suffixPart, suffixX, textY, font, suffixColor);
                } else {
                    GlHelper.drawText(text, textX, textY, font, textColor);
                }
            }

            drawContext.restore();
        }
    }

    private int getNavenRainbowColor(int index, int total) {
        long time = System.currentTimeMillis();

        float timePhase = (float) ((time % NAVEN_CYCLE_MS) / (double) NAVEN_CYCLE_MS);

        float rowPhase = total <= 1
                ? 0.0f
                : ((float) index / (float) (total - 1)) * NAVEN_BAND_SPAN;

        float hue = timePhase - rowPhase;
        hue = hue - (float) Math.floor(hue);

        return hsvToRgb(hue, NAVEN_SATURATION, NAVEN_BRIGHTNESS);
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = ((h % 1f) + 1f) % 1f;
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6f) % 2 - 1));
        float m = v - c;
        float r, g, b;
        int i = (int) (h * 6);
        switch (i) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            case 5: r = c; g = 0; b = x; break;
            default: r = 0; g = 0; b = 0; break;
        }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    private void renderRow(DrawContext drawContext, List<AnimatedRow> rows, RowRenderLayout layout,
                           boolean broken, Alignment alignment, int rowIndex, int rowCount,
                           List<RowRenderLayout> layouts) {
        RoundedRectangle bounds = this.rowBounds(layout, broken, alignment, rowIndex, rowCount, layouts);
        int rowColor = this.colorForPosition(layout.rowIndex, 0.5f, Math.max(1, rows.size() - 1));

        if (this.glowEnabled.getValue()) {
            float gRadius = this.glowRadius.getValue().floatValue();
            int gAlpha = Math.round(this.glowAlpha.getValue().floatValue() * layout.progress);
            if (gAlpha > 0 && gRadius > 0.0f) {
                int glowColor = rowColor;
                int layers = 8;
                for (int i = layers; i >= 0; i--) {
                    float progress = (float) i / (float) layers;
                    float radius = gRadius * progress;
                    int alpha = Math.round(gAlpha * (0.1f + 0.9f * (1.0f - progress)));
                    if (alpha <= 0 || radius <= 0.0f) continue;
                    float expand = radius;
                    RoundedRectangle glowBounds = RoundedRectangle.ofXYWHR(
                            bounds.x1 - expand, bounds.y1 - expand,
                            bounds.getWidth() + expand * 2.0f, bounds.getHeight() + expand * 2.0f,
                            this.backgroundRadius.getValue().floatValue() + expand);
                    try (Paint paint = new Paint()) {
                        paint.setColor((alpha << 24) | (glowColor & 0x00FFFFFF));
                        drawContext.drawRoundedRect(glowBounds, paint);
                    }
                }
                RenderUtil.enableBlend();
            }
        }

        if (this.backgroundEnabled.getValue()) {
            try (Paint paint = new Paint()) {
                int alpha = Math.round(this.backgroundAlpha.getValue().floatValue() * layout.progress);
                paint.setColor((alpha << 24) | 0x000000);
                if (this.backgroundRadius.getValue().floatValue() <= 0.0f) {
                    drawContext.drawRectXYWH(layout.x, layout.y, layout.width, layout.height, paint);
                } else {
                    drawContext.drawRoundedRect(bounds, paint);
                }
            }
        }
        if (this.sideLineEnabled.getValue()) {
            this.drawSideLine(drawContext, bounds, Argb.withAlpha(rowColor, layout.progress), alignment, broken);
        }
        drawContext.save();
        drawContext.clipRoundedRect(bounds, true);
        this.drawModuleName(drawContext, layout.row, layout.x, layout.y, layout.width, layout.fullHeight,
                layout.rowIndex, rows.size(), layout.progress, alignment);
        drawContext.restore();
    }

    private List<RowRenderLayout> computeRowLayouts(List<AnimatedRow> rows, float x, float y, float width, Alignment alignment) {
        List<RowRenderLayout> layouts = new ArrayList<>();
        float cursorY = y;
        float scale = this.isNavenStyle() ? this.getNavenScale() : 1.0f;
        float rowHeightValue = this.rowHeight.getValue().floatValue() * scale;
        float spacing = this.rowSpacing.getValue().floatValue() * scale;
        boolean hasRenderedRow = false;
        for (int i = 0; i < rows.size(); i++) {
            AnimatedRow row = rows.get(i);
            float progress = row.progress();
            if (progress <= 0.01f) continue;
            if (hasRenderedRow) cursorY += spacing * progress;
            float animatedWidth = Math.max(0.1f, row.rowWidth * progress);
            float animatedHeight = Math.max(0.1f, rowHeightValue * progress);
            float rowX = alignment == Alignment.RIGHT ? x + width - animatedWidth : x;
            float slideOffset = (alignment == Alignment.RIGHT ? SLIDE_DISTANCE : -SLIDE_DISTANCE) * (1.0f - progress);
            layouts.add(new RowRenderLayout(row, i, rowX + slideOffset, cursorY,
                    animatedWidth, animatedHeight, rowHeightValue, progress));
            cursorY += rowHeightValue * progress;
            hasRenderedRow = true;
        }
        for (int i = 0; i < layouts.size() - 1; i++) {
            layouts.get(i).linkTo(layouts.get(i + 1));
        }
        return layouts;
    }

    private RoundedRectangle rowBounds(RowRenderLayout layout, boolean broken, Alignment alignment, int rowIndex, int rowCount,
                                       List<RowRenderLayout> layouts) {
        float radius = this.backgroundRadius.getValue().floatValue();
        if (broken || radius <= 0.0f) {
            return RoundedRectangle.ofXYWHR(layout.x, layout.y, layout.width, layout.height, radius);
        }
        float widthDiffRadius = radius;
        if (rowIndex < rowCount - 1) {
            float nextWidth = layouts.get(rowIndex + 1).width;
            float diff = Math.abs(layout.width - nextWidth);
            widthDiffRadius = Math.min(radius, diff);
        }
        float tl, tr, br, bl;
        if (alignment == Alignment.LEFT) {
            tl = 0; tr = 0; br = widthDiffRadius; bl = 0;
            if (rowIndex == 0) { tl = radius; tr = radius; }
            if (rowIndex == rowCount - 1) { bl = radius; br = radius; }
        } else {
            tl = 0; tr = 0; br = 0; bl = widthDiffRadius;
            if (rowIndex == 0) { tl = radius; tr = radius; }
            if (rowIndex == rowCount - 1) { br = radius; bl = radius; }
        }
        return RoundedRectangle.ofXYWHRadii(layout.x, layout.y, layout.width, layout.visualHeight(false),
                new float[]{tl, tr, br, bl});
    }

    private RoundedRectangle expandedGlowBounds(RoundedRectangle bounds, float spread) {
        return RoundedRectangle.ofXYWHR(
                bounds.x1 - spread, bounds.y1 - spread,
                bounds.getWidth() + spread * 2.0f, bounds.getHeight() + spread * 2.0f,
                this.backgroundRadius.getValue().floatValue() + spread);
    }

    private void drawSideLine(DrawContext drawContext, RoundedRectangle bounds, int color, Alignment rowAlignment, boolean broken) {
        float lineWidth = this.sideLineWidth.getValue().floatValue();
        Alignment lineAlignment = this.resolveLineAlignment(rowAlignment);
        float lineX = lineAlignment == Alignment.RIGHT ? bounds.x2 - lineWidth : bounds.x1;
        try (Paint paint = new Paint()) {
            paint.setColor(color);
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(lineX, bounds.y1, lineWidth, bounds.getHeight(),
                    broken ? Math.min(this.backgroundRadius.getValue().floatValue(), lineWidth) : 0.0f), paint);
        }
    }

    private void drawModuleName(DrawContext drawContext, AnimatedRow row, float rowX, float rowY, float rowWidth, float rowHeight,
                                int rowIndex, int rowCount, float alpha, Alignment alignment) {
        boolean useMcFont = this.useMinecraftFont.getValue();
        String text = row.fullDisplayName;
        Module module = row.module;

        float textWidth;
        float textY;
        FontRenderer font = FontPresets.pingfang(16.0f);

        if (useMcFont) {
            textWidth = mc.font.width(text);
            textY = rowY + (rowHeight + mc.font.lineHeight) / 2.0f - 10.0f + this.paddingY.getValue().floatValue() * 0.25f;
        } else {
            textWidth = GlHelper.getStringWidth(text, font);
            textY = rowY + (rowHeight - (float) GlHelper.getFontAscent(font)) / 2.0f + this.paddingY.getValue().floatValue() * 0.25f;
        }

        float padding = this.paddingX.getValue().floatValue();
        float lineReserve = this.sideLineEnabled.getValue() ? this.sideLineWidth.getValue().floatValue() : 0.0f;
        float textX = alignment == Alignment.RIGHT
                ? rowX + rowWidth - padding - textWidth - (this.resolveLineAlignment(alignment) == Alignment.RIGHT ? lineReserve : 0.0f)
                : rowX + padding + (this.resolveLineAlignment(alignment) == Alignment.LEFT ? lineReserve : 0.0f);

        int color = this.colorForPosition(rowIndex, 0.5f, Math.max(1, rowCount - 1));
        int finalColor = Argb.withAlpha(color, alpha);

        String namePart = text;
        String suffixPart = "";
        int suffixColor = finalColor;

        if (this.showSuffix.getValue() && this.suffixColorEnabled.getValue() && text.contains(" ")) {
            int lastSpace = text.lastIndexOf(' ');
            namePart = text.substring(0, lastSpace);
            suffixPart = text.substring(lastSpace + 1);
            suffixColor = getSuffixColor(module);
            suffixColor = Argb.withAlpha(suffixColor, alpha);
        }

        if (useMcFont) {
            var bufferSource = drawContext.getGuiGraphics().bufferSource();
            if (suffixPart.length() > 0) {
                mc.font.drawInBatch(namePart, textX, textY, finalColor, false,
                        drawContext.getPoseStack().last().pose(),
                        bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
                float suffixX = textX + mc.font.width(namePart) + 2.0f;
                mc.font.drawInBatch(suffixPart, suffixX, textY, suffixColor, false,
                        drawContext.getPoseStack().last().pose(),
                        bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            } else {
                mc.font.drawInBatch(text, textX, textY, finalColor, false,
                        drawContext.getPoseStack().last().pose(),
                        bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
            bufferSource.endBatch();
        } else {
            if (suffixPart.length() > 0) {
                GlHelper.drawText(namePart, textX, textY, font, finalColor);
                float suffixX = textX + GlHelper.getStringWidth(namePart, font) + 2.0f;
                GlHelper.drawText(suffixPart, suffixX, textY, font, suffixColor);
            } else {
                GlHelper.drawText(text, textX, textY, font, finalColor);
            }
        }
    }

    public int getThemeColor(int positionIndex, float positionProgress, int maxPositionIndex) {
        return this.colorForPosition(positionIndex, positionProgress, maxPositionIndex);
    }

    private int colorForPosition(int rowIndex, float charProgress, int maxRowIndex) {
        if (this.useClientColor.getValue()) {
            return ColorUtil.getRainbowColor(this.rainbowSpeed.getValue().intValue(),
                    rowIndex * this.rainbowOffset.getValue().intValue()).getRGB();
        }
        String mode = this.textColorMode.getValue();
        int speed = this.rainbowSpeed.getValue().intValue();
        int offset = rowIndex * this.rainbowOffset.getValue().intValue();
        float saturation = this.rainbowSaturation.getValue().floatValue();
        float brightness = this.rainbowBrightness.getValue().floatValue();

        if ("Rainbow".equals(mode)) {
            return ColorUtil.getRainbowColor(speed, offset).getRGB();
        }
        if ("Gradient".equals(mode)) {
            String themeName = this.gradientTheme.getValue();
            if ("CyanRainbowVertical".equals(themeName)) {
                float t = maxRowIndex <= 0 ? 0.0f : (float) rowIndex / (float) maxRowIndex;
                int[] colors = {
                        0xFF55FFFF, 0xFF55FF55, 0xFFFFFF55,
                        0xFFFFAA00, 0xFFFF5555, 0xFFAA55AA
                };
                return getMultiColorGradient(colors, t);
            }
            GradientTheme theme = GradientTheme.fromName(themeName);
            double position = (double) ((System.currentTimeMillis() / (long) speed + (long) offset) % 1000L) / 1000.0;
            return theme.getColorAt(position, saturation, brightness).getRGB();
        }
        if ("Solid".equals(mode)) {
            return ColorUtil.getRainbowColor(speed, 0).getRGB();
        }
        return ColorUtil.getRainbowColor(speed, offset).getRGB();
    }

    private static int getMultiColorGradient(int[] colors, float t) {
        if (colors == null || colors.length == 0) return 0xFFFFFFFF;
        if (colors.length == 1) return colors[0];
        t = Mth.clamp(t, 0.0f, 1.0f);
        float scaled = t * (colors.length - 1);
        int index = (int) scaled;
        if (index >= colors.length - 1) return colors[colors.length - 1];
        float localT = scaled - index;
        return lerpColor(colors[index], colors[index + 1], localT);
    }

    private static int lerpColor(int start, int end, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int a = (int) Mth.lerp(t, (start >> 24) & 0xFF, (end >> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (start >> 16) & 0xFF, (end >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (start >> 8) & 0xFF, (end >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, start & 0xFF, end & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private Alignment resolveAlignment(float x, float width) {
        if ("Left".equals(this.sideMode.getValue())) return Alignment.LEFT;
        if ("Right".equals(this.sideMode.getValue())) return Alignment.RIGHT;
        return x + width / 2.0f < (float) mc.getWindow().getGuiScaledWidth() / 2.0f
                ? Alignment.LEFT : Alignment.RIGHT;
    }

    private Alignment resolveLineAlignment(Alignment rowAlignment) {
        if ("Left".equals(this.sideLineMode.getValue())) return Alignment.LEFT;
        if ("Right".equals(this.sideLineMode.getValue())) return Alignment.RIGHT;
        return rowAlignment;
    }

    private void clampToScreen(float width, float height) {
        if (mc == null || mc.getWindow() == null) return;
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float maxX = Math.max(MIN_VISIBLE_EDGE, screenWidth - Math.min(width, screenWidth) - MIN_VISIBLE_EDGE);
        float maxY = Math.max(MIN_VISIBLE_EDGE, screenHeight - Math.min(height, screenHeight) - MIN_VISIBLE_EDGE);
        this.setX(Mth.clamp(this.getX(), MIN_VISIBLE_EDGE, maxX));
        this.setY(Mth.clamp(this.getY(), MIN_VISIBLE_EDGE, maxY));
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
            NiloreClient.getInstance().getConfigManager().saveAll();
        }
    }

    private static class Argb {
        static int withAlpha(int color, float alpha) {
            int a = Math.round(alpha * 255);
            return (a << 24) | (color & 0x00FFFFFF);
        }
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
        if (glRenderEvent == null || glRenderEvent.drawContext() == null) return;
    }

    @Override
    public void onSettings() { }
}

class ModuleSuffixColorManager {
    private static final Map<Module, Integer> suffixColors = new IdentityHashMap<>();
    private static final Map<Module, Boolean> suffixEnabled = new IdentityHashMap<>();

    public static int getSuffixColor(Module module) {
        return suffixColors.getOrDefault(module, 0xFF888888);
    }

    public static void setSuffixColor(Module module, int color) {
        suffixColors.put(module, color);
    }

    public static boolean isSuffixEnabled(Module module) {
        return suffixEnabled.getOrDefault(module, true);
    }

    public static void setSuffixEnabled(Module module, boolean enabled) {
        suffixEnabled.put(module, enabled);
    }
}