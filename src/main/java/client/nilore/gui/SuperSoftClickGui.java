package client.nilore.gui;

import client.nilore.NiloreClient;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.render.ClickGui;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.GlHelper;
import client.nilore.render.Renderer;
import client.nilore.settings.Setting;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.MultiSelectSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.settings.impl.TextSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SuperSoftClickGui extends Screen {
    private final int PANEL_WIDTH;
    private final int HEADER_HEIGHT;
    private final int MODULE_HEIGHT;
    private final int SETTING_HEIGHT;
    private final int TOP_MARGIN;
    private final int SIDE_MARGIN;
    private final int GAP;
    private final float SCALE;
    private final float PANEL_ALPHA;

    private static final float FS_TITLE   = 13.5f;
    private static final float FS_MODULE  = 13.0f;
    private static final float FS_SETTING = 12.5f;
    private static final float FS_VALUE   = 12.0f;
    private static final float FS_ICON    = 14.0f;

    private static final float SWITCH_W      = 18f;
    private static final float SWITCH_H      = 7f;
    private static final float SWITCH_KNOB_W = 7f;
    private static final float SWITCH_KNOB_H = 9f;
    private static final float SWITCH_RIGHT_PAD = 5f;

    private static final int PANEL = 0xFF151515;
    private static final int HEADER = 0xFF222222;
    private static final int SETTING = 0xFF1A1A1A;
    private static final int SETTING_HOVER = 0xFF2E2E2E;

    private static final int MODULE_ENABLED  = 0xFF4AE8C0;
    private static final int MODULE_DISABLED = 0xFFD6D6D6;
    private static final int MODULE_HOVER    = 0xFFFFFFFF;

    private static final int TEXT_TITLE  = 0xFFFFFFFF;
    private static final int ICON_TITLE  = 0xFFE0E0E0;
    private static final int TEXT_MUTED  = 0xFFC8C8C8;
    private static final int ACCENT      = 0xFFFFAA00;
    private static final int TRACK       = 0xFF545454;
    private static final int KNOB        = 0xFFF4F4F4;

    private static final int SWITCH_OFF_COLOR = 0xFF555555;
    private static final int SWITCH_ON_COLOR  = 0xFFFFAA00;

    private static final Map<String, int[]> SAVED_POSITIONS = new HashMap<>();

    private final List<Panel> panels = new ArrayList<>();
    private final Map<Module, Boolean> expanded = new IdentityHashMap<>();
    private final Map<ModeSetting, Boolean> modeExpanded = new IdentityHashMap<>();
    private final Map<Module, List<Setting<?>>> settingsCache = new IdentityHashMap<>();
    private final Map<Module, Float> expandAnims = new IdentityHashMap<>();
    private final Map<BooleanSetting, Float> switchAnims = new IdentityHashMap<>();
    private long lastFrame = System.currentTimeMillis();
    private float deltaTime = 0.016f;

    private Module keyListening;
    private NumberSetting sliderDragging;
    private int sliderX;
    private int sliderW;
    private TextSetting textEditing;

    private Panel draggingPanel;
    private int dragOffsetX;
    private int dragOffsetY;

    private long openedAt;
    private boolean closing;
    private long closingAt;
    private Screen parent;

    public SuperSoftClickGui() {
        this(1.0f, 1.0f);
    }

    public SuperSoftClickGui(float scale) {
        this(scale, 1.0f);
    }

    public SuperSoftClickGui(float scale, float panelAlpha) {
        super(Component.literal("SuperSoft ClickGUI"));
        this.SCALE = scale;
        this.PANEL_ALPHA = Math.max(0.0f, Math.min(1.0f, panelAlpha));
        this.PANEL_WIDTH = (int) (95 * scale);
        this.HEADER_HEIGHT = (int) (18 * scale);
        this.MODULE_HEIGHT = (int) (13 * scale);
        this.SETTING_HEIGHT = (int) (16 * scale);
        this.TOP_MARGIN = (int) (15 * scale);
        this.SIDE_MARGIN = (int) (10 * scale);
        this.GAP = (int) (3 * scale);
    }

    public void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    private int withPanelAlpha(int argb) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int finalAlpha = Math.round(baseAlpha * PANEL_ALPHA);
        return (finalAlpha << 24) | (argb & 0xFFFFFF);
    }

    @Override
    protected void init() {
        super.init();
        settingsCache.clear();
        openedAt = System.currentTimeMillis();
        lastFrame = System.currentTimeMillis();
        closing = false;
        closingAt = 0L;
        if (panels.isEmpty()) buildPanels();
        else layoutPanels(false);
    }

    private void buildPanels() {
        panels.clear();
        panels.add(new Panel(ClickGui.cn("Combat"),   Arrays.asList(Category.COMBAT)));
        panels.add(new Panel(ClickGui.cn("Movement"), Arrays.asList(Category.MOVEMENT)));
        panels.add(new Panel(ClickGui.cn("Player"),   Arrays.asList(Category.PLAYER)));
        panels.add(new Panel(ClickGui.cn("Render"),   Arrays.asList(Category.RENDER)));
        panels.add(new Panel(ClickGui.cn("Misc"),     Arrays.asList(Category.WORLD)));
        panels.add(new Panel(ClickGui.cn("Client"),   Arrays.asList(Category.EXPLOIT)));
        panels.add(new Panel(ClickGui.cn("World"),    Arrays.asList(Category.MISC)));

        layoutPanels(true);
    }

    private void layoutPanels(boolean force) {
        int totalDesiredWidth = PANEL_WIDTH * 7 + GAP * 6;
        int availableWidth = width - SIDE_MARGIN * 2;

        int finalPanelW = PANEL_WIDTH;
        int finalGap = GAP;

        if (totalDesiredWidth > availableWidth) {
            finalGap = Math.max((int) (2 * SCALE), GAP / 2);
            finalPanelW = Math.max((int) (70 * SCALE), (availableWidth - finalGap * 6) / 7);
        }

        int startX = SIDE_MARGIN;
        int topY = TOP_MARGIN;
        int maxHeight = height - TOP_MARGIN * 2;

        for (int i = 0; i < panels.size(); i++) {
            Panel p = panels.get(i);
            int[] saved = SAVED_POSITIONS.get(p.title);
            if (saved != null) {
                p.x = saved[0];
                p.y = saved[1];
                p.movedByUser = true;
                p.width = finalPanelW;
                p.maxHeight = maxHeight;
            } else if (force || !p.movedByUser) {
                p.x = startX + i * (finalPanelW + finalGap);
                p.y = topY;
                p.width = finalPanelW;
                p.maxHeight = maxHeight;
            }
        }
    }

    private List<Module> modules(Panel panel) {
        List<Module> out = new ArrayList<>();
        for (Category cat : panel.categories) {
            out.addAll(NiloreClient.getInstance().getModuleManager().getModulesByCategory(cat));
        }
        out.removeIf(Module::isHiddenInModuleList);
        out.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Setting<?>> settingsOf(Module m) {
        if (m == null) return Collections.emptyList();
        List<Setting<?>> cached = settingsCache.get(m);
        if (cached != null) return cached;

        List<Setting<?>> raw;
        try {
            raw = m.getSettings();
        } catch (Throwable t) {
            raw = Collections.emptyList();
        }

        List<Setting<?>> resolved;
        if (raw == null || raw.isEmpty()) {
            resolved = Collections.emptyList();
        } else {
            List<Setting<?>> clean = new ArrayList<>(raw.size());
            Set<Setting<?>> seen = Collections.newSetFromMap(new IdentityHashMap<Setting<?>, Boolean>());
            for (Setting<?> s : raw) {
                if (s == null) continue;
                if (seen.add(s)) clean.add(s);
            }
            resolved = (clean.size() == raw.size()) ? raw : clean;
        }

        settingsCache.put(m, resolved);
        return resolved;
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        deltaTime = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float a = animation();
        Renderer.render(g, ctx -> {
            for (Panel p : panels) {
                drawPanel(g, p, mouseX, mouseY, a);
            }
        });
        if (closing && a <= 0.01f) {
            save();
            minecraft.setScreen(null);
        }
    }

    private void drawPanel(GuiGraphics g, Panel p, int mouseX, int mouseY, float a) {
        List<Module> mods = modules(p);

        for (Module m : mods) {
            tickExpandAnim(m);
        }

        int contentH = 0;
        for (Module m : mods) {
            contentH += MODULE_HEIGHT;
            float expAnim = getExpandAnim(m);
            if (expAnim > 0.001f) {
                int fullH = SETTING_HEIGHT;
                for (Setting<?> s : settingsOf(m)) {
                    if (visible(s)) fullH += settingHeight(s, p.width);
                }
                contentH += (int) (fullH * expAnim);
            }
        }

        p.viewportH = Math.min(contentH, p.maxHeight - HEADER_HEIGHT);
        p.contentH = contentH;
        int maxScroll = Math.max(0, contentH - p.viewportH);
        p.scroll = clamp(p.scroll, 0, maxScroll);

        int bottom = p.y + HEADER_HEIGHT + p.viewportH;

        fillA(g, p.x, p.y, p.x + p.width, bottom, withPanelAlpha(PANEL), a);
        fillA(g, p.x, p.y, p.x + p.width, p.y + HEADER_HEIGHT, withPanelAlpha(HEADER), a);

        FontRenderer titleFont = FontPresets.pingfang(FS_TITLE * SCALE);
        FontRenderer iconFont  = FontPresets.materialIcons(FS_ICON * SCALE);

        String icon = iconForPanel(p.title);
        String title = p.title;

        float iconW  = GlHelper.getStringWidth(icon, iconFont);
        float titleW = GlHelper.getStringWidth(title, titleFont);
        float gapIcon = 4f * SCALE;
        float totalW = iconW + gapIcon + titleW;
        float startX = p.x + (p.width - totalW) / 2f;

        float titleH = titleFont.getMetrics().capHeight();
        float iconH  = iconFont.getMetrics().capHeight();
        float midY   = p.y + HEADER_HEIGHT / 2f;
        float titleY = midY - titleH / 2f + 1f * SCALE;
        float iconY  = midY - iconH / 2f + 1f * SCALE;

        drawText(icon,  startX,                   iconY,  iconFont,  ICON_TITLE, a);
        drawText(title, startX + iconW + gapIcon, titleY, titleFont, TEXT_TITLE, a);

        p.hovered = inside(mouseX, mouseY, p.x, p.y, p.width, HEADER_HEIGHT + p.viewportH);

        int clipTop = p.y + HEADER_HEIGHT;
        int clipBottom = bottom;
        if (p.viewportH <= 0) return;

        g.enableScissor(p.x, clipTop, p.x + p.width, clipBottom);

        int cursorY = clipTop - p.scroll;
        for (Module m : mods) {
            if (cursorY + MODULE_HEIGHT >= clipTop && cursorY <= clipBottom) {
                drawModule(g, p, m, cursorY, mouseX, mouseY, a);
            }
            cursorY += MODULE_HEIGHT;

            float expAnim = getExpandAnim(m);
            if (expAnim <= 0.001f) continue;

            List<Setting<?>> settings = settingsOf(m);
            int fullH = SETTING_HEIGHT;
            for (Setting<?> s : settings) {
                if (visible(s)) fullH += settingHeight(s, p.width);
            }

            int visibleH = (int) (fullH * expAnim);
            if (visibleH <= 0) continue;

            int animBottom = Math.min(cursorY + visibleH, clipBottom);

            g.enableScissor(p.x, cursorY, p.x + p.width, animBottom);

            int sy = cursorY;
            if (sy + SETTING_HEIGHT >= clipTop && sy <= clipBottom) {
                drawKeybind(g, p.x, sy, m, mouseX, mouseY, a * expAnim, p.width);
            }
            sy += SETTING_HEIGHT;

            for (Setting<?> setting : settings) {
                if (!visible(setting)) continue;
                int h = settingHeight(setting, p.width);
                if (sy + h >= clipTop && sy <= clipBottom) {
                    drawSetting(g, p.x, sy, h, setting, mouseX, mouseY, a * expAnim, p.width);
                }
                sy += h;
            }

            g.disableScissor();
            cursorY += visibleH;
        }
        g.disableScissor();
    }

    private String iconForPanel(String panelTitle) {
        switch (panelTitle) {
            case "Combat": case "战斗": return "\uE55C";
            case "Movement": case "移动": return "\uE566";
            case "Player": case "玩家": return "\uE7FD";
            case "Render": case "渲染": return "\uE8F4";
            case "Misc": case "杂项": return "\uE574";
            case "Client": case "客户端": return "\uE8B8";
            case "World": case "世界": return "\uE80B";
            default: return "\uE5D3";
        }
    }

    private void drawModule(GuiGraphics g, Panel p, Module m, int y, int mx, int my, float a) {
        boolean hover = inside(mx, my, p.x, y, p.width, MODULE_HEIGHT);
        int textColor;
        if (m.isEnabled()) {
            textColor = hover ? MODULE_HOVER : MODULE_ENABLED;
        } else {
            textColor = hover ? MODULE_HOVER : MODULE_DISABLED;
        }

        FontRenderer font = FontPresets.pingfang(FS_MODULE * SCALE);
        String name = ClickGui.cn(m.getName());
        float textW = GlHelper.getStringWidth(name, font);
        float textX = p.x + (p.width - textW) / 2f;
        float textY = y + (MODULE_HEIGHT - font.getMetrics().capHeight()) / 2f + 1f * SCALE;
        drawText(name, textX, textY, font, textColor, a);

        boolean hasChildren = !settingsOf(m).isEmpty() || m.getKey() != 0;
        if (hasChildren) {
            float expAnim = getExpandAnim(m);
            String arrow = expAnim > 0.5f ? "v" : ">";
            float aw = GlHelper.getStringWidth(arrow, font);
            float ax = p.x + p.width - aw - 5f * SCALE;
            float ay = y + (MODULE_HEIGHT - font.getMetrics().capHeight()) / 2f + 1f * SCALE;
            drawText(arrow, ax, ay, font, 0xFFE6E6E6, a);
        }
    }

    private void drawKeybind(GuiGraphics g, int x, int y, Module m, int mx, int my, float a, int panelW) {
        boolean hover = inside(mx, my, x, y, panelW, SETTING_HEIGHT);
        fillA(g, x, y, x + panelW, y + SETTING_HEIGHT, withPanelAlpha(hover ? SETTING_HOVER : SETTING), a);

        FontRenderer font = FontPresets.pingfang(FS_SETTING * SCALE);
        float textY = y + (SETTING_HEIGHT - font.getMetrics().capHeight()) / 2f + 1f * SCALE;
        drawText(ClickGui.cn("KeyBind"), x + 5f * SCALE, textY, font, TEXT_MUTED, a);

        String value = keyListening == m ? "..." : keyName(m.getKey());
        float vw = GlHelper.getStringWidth(value, font);
        drawText(value, x + panelW - vw - 5f * SCALE, textY, font,
                keyListening == m ? 0xFFFFFF78 : ACCENT, a);
    }

    private int settingHeight(Setting<?> setting, int panelW) {
        int base = settingBaseHeight(setting, panelW);
        if (setting instanceof ModeSetting s && Boolean.TRUE.equals(modeExpanded.get(s))) {
            for (String mode : s.getModes()) {
                base += modeOptionHeight(ClickGui.cn(mode), panelW);
            }
        }
        return base;
    }

    private int settingBaseHeight(Setting<?> setting, int panelW) {
        final float leftPad = 5f * SCALE;
        final float rightPad = 5f * SCALE;
        final float gap = 5f * SCALE;
        final float vPad = 4f * SCALE;
        final FontRenderer nameFont = FontPresets.pingfang(FS_SETTING * SCALE);
        final FontRenderer valueFont = FontPresets.pingfang(FS_VALUE * SCALE);
        final String name = ClickGui.cn(setting.getName() == null ? "" : setting.getName());

        if (setting instanceof BooleanSetting) {
            float maxNameW = Math.max(12f * SCALE,
                    panelW - leftPad - rightPad - SWITCH_W * SCALE - gap);
            int lines = wrapLines(name, nameFont, maxNameW).size();
            return Math.max(SETTING_HEIGHT, wrappedBlockHeight(lines, nameFont, vPad));
        }

        String value;
        float firstLineRightReserve = 0f;
        if (setting instanceof NumberSetting s) {
            value = pretty(s.getValue().doubleValue());
        } else if (setting instanceof ModeSetting s) {
            value = ClickGui.cn(String.valueOf(s.getValue()));
            FontRenderer arrowFont = FontPresets.pingfang(FS_VALUE * SCALE);
            firstLineRightReserve = GlHelper.getStringWidth("v", arrowFont) + gap;
        } else if (setting instanceof MultiSelectSetting s) {
            String selected = String.join(", ", s.getValue());
            value = selected.isEmpty() ? "无" : selected;
        } else if (setting instanceof TextSetting s) {
            value = s.getValue() == null ? "" : s.getValue();
            if (textEditing == s) value += "_";
        } else {
            value = String.valueOf(setting.getValue());
        }

        float contentW = Math.max(8f * SCALE, panelW - leftPad - rightPad);
        int lines = inlinePairLineCount(name, nameFont, value, valueFont,
                contentW, gap, firstLineRightReserve);
        int textH = inlineBlockHeight(lines, nameFont, valueFont, vPad);

        if (setting instanceof NumberSetting) {
            return Math.max(SETTING_HEIGHT + (int) (4 * SCALE), textH + (int) (5 * SCALE));
        }
        return Math.max(SETTING_HEIGHT, textH);
    }

    private int modeOptionHeight(String mode, int panelW) {
        FontRenderer font = FontPresets.pingfang(FS_VALUE * SCALE);
        float maxW = Math.max(12f * SCALE, panelW - 10f * SCALE);
        int lines = wrapLines(mode == null ? "" : mode, font, maxW).size();
        return Math.max(SETTING_HEIGHT, wrappedBlockHeight(lines, font, 4f * SCALE));
    }

    private void drawSetting(GuiGraphics g, int x, int y, int h, Setting<?> setting,
                             int mx, int my, float a, int panelW) {
        boolean hover = inside(mx, my, x, y, panelW, h);
        fillA(g, x, y, x + panelW, y + h, withPanelAlpha(hover ? SETTING_HOVER : SETTING), a);

        final float leftPad = 5f * SCALE;
        final float rightPad = 5f * SCALE;
        final float gap = 5f * SCALE;
        final float topPad = 4f * SCALE;
        final FontRenderer nameFont = FontPresets.pingfang(FS_SETTING * SCALE);
        final FontRenderer valueFont = FontPresets.pingfang(FS_VALUE * SCALE);
        final String settingName = ClickGui.cn(setting.getName() == null ? "" : setting.getName());
        final int baseH = settingBaseHeight(setting, panelW);

        if (setting instanceof BooleanSetting s) {
            int switchW = (int) (SWITCH_W * SCALE);
            int switchH = (int) (SWITCH_H * SCALE);
            int switchX = x + panelW - switchW - (int) (SWITCH_RIGHT_PAD * SCALE);
            int switchY = y + (baseH - switchH) / 2;

            float maxNameW = Math.max(12f * SCALE, switchX - gap - (x + leftPad));
            List<String> lines = wrapLines(settingName, nameFont, maxNameW);
            float blockH = wrappedTextHeight(lines.size(), nameFont);
            float nameY = y + Math.max(topPad, (baseH - blockH) / 2f);
            drawWrappedLeft(lines, x + leftPad, nameY, nameFont, TEXT_MUTED, a);
            drawSwitch(g, switchX, switchY, Boolean.TRUE.equals(s.getValue()), a, s);
            return;
        }

        if (setting instanceof NumberSetting s) {
            String value = pretty(s.getValue().doubleValue());
            float contentW = Math.max(8f * SCALE, panelW - leftPad - rightPad);
            drawInlinePair(settingName, nameFont, TEXT_MUTED,
                    value, valueFont, ACCENT,
                    x + leftPad, y + topPad, contentW, gap, 0f, a);

            int sx = x + (int) (5 * SCALE);
            int sw = panelW - (int) (10 * SCALE);
            int sy = y + baseH - (int) (4 * SCALE);
            int trackH = Math.max(1, (int) (2 * SCALE));

            drawRoundedRect(g, sx, sy, sw, trackH, TRACK, a);

            double min = s.getMin().doubleValue();
            double max = s.getMax().doubleValue();
            double v = s.getValue().doubleValue();
            double t = max <= min ? 0.0 : (v - min) / (max - min);
            t = Math.max(0, Math.min(1, t));

            int fillW = (int) Math.round(sw * t);
            if (fillW > 0) drawRoundedRect(g, sx, sy, fillW, trackH, ACCENT, a);

            int kx = sx + fillW - (int) (3 * SCALE);
            drawRoundedRect(g, kx, sy - (int) (2 * SCALE),
                    Math.max(2, (int) (6 * SCALE)), Math.max(2, (int) (6 * SCALE)), ACCENT, a);
            return;
        }

        if (setting instanceof ModeSetting s) {
            boolean open = Boolean.TRUE.equals(modeExpanded.get(s));
            String arrow = open ? "^" : "v";
            String value = ClickGui.cn(String.valueOf(s.getValue()));
            FontRenderer arrowFont = FontPresets.pingfang(FS_VALUE * SCALE);
            float arrowW = GlHelper.getStringWidth(arrow, arrowFont);
            float arrowX = x + panelW - rightPad - arrowW;
            float contentW = Math.max(8f * SCALE, panelW - leftPad - rightPad);
            float reserve = arrowW + gap;

            drawInlinePair(settingName, nameFont, TEXT_MUTED,
                    value, valueFont, ACCENT,
                    x + leftPad, y + topPad, contentW, gap, reserve, a);
            drawText(arrow, arrowX, y + topPad, arrowFont, TEXT_MUTED, a);

            if (open) {
                int yy = y + baseH;
                for (String mode : s.getModes()) {
                    String cnMode = ClickGui.cn(mode);
                    int optionH = modeOptionHeight(cnMode, panelW);
                    boolean selected = mode.equals(s.getValue());
                    fillA(g, x, yy, x + panelW, yy + optionH,
                            withPanelAlpha(selected ? 0xFF444444 : SETTING), a);

                    List<String> optionLines = wrapLines(cnMode, valueFont, panelW - 10f * SCALE);
                    float blockH = wrappedTextHeight(optionLines.size(), valueFont);
                    float oy = yy + Math.max(3f * SCALE, (optionH - blockH) / 2f);
                    drawWrappedCentered(optionLines, x, panelW, oy, valueFont,
                            selected ? ACCENT : TEXT_MUTED, a);
                    yy += optionH;
                }
            }
            return;
        }

        String value;
        if (setting instanceof MultiSelectSetting s) {
            String selected = String.join(", ", s.getValue());
            value = selected.isEmpty() ? "无" : selected;
        } else if (setting instanceof TextSetting s) {
            value = s.getValue() == null ? "" : s.getValue();
            if (textEditing == s) value += "_";
        } else {
            value = String.valueOf(setting.getValue());
        }

        float contentW = Math.max(8f * SCALE, panelW - leftPad - rightPad);
        drawInlinePair(settingName, nameFont, TEXT_MUTED,
                value, valueFont, ACCENT,
                x + leftPad, y + topPad, contentW, gap, 0f, a);
    }

    private int inlinePairLineCount(String name, FontRenderer nameFont,
                                    String value, FontRenderer valueFont,
                                    float fullWidth, float gap, float firstLineRightReserve) {
        FlowCursor c = new FlowCursor(Math.max(1f, fullWidth), Math.max(0f, firstLineRightReserve));
        flowMeasureText(c, name == null ? "" : name, nameFont, false);
        if (value != null && !value.isEmpty()) {
            flowAdvanceGap(c, gap);
            flowMeasureText(c, value, valueFont, true);
        }
        return Math.max(1, c.line + 1);
    }

    private void drawInlinePair(String name, FontRenderer nameFont, int nameColor,
                                String value, FontRenderer valueFont, int valueColor,
                                float startX, float startY, float fullWidth, float gap,
                                float firstLineRightReserve, float a) {
        FlowCursor c = new FlowCursor(Math.max(1f, fullWidth), Math.max(0f, firstLineRightReserve));
        float lineStep = inlineLineStep(nameFont, valueFont);
        flowDrawText(c, name == null ? "" : name, nameFont, nameColor,
                startX, startY, lineStep, a, false);
        if (value != null && !value.isEmpty()) {
            flowAdvanceGap(c, gap);
            flowDrawText(c, value, valueFont, valueColor,
                    startX, startY, lineStep, a, true);
        }
    }

    private static final class FlowCursor {
        final float fullWidth;
        final float firstReserve;
        int line;
        float x;

        FlowCursor(float fullWidth, float firstReserve) {
            this.fullWidth = fullWidth;
            this.firstReserve = firstReserve;
        }

        float lineWidth() {
            return Math.max(1f, fullWidth - (line == 0 ? firstReserve : 0f));
        }

        void nextLine() {
            line++;
            x = 0f;
        }
    }

    private void flowAdvanceGap(FlowCursor c, float gap) {
        if (gap <= 0f || c.x <= 0f) return;
        if (c.x + gap <= c.lineWidth()) c.x += gap;
        else c.nextLine();
    }

    private void flowMeasureText(FlowCursor c, String text, FontRenderer font, boolean preferWords) {
        flowTextInternal(c, text, font, 0, 0f, 0f, 0f, 0f, false, preferWords);
    }

    private void flowDrawText(FlowCursor c, String text, FontRenderer font, int color,
                              float startX, float startY, float lineStep, float a,
                              boolean preferWords) {
        flowTextInternal(c, text, font, color, startX, startY, lineStep, a, true, preferWords);
    }

    private void flowTextInternal(FlowCursor c, String text, FontRenderer font, int color,
                                  float startX, float startY, float lineStep, float a,
                                  boolean draw, boolean preferWords) {
        if (text == null || text.isEmpty()) return;

        List<String> pieces = splitFlowPieces(text, preferWords);
        for (String piece : pieces) {
            if (piece.isEmpty()) continue;
            float pieceW = GlHelper.getStringWidth(piece, font);
            float available = c.lineWidth() - c.x;

            if (pieceW <= available) {
                if (draw) drawText(piece, startX + c.x, startY + c.line * lineStep, font, color, a);
                c.x += pieceW;
                continue;
            }

            if (c.x > 0f) {
                c.nextLine();
                available = c.lineWidth();
                if (pieceW <= available) {
                    if (draw) drawText(piece, startX, startY + c.line * lineStep, font, color, a);
                    c.x = pieceW;
                    continue;
                }
            }

            int from = 0;
            while (from < piece.length()) {
                float maxW = c.lineWidth() - c.x;
                int count = fittingChars(piece, from, font, maxW);
                if (count <= 0) {
                    c.nextLine();
                    continue;
                }
                String part = piece.substring(from, from + count);
                float w = GlHelper.getStringWidth(part, font);
                if (draw) drawText(part, startX + c.x, startY + c.line * lineStep, font, color, a);
                c.x += w;
                from += count;
                if (from < piece.length()) c.nextLine();
            }
        }
    }

    private List<String> splitFlowPieces(String text, boolean preferWords) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        if (!preferWords) {
            out.add(text);
            return out;
        }

        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                int end = i + 1;
                out.add(text.substring(start, end));
                start = end;
            }
        }
        if (start < text.length()) out.add(text.substring(start));
        if (out.isEmpty()) out.add(text);
        return out;
    }

    private int fittingChars(String text, int from, FontRenderer font, float maxWidth) {
        if (maxWidth <= 0f) return 0;
        int best = 0;
        for (int i = from + 1; i <= text.length(); i++) {
            float w = GlHelper.getStringWidth(text.substring(from, i), font);
            if (w > maxWidth) break;
            best = i - from;
        }
        return best;
    }

    private float inlineLineStep(FontRenderer nameFont, FontRenderer valueFont) {
        return Math.max(nameFont.getMetrics().capHeight(), valueFont.getMetrics().capHeight()) + 3f * SCALE;
    }

    private int inlineBlockHeight(int lines, FontRenderer nameFont, FontRenderer valueFont, float verticalPadding) {
        float cap = Math.max(nameFont.getMetrics().capHeight(), valueFont.getMetrics().capHeight());
        float h = cap + Math.max(0, lines - 1) * inlineLineStep(nameFont, valueFont);
        return (int) Math.ceil(h + verticalPadding * 2f);
    }

    private List<String> wrapLines(String text, FontRenderer font, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        if (maxWidth <= 1f) {
            lines.add(text);
            return lines;
        }

        String remaining = text.trim();
        while (!remaining.isEmpty()) {
            if (GlHelper.getStringWidth(remaining, font) <= maxWidth) {
                lines.add(remaining);
                break;
            }

            int best = 0;
            int lastSpace = -1;
            for (int i = 1; i <= remaining.length(); i++) {
                String part = remaining.substring(0, i);
                if (GlHelper.getStringWidth(part, font) > maxWidth) break;
                best = i;
                if (Character.isWhitespace(remaining.charAt(i - 1))) lastSpace = i - 1;
            }

            if (best <= 0) best = 1;
            int cut = lastSpace > 0 ? lastSpace : best;
            String line = remaining.substring(0, cut).trim();
            if (line.isEmpty()) {
                line = remaining.substring(0, best);
                cut = best;
            }
            lines.add(line);
            remaining = remaining.substring(cut).trim();
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private float wrappedLineStep(FontRenderer font) {
        return font.getMetrics().capHeight() + 3f * SCALE;
    }

    private float wrappedTextHeight(int lines, FontRenderer font) {
        if (lines <= 0) return 0f;
        return font.getMetrics().capHeight() + (lines - 1) * wrappedLineStep(font);
    }

    private int wrappedBlockHeight(int lines, FontRenderer font, float verticalPadding) {
        return (int) Math.ceil(wrappedTextHeight(lines, font) + verticalPadding * 2f);
    }

    private void drawWrappedLeft(List<String> lines, float x, float y, FontRenderer font, int color, float a) {
        float yy = y;
        for (String line : lines) {
            drawText(line, x, yy, font, color, a);
            yy += wrappedLineStep(font);
        }
    }

    private void drawWrappedCentered(List<String> lines, float x, float width, float y,
                                     FontRenderer font, int color, float a) {
        float yy = y;
        for (String line : lines) {
            float w = GlHelper.getStringWidth(line, font);
            drawText(line, x + (width - w) / 2f, yy, font, color, a);
            yy += wrappedLineStep(font);
        }
    }

    private float getExpandAnim(Module m) {
        Float v = expandAnims.get(m);
        if (v == null) {
            v = Boolean.TRUE.equals(expanded.get(m)) ? 1f : 0f;
            expandAnims.put(m, v);
        }
        return v;
    }

    private float tickExpandAnim(Module m) {
        boolean target = Boolean.TRUE.equals(expanded.get(m));
        float current = getExpandAnim(m);
        float targetF = target ? 1f : 0f;
        float speed = 6f;
        if (current < targetF) current = Math.min(targetF, current + speed * deltaTime);
        else if (current > targetF) current = Math.max(targetF, current - speed * deltaTime);
        expandAnims.put(m, current);
        return current;
    }

    private float getSwitchAnim(BooleanSetting s, boolean on) {
        Float v = switchAnims.get(s);
        if (v == null) {
            v = on ? 1f : 0f;
            switchAnims.put(s, v);
        }
        return v;
    }

    private float tickSwitchAnim(BooleanSetting s, boolean on) {
        float current = getSwitchAnim(s, on);
        float targetF = on ? 1f : 0f;
        float speed = 10f;
        if (current < targetF) current = Math.min(targetF, current + speed * deltaTime);
        else if (current > targetF) current = Math.max(targetF, current - speed * deltaTime);
        switchAnims.put(s, current);
        return current;
    }

    private int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawSwitch(GuiGraphics g, int x, int y, boolean on, float a, BooleanSetting setting) {
        float anim = tickSwitchAnim(setting, on);

        int w = (int) (SWITCH_W * SCALE);
        int h = (int) (SWITCH_H * SCALE);
        int knobW = (int) (SWITCH_KNOB_W * SCALE);
        int knobH = (int) (SWITCH_KNOB_H * SCALE);

        int trackColor = lerpColor(SWITCH_OFF_COLOR, SWITCH_ON_COLOR, anim);
        fillA(g, x, y + 1, x + w, y + h, trackColor, a);

        int pad = (int) (1 * SCALE);
        int travel = w - knobW - pad * 2;
        int kx = x + pad + (int) (travel * anim);
        fillA(g, kx, y, kx + knobW, y + knobH, KNOB, a);
    }

    private void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color, float a) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(h / 2, w / 2);
        fillA(g, x + r, y, x + w - r, y + h, color, a);
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = (int) Math.round(Math.sqrt(r * r - dy * dy));
            fillA(g, x + r - dx, y + i, x + r + dx, y + i + 1, color, a);
            fillA(g, x + w - r - dx, y + i, x + w - r + dx, y + i + 1, color, a);
            int iy = h - i - 1;
            fillA(g, x + r - dx, y + iy, x + r + dx, y + iy + 1, color, a);
            fillA(g, x + w - r - dx, y + iy, x + w - r + dx, y + iy + 1, color, a);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Panel> reverse = new ArrayList<>(panels);
        Collections.reverse(reverse);
        for (Panel p : reverse) {
            if (!p.hovered) continue;

            if (inside(mouseX, mouseY, p.x, p.y, p.width, HEADER_HEIGHT)) {
                if (button == 0) {
                    draggingPanel = p;
                    dragOffsetX = (int) mouseX - p.x;
                    dragOffsetY = (int) mouseY - p.y;
                    p.movedByUser = true;
                    panels.remove(p);
                    panels.add(p);
                    return true;
                }
            }

            int cursorY = p.y + HEADER_HEIGHT - p.scroll;
            for (Module m : modules(p)) {
                if (inside(mouseX, mouseY, p.x, cursorY, p.width, MODULE_HEIGHT)) {
                    if (button == 0) m.toggle();
                    else if (button == 1) {
                        boolean nowExpanded = !Boolean.TRUE.equals(expanded.get(m));
                        expanded.put(m, nowExpanded);
                    } else if (button == 2) keyListening = m;
                    return true;
                }
                cursorY += MODULE_HEIGHT;

                float expAnim = getExpandAnim(m);
                if (expAnim <= 0.001f) continue;

                List<Setting<?>> settings = settingsOf(m);
                int fullH = SETTING_HEIGHT;
                for (Setting<?> s : settings) {
                    if (visible(s)) fullH += settingHeight(s, p.width);
                }
                int visibleH = (int) (fullH * expAnim);

                if (inside(mouseX, mouseY, p.x, cursorY, p.width, SETTING_HEIGHT)) {
                    keyListening = keyListening == m ? null : m;
                    return true;
                }
                cursorY += SETTING_HEIGHT;

                for (Setting<?> setting : settings) {
                    if (!visible(setting)) continue;
                    int h = settingHeight(setting, p.width);
                    if (inside(mouseX, mouseY, p.x, cursorY, p.width, h)) {
                        return clickSetting(setting, p.x, cursorY, mouseX, mouseY, button, p.width);
                    }
                    cursorY += h;
                }
                int extra = fullH - visibleH;
                if (extra > 0) cursorY -= extra;
            }
        }
        textEditing = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickSetting(Setting<?> setting, int x, int y, double mouseX, double mouseY, int button, int panelW) {
        if (setting instanceof BooleanSetting s) {
            s.setValue(!Boolean.TRUE.equals(s.getValue()));

            // 动态更新静态变量并重绘
            if (s.getName().equals("Chinese")) {
                ClickGui.chinese = s.getValue();
                settingsCache.clear();
                buildPanels();
            }
            return true;
        }
        if (setting instanceof NumberSetting s) {
            sliderDragging = s;
            sliderX = x + (int) (5 * SCALE);
            sliderW = panelW - (int) (10 * SCALE);
            updateSlider(mouseX);
            return true;
        }
        if (setting instanceof ModeSetting s) {
            boolean open = Boolean.TRUE.equals(modeExpanded.get(s));
            if (button == 1 || (button == 0 && !open)) {
                modeExpanded.put(s, !open);
                return true;
            }
            if (button == 0 && open) {
                int yy = y + settingBaseHeight(s, panelW);
                for (String mode : s.getModes()) {
                    String cnMode = ClickGui.cn(mode);
                    int optionH = modeOptionHeight(cnMode, panelW);
                    if (inside(mouseX, mouseY, x, yy, panelW, optionH)) {
                        s.setValue(mode);
                        modeExpanded.put(s, false);
                        return true;
                    }
                    yy += optionH;
                }
                return true;
            }
            return true;
        }
        if (setting instanceof MultiSelectSetting s) {
            if (s.getOptions().isEmpty()) return true;
            List<String> now = new ArrayList<>(s.getValue());
            if (button == 1) now.clear();
            else {
                String candidate = null;
                for (String option : s.getOptions()) {
                    if (!now.contains(option)) { candidate = option; break; }
                }
                if (candidate == null) now.clear();
                else now.add(candidate);
            }
            s.setValue(now);
            return true;
        }
        if (setting instanceof TextSetting s) {
            textEditing = textEditing == s ? null : s;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPanel != null) {
            draggingPanel.x = clamp((int) mouseX - dragOffsetX, 0, Math.max(0, width - draggingPanel.width));
            draggingPanel.y = clamp((int) mouseY - dragOffsetY, 0, Math.max(0, height - HEADER_HEIGHT));
            SAVED_POSITIONS.put(draggingPanel.title, new int[]{draggingPanel.x, draggingPanel.y});
            return true;
        }
        if (sliderDragging != null) {
            updateSlider(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPanel != null) {
            SAVED_POSITIONS.put(draggingPanel.title, new int[]{draggingPanel.x, draggingPanel.y});
        }
        draggingPanel = null;
        sliderDragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (Panel p : panels) {
            if (!p.hovered) continue;
            int max = Math.max(0, p.contentH - p.viewportH);
            p.scroll = clamp(p.scroll - (int) Math.round(delta * 16.0), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyListening != null) {
            keyListening.setKey(keyCode == GLFW.GLFW_KEY_ESCAPE ? 0 : keyCode);
            keyListening = null;
            save();
            return true;
        }
        if (textEditing != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                textEditing = null;
                save();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String v = textEditing.getValue();
                if (v != null && !v.isEmpty()) textEditing.setValue(v.substring(0, v.length() - 1));
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (textEditing != null && !Character.isISOControl(codePoint)) {
            String v = textEditing.getValue();
            textEditing.setValue((v == null ? "" : v) + codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            closingAt = System.currentTimeMillis();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateSlider(double mouseX) {
        if (sliderDragging == null) return;
        double t = Math.max(0.0, Math.min(1.0, (mouseX - sliderX) / (double) sliderW));
        double min = sliderDragging.getMin().doubleValue();
        double max = sliderDragging.getMax().doubleValue();
        double step = Math.max(0.0000001, sliderDragging.getStep().doubleValue());
        double value = min + (max - min) * t;
        value = Math.round(value / step) * step;
        value = Math.max(min, Math.min(max, value));
        sliderDragging.setValue(value);
    }

    private boolean visible(Setting<?> setting) {
        try {
            return setting.getVisibility() == null || setting.getVisibility().displayable();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private float animation() {
        long now = System.currentTimeMillis();
        float t;
        if (closing) t = 1f - Math.min(1f, (now - closingAt) / 160f);
        else t = Math.min(1f, (now - openedAt) / 190f);
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private void save() {
        try {
            if (NiloreClient.getInstance() != null && NiloreClient.getInstance().getConfigManager() != null) {
                NiloreClient.getInstance().getConfigManager().saveAll();
            }
        } catch (Throwable ignored) {
        }
    }

    private void fillA(GuiGraphics g, int x1, int y1, int x2, int y2, int argb, float a) {
        int alpha = (argb >>> 24) & 0xFF;
        int aa = clamp((int) (alpha * a), 0, 255);
        if (aa <= 1) return;
        g.fill(x1, y1, x2, y2, (aa << 24) | (argb & 0xFFFFFF));
    }

    private void drawText(String s, float x, float y, FontRenderer font, int color, float alpha) {
        if (s == null || s.isEmpty()) return;
        int a = clamp((int) (255 * alpha), 0, 255);
        int c = (a << 24) | (color & 0xFFFFFF);
        GlHelper.drawText(s, x, y, font, c);
    }

    private static String pretty(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0E-8) return Integer.toString((int) Math.rint(v));
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // 首字母大写，其余小写（按单词处理）
    private static String formatKeyName(String name) {
        if (name == null || name.isEmpty()) return name;
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : lower.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String keyName(int key) {
        if (key == 0) return "无";
        try {
            String name = InputConstants.getKey(key, 0).getDisplayName().getString();
            return formatKeyName(name);
        } catch (Throwable ignored) {
        }

        String n = GLFW.glfwGetKeyName(key, 0);
        if (n != null && !n.isEmpty()) {
            return formatKeyName(n);
        }
        return "K" + key;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    private static final class Panel {
        final String title;
        final List<Category> categories;
        int x, y, scroll, contentH, viewportH, width, maxHeight;
        boolean hovered, movedByUser;

        Panel(String title, List<Category> categories) {
            this.title = title;
            this.categories = categories;
        }
    }
}