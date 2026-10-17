package client.nilore.hud;

import client.nilore.NiloreClient;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.utils.render.ColorUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.ModuleToggleEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.render.DrawContext;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.Fonts;
import client.nilore.render.GlHelper;
import client.nilore.render.Paint;
import client.nilore.render.Rectangle;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.misc.SoundUtil;
import client.nilore.utils.render.RenderUtil;
import client.nilore.utils.render.TextureUtil;
import com.mojang.blaze3d.vertex.PoseStack;

public class NotificationHud extends HudElement {

    private static final float SOUTHSIDE_WIDTH = 171.0f;
    private static final float SOUTHSIDE_HEIGHT = 45.0f;
    private static final float SOUTHSIDE_RADIUS = 5.4f;
    private static final float SOUTHSIDE_PADDING = 7.2f;
    private static final float SOUTHSIDE_BAR_HEIGHT = 2.7f;
    private static final float SOUTHSIDE_SPACING = 5.4f;
    private static final float SOUTHSIDE_ICON_SIZE = 21.6f;
    private static final float SIMPLE_HEIGHT = 30.0f;
    private static final float SIMPLE_RADIUS = 5.0f;
    private static final float SIMPLE_PADDING = 7.0f;
    private static final float SIMPLE_ICON_SIZE = 16.0f;
    private static final float SIMPLE_ICON_GAP = 6.0f;
    private static final float SIMPLE_SPACING = 4.0f;
    private static final float AKARIN_MIN_WIDTH = 110.0f;
    private static final float AKARIN_HEIGHT = 29.0f;
    private static final float AKARIN_RADIUS = 6.0f;
    private static final float AKARIN_TEXT_X = 6.0f;
    private static final float AKARIN_RIGHT_PADDING = 6.0f;
    private static final float AKARIN_STRIPE_X = 0.0f;
    private static final float AKARIN_STRIPE_WIDTH = 1.5f;
    private static final float AKARIN_SPACING = 5.0f;
    private static final FontRenderer AKARIN_TITLE_FONT = Fonts.getRenderer("quicksand.ttf", 17.0f);
    private static final FontRenderer AKARIN_STATUS_FONT = Fonts.getRenderer("quicksand.ttf", 18.0f);
    private static final int BG_COLOR = 0xFF111615;
    private static final int BAR_COLOR = 0xFFFFFFFF;
    private static final int BAR_BG_COLOR = 0xFF3A3A3A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final NumberSetting margin = new NumberSetting("Margin", 8.0f, 0.0f, 100.0f, 1.0f);
    private final NumberSetting duration = new NumberSetting("Duration (ms)", 800, 500, 10000, 100);
    private final NumberSetting maxNotifications = new NumberSetting("Max Notifications", 7, 1, 10, 1);
    private final BooleanSetting needSound = new BooleanSetting("Sound", true);

    /**
     * 通知音效模式：共 27 种，各不相同。
     *
     * ==== MC 原版音效类 ====
     *   Lever    —— 原版拉杆声
     *   Bell     —— 升级铃声（PLAYER_LEVELUP）
     *   Pling    —— 经验球 + 短音
     *   Click    —— UI 按钮点击
     *   Bass     —— 低音贝斯
     *   Chime    —— 风铃和弦（两个 CHIME 音混合）
     *   Harp     —— 竖琴三音和弦
     *   Celesta  —— 八音盒 / 木琴
     *   Soft     —— 柔和颤音琴
     *   Crystal  —— 水晶风铃（BELL + CHIME 高音）
     *   Dreamy   —— 梦幻：风铃 + 长笛高音
     *   Sparkle  —— 闪光：铃铛 + 风铃双高音
     *   Sunshine —— 阳光：竖琴欢快大三和弦
     *   Twinkle  —— 闪烁：风铃高音快速连击
     *   Candy    —— 糖果：Pling 高音 + 经验球
     *   Melody   —— 旋律：竖琴四音阶上行
     *   Flower   —— 花朵：木琴高音阶
     *   Lullaby  —— 摇篮曲：长笛柔和下行 + 风铃轻收尾（不使用任何琴类音效）
     *
     * ==== 自定义 wav 资源类（不依赖 MC 原版音色）====
     *   Sigma    —— 自定义 wav 资源（Enabled.wav / Disabled.wav）
     *   Sweet    —— 甜妹音：原版高音 + 甜妹 wav
     *   Anime    —— 二次元语音：原版高音 + anime wav
     *   Jelly    —— 果冻：软弹「啵嘤～」Q 弹果冻声（纯自定义）
     *   Mochi    —— 麻薯：软糯「嗯～」黏糊糊（纯自定义）
     *   Kitty    —— 小猫：「喵呜～」小动物回应（纯自定义）
     *   Bubble   —— 泡泡：「啵！」泡泡破裂，短促清脆（纯自定义）
     *   Cream    —— 奶油：软乎乎「呼～」奶油化开（纯自定义）
     *   Pop      —— 泡泡糖：「啪嗒」一声，俏皮可爱（纯自定义）
     *
     * 自定义音频文件需放在：
     *   src/main/resources/assets/nilore/notifications/
     * 命名规则：
     *   {name}_enabled.wav  —— 模块开启时播放
     *   {name}_disabled.wav —— 模块关闭时播放
     */
    private final ModeSetting whichSound = new ModeSetting(
            "Sound",
            "Lever",
            "Sigma",
            "Sweet",
            "Bell",
            "Pling",
            "Click",
            "Bass",
            "Anime",
            "Chime",
            "Harp",
            "Celesta",
            "Soft",
            "Crystal",
            "Dreamy",
            "Sparkle",
            "Sunshine",
            "Twinkle",
            "Candy",
            "Melody",
            "Flower",
            "Lullaby",
            "Jelly",
            "Mochi",
            "Kitty",
            "Bubble",
            "Cream",
            "Pop"
    ).withDefault("Lever");

    private final ModeSetting style = new ModeSetting("Style", "Southside", "Simple", "Naven", "Akarin", "Nova", "Glass", "Pulse", "Aurora", "Compact", "Mono", "Prism", "Circuit", "Sakura", "Neon", "Orbit", "Matrix", "Ribbon", "Hex", "Wave", "Eclipse", "Cyber", "Blossom", "Frost", "Ember", "Galaxy", "Pixel", "Rune", "Comet", "Crown", "Spectrum").withDefault("Nova");

    // 所有通知样式共用的透明度设置。
    private final BooleanSetting customAlpha = new BooleanSetting("Custom Alpha", false);
    private final NumberSetting globalAlpha = new NumberSetting(
            "Global Alpha",
            210,
            20,
            255,
            5,
            this.customAlpha::getValue
    );
    private final NumberSetting navenTextXOff = new NumberSetting("Naven Text X Off", 7, 0, 20, 0.5f);
    private final NumberSetting navenTextYOff = new NumberSetting("Naven Text Y Off", 11.5, 0, 20, 0.5f);
    private final NumberSetting navenWidthPad = new NumberSetting("Naven Width Pad", 10, 0, 20, 1);
    private final NumberSetting navenCardH = new NumberSetting("Naven Card Height", 19, 12, 30, 1);
    private final NumberSetting navenRadius = new NumberSetting("Naven Radius", 6, 0, 10, 0.5f);
    private final NumberSetting navenSlideMs = new NumberSetting("Naven Slide (ms)", 120, 20, 300, 10);

    private final List<NotificationEntry> notifications = new ArrayList<>();

    private DynamicTexture enabledIcon;
    private DynamicTexture disabledIcon;

    public NotificationHud() {
        super("Notification");
        this.setWidth(SOUTHSIDE_WIDTH);
        this.setHeight(SOUTHSIDE_HEIGHT);
        this.setEnabled(true);
        navenTextXOff.setVisibility(() -> style.is("Naven"));
        navenTextYOff.setVisibility(() -> style.is("Naven"));
        navenWidthPad.setVisibility(() -> style.is("Naven"));
        navenCardH.setVisibility(() -> style.is("Naven"));
        navenRadius.setVisibility(() -> style.is("Naven"));
        navenSlideMs.setVisibility(() -> style.is("Naven"));
    }

    @Override
    public void registerSettings() {
        this.registerSetting(margin, duration, maxNotifications, needSound, whichSound, style, customAlpha, globalAlpha, navenTextXOff, navenTextYOff, navenWidthPad, navenCardH, navenRadius, navenSlideMs);
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

    // ======================================================================
    // 音效：27 种模式
    //  - 前 21 种为 MC 原版音效组合
    //  - 后 6 种为纯自定义 wav 资源（Jelly / Mochi / Kitty / Bubble / Cream / Pop）
    // ======================================================================
    private void playNotificationSound(boolean enabled) {
        if (mc == null) return;

        final String mode = this.whichSound.getValue();
        if (mode == null) return;

        switch (mode) {
            // ==============================================================
            // MC 原版音效组合
            // ==============================================================
            case "Lever" -> {
                // 原版拉杆声
                if (mc.player != null) {
                    if (enabled) {
                        mc.player.playSound(SoundEvents.LEVER_CLICK, 1.0f, 0.6f);
                    } else {
                        mc.player.playSound(SoundEvents.LEVER_CLICK, 1.0f, 0.5f);
                    }
                }
            }
            case "Sigma" -> {
                // 自定义 wav 资源（Enabled/Disabled.wav）
                String soundPath = enabled
                        ? "/assets/nilore/notifications/Enabled.wav"
                        : "/assets/nilore/notifications/Disabled.wav";
                SoundUtil.playResourceSound(soundPath, 0.0f);
            }
            case "Sweet" -> {
                // 二次元甜妹音：高音铃 + 经验球 + 甜妹语音
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, enabled ? 1.95f : 1.55f);
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, enabled ? 2.0f : 1.7f);
                }
                String path = enabled
                        ? "/assets/nilore/notifications/sweet_enabled.wav"
                        : "/assets/nilore/notifications/sweet_disabled.wav";
                SoundUtil.playResourceSound(path, 0.0f);
            }
            case "Bell" -> {
                // 升级铃声
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, enabled ? 1.2f : 0.9f);
                }
            }
            case "Pling" -> {
                // 经验球短音
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, enabled ? 1.5f : 1.0f);
                }
            }
            case "Click" -> {
                // UI 按钮点击
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, enabled ? 1.2f : 0.9f);
                }
            }
            case "Bass" -> {
                // 低音贝斯
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, enabled ? 0.7f : 0.5f);
                }
            }
            case "Anime" -> {
                // 二次元语音：超高音 Pling + anime wav 资源
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, enabled ? 2.0f : 1.75f);
                }
                String path2 = enabled
                        ? "/assets/nilore/notifications/anime_enabled.wav"
                        : "/assets/nilore/notifications/anime_disabled.wav";
                SoundUtil.playResourceSound(path2, 0.0f);
            }
            case "Chime" -> {
                // 风铃和弦：两个 CHIME 混合形成悦耳和声
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, enabled ? 1.40f : 1.00f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.85f, enabled ? 1.85f : 1.30f);
                }
            }
            case "Harp" -> {
                // 竖琴三音和弦：C 大三和弦（do-mi-so）
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 1.0f, enabled ? 0.80f : 0.60f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.90f, enabled ? 1.00f : 0.80f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.80f, enabled ? 1.20f : 1.00f);
                }
            }
            case "Celesta" -> {
                // 八音盒 / 音乐盒：木琴高音阶
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 1.0f, enabled ? 1.50f : 1.20f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.80f, enabled ? 2.00f : 1.60f);
                }
            }
            case "Soft" -> {
                // 柔和颤音琴
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 1.0f, enabled ? 0.80f : 0.60f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.65f, enabled ? 1.20f : 1.00f);
                }
            }
            case "Crystal" -> {
                // 水晶风铃：BELL 高音 + CHIME 高音，通透清澈
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.75f, enabled ? 1.55f : 1.20f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.95f, enabled ? 2.00f : 1.70f);
                }
            }
            case "Dreamy" -> {
                // 梦幻：风铃 + 长笛高音，轻柔空灵
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.75f, enabled ? 1.55f : 1.20f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.85f, enabled ? 1.80f : 1.45f);
                }
            }
            case "Sparkle" -> {
                // 闪光：铃铛 + 风铃双高音，清脆闪亮
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.90f, enabled ? 1.85f : 1.50f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.80f, enabled ? 2.00f : 1.65f);
                }
            }
            case "Sunshine" -> {
                // 阳光：竖琴欢快大三和弦（do-mi-so），明亮开朗
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 1.00f, enabled ? 1.00f : 0.75f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.90f, enabled ? 1.25f : 1.00f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.85f, enabled ? 1.55f : 1.20f);
                }
            }
            case "Twinkle" -> {
                // 闪烁：风铃高音快速连击，闪亮可爱
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.85f, enabled ? 1.95f : 1.60f);
                }
            }
            case "Candy" -> {
                // 糖果：Pling 高音 + 经验球，甜美可口
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.00f, enabled ? 2.00f : 1.70f);
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.85f, enabled ? 1.85f : 1.55f);
                }
            }
            case "Melody" -> {
                // 旋律：竖琴四音阶上行（do-re-mi-so），轻快愉悦
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 1.00f, enabled ? 0.85f : 0.65f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.90f, enabled ? 1.05f : 0.85f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.85f, enabled ? 1.25f : 1.05f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.80f, enabled ? 1.50f : 1.25f);
                }
            }
            case "Flower" -> {
                // 花朵：木琴高音阶，轻盈清澈
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 1.00f, enabled ? 1.65f : 1.35f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.80f, enabled ? 2.00f : 1.75f);
                }
            }
            case "Lullaby" -> {
                // 摇篮曲：长笛柔和下行 + 风铃轻收尾，温柔安详（不使用任何琴类音效）
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.95f, enabled ? 1.20f : 0.95f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.85f, enabled ? 0.95f : 0.75f);
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.60f, enabled ? 0.90f : 0.70f);
                }
            }
            // ==============================================================
            // 纯自定义 wav 资源（MC 原版没有的声音）
            // 全部走 SoundUtil.playResourceSound，不依赖音符盒音色
            // ==============================================================
            case "Jelly" -> {
                // 果冻：软弹「啵嘤～」Q 弹果冻声
                String p = enabled
                        ? "/assets/nilore/notifications/jelly_enabled.wav"
                        : "/assets/nilore/notifications/jelly_disabled.wav";
                SoundUtil.playResourceSound(p, 0.0f);
            }
            case "Mochi" -> {
                // 麻薯：软糯「嗯～」黏糊糊
                String p = enabled
                        ? "/assets/nilore/notifications/mochi_enabled.wav"
                        : "/assets/nilore/notifications/mochi_disabled.wav";
                SoundUtil.playResourceSound(p, 0.0f);
            }
            case "Kitty" -> {
                // 小猫：「喵呜～」小动物回应
                String p = enabled
                        ? "/assets/nilore/notifications/kitty_enabled.wav"
                        : "/assets/nilore/notifications/kitty_disabled.wav";
                SoundUtil.playResourceSound(p, 0.0f);
            }
            case "Bubble" -> {
                // 泡泡：「啵！」泡泡破裂，短促清脆
                String p = enabled
                        ? "/assets/nilore/notifications/bubble_enabled.wav"
                        : "/assets/nilore/notifications/bubble_disabled.wav";
                SoundUtil.playResourceSound(p, 0.0f);
            }
            case "Cream" -> {
                // 奶油：软乎乎「呼～」奶油化开
                String p = enabled
                        ? "/assets/nilore/notifications/cream_enabled.wav"
                        : "/assets/nilore/notifications/cream_disabled.wav";
                SoundUtil.playResourceSound(p, 0.0f);
            }
            case "Pop" -> {
                // 泡泡糖：「啪嗒」一声，俏皮可爱
                String p = enabled
                        ? "/assets/nilore/notifications/pop_enabled.wav"
                        : "/assets/nilore/notifications/pop_disabled.wav";
                SoundUtil.playResourceSound(p, 0.0f);
            }
            default -> {
                // 默认回退为拉杆声
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.LEVER_CLICK, 1.0f, enabled ? 0.6f : 0.5f);
                }
            }
        }
    }

    @EventTarget
    public void onModuleToggle(ModuleToggleEvent event) {
        if (event.module() == this) {
            return;
        }
        loadTextures();
        String entryStyle = style.getValue();
        boolean simple = "Simple".equals(entryStyle);
        boolean naven = "Naven".equals(entryStyle);
        boolean akarin = "Akarin".equals(entryStyle);

        FontRenderer navenFont = FontPresets.openSans(18f);
        FontRenderer modernFont = FontPresets.openSans(15f);

        String displayText = akarin
                ? (event.enabled() ? "Enabled " : "Disabled ") + event.module().getName()
                : event.module().getName() + (event.enabled() ? " Enabled" : " Disabled");

        float width;
        float height;
        float spacing;

        switch (entryStyle) {
            case "Akarin" -> {
                width = Math.max(AKARIN_MIN_WIDTH,
                        AKARIN_TEXT_X + AKARIN_STATUS_FONT.getWidth(displayText) + AKARIN_RIGHT_PADDING);
                height = AKARIN_HEIGHT;
                spacing = AKARIN_SPACING;
            }
            case "Naven" -> {
                width = navenFont.getWidth(displayText) + navenWidthPad.getValue().floatValue();
                height = navenCardH.getValue().floatValue() + 4f;
                spacing = 4f;
            }
            case "Simple" -> {
                width = SIMPLE_PADDING * 2.0f + SIMPLE_ICON_SIZE + SIMPLE_ICON_GAP
                        + GlHelper.getStringWidth(displayText, FontPresets.pingfang(18.0f)) + 3.0f;
                height = SIMPLE_HEIGHT;
                spacing = SIMPLE_SPACING;
            }
            case "Nova" -> {
                float nameW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(16f));
                width = Math.max(142f, nameW + 78f);
                height = 38f;
                spacing = 5f;
            }
            case "Glass" -> {
                float nameW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(16f));
                width = Math.max(150f, nameW + 72f);
                height = 40f;
                spacing = 6f;
            }
            case "Pulse" -> {
                float nameW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(16f));
                width = Math.max(138f, nameW + 68f);
                height = 35f;
                spacing = 5f;
            }
            case "Aurora" -> {
                float nameW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(16f));
                width = Math.max(158f, nameW + 82f);
                height = 42f;
                spacing = 6f;
            }
            case "Compact" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), modernFont);
                width = Math.max(112f, textW + 54f);
                height = 28f;
                spacing = 4f;
            }
            case "Mono" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(14f));
                width = Math.max(105f, textW + 42f);
                height = 25f;
                spacing = 3f;
            }
            case "Prism" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(132.0f, textW + 58.0f);
                height = 30.0f;
                spacing = 4.0f;
            }
            case "Circuit" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(139.0f, textW + 63.0f);
                height = 34.0f;
                spacing = 5.0f;
            }
            case "Sakura" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(146.0f, textW + 68.0f);
                height = 38.0f;
                spacing = 5.0f;
            }
            case "Neon" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(153.0f, textW + 73.0f);
                height = 40.0f;
                spacing = 6.0f;
            }
            case "Orbit" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(160.0f, textW + 58.0f);
                height = 36.0f;
                spacing = 4.0f;
            }
            case "Matrix" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(132.0f, textW + 63.0f);
                height = 30.0f;
                spacing = 4.0f;
            }
            case "Ribbon" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(139.0f, textW + 68.0f);
                height = 34.0f;
                spacing = 5.0f;
            }
            case "Hex" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(146.0f, textW + 73.0f);
                height = 38.0f;
                spacing = 5.0f;
            }
            case "Wave" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(153.0f, textW + 58.0f);
                height = 40.0f;
                spacing = 6.0f;
            }
            case "Eclipse" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(160.0f, textW + 63.0f);
                height = 36.0f;
                spacing = 4.0f;
            }
            case "Cyber" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(132.0f, textW + 68.0f);
                height = 30.0f;
                spacing = 4.0f;
            }
            case "Blossom" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(139.0f, textW + 73.0f);
                height = 34.0f;
                spacing = 5.0f;
            }
            case "Frost" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(146.0f, textW + 58.0f);
                height = 38.0f;
                spacing = 5.0f;
            }
            case "Ember" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(153.0f, textW + 63.0f);
                height = 40.0f;
                spacing = 6.0f;
            }
            case "Galaxy" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(160.0f, textW + 68.0f);
                height = 36.0f;
                spacing = 4.0f;
            }
            case "Pixel" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(132.0f, textW + 73.0f);
                height = 30.0f;
                spacing = 4.0f;
            }
            case "Rune" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(139.0f, textW + 58.0f);
                height = 34.0f;
                spacing = 5.0f;
            }
            case "Comet" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(146.0f, textW + 63.0f);
                height = 38.0f;
                spacing = 5.0f;
            }
            case "Crown" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.openSans(15f));
                width = Math.max(153.0f, textW + 68.0f);
                height = 40.0f;
                spacing = 6.0f;
            }
            case "Spectrum" -> {
                float textW = GlHelper.getStringWidth(event.module().getName(), FontPresets.pingfang(15f));
                width = Math.max(160.0f, textW + 73.0f);
                height = 36.0f;
                spacing = 4.0f;
            }
            default -> {
                width = SOUTHSIDE_WIDTH;
                height = SOUTHSIDE_HEIGHT;
                spacing = SOUTHSIDE_SPACING;
            }
        }
        notifications.add(new NotificationEntry(event.module().getName(), event.enabled(), System.currentTimeMillis(),
                entryStyle, displayText, width, height, spacing));
        while (notifications.size() > maxNotifications.getValue().intValue()) {
            notifications.remove(0);
        }
        if (this.isEnabled() && needSound.getValue()) {
            playNotificationSound(event.enabled());
        }
    }

    @Override
    public void onRender2D(Render2DEvent event, float px, float py) {
        if (mc.getWindow() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long dur = duration.getValue().longValue();
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float marginVal = margin.getValue().floatValue();
        boolean dynamicStyle = isSimpleStyle() || isNavenStyle() || isAkarinStyle()
                || isModernStyle();
        this.setWidth(dynamicStyle ? 0.0f : SOUTHSIDE_WIDTH);
        this.setHeight(isAkarinStyle() ? AKARIN_HEIGHT
                : isNavenStyle() ? navenCardH.getValue().floatValue() + 4f
                : isSimpleStyle() ? SIMPLE_HEIGHT
                : isModernStyle() ? 40.0f
                : SOUTHSIDE_HEIGHT);

        Iterator<NotificationEntry> it = notifications.iterator();
        while (it.hasNext()) {
            NotificationEntry entry = it.next();
            long elapsed = now - entry.time;
            float targetX = screenW - entry.width - marginVal;
            float offscreenX = screenW + 10.0f;

            if (elapsed < dur) {
                if (!entry.entranceStarted) {
                    entry.entranceStarted = true;
                    entry.entranceTime = now;
                    entry.x = offscreenX;
                }
                float entranceElapsed = (now - entry.entranceTime) / 1000f;
                float slideSec = "Naven".equals(entry.style)
                        ? navenSlideMs.getValue().floatValue() / 1000f
                        : isEntryModernStyle(entry.style) ? 0.16f : 0.08f;
                float t = Math.min(1.0f, entranceElapsed / slideSec);
                float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
                entry.x = offscreenX + (targetX - offscreenX) * eased;
                entry.alpha = Math.min(1.0f, entry.alpha + (isEntryModernStyle(entry.style) ? 0.075f : 0.05f));
            } else if (!entry.exiting) {
                entry.exiting = true;
                entry.lastBarProgress = 0.0f;
                entry.exitStartTime = now;
            } else {
                float exitElapsed = (now - entry.exitStartTime) / 1000f;
                float exitSec = "Naven".equals(entry.style)
                        ? navenSlideMs.getValue().floatValue() / 1000f
                        : isEntryModernStyle(entry.style) ? 0.13f : 0.04f;
                float t = Math.min(1.0f, exitElapsed / exitSec);
                float eased = t * t;
                entry.x = targetX + (offscreenX - targetX) * eased;
                entry.alpha = 1.0f - t;
                if (t >= 1.0f) {
                    it.remove();
                }
            }
        }

        if (notifications.isEmpty()) {
            return;
        }

        Renderer.render(event.guiGraphics(), drawContext -> {
            float nextY = screenH - marginVal;
            for (NotificationEntry entry : notifications) {
                float cardY = nextY - entry.height;
                nextY = cardY - entry.spacing;
                long elapsed = now - entry.time;
                float progress = entry.exiting ? entry.lastBarProgress
                        : Mth.clamp((float) elapsed / dur, 0.0f, 1.0f);
                if (!entry.exiting) {
                    entry.lastBarProgress = progress;
                }
                renderCard(drawContext, entry, entry.x, cardY, progress, Mth.clamp(entry.alpha, 0.0f, 1.0f));
            }
        });
    }

    private void renderCard(DrawContext drawContext, NotificationEntry entry, float x, float y, float progress, float alpha) {
        if (this.customAlpha.getValue()) {
            alpha *= Mth.clamp(this.globalAlpha.getValue().floatValue() / 255.0f, 0.0f, 1.0f);
        }

        switch (entry.style) {
            case "Simple" -> renderSimpleCard(drawContext, entry, x, y, alpha);
            case "Naven" -> renderNavenCard(drawContext, entry, x, y, alpha);
            case "Akarin" -> renderAkarinCard(drawContext, entry, x, y, alpha);
            case "Nova" -> renderNovaCard(drawContext, entry, x, y, progress, alpha);
            case "Glass" -> renderGlassCard(drawContext, entry, x, y, progress, alpha);
            case "Pulse" -> renderPulseCard(drawContext, entry, x, y, progress, alpha);
            case "Aurora" -> renderAuroraCard(drawContext, entry, x, y, progress, alpha);
            case "Compact" -> renderCompactCard(drawContext, entry, x, y, alpha);
            case "Mono" -> renderMonoCard(drawContext, entry, x, y, alpha);
            case "Prism" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Circuit" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Sakura" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Neon" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Orbit" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Matrix" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Ribbon" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Hex" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Wave" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Eclipse" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Cyber" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Blossom" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Frost" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Ember" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Galaxy" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Pixel" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Rune" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Comet" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Crown" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            case "Spectrum" -> renderDesignerCard(drawContext, entry, x, y, progress, alpha);
            default -> renderSouthsideCard(drawContext, entry, x, y, progress, alpha);
        }
    }

    private void renderSouthsideCard(DrawContext drawContext, NotificationEntry entry,
                                     float x, float y, float progress, float alpha) {
        float barY = y + entry.height - SOUTHSIDE_BAR_HEIGHT;
        float barH = SOUTHSIDE_BAR_HEIGHT;
        float barWidth = entry.width * progress;
        float[] radii = new float[]{SOUTHSIDE_RADIUS, SOUTHSIDE_RADIUS, SOUTHSIDE_RADIUS, SOUTHSIDE_RADIUS};

        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(BG_COLOR, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHRadii(x, y, entry.width, entry.height, radii), paint);

            drawContext.save();
            drawContext.clip(Rectangle.ofXYWH(x, barY + 1, entry.width, barH - 1));
            paint.setColor(ColorUtil.withAlpha(BAR_BG_COLOR, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHRadii(x, y, entry.width, entry.height, radii), paint);
            drawContext.restore();

            if (barWidth > 0.5f) {
                drawContext.save();
                drawContext.clip(Rectangle.ofXYWH(x, barY + 1, barWidth, barH - 1));
                paint.setColor(ColorUtil.withAlpha(BAR_COLOR, alpha));
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHRadii(x, y, entry.width, entry.height, radii), paint);
                drawContext.restore();
            }
        }

        float drawSize = entry.enabled ? SOUTHSIDE_ICON_SIZE * 0.9f : SOUTHSIDE_ICON_SIZE;
        drawIcon(drawContext, entry.enabled, x + SOUTHSIDE_PADDING,
                y + (entry.height - SOUTHSIDE_BAR_HEIGHT - drawSize) / 2.0f, drawSize, alpha);
        float textX = x + SOUTHSIDE_PADDING + SOUTHSIDE_ICON_SIZE + 5.4f;
        FontRenderer titleFont = FontPresets.pingfang(16.2f);
        FontRenderer descFont = FontPresets.pingfang(12.6f);
        GlHelper.drawText("Module", textX, y + SOUTHSIDE_PADDING + 6.05f, titleFont, ColorUtil.withAlpha(TEXT_COLOR, alpha));
        GlHelper.drawText("Toggled " + entry.name + " " + (entry.enabled ? "on" : "off"), textX,
                y + SOUTHSIDE_PADDING + 18.25f, descFont, ColorUtil.withAlpha(0xFFCCCCCC, alpha));
    }

    private void renderSimpleCard(DrawContext drawContext, NotificationEntry entry, float x, float y, float alpha) {
        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(BG_COLOR, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, SIMPLE_RADIUS), paint);
        }
        float drawSize = entry.enabled ? SIMPLE_ICON_SIZE * 0.9f : SIMPLE_ICON_SIZE;
        drawIcon(drawContext, entry.enabled, x + SIMPLE_PADDING, y + (entry.height - drawSize) / 2.0f, drawSize, alpha);
        FontRenderer font = FontPresets.pingfang(18.0f);
        GlHelper.drawText(entry.displayText, x + SIMPLE_PADDING + SIMPLE_ICON_SIZE + SIMPLE_ICON_GAP,
                y + entry.height / 2.0f - 2f, font, ColorUtil.withAlpha(TEXT_COLOR, alpha));
    }

    private void renderAkarinCard(DrawContext drawContext, NotificationEntry entry, float x, float y, float alpha) {
        PoseStack poseStack = drawContext.getPoseStack();
        poseStack.pushPose();
        RenderUtil.drawBlurredRect(poseStack, x, y, entry.width, entry.height, AKARIN_RADIUS, 15.0f, alpha * 0.95f, 0);
        poseStack.popPose();

        int backgroundAlpha = Math.round(80.0f * alpha);
        try (Paint paint = new Paint()) {
            paint.setColor((backgroundAlpha << 24) | 0x00080C16);
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, AKARIN_RADIUS), paint);
        }

        ModuleListHud moduleList = NiloreClient.getInstance().getHudManager() == null
                ? null
                : NiloreClient.getInstance().getHudManager().getHudElement(ModuleListHud.class);
        int topColor = moduleList == null ? 0xFFFFFFFF : moduleList.getThemeColor(0, 0.0f, 1);
        int bottomColor = moduleList == null ? 0xFFFFFFFF : moduleList.getThemeColor(1, 1.0f, 1);
        float titleY = y + 7.0f;
        float stripeY = y + 5.0f;
        float stripeHeight = AKARIN_TITLE_FONT.getMetrics().capHeight();

        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(topColor, alpha * 0.13f));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                    x + AKARIN_STRIPE_X, stripeY - 1.5f,
                    AKARIN_STRIPE_WIDTH + 1.5f, stripeHeight + 3.0f, 2.0f), paint);
        }

        int gradientTop = ColorUtil.withAlpha(topColor, alpha);
        int gradientBottom = ColorUtil.withAlpha(bottomColor, alpha);
        try (Paint paint = new Paint()) {
            paint.setGradCoords(new Paint.GradientCoords(
                    x + AKARIN_STRIPE_X, stripeY,
                    x + AKARIN_STRIPE_X, stripeY + stripeHeight,
                    gradientTop, gradientBottom));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                    x + AKARIN_STRIPE_X, stripeY,
                    AKARIN_STRIPE_WIDTH, stripeHeight, AKARIN_STRIPE_WIDTH / 2.0f), paint);
        }

        GlHelper.drawText("Module", x + AKARIN_TEXT_X, titleY,
                AKARIN_TITLE_FONT, ColorUtil.withAlpha(0xFFF4F6FA, alpha));
        GlHelper.drawText(entry.displayText, x + AKARIN_TEXT_X, y + 19.0f,
                AKARIN_STATUS_FONT, ColorUtil.withAlpha(0xFFF4F6FA, alpha * (160.0f / 255.0f)));
    }


    private void renderNovaCard(DrawContext drawContext, NotificationEntry entry,
                                float x, float y, float progress, float alpha) {
        int accent = getAccentColor(entry.enabled, 0.15f);
        int accent2 = getAccentColor(entry.enabled, 0.85f);

        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(0xEC0C1017, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, 8.0f), paint);

            paint.setColor(ColorUtil.withAlpha(0x301F2938, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 1.0f, y + 1.0f,
                            entry.width - 2.0f, entry.height - 2.0f, 7.0f), paint);

            paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.16f));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 8.0f, y + 8.0f, 22.0f, 22.0f, 11.0f), paint);

            paint.setColor(ColorUtil.withAlpha(accent, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 15.0f, y + 15.0f, 8.0f, 8.0f, 4.0f), paint);

            paint.setColor(ColorUtil.withAlpha(0x283C4556, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 8.0f, y + entry.height - 3.5f,
                            entry.width - 16.0f, 1.5f, 0.75f), paint);

            float left = entry.width - 16.0f;
            float remain = left * (1.0f - progress);
            if (remain > 0.5f) {
                paint.setGradCoords(new Paint.GradientCoords(
                        x + 8.0f, y, x + 8.0f + remain, y,
                        ColorUtil.withAlpha(accent, alpha),
                        ColorUtil.withAlpha(accent2, alpha)));
                drawContext.drawRoundedRect(
                        RoundedRectangle.ofXYWHR(x + 8.0f, y + entry.height - 3.5f,
                                remain, 1.5f, 0.75f), paint);
            }
        }

        FontRenderer title = FontPresets.pingfang(16f);
        FontRenderer sub = FontPresets.openSans(12f);
        GlHelper.drawText(entry.name, x + 37.0f, y + 8.2f, title,
                ColorUtil.withAlpha(0xFFF3F6FB, alpha));
        GlHelper.drawText(entry.enabled ? "MODULE ENABLED" : "MODULE DISABLED",
                x + 37.0f, y + 22.2f, sub,
                ColorUtil.withAlpha(entry.enabled ? 0xFF8ED8A1 : 0xFFE39191, alpha * 0.92f));
    }

    private void renderGlassCard(DrawContext drawContext, NotificationEntry entry,
                                 float x, float y, float progress, float alpha) {
        PoseStack poseStack = drawContext.getPoseStack();
        poseStack.pushPose();
        RenderUtil.drawBlurredRect(poseStack, x, y, entry.width, entry.height,
                9.0f, 18.0f, alpha * 0.90f, 0);
        poseStack.popPose();

        int accent = getAccentColor(entry.enabled, 0.5f);

        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(0x70DCE8F7, alpha * 0.32f));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, 9.0f), paint);

            paint.setColor(ColorUtil.withAlpha(0xB3141922, alpha * 0.78f));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 0.8f, y + 0.8f,
                            entry.width - 1.6f, entry.height - 1.6f, 8.2f), paint);

            float pillW = 34.0f;
            paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.20f));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + entry.width - pillW - 8.0f,
                            y + 12.0f, pillW, 16.0f, 8.0f), paint);

            paint.setColor(ColorUtil.withAlpha(accent, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + entry.width - pillW - 3.0f,
                            y + 18.0f, 4.0f, 4.0f, 2.0f), paint);
        }

        float iconSize = 18.0f;
        drawIcon(drawContext, entry.enabled, x + 9.0f,
                y + (entry.height - iconSize) / 2.0f, iconSize, alpha);

        FontRenderer font = FontPresets.pingfang(15.5f);
        GlHelper.drawText(entry.name, x + 34.0f, y + 16.0f, font,
                ColorUtil.withAlpha(0xFFF4F7FB, alpha));

        FontRenderer stateFont = FontPresets.openSans(11f);
        GlHelper.drawText(entry.enabled ? "ON" : "OFF",
                x + entry.width - 33.0f, y + 17.3f, stateFont,
                ColorUtil.withAlpha(0xFFF7FAFF, alpha));
    }

    private void renderPulseCard(DrawContext drawContext, NotificationEntry entry,
                                 float x, float y, float progress, float alpha) {
        int accent = getAccentColor(entry.enabled, progress);

        float pulse = (float) (0.75 + Math.sin(System.currentTimeMillis() / 110.0) * 0.25);

        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(0xE90B0E14, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, 6.0f), paint);

            paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.12f * pulse));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 4.0f, y + 5.0f,
                            7.0f, entry.height - 10.0f, 3.5f), paint);

            paint.setColor(ColorUtil.withAlpha(accent, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 6.0f, y + 8.0f,
                            2.0f, entry.height - 16.0f, 1.0f), paint);
        }

        FontRenderer name = FontPresets.pingfang(15.5f);
        FontRenderer state = FontPresets.openSans(11f);

        GlHelper.drawText(entry.name, x + 17.0f, y + 8.0f, name,
                ColorUtil.withAlpha(0xFFF1F3F7, alpha));
        GlHelper.drawText(entry.enabled ? "ACTIVE" : "INACTIVE",
                x + 17.0f, y + 21.0f, state,
                ColorUtil.withAlpha(accent, alpha * 0.90f));
    }

    private void renderAuroraCard(DrawContext drawContext, NotificationEntry entry,
                                  float x, float y, float progress, float alpha) {
        int c1 = getAccentColor(entry.enabled, 0.0f);
        int c2 = getAccentColor(entry.enabled, 1.0f);

        PoseStack poseStack = drawContext.getPoseStack();
        poseStack.pushPose();
        RenderUtil.drawBlurredRect(poseStack, x, y, entry.width, entry.height,
                10.0f, 20.0f, alpha * 0.88f, 0);
        poseStack.popPose();

        try (Paint paint = new Paint()) {
            paint.setGradCoords(new Paint.GradientCoords(
                    x, y, x + entry.width, y + entry.height,
                    ColorUtil.withAlpha(c1, alpha * 0.70f),
                    ColorUtil.withAlpha(c2, alpha * 0.70f)));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, 10.0f), paint);

            paint.setGradCoords(null);
            paint.setColor(ColorUtil.withAlpha(0xE5090D13, alpha * 0.96f));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 1.2f, y + 1.2f,
                            entry.width - 2.4f, entry.height - 2.4f, 8.8f), paint);

            paint.setColor(ColorUtil.withAlpha(c1, alpha * 0.18f));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 9.0f, y + 10.0f, 22.0f, 22.0f, 11.0f), paint);
            paint.setColor(ColorUtil.withAlpha(c2, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 16.0f, y + 17.0f, 8.0f, 8.0f, 4.0f), paint);
        }

        FontRenderer title = FontPresets.pingfang(16f);
        FontRenderer sub = FontPresets.openSans(12f);

        GlHelper.drawText(entry.name, x + 39.0f, y + 9.0f, title,
                ColorUtil.withAlpha(0xFFF5F7FB, alpha));
        GlHelper.drawText(entry.enabled ? "Ready • enabled" : "Stopped • disabled",
                x + 39.0f, y + 25.0f, sub,
                ColorUtil.withAlpha(0xFFADB6C5, alpha));
    }

    private void renderCompactCard(DrawContext drawContext, NotificationEntry entry,
                                   float x, float y, float alpha) {
        int accent = getAccentColor(entry.enabled, 0.5f);

        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(0xE812151B, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, 5.0f), paint);

            paint.setColor(ColorUtil.withAlpha(accent, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x, y + 5.0f, 2.0f,
                            entry.height - 10.0f, 1.0f), paint);

            paint.setColor(ColorUtil.withAlpha(accent, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + entry.width - 15.0f,
                            y + entry.height / 2.0f - 2.5f, 5.0f, 5.0f, 2.5f), paint);
        }

        FontRenderer font = FontPresets.openSans(15f);
        GlHelper.drawText(entry.name, x + 9.0f, y + 10.0f, font,
                ColorUtil.withAlpha(0xFFF0F2F6, alpha));
    }

    private void renderMonoCard(DrawContext drawContext, NotificationEntry entry,
                                float x, float y, float alpha) {
        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(0xD80A0A0C, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, 3.5f), paint);

            paint.setColor(ColorUtil.withAlpha(
                    entry.enabled ? 0xFFEAEAEA : 0xFF777777, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x + 5.0f, y + entry.height / 2.0f - 1.0f,
                            10.0f, 2.0f, 1.0f), paint);
        }

        FontRenderer font = FontPresets.openSans(14f);
        GlHelper.drawText(entry.name, x + 20.0f, y + 8.5f, font,
                ColorUtil.withAlpha(0xFFF2F2F2, alpha));

        FontRenderer state = FontPresets.openSans(10f);
        String s = entry.enabled ? "ON" : "OFF";
        float stateW = GlHelper.getStringWidth(s, state);
        GlHelper.drawText(s, x + entry.width - stateW - 8.0f, y + 9.3f, state,
                ColorUtil.withAlpha(entry.enabled ? 0xFFFFFFFF : 0xFF888888, alpha));
    }


    private void renderDesignerCard(DrawContext drawContext, NotificationEntry entry,
                                    float x, float y, float progress, float alpha) {
        int variant = getDesignerVariant(entry.style);
        int accent = getAccentColor(entry.enabled, (variant % 10) / 9.0f);
        int accent2 = getAccentColor(entry.enabled, ((variant + 4) % 10) / 9.0f);

        float radius = 4.0f + (variant % 5);
        int base = switch (variant % 5) {
            case 0 -> 0xEF0A0D13;
            case 1 -> 0xE9121720;
            case 2 -> 0xEC10111A;
            case 3 -> 0xE80D1217;
            default -> 0xED111018;
        };

        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(base, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(x, y, entry.width, entry.height, radius), paint);

            if (variant % 4 == 0) {
                paint.setGradCoords(new Paint.GradientCoords(
                        x, y, x + entry.width, y,
                        ColorUtil.withAlpha(accent, alpha),
                        ColorUtil.withAlpha(accent2, alpha)));
                drawContext.drawRoundedRect(
                        RoundedRectangle.ofXYWHR(x + 5.0f, y + 3.0f,
                                entry.width - 10.0f, 1.5f, 0.75f), paint);
                paint.setGradCoords(null);

                for (int i = 0; i < 3; i++) {
                    float d = 2.0f + i * 0.7f;
                    paint.setColor(ColorUtil.withAlpha(i == 1 ? accent2 : accent, alpha * (0.55f + i * 0.12f)));
                    drawContext.drawRoundedRect(
                            RoundedRectangle.ofXYWHR(
                                    x + entry.width - 24.0f + i * 6.0f,
                                    y + 9.0f + (i % 2) * 4.0f,
                                    d, d, d / 2.0f), paint);
                }
            }

            if (variant % 4 == 1) {
                for (int i = 0; i < 4; i++) {
                    paint.setColor(ColorUtil.withAlpha(
                            i % 2 == 0 ? accent : accent2,
                            alpha * 0.18f));
                    drawContext.drawRoundedRect(
                            RoundedRectangle.ofXYWHR(
                                    x + 5.0f + i * 5.0f,
                                    y + 5.0f,
                                    2.0f,
                                    entry.height - 10.0f - i,
                                    1.0f), paint);
                }
                paint.setColor(ColorUtil.withAlpha(accent, alpha));
                drawContext.drawRoundedRect(
                        RoundedRectangle.ofXYWHR(
                                x + 11.0f,
                                y + entry.height / 2.0f - 3.5f,
                                7.0f, 7.0f, 2.0f), paint);
            }

            if (variant % 4 == 2) {
                float cx = x + 15.0f;
                float cy = y + entry.height / 2.0f;

                paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.16f));
                drawContext.drawRoundedRect(
                        RoundedRectangle.ofXYWHR(cx - 10.0f, cy - 10.0f, 20.0f, 20.0f, 10.0f), paint);
                paint.setColor(ColorUtil.withAlpha(base, alpha));
                drawContext.drawRoundedRect(
                        RoundedRectangle.ofXYWHR(cx - 7.0f, cy - 7.0f, 14.0f, 14.0f, 7.0f), paint);
                paint.setColor(ColorUtil.withAlpha(accent2, alpha));
                drawContext.drawRoundedRect(
                        RoundedRectangle.ofXYWHR(cx - 2.5f, cy - 2.5f, 5.0f, 5.0f, 2.5f), paint);
            }

            if (variant % 4 == 3) {
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 4; col++) {
                        if ((row + col + variant) % 2 == 0) {
                            paint.setColor(ColorUtil.withAlpha(
                                    col % 2 == 0 ? accent : accent2,
                                    alpha * 0.28f));
                            drawContext.drawRoundedRect(
                                    RoundedRectangle.ofXYWHR(
                                            x + 5.0f + col * 4.0f,
                                            y + 6.0f + row * 4.0f,
                                            3.0f, 3.0f, 0.8f), paint);
                        }
                    }
                }
            }

            switch (variant % 10) {
                case 0 -> {
                    paint.setColor(ColorUtil.withAlpha(accent, alpha));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x, y + 5.0f, 2.0f, entry.height - 10.0f, 1.0f), paint);
                }
                case 1 -> {
                    paint.setColor(ColorUtil.withAlpha(accent2, alpha * 0.55f));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + entry.width - 4.0f, y + 4.0f, 1.5f, entry.height - 8.0f, 0.75f), paint);
                }
                case 2 -> {
                    paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.20f));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + 3.0f, y + 3.0f, entry.width - 6.0f, 5.0f, 2.5f), paint);
                }
                case 3 -> {
                    paint.setColor(ColorUtil.withAlpha(accent2, alpha * 0.16f));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + 4.0f, y + entry.height - 8.0f, entry.width - 8.0f, 4.0f, 2.0f), paint);
                }
                case 4 -> {
                    for (int i = 0; i < 3; i++) {
                        float d = 3.0f + i * 2.0f;
                        paint.setColor(ColorUtil.withAlpha(accent, alpha * (0.28f - i * 0.06f)));
                        drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                                x + entry.width - 15.0f - i,
                                y + entry.height / 2.0f - d / 2.0f,
                                d, d, d / 2.0f), paint);
                    }
                }
                case 5 -> {
                    paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.30f));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + 5.0f, y + 5.0f, 10.0f, entry.height - 10.0f, 5.0f), paint);
                }
                case 6 -> {
                    paint.setColor(ColorUtil.withAlpha(accent2, alpha * 0.30f));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + entry.width - 20.0f, y + 5.0f, 15.0f, 3.0f, 1.5f), paint);
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + entry.width - 13.0f, y + 10.0f, 8.0f, 2.0f, 1.0f), paint);
                }
                case 7 -> {
                    paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.22f));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + 4.0f, y + 4.0f, 18.0f, 3.0f, 1.5f), paint);
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + 4.0f, y + 9.0f, 11.0f, 2.0f, 1.0f), paint);
                }
                case 8 -> {
                    paint.setColor(ColorUtil.withAlpha(accent2, alpha * 0.20f));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + 6.0f, y + entry.height / 2.0f - 7.0f,
                            14.0f, 14.0f, 3.0f), paint);
                }
                default -> {
                    paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.24f));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + 5.0f, y + 5.0f, entry.width - 10.0f, 1.0f, 0.5f), paint);
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                            x + 5.0f, y + entry.height - 6.0f, entry.width - 10.0f, 1.0f, 0.5f), paint);
                }
            }

            float remain = Math.max(0.0f, (entry.width - 12.0f) * (1.0f - progress));
            if (remain > 0.5f) {
                paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.85f));
                if (variant % 2 == 0) {
                    drawContext.drawRoundedRect(
                            RoundedRectangle.ofXYWHR(x + 6.0f, y + entry.height - 2.5f,
                                    remain, 1.2f, 0.6f), paint);
                } else {
                    float vertical = Math.max(2.0f, (entry.height - 10.0f) * (1.0f - progress));
                    drawContext.drawRoundedRect(
                            RoundedRectangle.ofXYWHR(x + entry.width - 2.5f, y + 5.0f,
                                    1.2f, vertical, 0.6f), paint);
                }
            }

            float badgeW = (variant % 3 == 0) ? 30.0f : 26.0f;
            float badgeH = 13.0f;
            float badgeX = x + entry.width - badgeW - 7.0f;
            float badgeY = y + (entry.height - badgeH) / 2.0f;
            paint.setColor(ColorUtil.withAlpha(accent, alpha * 0.17f));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(badgeX, badgeY, badgeW, badgeH, badgeH / 2.0f), paint);
        }

        float textX;
        if (variant % 4 == 1 || variant % 4 == 2) {
            textX = x + 31.0f;
        } else if (variant % 4 == 3) {
            textX = x + 25.0f;
        } else {
            textX = x + 11.0f;
        }

        if (variant % 5 == 0 || variant % 5 == 3) {
            float iconSize = 14.0f + (variant % 3);
            drawIcon(drawContext, entry.enabled,
                    x + 8.0f,
                    y + (entry.height - iconSize) / 2.0f,
                    iconSize,
                    alpha);
            textX = x + 29.0f;
        }

        FontRenderer titleFont = (variant % 2 == 0)
                ? FontPresets.pingfang(15.0f)
                : FontPresets.openSans(15.0f);
        FontRenderer stateFont = FontPresets.openSans(10.5f);

        float titleY = y + entry.height / 2.0f - 2.0f;
        if (entry.height >= 36.0f) {
            titleY = y + 8.0f;
        }

        GlHelper.drawText(
                entry.name,
                textX,
                titleY,
                titleFont,
                ColorUtil.withAlpha(0xFFF4F6FA, alpha)
        );

        if (entry.height >= 34.0f) {
            String subtitle = getDesignerSubtitle(entry.style, entry.enabled);
            GlHelper.drawText(
                    subtitle,
                    textX,
                    y + entry.height - 11.0f,
                    stateFont,
                    ColorUtil.withAlpha(accent, alpha * 0.88f)
            );
        }

        String stateText = entry.enabled ? "ON" : "OFF";
        float stateW = GlHelper.getStringWidth(stateText, stateFont);
        GlHelper.drawText(
                stateText,
                x + entry.width - stateW - 13.0f,
                y + entry.height / 2.0f - 0.5f,
                stateFont,
                ColorUtil.withAlpha(0xFFFFFFFF, alpha)
        );
    }

    private static int getDesignerVariant(String styleName) {
        return switch (styleName) {
            case "Prism" -> 0;
            case "Circuit" -> 1;
            case "Sakura" -> 2;
            case "Neon" -> 3;
            case "Orbit" -> 4;
            case "Matrix" -> 5;
            case "Ribbon" -> 6;
            case "Hex" -> 7;
            case "Wave" -> 8;
            case "Eclipse" -> 9;
            case "Cyber" -> 10;
            case "Blossom" -> 11;
            case "Frost" -> 12;
            case "Ember" -> 13;
            case "Galaxy" -> 14;
            case "Pixel" -> 15;
            case "Rune" -> 16;
            case "Comet" -> 17;
            case "Crown" -> 18;
            case "Spectrum" -> 19;
            default -> 0;
        };
    }

    private static String getDesignerSubtitle(String styleName, boolean enabled) {
        String action = enabled ? "enabled" : "disabled";
        return switch (styleName) {
            case "Prism" -> "PRISM • " + action;
            case "Circuit" -> "SYSTEM LINK • " + action;
            case "Sakura" -> "SAKURA • " + action;
            case "Neon" -> "NEON STATE • " + action;
            case "Orbit" -> "ORBIT • " + action;
            case "Matrix" -> "MATRIX • " + action;
            case "Ribbon" -> "RIBBON • " + action;
            case "Hex" -> "HEX CORE • " + action;
            case "Wave" -> "WAVE • " + action;
            case "Eclipse" -> "ECLIPSE • " + action;
            case "Cyber" -> "CYBER • " + action;
            case "Blossom" -> "BLOSSOM • " + action;
            case "Frost" -> "FROST • " + action;
            case "Ember" -> "EMBER • " + action;
            case "Galaxy" -> "GALAXY • " + action;
            case "Pixel" -> "PIXEL • " + action;
            case "Rune" -> "RUNE • " + action;
            case "Comet" -> "COMET • " + action;
            case "Crown" -> "CROWN • " + action;
            case "Spectrum" -> "SPECTRUM • " + action;
            default -> "MODULE • " + action;
        };
    }

    private int getAccentColor(boolean enabled, float position) {
        ModuleListHud moduleList = NiloreClient.getInstance().getHudManager() == null
                ? null
                : NiloreClient.getInstance().getHudManager().getHudElement(ModuleListHud.class);

        if (moduleList != null) {
            int themed = moduleList.getThemeColor(
                    position < 0.5f ? 0 : 1,
                    Mth.clamp(position, 0.0f, 1.0f),
                    1
            );

            if (enabled) {
                return themed;
            }

            int r = (themed >> 16) & 0xFF;
            int g = (themed >> 8) & 0xFF;
            int b = themed & 0xFF;
            r = Math.min(255, (int)(r * 0.55f + 110));
            g = (int)(g * 0.35f);
            b = (int)(b * 0.35f);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        return enabled ? 0xFF66E5A3 : 0xFFE06A72;
    }

    private boolean isModernStyle() {
        return isEntryModernStyle(style.getValue());
    }

    private static boolean isEntryModernStyle(String value) {
        return "Nova".equals(value)
                || "Glass".equals(value)
                || "Pulse".equals(value)
                || "Aurora".equals(value)
                || "Compact".equals(value)
                || "Mono".equals(value)
                || "Prism".equals(value)
                || "Circuit".equals(value)
                || "Sakura".equals(value)
                || "Neon".equals(value)
                || "Orbit".equals(value)
                || "Matrix".equals(value)
                || "Ribbon".equals(value)
                || "Hex".equals(value)
                || "Wave".equals(value)
                || "Eclipse".equals(value)
                || "Cyber".equals(value)
                || "Blossom".equals(value)
                || "Frost".equals(value)
                || "Ember".equals(value)
                || "Galaxy".equals(value)
                || "Pixel".equals(value)
                || "Rune".equals(value)
                || "Comet".equals(value)
                || "Crown".equals(value)
                || "Spectrum".equals(value);
    }

    private void drawIcon(DrawContext drawContext, boolean enabled, float x, float y, float size, float alpha) {
        DynamicTexture icon = enabled ? enabledIcon : disabledIcon;
        if (icon == null) {
            return;
        }
        int iconAlpha = Math.round(alpha * 255.0f);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, icon.getId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = drawContext.getPoseStack().last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferBuilder.vertex(pose, x, y, 0.0f).uv(0.0f, 0.0f).color(255, 255, 255, iconAlpha).endVertex();
        bufferBuilder.vertex(pose, x, y + size, 0.0f).uv(0.0f, 1.0f).color(255, 255, 255, iconAlpha).endVertex();
        bufferBuilder.vertex(pose, x + size, y + size, 0.0f).uv(1.0f, 1.0f).color(255, 255, 255, iconAlpha).endVertex();
        bufferBuilder.vertex(pose, x + size, y, 0.0f).uv(1.0f, 0.0f).color(255, 255, 255, iconAlpha).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    @Override
    public void onGlRender(GlRenderEvent event, float x, float y) {
    }

    @Override
    public void onSettings() {
    }

    private boolean isSimpleStyle() {
        return "Simple".equals(style.getValue());
    }

    private boolean isNavenStyle() {
        return "Naven".equals(style.getValue());
    }

    private boolean isAkarinStyle() {
        return "Akarin".equals(style.getValue());
    }

    private void renderNavenCard(DrawContext drawContext, NotificationEntry entry, float x, float y, float alpha) {
        int bgColor = entry.enabled ? 0xFF179626 : 0xFF942A2B;
        float cardW = entry.width;
        float cardH = navenCardH.getValue().floatValue();
        float radius = navenRadius.getValue().floatValue();
        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(bgColor, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x + 2f, y + 4f, cardW, cardH, radius), paint);
        }
        FontRenderer font = FontPresets.openSans(18f);
        int textColor = ((int)(alpha * 255f) & 0xFF) << 24 | 0x00FFFFFF;
        GlHelper.drawText(entry.displayText, x + navenTextXOff.getValue().floatValue(), y + navenTextYOff.getValue().floatValue(), font, textColor);
    }

    private void loadTextures() {
        if (enabledIcon != null && disabledIcon != null) {
            return;
        }
        enabledIcon = TextureUtil.loadResourceTexture("/assets/nilore/notifications/Enabled.png");
        disabledIcon = TextureUtil.loadResourceTexture("/assets/nilore/notifications/Disabled.png");
    }

    private static class NotificationEntry {
        final String name;
        final boolean enabled;
        final long time;
        final String style;
        final String displayText;
        final float width;
        final float height;
        final float spacing;
        float x = 9999f;
        float alpha;
        boolean entranceStarted;
        long entranceTime;
        boolean exiting;
        long exitStartTime;
        float lastBarProgress = 1.0f;

        NotificationEntry(String name, boolean enabled, long time, String style, String displayText,
                          float width, float height, float spacing) {
            this.name = name;
            this.enabled = enabled;
            this.time = time;
            this.style = style;
            this.displayText = displayText;
            this.width = width;
            this.height = height;
            this.spacing = spacing;
        }
    }
}