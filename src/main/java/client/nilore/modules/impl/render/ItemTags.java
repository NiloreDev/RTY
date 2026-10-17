package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.WorldChangeEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.player.InventoryManager;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.ItemUtil;
import client.nilore.utils.render.NavenProjectionUtil;
import client.nilore.utils.render.RenderUtil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ItemTags extends Module {

    public static ItemTags INSTANCE;

    public final NumberSetting scale =
            new NumberSetting("Scale", 0.25, 0.10, 0.50, 0.01);

    public final NumberSetting backgroundAlpha =
            new NumberSetting("Background Alpha", 144, 0, 255, 1);

    public final BooleanSetting allItems =
            new BooleanSetting("All Items", false);

    public final BooleanSetting godItems =
            new BooleanSetting("God Items", true, () -> !allItems.getValue());

    public final BooleanSetting diamond =
            new BooleanSetting("Diamond", true, () -> !allItems.getValue());

    public final BooleanSetting gold =
            new BooleanSetting("Gold", true, () -> !allItems.getValue());

    public final BooleanSetting iron =
            new BooleanSetting("Iron", true, () -> !allItems.getValue());

    public final BooleanSetting enderPearl =
            new BooleanSetting("Ender Pearl", true, () -> !allItems.getValue());

    public final BooleanSetting goldenApple =
            new BooleanSetting("Golden Apple", true, () -> !allItems.getValue());

    public final BooleanSetting usefulItem =
            new BooleanSetting("Useful Item", true, () -> !allItems.getValue());

    private static final int NORMAL_COLOR = 0xFFFFFFFF;
    private static final int GOD_COLOR = 0xFFFF0000;

    private final ConcurrentHashMap<ItemEntity, Vector2f> entityPositions =
            new ConcurrentHashMap<>();


    public ItemTags() {
        super("ItemTags", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public String getModuleName() {
        return "ItemTags";
    }

    @Override
    public String getDisplayName() {
        return "§fItemTags";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onEnable() {
        entityPositions.clear();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        entityPositions.clear();
        super.onDisable();
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        entityPositions.clear();
    }

    private int getBackgroundColor() {
        int alpha = this.backgroundAlpha.getValue().intValue();
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | 0x000000;
    }

    private static String getDisplayName(ItemEntity entity) {
        ItemStack stack = entity.getItem();

        return stack.getDisplayName().getString()
                + " * "
                + stack.getCount();
    }

    private static boolean isKbBall(ItemStack stack) {
        return stack.getItem() == Items.SLIME_BALL
                && EnchantmentHelper.getItemEnchantmentLevel(
                Enchantments.KNOCKBACK,
                stack
        ) > 1;
    }

    private static boolean isGodAxe(ItemStack stack) {
        return stack.getItem() == Items.GOLDEN_AXE
                && EnchantmentHelper.getItemEnchantmentLevel(
                Enchantments.SHARPNESS,
                stack
        ) > 100;
    }

    private static boolean isMace(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        /*
         * Do not reference Items.MACE directly. Some mappings/client
         * versions do not expose that constant, which causes a compile
         * error even when a server/plugin presents a mace-like item.
         * Detect it through stable textual identifiers instead.
         */
        String descriptionId = stack.getItem().getDescriptionId();
        if (descriptionId != null && descriptionId.toLowerCase().contains("mace")) {
            return true;
        }

        String displayName = stack.getHoverName().getString();
        if (displayName == null) {
            return false;
        }

        String name = displayName.toLowerCase();
        return name.contains("mace") || displayName.contains("重锤");
    }

    private static boolean isNetheriteEquipment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        return stack.getItem() == Items.NETHERITE_HELMET
                || stack.getItem() == Items.NETHERITE_CHESTPLATE
                || stack.getItem() == Items.NETHERITE_LEGGINGS
                || stack.getItem() == Items.NETHERITE_BOOTS
                || stack.getItem() == Items.NETHERITE_SWORD
                || stack.getItem() == Items.NETHERITE_PICKAXE
                || stack.getItem() == Items.NETHERITE_AXE
                || stack.getItem() == Items.NETHERITE_SHOVEL
                || stack.getItem() == Items.NETHERITE_HOE;
    }

    private static boolean isPriorityItem(ItemStack stack) {
        return isMace(stack) || isNetheriteEquipment(stack);
    }

    private boolean isGodItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        return isPriorityItem(stack)
                || isKbBall(stack)
                || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE
                || stack.getItem() == Items.END_CRYSTAL
                || isGodAxe(stack);
    }

    private boolean isValidItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (isPriorityItem(stack)) {
            return true;
        }

        if (allItems.getValue()) {
            return true;
        }

        if (godItems.getValue()) {
            if (isKbBall(stack)
                    || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE
                    || isGodAxe(stack)) {
                return true;
            }
        }

        if (diamond.getValue()
                && stack.getItem() == Items.DIAMOND) {
            return true;
        }

        if (gold.getValue()
                && stack.getItem() == Items.GOLD_INGOT) {
            return true;
        }

        if (iron.getValue()
                && stack.getItem() == Items.IRON_INGOT) {
            return true;
        }

        if (enderPearl.getValue()
                && stack.getItem() == Items.ENDER_PEARL) {
            return true;
        }

        if (goldenApple.getValue()
                && stack.getItem() == Items.GOLDEN_APPLE) {
            return true;
        }

        if (usefulItem.getValue()) {
            if (stack.getItem() instanceof BlockItem
                    && stack.getCount() < 8) {
                return false;
            }

            if ((stack.getItem() instanceof SnowballItem
                    || stack.getItem() instanceof EggItem)
                    && stack.getCount() < 3) {
                return false;
            }

            InventoryManager manager =
                    InventoryManager.INSTANCE;

            if (manager != null) {
                try {
                    if (manager.isUsefulItem(stack)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }

            try {
                return ItemUtil.isUsableItem(stack);
            } catch (Throwable ignored) {
                return false;
            }
        }

        return false;
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        // Intentionally left empty. Item-tag projection is done in Render2D
        // using the final world/camera state of the frame. Reusing the
        // RenderEvent partial tick here causes a small W/A/S/D induced offset.
    }

    private Map<ItemEntity, Vector2f> getRenderPositions() {
        Map<ItemEntity, Vector2f> positions = new ConcurrentHashMap<>();

        if (mc.player == null
                || mc.level == null
                || mc.gameRenderer == null) {
            return positions;
        }

        float partialTick = mc.getFrameTime();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity)) {
                continue;
            }

            if (itemEntity.isRemoved()
                    || !isValidItem(itemEntity.getItem())) {
                continue;
            }

            /*
             * Project every frame at draw time.
             * Do not keep projected coordinates between frames.
             * This prevents camera rotation / forward movement from
             * producing a fake offset.
             */
            double ix = Mth.lerp(partialTick, itemEntity.xOld, itemEntity.getX());
            double iy = Mth.lerp(partialTick, itemEntity.yOld, itemEntity.getY());
            double iz = Mth.lerp(partialTick, itemEntity.zOld, itemEntity.getZ());

            Vector2f projected = NavenProjectionUtil.project(
                    ix,
                    iy + itemEntity.getBbHeight() + 0.5D,
                    iz,
                    partialTick
            );

            if (projected != null
                    && Float.isFinite(projected.x)
                    && Float.isFinite(projected.y)) {
                positions.put(itemEntity, projected);
            }
        }

        return positions;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null
                || mc.level == null
                || mc.gameRenderer == null) {
            entityPositions.clear();
            return;
        }

        /*
         * Re-project immediately before drawing, using the final position of
         * both the item and camera for this frame. This prevents movement keys
         * from adding a one-frame/partial-tick displacement to the label.
         */
        Map<ItemEntity, Vector2f> entityPositions = getRenderPositions();

        if (entityPositions.isEmpty()) {
            return;
        }

        PoseStack poseStack =
                event.poseStack();

        if (poseStack == null) {
            return;
        }

        float width =
                mc.getWindow().getGuiScaledWidth();

        float height =
                mc.getWindow().getGuiScaledHeight();

        float fontScale =
                (32.0F / 9.0F)
                        * scale.getValue().floatValue();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        List<Map.Entry<ItemEntity, Vector2f>> entries =
                new ArrayList<>(entityPositions.entrySet());

        // Draw normal tags first and important red tags last so mace/netherite
        // remain visible when many dropped-item labels overlap.
        entries.sort(
                Comparator.comparingInt(entry ->
                        isPriorityItem(entry.getKey().getItem()) ? 1 : 0
                )
        );

        for (Map.Entry<ItemEntity, Vector2f> entry : entries) {

            ItemEntity entity =
                    entry.getKey();

            Vector2f pos =
                    entry.getValue();

            if (entity == null
                    || entity.isRemoved()
                    || pos == null) {
                continue;
            }

            if (pos.x < -100.0F
                    || pos.x > width + 100.0F
                    || pos.y < -50.0F
                    || pos.y > height + 50.0F) {
                continue;
            }

            String text =
                    getDisplayName(entity);

            int color =
                    isGodItem(entity.getItem())
                            ? GOD_COLOR
                            : NORMAL_COLOR;

            float textWidth =
                    mc.font.width(text)
                            * fontScale;

            float boxWidth =
                    textWidth + 8.0F;

            float left =
                    pos.x - boxWidth / 2.0F;

            float top =
                    pos.y - 14.0F;

            RenderUtil.drawFilledRect(
                    poseStack,
                    left,
                    top,
                    boxWidth,
                    14.0F,
                    getBackgroundColor()
            );

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            poseStack.pushPose();

            try {
                poseStack.translate(
                        left + 4.0F,
                        pos.y - 12.0F,
                        0.0F
                );

                poseStack.scale(
                        fontScale,
                        fontScale,
                        1.0F
                );

                event.guiGraphics().drawString(
                        mc.font,
                        text,
                        0,
                        0,
                        color,
                        false
                );
            } finally {
                poseStack.popPose();
            }
        }

        RenderSystem.setShaderColor(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}