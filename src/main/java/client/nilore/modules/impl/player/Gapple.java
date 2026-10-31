package client.nilore.modules.impl.player;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.misc.ReflectionUtil;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.EventTarget;

public class Gapple extends Module {
    private final NumberSetting health = new NumberSetting("Health", 10f, 1f, 20f, 0.5f);
    private final ModeSetting mode = new ModeSetting("Mode", "Normal", "Instant").withDefault("Normal");
    private final BooleanSetting enchanted = new BooleanSetting("Enchanted", true);
    private final BooleanSetting absorption = new BooleanSetting("Absorption", false);
    private final BooleanSetting switchBack = new BooleanSetting("Switch Back", true);

    private int pendingRestoreSlot = -1;
    private boolean hasTriggered = false; // 防止重复发包导致进食中断

    public Gapple() {
        super("Gapple", Category.PLAYER);
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (mc.player == null || mc.level == null) return;

        float threshold = this.health.getValue().floatValue();
        float current = mc.player.getHealth();
        if (this.absorption.getValue()) current += mc.player.getAbsorptionAmount();

        // 1. 如果已经在进食中，立刻退出，什么都不做，让客户端和服务器自行走完进食判定
        if (mc.player.isUsingItem()) {
            return;
        }

        // 2. 血量高于阈值，重置状态并归还槽位
        if (current > threshold) {
            this.hasTriggered = false;
            this.restore();
            return;
        }

        // 3. 血量低于阈值，且尚未触发过进食
        if (!this.hasTriggered) {
            // 优先吃副手
            if (this.isGapple(mc.player.getOffhandItem())) {
                this.hasTriggered = true;
                this.startEat(InteractionHand.OFF_HAND);
                return;
            }

            // 其次吃主手
            if (this.isGapple(mc.player.getMainHandItem())) {
                this.hasTriggered = true;
                this.startEat(InteractionHand.MAIN_HAND);
                return;
            }

            // 最后去背包找，换到副手
            int slot = this.findGappleSlot();
            if (slot != -1) {
                if (this.pendingRestoreSlot == -1) this.pendingRestoreSlot = slot;
                this.swapToOffhand(slot);
                this.hasTriggered = true;
                this.startEat(InteractionHand.OFF_HAND);
            }
        }
    }

    private void startEat(InteractionHand hand) {
        ReflectionUtil.setRightClickDelay(0);
        if (mc.gameMode != null) {
            mc.gameMode.useItem(mc.player, hand);
        }
        if (this.mode.is("Instant")) {
            this.instantComplete();
        }
    }

    private void instantComplete() {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null && mc.player != null) {
            mc.getSingleplayerServer().execute(() -> {
                try {
                    net.minecraft.server.level.ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp != null && sp.isUsingItem()) {
                        java.lang.reflect.Method method = LivingEntity.class.getDeclaredMethod("completeUsingItem");
                        method.setAccessible(true);
                        method.invoke(sp);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    private void swapToOffhand(int inventorySlot) {
        if (mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(
                mc.player.inventoryMenu.containerId,
                inventorySlot,
                40, // 40 = 与副手交换 (F键逻辑)
                ClickType.SWAP,
                mc.player);
    }

    private int findGappleSlot() {
        for (int i = 9; i <= 44; i++) {
            if (this.isGapple(mc.player.inventoryMenu.getSlot(i).getItem())) return i;
        }
        return -1;
    }

    private boolean isGapple(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.GOLDEN_APPLE)) return true;
        return this.enchanted.getValue() && stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private void restore() {
        if (this.switchBack.getValue() && this.pendingRestoreSlot != -1) {
            this.swapToOffhand(this.pendingRestoreSlot);
            this.pendingRestoreSlot = -1;
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && !mc.player.isUsingItem()) {
            this.restore();
        }
        this.pendingRestoreSlot = -1;
        this.hasTriggered = false;
    }

    @Override
    public String getModuleName() {
        return "Gapple";
    }

    @Override
    public String getDisplayName() {
        return "§fGapple";
    }

    @Override
    public String getSuffix() {
        return this.mode.is("Instant")
                ? "Instant"
                : String.valueOf(this.health.getValue().floatValue());
    }
}
