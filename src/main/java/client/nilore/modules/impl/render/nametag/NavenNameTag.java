package client.nilore.modules.impl.render.nametag;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.impl.render.NameTags;
import client.nilore.modules.impl.world.Teams;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.GlHelper;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.utils.math.MathUtil;
import client.nilore.utils.math.Vector2f;
import client.nilore.utils.render.ProjectionUtil;

public class NavenNameTag extends NameTagStyle {

    private static final int SHADOW_COLOR = new Color(0, 0, 0, 40).getRGB();
    private static final int HEALTH_FILL_COLOR = new Color(0, 0, 0, 80).getRGB();
    private static final int TOP_BAR_COLOR = new Color(255, 0, 0, 100).getRGB();
    private static final int TEXT_COLOR = new Color(255, 255, 255, 204).getRGB();

    private final Map<Entity, Vector2f> entityPositions = new ConcurrentHashMap<>();
    private final List<ItemRenderData> deferredItems = new ArrayList<>();
    private final FontRenderer font = FontPresets.pingfang(16.0f);
    private final Paint paint = new Paint();

    public NavenNameTag() { super("Naven"); }

    @Override public String getName() { return "Naven"; }

    @Override public void onEnable() { this.entityPositions.clear(); this.deferredItems.clear(); }
    @Override public void onDisable() { this.entityPositions.clear(); this.deferredItems.clear(); }

    private boolean shouldShowEntity(Entity entity) {
        if (NameTags.shouldPreserveVanillaNameTag(entity)) return false;
        if (entity instanceof Player) return NameTags.INSTANCE.showPlayersSetting.getValue();
        if (entity instanceof Animal || entity instanceof Squid) return NameTags.INSTANCE.showAnimalsSetting.getValue();
        if (entity instanceof Mob) return NameTags.INSTANCE.showMonstersSetting.getValue();
        if (entity instanceof ItemEntity) return NameTags.INSTANCE.showItemsSetting.getValue();
        if (entity instanceof LivingEntity) return NameTags.INSTANCE.showOtherEntitiesSetting.getValue();
        return false;
    }

    private void updatePositions(float partialTick) {
        if (mc.level == null || mc.player == null) { this.entityPositions.clear(); return; }
        ProjectionUtil.updateMatrices();
        double maxDistanceSq = Math.pow(NameTags.INSTANCE.distanceSetting.getValue().doubleValue(), 2.0);
        Set<Entity> seen = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entity.isAlive()) continue;
            if (entity.getName().getString().startsWith("CIT-")) continue;
            if (entity.distanceToSqr(mc.player) > maxDistanceSq) continue;
            if (!shouldShowEntity(entity)) continue;
            if (entity.isInvisible()) {
                if (entity instanceof Player) { if (!NameTags.INSTANCE.showHealthSetting.getValue()) continue; }
                else { if (!NameTags.INSTANCE.showInvisiblePlayersSetting.getValue()) continue; }
            }
            if (entity instanceof Player && NameTags.INSTANCE.showPingSetting.getValue() && Teams.isSameTeam(entity)) continue;
            double x = MathUtil.lerp(partialTick, entity.xo, entity.getX());
            double y = MathUtil.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() + 0.5;
            double z = MathUtil.lerp(partialTick, entity.zo, entity.getZ());
            Vector2f screen = ProjectionUtil.project(x, y, z);
            if (screen == null || !Float.isFinite(screen.x) || !Float.isFinite(screen.y)) continue;
            screen.setY(screen.getY() - 2.0f);
            this.entityPositions.put(entity, screen);
            seen.add(entity);
        }
        this.entityPositions.keySet().removeIf(entity -> !seen.contains(entity));
    }

    @Override public void onRender(RenderEvent event) { try { this.updatePositions(event.partialTick()); } catch (Exception ignored) {} }

    private int applyOpacity(int color, float opacity) {
        int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (Math.max(0, Math.min(255, Math.round(a * opacity))) << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (mc.level == null || mc.player == null || this.entityPositions.isEmpty()) return;
        this.deferredItems.clear();
        final float scale = NameTags.INSTANCE.scaleSetting.getValue().floatValue();
        final float shadowOpacity = NameTags.INSTANCE.opacitySetting.getValue().floatValue();
        final boolean showDistance = NameTags.INSTANCE.showDistanceSetting.getValue();
        final boolean showGear = NameTags.INSTANCE.showGearSetting.getValue();

        Renderer.renderConsumer(ctx -> {
            for (Map.Entry<Entity, Vector2f> entry : this.entityPositions.entrySet()) {
                Entity entity = entry.getKey();
                if (!(entity instanceof LivingEntity living)) continue;
                Vector2f position = entry.getValue();

                float health = Math.max(0.0f, Math.min(living.getHealth(), 20.0f));
                float maxHealth = Math.max(1.0f, Math.min(living.getMaxHealth(), 20.0f));
                float healthRatio = Math.max(0.0f, Math.min(1.0f, health / maxHealth));

                StringBuilder text = new StringBuilder();
                if (entity instanceof Player && Teams.isSameTeam(entity)) text.append("§aTeam§f | ");
                text.append(entity.getName().getString()).append("§f | §c").append(Math.round(health));
                if (living.getAbsorptionAmount() > 0.0f) text.append('+').append(Math.round(living.getAbsorptionAmount()));
                text.append("HP");
                if (showDistance) text.append("§f | §e").append(String.format("%.1f", mc.player.distanceTo(entity))).append("m");
                String label = text.toString();

                List<ItemStack> equipmentList = new ArrayList<>();
                if (entity instanceof Player player && showGear) {
                    if (!player.getMainHandItem().isEmpty()) equipmentList.add(player.getMainHandItem());
                    if (!player.getOffhandItem().isEmpty()) equipmentList.add(player.getOffhandItem());
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                            ItemStack stack = player.getItemBySlot(slot);
                            if (!stack.isEmpty()) equipmentList.add(stack);
                        }
                    }
                }

                float textWidth = GlHelper.getStringWidth(label, this.font);
                float textHeight = this.font.getMetrics().getLineHeight();
                float gap = 6.0f;
                int equipmentCount = equipmentList.size();
                float itemBoxSize = 18.0f;

                // 名字框尺寸：只包含文字
                float boxWidth = textWidth + 8.0f;
                // 【修改点1】将 +4.0f 调整为 +6.0f，给顶部红条和文字留出合理的缓冲空间
                float boxHeight = textHeight + 6.0f;

                float originX = -boxWidth / 2.0f;
                float originY = -2.0f;
                float radius = 3.0f;
                float currentRadius = (boxWidth * healthRatio < radius * 2.0f) ? 0.0f : radius;

                // 装备的 Y 坐标紧贴名字框上方
                float equipY = originY - itemBoxSize - 1.0f;

                ctx.save();
                ctx.translate(Math.round(position.x), Math.round(position.y));
                ctx.scale(scale, scale);

                // 名字背景框（红色血条在顶部，文字显示在血条下方）
                this.paint.setColor(applyOpacity(SHADOW_COLOR, shadowOpacity));
                ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(originX, originY, boxWidth, boxHeight, radius), this.paint);
                if (healthRatio > 0.0f) {
                    this.paint.setColor(HEALTH_FILL_COLOR);
                    ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(originX, originY, boxWidth * healthRatio, boxHeight, currentRadius), this.paint);
                }
                this.paint.setColor(TOP_BAR_COLOR);
                ctx.drawRectXYWH(originX, originY, boxWidth, 2.0f, this.paint);

                // 渲染装备（不画框，仅计算物品渲染位置）
                if (equipmentCount > 0) {
                    float totalEquipWidth = itemBoxSize * equipmentCount + gap * (equipmentCount - 1);
                    float equipX = originX + 2.0f + (textWidth - totalEquipWidth) / 2.0f;
                    for (ItemStack item : equipmentList) {
                        float itemScreenX = position.x + equipX * scale;
                        float itemScreenY = position.y + equipY * scale;
                        float centerX = itemScreenX + (itemBoxSize * scale) / 2.0f;
                        float centerY = itemScreenY + (itemBoxSize * scale) / 2.0f;
                        this.deferredItems.add(new ItemRenderData(item, new Vector2f(centerX - 8.0f * scale, centerY - 8.0f * scale)));

                        equipX += itemBoxSize + gap;
                    }
                }

                // 【修改点2】名字文字垂直居中：在标准居中公式基础上增加 1.0f 的补偿偏移
                // 避免文字紧贴顶部红条，确保视觉上下留白对称
                float textY = originY + (boxHeight - textHeight) / 3.0f + 4.0f;
                this.paint.setColor(TEXT_COLOR);
                GlHelper.drawTextFormatted(label, originX + 4.0f, textY, this.font, this.paint, false);

                ctx.restore();
            }
        });

        for (ItemRenderData data : this.deferredItems) {
            PoseStack stack = event.guiGraphics().pose();
            stack.pushPose();
            stack.translate(data.position.x, data.position.y, 0.0f);
            stack.scale(scale, scale, 1.0f);
            event.guiGraphics().renderItem(data.itemStack, 0, 0);
            event.guiGraphics().renderItemDecorations(mc.font, data.itemStack, 0, 0);
            stack.popPose();
        }
    }

    @Override public void onPacket(PacketEvent event) {}
    private record ItemRenderData(ItemStack itemStack, Vector2f position) {}
}