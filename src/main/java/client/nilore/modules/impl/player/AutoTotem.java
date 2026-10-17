package client.nilore.modules.impl.player;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.ItemUtil;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * AutoTotem —— 血量低 / 副手空 / 虚空 / 手持金苹果时，自动把图腾移到副手。
 * 适配自 Epsilon 版，改为 Nilore 的模块 / 事件体系。
 */
public class AutoTotem extends Module {

    public static AutoTotem INSTANCE;

    // ==================== 设置 ====================
    /** 严格模式：走完整的两步点击流程。 */
    public final BooleanSetting strict =
            new BooleanSetting("Strict", true);

    /** 血量阈值（含吸收）。低于该值就持有图腾。 */
    public final NumberSetting health =
            new NumberSetting("Health", 16.0, 0.0, 36.0, 0.5);

    /** 手持金苹果时也持有图腾。 */
    public final BooleanSetting checkGapple =
            new BooleanSetting("Check Gapple", true);

    // ==================== 构造 ====================
    public AutoTotem() {
        super("Auto Totem", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getModuleName() {
        return "AutoTotem";
    }

    @Override
    public String getDisplayName() {
        return "§fAuto Totem";
    }

    @Override
    public String getSuffix() {
        if (mc.player == null) return null;
        int count = ItemUtil.countItem(Items.TOTEM_OF_UNDYING);
        return "[" + count + "]";
    }

    @Override
    public void onTick() {
    }

    // ==================== 主逻辑 ====================
    @EventTarget
    public void onTickEvent(TickEvent event) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;

        if (!shouldHoldTotem()) {
            return;
        }

        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        int slot = findTotemSlot();
        if (slot == -1) {
            return;
        }

        moveItemToOffhand(slot);
    }

    // ==================== 判定 ====================
    private boolean shouldHoldTotem() {
        float totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (totalHealth <= health.getValue().floatValue()) {
            return true;
        }

        // 副手空时补图腾
        if (mc.player.getOffhandItem().isEmpty()) {
            return true;
        }

        // 虚空保护
        if (mc.player.getY() < -64.0) {
            return true;
        }

        if (checkGapple.getValue()) {
            Item mainHandItem = mc.player.getMainHandItem().getItem();
            if (mainHandItem == Items.GOLDEN_APPLE || mainHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                return true;
            }
        }

        return false;
    }

    /** 找图腾槽位（hotbar + 背包 + 副手）。 */
    private int findTotemSlot() {
        if (mc.player == null) return -1;

        // 快捷栏
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }

        // 背包
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }

        // 副手
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return 40;
        }

        return -1;
    }

    // ==================== 移动 ====================
    private void moveItemToOffhand(int slot) {
        if (mc.gameMode == null || mc.player == null) return;

        // hotbar → 背包编号
        if (slot < 9) {
            slot += 36;
        }

        if (!strict.getValue()) {
            // 直接 swap 到副手（slot 40）
            mc.gameMode.handleInventoryMouseClick(
                    mc.player.inventoryMenu.containerId,
                    slot,
                    40,
                    ClickType.SWAP,
                    mc.player
            );
            return;
        }

        // 严格模式：先拿起 → 放到副手 → 如果手上有剩，放回原槽
        mc.gameMode.handleInventoryMouseClick(
                mc.player.inventoryMenu.containerId,
                slot,
                0,
                ClickType.PICKUP,
                mc.player
        );
        mc.gameMode.handleInventoryMouseClick(
                mc.player.inventoryMenu.containerId,
                45,
                0,
                ClickType.PICKUP,
                mc.player
        );

        if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
            mc.gameMode.handleInventoryMouseClick(
                    mc.player.inventoryMenu.containerId,
                    slot,
                    0,
                    ClickType.PICKUP,
                    mc.player
            );
        }
    }
}