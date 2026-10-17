package client.nilore.utils.epsilon.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;

/** Epsilon click helper adapted from ContainerInput to the Nilore Minecraft version. */
public final class ClickSlotUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    private ClickSlotUtils() {}

    public static void clickSlot(int containerId, int slot, int button, ClickType action) {
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(containerId, slot, button, action, mc.player);
    }

    public static void clickSlot(int slot, int button, ClickType action) {
        if (mc.player == null) return;
        clickSlot(mc.player.inventoryMenu.containerId, slot, button, action);
    }

    public static void click(int slot) { clickSlot(slot, 0, ClickType.PICKUP); }
    public static void shiftClick(int slot) { clickSlot(slot, 0, ClickType.QUICK_MOVE); }
    public static void drop(int slot) { clickSlot(slot, 0, ClickType.THROW); }
    public static void dropAll(int slot) { clickSlot(slot, 1, ClickType.THROW); }
    public static void swap(int slot, int hotbarSlot) { clickSlot(slot, hotbarSlot, ClickType.SWAP); }
    public static void swapOffhand(int slot) { clickSlot(slot, 40, ClickType.SWAP); }
}
