package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.EntityRemoveEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.event.impl.WorldChangeEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * DamageIndicator
 *
 * 只有玩家主动攻击（EntityRemoveEvent dead()==false）时才记录伤害。
 * 被攻击生物掉血后，在它的周围随机生成 3~5 个伤害数字。
 * 数字会被描边并带亮色显示，非常明显。
 *
 * 颜色：
 *   damage > 5        → 亮红色 0xFF3333
 *   3 <= damage <= 5  → 亮黄色 0xFFFF33
 *   damage < 3        → 亮绿色 0x33FF33
 */
public final class DamageIndicator extends Module {

    private final BooleanSetting showDamage = new BooleanSetting("Show Damage", true);
    private final NumberSetting scale = new NumberSetting("Scale", 1.6f, 0.5f, 4.0f, 0.05f);
    private final NumberSetting durationMs = new NumberSetting("Duration", 1500.0f, 500.0f, 5000.0f, 100.0f);
    private final NumberSetting riseHeight = new NumberSetting("Rise Height", 0.6f, 0.0f, 3.0f, 0.1f);
    private final NumberSetting spreadRadius = new NumberSetting("Spread", 1.0f, 0.0f, 3.0f, 0.1f);
    private final BooleanSetting showShadow = new BooleanSetting("Shadow", true);
    private final BooleanSetting outline = new BooleanSetting("Outline", true);

    /** 最近一次攻击记录（攻击前血量 + 时间戳），下一 tick 用来计算伤害 */
    private final Map<LivingEntity, AttackRecord> pendingAttack = new HashMap<>();
    /** 当前正在显示的伤害数字 */
    private final List<Indicator> indicators = new ArrayList<>();

    private final Random random = new Random();

    public DamageIndicator() {
        super("DamageIndicator", Category.RENDER);
        this.registerSetting(showDamage);
        this.registerSetting(scale);
        this.registerSetting(durationMs);
        this.registerSetting(riseHeight);
        this.registerSetting(spreadRadius);
        this.registerSetting(showShadow);
        this.registerSetting(outline);
    }

    @Override
    public String getDisplayName() {
        return "DamageIndicator";
    }

    @Override
    public String getModuleName() {
        return "DamageIndicator";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onEnable() {
        pendingAttack.clear();
        indicators.clear();
    }

    @Override
    protected void onDisable() {
        pendingAttack.clear();
        indicators.clear();
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        pendingAttack.clear();
        indicators.clear();
    }

    /**
     * 攻击事件：只有玩家主动攻击时才有 dead()==false。
     * 记录被攻击实体攻击前的血量，下一 tick 判断是否掉血。
     */
    @EventTarget
    public void onAttack(EntityRemoveEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!showDamage.getValue()) return;
        if (event.dead()) return;

        Entity entity = event.entity();
        if (entity instanceof LivingEntity living && living != mc.player) {
            pendingAttack.put(living, new AttackRecord(living.getHealth(), System.currentTimeMillis()));
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null) return;
        if (!showDamage.getValue()) return;

        long now = System.currentTimeMillis();
        long duration = (long) durationMs.getValue().floatValue();

        // 检查所有待判定的攻击是否产生了掉血
        Iterator<Map.Entry<LivingEntity, AttackRecord>> it = pendingAttack.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, AttackRecord> entry = it.next();
            LivingEntity living = entry.getKey();
            AttackRecord record = entry.getValue();

            if (living.isRemoved() || !living.isAlive()) {
                it.remove();
                continue;
            }

            // 超过 1 秒没变化就丢弃，避免残留
            if (now - record.time > 1000L) {
                it.remove();
                continue;
            }

            float current = living.getHealth();
            if (current < record.healthBefore) {
                float damage = record.healthBefore - current;
                if (damage > 0.01f) {
                    spawnIndicatorsAround(living, damage, now, duration);
                }
                it.remove();
            }
        }

        // 清理过期指示器
        indicators.removeIf(i -> now - i.startTime > i.duration);
    }

    /**
     * 在被攻击生物周围随机生成 3~5 个伤害数字。
     */
    private void spawnIndicatorsAround(LivingEntity living, float damage, long now, long duration) {
        Vec3 center = living.position();
        float bbHeight = living.getBbHeight();
        float bbWidth = living.getBbWidth();

        int count = 3 + random.nextInt(3); // 3 ~ 5

        double baseRadius = Math.max(0.6, bbWidth * 1.4) + spreadRadius.getValue().floatValue();

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = baseRadius + random.nextDouble() * 0.5;

            double x = center.x + Math.cos(angle) * r;
            double z = center.z + Math.sin(angle) * r;

            // 高度分布在腰部到头顶之间
            double y = center.y + bbHeight * (0.5 + random.nextDouble() * 0.5);

            Vec3 pos = new Vec3(x, y, z);
            indicators.add(new Indicator(pos, damage, now, duration));
        }
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.level == null || mc.player == null) return;
        if (!showDamage.getValue()) return;
        if (indicators.isEmpty()) return;

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.poseStack();
        long now = System.currentTimeMillis();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        boolean shadow = showShadow.getValue();
        boolean outlineEnabled = outline.getValue();

        for (Indicator indicator : indicators) {
            long elapsed = now - indicator.startTime;
            if (elapsed < 0 || elapsed > indicator.duration) continue;

            float progress = (float) elapsed / (float) indicator.duration;

            // 上飘
            double riseOffset = progress * riseHeight.getValue().floatValue();

            // 淡出
            int alpha = (int) (255 * (1.0f - progress));
            if (alpha <= 5) continue;

            int color = getDamageColor(indicator.damage, alpha);

            double renderX = indicator.position.x - camera.x;
            double renderY = indicator.position.y - camera.y + riseOffset;
            double renderZ = indicator.position.z - camera.z;

            poseStack.pushPose();
            poseStack.translate(renderX, renderY, renderZ);
            // billboard：始终面向摄像机
            poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

            float s = -0.035f * scale.getValue().floatValue();
            poseStack.scale(s, s, s);

            String text = String.format(Locale.ROOT, "%.1f", indicator.damage);
            Matrix4f matrix = poseStack.last().pose();

            float textX = -mc.font.width(text) / 2.0f;

            // 1) 黑色描边，让数字在任何背景下都能看清
            if (outlineEnabled) {
                int outlineColor = (alpha << 24) | 0x000000;
                float off = 0.7f;
                for (float dx = -off; dx <= off; dx += off) {
                    for (float dy = -off; dy <= off; dy += off) {
                        if (dx == 0.0f && dy == 0.0f) continue;
                        mc.font.drawInBatch(
                                text,
                                textX + dx,
                                dy,
                                outlineColor,
                                false,
                                matrix,
                                bufferSource,
                                Font.DisplayMode.NORMAL,
                                0,
                                0xF000F0
                        );
                    }
                }
            }

            // 2) 主文字（亮色）
            mc.font.drawInBatch(
                    text,
                    textX,
                    0.0f,
                    color,
                    shadow,
                    matrix,
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0,
                    0xF000F0
            );

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }

    /**
     * 高亮伤害颜色：
     *   damage > 5        → 亮红
     *   3 <= damage <= 5  → 亮黄
     *   damage < 3        → 亮绿
     */
    private int getDamageColor(float damage, int alpha) {
        int rgb;
        if (damage > 5.0f) {
            rgb = 0xFF3333; // 亮红色
        } else if (damage < 3.0f) {
            rgb = 0x33FF33; // 亮绿色
        } else {
            rgb = 0xFFFF33; // 亮黄色
        }
        return (alpha << 24) | rgb;
    }

    /** 一次攻击的临时记录 */
    private static final class AttackRecord {
        final float healthBefore;
        final long time;

        AttackRecord(float healthBefore, long time) {
            this.healthBefore = healthBefore;
            this.time = time;
        }
    }

    /** 单个正在显示的伤害数字 */
    private static final class Indicator {
        final Vec3 position;
        final float damage;
        final long startTime;
        final long duration;

        Indicator(Vec3 position, float damage, long startTime, long duration) {
            this.position = position;
            this.damage = damage;
            this.startTime = startTime;
            this.duration = duration;
        }
    }
}