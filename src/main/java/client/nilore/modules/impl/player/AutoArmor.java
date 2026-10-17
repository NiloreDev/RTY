package client.nilore.modules.impl.player;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.MotionEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.game.ItemUtil;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * AutoArmor —— 自动穿戴最佳盔甲 / 丢弃较差盔甲。
 * 从 InventoryManager 里拆出的独立模块。
 *
 * 行为：
 * - 未打开背包：
 *     1) 身上没穿装备，物品栏有 → 直接穿物品栏的
 *     2) 身上有装备，物品栏有更好的 → 直接穿物品栏的
 *     3) 都没有 → 不动作，不打开背包
 * - 打开背包：
 *     1) 先扫物品栏，有更好就穿
 *     2) 物品栏没有更好，再扫背包界面其他格子
 */
public class AutoArmor extends Module {

    public static AutoArmor INSTANCE;

    // ==================== 设置 ====================
    /** 动作间隔（毫秒）。 */
    public final NumberSetting actionDelay =
            new NumberSetting("Delay", 110, 0, 500, 10);

    /** 是否启用。 */
    public final BooleanSetting enabled =
            new BooleanSetting("Enabled", true);

    /**
     * 只在打开背包界面时才工作。
     * true  ：只在背包界面工作，先扫物品栏，没有更好的再扫背包其他格子
     * false ：只有玩家没移动时才工作，只扫物品栏，没有更好的不动作
     */
    public final BooleanSetting onlyInInventory =
            new BooleanSetting("Only In Inventory", false);

    /** 是否主动穿戴更好的盔甲。 */
    public final BooleanSetting autoEquip =
            new BooleanSetting("Auto Equip", true);

    // ==================== 状态 ====================
    private static final Timer actionTimer = new Timer();

    public AutoArmor() {
        super("AutoArmor", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public String getDisplayName() {
        return "§fAutoArmor";
    }

    @Override
    public String getModuleName() {
        return "AutoArmor";
    }

    @Override
    public String getSuffix() {
        long delay = this.actionDelay.getValue().longValue();
        return "[Delay:" + delay + "]";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onDisable() {
        actionTimer.reset();
        super.onDisable();
    }

    // ==================== 主逻辑 ====================
    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPost()) return;
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;
        if (!this.enabled.getValue()) return;

        boolean inInventoryScreen = mc.screen instanceof InventoryScreen;

        if (this.onlyInInventory.getValue()) {
            // Only In Inventory = true：只在背包界面工作
            if (!inInventoryScreen) {
                return;
            }
        } else {
            // Only In Inventory = false：只有玩家没移动时才工作
            if (isPlayerMoving()) {
                return;
            }
        }

        if (!actionTimer.hasPassed(this.actionDelay.getValue().intValue())) return;

        // 1) 丢弃比当前更差的已穿盔甲
        if (this.tryDropBadArmor()) return;

        // 2) 穿戴更好的盔甲（可开关）
        if (this.autoEquip.getValue()) {
            this.tryEquipBestArmor(inInventoryScreen);
        }
    }

    // ==================== 盔甲逻辑 ====================
    private boolean tryDropBadArmor() {
        for (int i = 0; i < mc.player.getInventory().armor.size(); i++) {
            ItemStack equipped = mc.player.getInventory().armor.get(i);
            if (equipped.isEmpty()) continue;
            if (!(equipped.getItem() instanceof ArmorItem armor)) continue;

            if (ItemUtil.getBestArmorScore(armor.getEquipmentSlot()) > ItemUtil.getArmorScore(equipped)) {
                this.silentClick(
                        mc.player.inventoryMenu.containerId,
                        4 + (4 - i),
                        1,
                        ClickType.THROW
                );
                actionTimer.reset();
                return true;
            }
        }
        return false;
    }

    /**
     * 穿戴更好的盔甲。
     *
     * @param inInventoryScreen 是否在背包界面
     *        false：只扫物品栏，物品栏没有更好的不动作
     *        true ：先扫物品栏，物品栏没有更好的再扫背包界面其他格子
     */
    private boolean tryEquipBestArmor(boolean inInventoryScreen) {
        // 1) 先扫物品栏（hotbar + 背包主区）
        if (this.tryEquipFromItems()) {
            return true;
        }

        // 2) 只有在背包界面时，才允许扫背包界面里的其他格子
        if (inInventoryScreen) {
            return this.tryEquipFromOpenContainer();
        }

        return false;
    }

    /**
     * 扫物品栏（hotbar + 背包主区）。
     * 会同时处理两种情况：
     *  - 身上没穿装备 → 物品栏里有对应部位装备 → 穿
     *  - 身上有装备，但物品栏里有更好的 → 穿
     */
    private boolean tryEquipFromItems() {
        for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
            ItemStack candidate = mc.player.getInventory().items.get(i);
            if (candidate.isEmpty()) continue;
            if (!(candidate.getItem() instanceof ArmorItem armor)) continue;

            float candidateScore = ItemUtil.getArmorScore(candidate);
            boolean isBest = ItemUtil.getBestArmorScore(armor.getEquipmentSlot()) == candidateScore;
            boolean betterThanEquipped =
                    ItemUtil.getEquippedArmorScore(armor.getEquipmentSlot()) < candidateScore;

            if (isBest && betterThanEquipped) {
                int target = i < 9 ? i + 36 : i;
                this.silentClick(
                        mc.player.inventoryMenu.containerId,
                        target,
                        0,
                        ClickType.QUICK_MOVE
                );
                actionTimer.reset();
                return true;
            }
        }
        return false;
    }

    /**
     * 扫当前打开的背包界面里的其他格子。
     * 只在 Only In Inventory = true 且已经打开背包界面时调用。
     */
    private boolean tryEquipFromOpenContainer() {
        if (mc.player == null) return false;

        var menu = mc.player.containerMenu;
        if (menu == null) return false;

        int size = menu.slots.size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack candidate = menu.getSlot(slot).getItem();
            if (candidate.isEmpty()) continue;
            if (!(candidate.getItem() instanceof ArmorItem armor)) continue;

            float candidateScore = ItemUtil.getArmorScore(candidate);
            boolean isBest = ItemUtil.getBestArmorScore(armor.getEquipmentSlot()) == candidateScore;
            boolean betterThanEquipped =
                    ItemUtil.getEquippedArmorScore(armor.getEquipmentSlot()) < candidateScore;

            if (isBest && betterThanEquipped) {
                this.silentClick(
                        menu.containerId,
                        slot,
                        0,
                        ClickType.QUICK_MOVE
                );
                actionTimer.reset();
                return true;
            }
        }
        return false;
    }

    // ==================== 工具 ====================
    /** 玩家是否有水平移动（输入或实际位移）。 */
    private boolean isPlayerMoving() {
        if (mc.player == null) return false;
        double dx = mc.player.getX() - mc.player.xOld;
        double dz = mc.player.getZ() - mc.player.zOld;
        boolean moved = dx * dx + dz * dz > 1.0E-6;
        boolean input = mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown();
        return moved || input;
    }

    /**
     * 静默点击。
     * 如果当前没有打开 GUI，点击后立刻发 ContainerClose，避免服务端状态不同步。
     */
    private void silentClick(int containerId, int slotId, int button, ClickType clickType) {
        if (mc.gameMode == null || mc.player == null) return;

        if (mc.screen != null) {
            mc.gameMode.handleInventoryMouseClick(containerId, slotId, button, clickType, mc.player);
            return;
        }

        mc.gameMode.handleInventoryMouseClick(containerId, slotId, button, clickType, mc.player);
        client.nilore.utils.misc.PacketUtil.sendQueued(
                new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(containerId)
        );
    }
}