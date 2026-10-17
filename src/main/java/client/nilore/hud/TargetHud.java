package client.nilore.hud;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.hud.target.*;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.SmoothAnimationTimer;
import client.nilore.utils.math.Easings;

public class TargetHud extends HudElement {
    private final SmoothAnimationTimer healthAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer healthLagAnim = new SmoothAnimationTimer();
    public static final Map<String, AtomicInteger> playerHealthMap = new HashMap<>();
    private float lastHealth;
    private float healthDelta;
    private final Random rand = new Random();

    // 新增全局大小调节
    private final NumberSetting scaleSetting = new NumberSetting("Scale", 1.0f, 0.5f, 2.0f, 0.05f);

    private final ModeSetting styleMode = new ModeSetting("Mode",
            "Nebula",
            "Sakura", "Lightning", "Orbit", "NeonFlow", "DigitalRain", "Nebula", // 全新实用特效
            "Round", "Rise", "Moon", "Simple", "Naven", "Exhibition") // 原有模式
            .withDefault("Nebula");

    public TargetHud() {
        super("TargetHUD");
        this.setWidth(150.0f);
        this.setHeight(45.0f);
        TargetStyle.initStyles();
        this.setEnabled(true);
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        Packet<?> packet = packetEvent.getPacket();
        if (packet instanceof ClientboundSetScorePacket scorePacket) {
            if (mc.level != null && mc.player != null
                    && ("belowHealth".equals(scorePacket.getObjectiveName()) || "health".equals(scorePacket.getObjectiveName()))
                    && !scorePacket.getOwner().equals(mc.player.getGameProfile().getName())) {
                playerHealthMap.computeIfAbsent(scorePacket.getOwner(), s -> new AtomicInteger()).set(scorePacket.getScore());
            }
        }
    }

    @Override
    public void onSettings() {}

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {}

    @Override
    public void onRender2D(Render2DEvent event, float x, float y) {
        if (mc.level == null || mc.player == null) return;

        // 同步计分板血量
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || !playerHealthMap.containsKey(player.getName().getString())) continue;
            player.setHealth((float) Math.max(1, playerHealthMap.get(player.getName().getString()).get()));
        }

        LivingEntity target = null;
        if (mc.screen instanceof ChatScreen) {
            target = mc.player;
        } else if (KillAura.aimingTarget instanceof LivingEntity le) {
            target = le;
        }

        // 动画驱动
        if (target != null) {
            if (!Mth.equal(this.lastHealth, target.getHealth())) {
                this.healthDelta = target.getHealth() - this.lastHealth;
                this.lastHealth = target.getHealth();
            }
            float maxHealth = Math.min(target.getMaxHealth(), 20.0f);
            float currentHealth = Math.min(target.getHealth(), 20.0f);
            float ratio = maxHealth > 0.0f ? currentHealth / maxHealth : 0.0f;
            this.healthAnim.animate(ratio, 0.3, Easings.EASE_OUT_POW4);
            this.healthLagAnim.animate(ratio, 1.0, Easings.EASE_OUT_POW5);
        } else {
            this.healthDelta = 0.0f;
            this.healthAnim.animate(0.0, 0.5);
            this.healthLagAnim.animate(0.0, 0.5);
        }

        this.healthAnim.tick();
        this.healthLagAnim.tick();

        float hp = (float) this.healthAnim.getValue();
        float hpLag = (float) this.healthLagAnim.getValue();
        long time = System.currentTimeMillis();
        String mode = this.styleMode.getValue();

        // 处理全局缩放
        float scale = this.scaleSetting.getValue().floatValue();
        PoseStack pose = event.poseStack();
        pose.pushPose();
        pose.translate(x, y, 0.0f);
        pose.scale(scale, scale, 1.0f);

        // 分发渲染 (坐标基点设为 0,0)
        switch (mode) {
            case "Sakura" -> { renderSakura(event, target, hp, hpLag, time); }
            case "Lightning" -> { renderLightning(event, target, hp, hpLag, time); }
            case "Orbit" -> { renderOrbit(event, target, hp, hpLag, time); }
            case "NeonFlow" -> { renderNeonFlow(event, target, hp, hpLag, time); }
            case "DigitalRain" -> { renderDigitalRain(event, target, hp, hpLag, time); }
            case "Nebula" -> { renderNebula(event, target, hp, hpLag, time); }
            default -> {
                pose.popPose(); // 原有模式不走缩放逻辑，还原
                TargetStyle targetStyle = TargetStyle.getByName(mode);
                if (targetStyle != null) {
                    float maxHealth = target != null ? (target.getMaxHealth() > 0.0f ? Math.min(target.getHealth(), 20.0f) / Math.min(target.getMaxHealth(), 20.0f) : 0.0f) : 0.0f;
                    targetStyle.render(event, target, this.healthAnim, this.healthLagAnim, maxHealth, x, y);

                    if (targetStyle instanceof RoundTargetStyle) { this.setWidth(120.0f); this.setHeight(38.0f); }
                    else if (targetStyle instanceof MoonTargetStyle) { this.setWidth(200.0f); this.setHeight(40.0f); }
                    else if (targetStyle instanceof RiseTargetStyle) { this.setWidth(220.0f); this.setHeight(45.0f); }
                    else if (targetStyle instanceof SimpleTargetStyle) { this.setWidth(120.0f); this.setHeight(38.0f); }
                    else if (targetStyle instanceof NavenTargetStyle) { this.setWidth(160.0f); this.setHeight(48.0f); }
                    else if (targetStyle instanceof ExhibitionTargetStyle) { this.setWidth(120.0f); this.setHeight(45.0f); }
                    else { this.setWidth(150.0f); this.setHeight(36.0f); }
                }
                return;
            }
        }

        pose.popPose();
    }

    // =================================================================================
    // 实用工具：获取护甲值等附加信息
    // =================================================================================
    private String getSuffixText(LivingEntity target) {
        String hpStr = String.format("%.1f", target.getHealth());
        if (target instanceof Player p && p.getArmorValue() > 0) {
            return "🛡" + p.getArmorValue() + "  " + hpStr;
        }
        return hpStr;
    }

    // =================================================================================
    // 1. Sakura (落樱) - 粉红柔光，飘落花瓣，极简高级
    // =================================================================================
    private void renderSakura(Render2DEvent event, LivingEntity target, float hp, float hpLag, long time) {
        int w = 150, h = 42;
        this.setWidth(w * scaleSetting.getValue().floatValue());
        this.setHeight(h * scaleSetting.getValue().floatValue());
        if (target == null) return;
        GuiGraphics g = event.guiGraphics();

        String name = target.getName().getString();
        String suffix = getSuffixText(target);

        // 极透的深粉底
        g.fill(0, 0, w, h, new Color(25, 5, 10, 120).getRGB());

        // 飘落樱花
        for (int i = 0; i < 6; i++) {
            float px = (float)(Math.sin(i * 2.0 + time / 1000.0) * 0.5 + 0.5) * w;
            float py = (time / 12 + i * 30) % h;
            int alpha = (int)(Math.sin(time / 200.0 + i) * 100 + 155);
            g.fill((int)px, (int)py, (int)px + 2, (int)py + 2, (alpha << 24) | 0xFFB7C5);
        }

        // 名字
        g.drawString(mc.font, name, 8, 6, new Color(255, 182, 193).getRGB(), true);
        // 右侧实用数据
        g.drawString(mc.font, suffix, w - 8 - mc.font.width(suffix), 6, new Color(255, 255, 255, 200).getRGB(), true);

        // 血条
        int bw = w - 16, by = 20;
        g.fill(8, by, 8 + bw, by + 4, new Color(0, 0, 0, 100).getRGB());
        g.fill(8, by, 8 + (int)(bw * hpLag), by + 4, new Color(255, 255, 255, 80).getRGB());
        g.fill(8, by, 8 + (int)(bw * hp), by + 4, new Color(255, 105, 180).getRGB()); // 亮粉
    }

    // =================================================================================
    // 2. Lightning (闪电) - 高频闪烁电弧，赛博深蓝底
    // =================================================================================
    private void renderLightning(Render2DEvent event, LivingEntity target, float hp, float hpLag, long time) {
        int w = 150, h = 42;
        this.setWidth(w * scaleSetting.getValue().floatValue());
        this.setHeight(h * scaleSetting.getValue().floatValue());
        if (target == null) return;
        GuiGraphics g = event.guiGraphics();

        String name = target.getName().getString();
        String suffix = getSuffixText(target);

        // 极透深蓝底
        g.fill(0, 0, w, h, new Color(5, 10, 25, 120).getRGB());

        // 闪电影子
        if (time % 200 < 100) {
            int lx = rand.nextInt(w);
            for (int i = 0; i < 3; i++) {
                int ey = rand.nextInt(h);
                g.fill(lx + rand.nextInt(10) - 5, ey, lx + rand.nextInt(10) - 5, ey + rand.nextInt(10), 0xFF00FFFF);
            }
        }

        // 边框
        g.fill(0, 0, w, 1, new Color(0, 200, 255, 100).getRGB());
        g.fill(0, h - 1, w, h, new Color(0, 200, 255, 100).getRGB());

        g.drawString(mc.font, name, 8, 6, new Color(0, 220, 255).getRGB(), true);
        g.drawString(mc.font, suffix, w - 8 - mc.font.width(suffix), 6, new Color(200, 230, 255, 200).getRGB(), true);

        int bw = w - 16, by = 20;
        g.fill(8, by, 8 + bw, by + 4, new Color(0, 0, 0, 150).getRGB());
        g.fill(8, by, 8 + (int)(bw * hpLag), by + 4, new Color(255, 255, 255, 80).getRGB());

        // 闪电血量 (蓝色渐变)
        int hpCol = Color.HSBtoRGB(0.55f, 0.9f, 0.8f + hp * 0.2f);
        g.fill(8, by, 8 + (int)(bw * hp), by + 4, hpCol | 0xFF000000);
    }

    // =================================================================================
    // 3. Orbit (星轨) - 左侧星轨环绕，高级深空感
    // =================================================================================
    private void renderOrbit(Render2DEvent event, LivingEntity target, float hp, float hpLag, long time) {
        int w = 150, h = 42;
        this.setWidth(w * scaleSetting.getValue().floatValue());
        this.setHeight(h * scaleSetting.getValue().floatValue());
        if (target == null) return;
        GuiGraphics g = event.guiGraphics();

        String name = target.getName().getString();
        String suffix = getSuffixText(target);

        g.fill(0, 0, w, h, new Color(10, 10, 15, 150).getRGB());

        // 名字周围的星轨
        float cx = 20, cy = 13;
        for (int i = 0; i < 2; i++) {
            float angle = (float)(time / 200.0 + i * Math.PI);
            int ox = (int)(cx + Math.cos(angle) * 16);
            int oy = (int)(cy + Math.sin(angle) * 5);
            g.fill(ox - 1, oy - 1, ox + 2, oy + 2, 0xFFFFFF00);
        }

        // 名字偏右避开星轨
        g.drawString(mc.font, name, 40, 6, new Color(255, 235, 150).getRGB(), true);
        g.drawString(mc.font, suffix, w - 8 - mc.font.width(suffix), 6, new Color(220, 220, 220, 200).getRGB(), true);

        int bw = w - 48, by = 20;
        g.fill(40, by, 40 + bw, by + 4, new Color(0, 0, 0, 100).getRGB());
        g.fill(40, by, 40 + (int)(bw * hpLag), by + 4, new Color(255, 255, 255, 80).getRGB());
        g.fill(40, by, 40 + (int)(bw * hp), by + 4, new Color(255, 215, 0).getRGB()); // 金色
    }

    // =================================================================================
    // 4. NeonFlow (霓虹流光) - 镂空无底，逐字全息错位
    // =================================================================================
    private void renderNeonFlow(Render2DEvent event, LivingEntity target, float hp, float hpLag, long time) {
        int w = 150, h = 36;
        this.setWidth(w * scaleSetting.getValue().floatValue());
        this.setHeight(h * scaleSetting.getValue().floatValue());
        if (target == null) return;
        GuiGraphics g = event.guiGraphics();

        String name = target.getName().getString();
        String suffix = getSuffixText(target);

        // 完全无底，仅文字发光
        int cx = 8;
        for (int i = 0; i < name.length(); i++) {
            String ch = String.valueOf(name.charAt(i));
            int color = Color.HSBtoRGB((time % 3000) / 3000f + (i / 10f), 0.9f, 1f);
            // 故障错位
            g.drawString(mc.font, ch, cx + 1, 6, (60 << 24) | 0xFF0000, false);
            g.drawString(mc.font, ch, cx - 1, 6, (60 << 24) | 0x00FFFF, false);
            g.drawString(mc.font, ch, cx, 6, color, true);
            cx += mc.font.width(ch);
        }

        // 右侧数据纯白
        g.drawString(mc.font, suffix, w - 8 - mc.font.width(suffix), 6, 0xFFFFFFFF, true);

        // 底部流光极细线
        int bw = w - 16, by = 20;
        g.fill(8, by, 8 + bw, by + 1, new Color(255, 255, 255, 40).getRGB());
        int currentW = (int)(bw * hp);
        for (int i = 0; i < currentW; i++) {
            int col = Color.HSBtoRGB((time % 2000) / 2000f + (i / 30f), 1f, 1f);
            g.fill(8 + i, by, 8 + i + 1, by + 1, col | 0xFF000000);
        }
    }

    // =================================================================================
    // 5. DigitalRain (数字雨) - 黑客帝国，极客代码掉落
    // =================================================================================
    private void renderDigitalRain(Render2DEvent event, LivingEntity target, float hp, float hpLag, long time) {
        int w = 150, h = 42;
        this.setWidth(w * scaleSetting.getValue().floatValue());
        this.setHeight(h * scaleSetting.getValue().floatValue());
        if (target == null) return;
        GuiGraphics g = event.guiGraphics();

        String name = target.getName().getString();
        String suffix = getSuffixText(target);

        g.fill(0, 0, w, h, new Color(0, 5, 0, 150).getRGB());

        // 代码雨背景
        for (int i = 0; i < 6; i++) {
            int rx = 5 + i * 22;
            int ry = (int)((time / 15 + i * 40) % h);
            String code = String.valueOf((char)(33 + rand.nextInt(93)));
            int alpha = (int)(Math.sin(time / 200.0 + i) * 100 + 155);
            g.drawString(mc.font, code, rx, ry, (alpha << 24) | 0x00FF00, false);
        }

        // 名字与数据
        g.drawString(mc.font, name, 8, 6, 0x00FF00, true);
        g.drawString(mc.font, suffix, w - 8 - mc.font.width(suffix), 6, 0x88FF88, true);

        int bw = w - 16, by = 20;
        g.fill(8, by, 8 + bw, by + 4, new Color(0, 20, 0, 150).getRGB());
        g.fill(8, by, 8 + (int)(bw * hpLag), by + 4, new Color(0, 150, 0, 100).getRGB());
        g.fill(8, by, 8 + (int)(bw * hp), by + 4, 0x00FF00);
    }

    // =================================================================================
    // 6. Nebula (星云) - 极透玻璃底，呼吸高光边框，双层重影
    // =================================================================================
    private void renderNebula(Render2DEvent event, LivingEntity target, float hp, float hpLag, long time) {
        int w = 150, h = 42;
        this.setWidth(w * scaleSetting.getValue().floatValue());
        this.setHeight(h * scaleSetting.getValue().floatValue());
        if (target == null) return;
        GuiGraphics g = event.guiGraphics();

        String name = target.getName().getString();
        String suffix = getSuffixText(target);

        // 1. 极透底色
        g.fill(0, 0, w, h, new Color(5, 5, 10, 80).getRGB());

        // 2. 边缘呼吸发光
        float hue = (time % 6000) / 6000f;
        int borderCol = Color.HSBtoRGB(hue, 0.8f, 1f);
        int alpha = (int)(Math.sin(time / 300.0) * 50 + 150);
        g.fill(0, 0, w, 1, (alpha << 24) | (borderCol & 0xFFFFFF)); // 顶
        g.fill(0, h - 1, w, h, (alpha << 24) | (borderCol & 0xFFFFFF)); // 底

        // 3. 文字重影 (红蓝错位)
        int tx = 8, ty = 6;
        for (int i = 0; i < name.length(); i++) {
            String ch = String.valueOf(name.charAt(i));
            g.drawString(mc.font, ch, tx + 1, ty, (80 << 24) | 0xFF0000, false);
            g.drawString(mc.font, ch, tx - 1, ty, (80 << 24) | 0x00FFFF, false);
            g.drawString(mc.font, ch, tx, ty, 0xFFFFFFFF, true);
            tx += mc.font.width(ch);
        }

        g.drawString(mc.font, suffix, w - 8 - mc.font.width(suffix), 6, 0xFFFFFFFF, true);

        // 4. 流光血条
        int bw = w - 16, by = 20;
        g.fill(8, by, 8 + bw, by + 4, new Color(0, 0, 0, 100).getRGB());
        g.fill(8, by, 8 + (int)(bw * hpLag), by + 4, new Color(255, 255, 255, 100).getRGB());

        int c1 = Color.HSBtoRGB(hue, 0.8f, 1f);
        int c2 = Color.HSBtoRGB((hue + 0.3f) % 1f, 0.8f, 1f);
        g.fillGradient(8, by, 8 + (int)(bw * hp), by + 4, c1 | 0xFF000000, c2 | 0xFF000000);
    }

    @Override
    public String getDisplayName() { return ""; }

    @Override
    public String getModuleName() { return ""; }

    @Override
    public void onTick() {}
}