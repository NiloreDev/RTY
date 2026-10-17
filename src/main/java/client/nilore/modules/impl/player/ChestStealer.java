package client.nilore.modules.impl.player;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.game.BlockUtil;
import client.nilore.utils.game.ItemUtil;
import client.nilore.utils.misc.PacketUtil;
import client.nilore.utils.misc.ReflectionUtil;
import client.nilore.event.EventTarget;

public class ChestStealer
        extends Module {

    public record StealTarget(int slotIndex, ItemStack itemStack, int priority, double score) {
    }

    public static ChestStealer INSTANCE;
    private static final Timer actionTimer;
    private final NumberSetting clickDelaySetting = new NumberSetting("Delay", 110, 0, 1000, 10);
    private final NumberSetting openDelaySetting = new NumberSetting("Open Delay", 2, 0, 10, 1);
    private final BooleanSetting chestSetting = new BooleanSetting("Chest", true);
    private final BooleanSetting enderChestSetting = new BooleanSetting("Ender Chest", false);
    private final BooleanSetting furnaceSetting = new BooleanSetting("Furnace", true);
    private final BooleanSetting brewingStandSetting = new BooleanSetting("BrewingStand", true);
    private final BooleanSetting pickTrashSetting = new BooleanSetting("PickTrash", false);
    private final BooleanSetting onlyBestSetting = new BooleanSetting("Only Best", true);
    private final BooleanSetting randomClickSetting = new BooleanSetting("Random Click", true);
    private final BooleanSetting smartStealingSetting = new BooleanSetting("Smart Stealing", true);
    private final BooleanSetting dangerSetting = new BooleanSetting("Danger", false);

    // =========================================================
    // Silent 相关设置
    // =========================================================
    /** Silent —— 开启后打开箱子时隐藏原版 GUI，用自定义 HUD 显示容器内容 + 玩家背包。 */
    private final BooleanSetting silent = new BooleanSetting("Silent", false);

    /**
     * Silent HUD 相对原版槽位的 X 像素偏移。
     *   0 = 与原版槽位 100% 对齐（默认）
     *   正数 = 右移，负数 = 左移
     */
    private final NumberSetting silentOffsetX =
            new NumberSetting("Silent Offset X", 0.0f, -500.0f, 500.0f, 1.0f);

    /**
     * Silent HUD 相对原版槽位的 Y 像素偏移。
     */
    private final NumberSetting silentOffsetY =
            new NumberSetting("Silent Offset Y", 0.0f, -500.0f, 500.0f, 1.0f);

    /**
     * Silent HUD 背景透明度：
     *   0.0 = 完全透明（只剩文字/物品）
     *   1.0 = 完全不透明深色
     */
    private final NumberSetting silentBgAlpha =
            new NumberSetting("Silent Bg Alpha", 0.35f, 0.0f, 1.0f, 0.01f);

    private static final Timer stealTimer;
    private static final Timer openTimer;
    private final Random random = new Random();
    private AbstractContainerMenu pendingMenu = null;
    private boolean hasPendingClick = false;
    private int totalBlockCount = 0;
    private int pendingSlot = -1;
    private int ticksSinceMenu = 0;
    private static long clickDelayMs;
    private int serverPing = -1;
    private int accessCount;
    private Screen lastScreen;
    private int openDelayTicks = 0;
    private final List<ChestStealer.StealTarget> stealTargetQueue = new ArrayList<>();
    private int stealIndex = 0;
    private boolean queueBuilt = false;

    // =========================================================
    // Silent 状态
    // =========================================================
    /** 当前打开的容器菜单（用于 Silent HUD 显示）。 */
    private AbstractContainerMenu silentMenu = null;

    public ChestStealer() {
        super("ChestStealer", Category.PLAYER);
        INSTANCE = this;
    }

    public static boolean isRateLimited() {
        return !stealTimer.hasPassed(100L) && !openTimer.hasPassed((int)clickDelayMs);
    }

    @Override
    public void onDisable() {
        this.resetAll();
        this.silentMenu = null;
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

    @EventTarget
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        this.resetAll();
        this.silentMenu = null;
    }

    @EventTarget
    public void onReceivePacket(ReceivePacketEvent receivePacketEvent) {
        if (receivePacketEvent.getPacket() instanceof ClientboundPingPacket pingPacket) {
            this.serverPing = pingPacket.getId();
        }
    }

    @EventTarget
    public void onGameTick(GameTickEvent gameTickEvent) {
        if (this.hasPendingClick && this.pendingMenu != null && this.pendingSlot >= 0) {
            ++this.ticksSinceMenu;
            if (this.ticksSinceMenu >= 1) {
                this.executePendingClick();
                this.resetState();
            }
        }
    }

    // ================================================================
    // Silent —— 用自定义 Screen 替换原版 ContainerScreen，屏蔽渲染
    // ================================================================
    @EventTarget
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onSilentTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        boolean silentEnabled = this.silent.getValue();

        if (!silentEnabled) {
            this.silentMenu = null;
            return;
        }

        if (mc.screen instanceof ContainerScreen cs
                && !(cs instanceof SilentContainerScreen)) {

            ChestMenu chestMenu = cs.getMenu();
            if (chestMenu != null) {
                try {
                    mc.setScreen(new SilentContainerScreen(
                            chestMenu,
                            mc.player.getInventory(),
                            cs.getTitle()
                    ));
                } catch (Throwable ignored) {
                }
            }
        }

        AbstractContainerMenu current = mc.player.containerMenu;
        if (current == null || current == mc.player.inventoryMenu) {
            this.silentMenu = null;
        } else {
            this.silentMenu = current;
        }
    }

    /**
     * 静默版本的箱子 Screen：
     *   - 完全屏蔽原版 GUI 渲染（清空 render）
     *   - 保留鼠标、点击、槽位交互逻辑（继承自 ContainerScreen）
     *   - 暴露 leftPos / topPos 供 HUD 对齐使用
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class SilentContainerScreen extends ContainerScreen {
        public SilentContainerScreen(ChestMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
            // 不渲染任何 GUI
        }

        public int nilore$leftPos() {
            return this.leftPos;
        }

        public int nilore$topPos() {
            return this.topPos;
        }
    }

    // ================================================================
    // Silent HUD 渲染
    // ================================================================
    @EventTarget
    public void onRenderSilent(Render2DEvent event) {
        if (!this.silent.getValue()) return;
        if (this.silentMenu == null) return;
        if (!(this.silentMenu instanceof ChestMenu chestMenu)) return;

        GuiGraphics gg = event.guiGraphics();
        if (gg == null) return;

        this.drawSilentContainerHud(gg, chestMenu);
    }

    /**
     * 绘制 "Container Contents" HUD。
     *
     * 关键：每个物品格直接使用原版 Slot 的坐标 (leftPos + slot.x, topPos + slot.y)。
     * 这样"画在哪"就等于"点击判定在哪"，视觉和点击 100% 对齐。
     */
    private void drawSilentContainerHud(GuiGraphics gg, ChestMenu menu) {
        if (mc.player == null) return;

        final int screenW = mc.getWindow().getGuiScaledWidth();
        final int screenH = mc.getWindow().getGuiScaledHeight();

        final int chestRows = menu.getRowCount();

        // ==== 原版箱子 GUI 的左上角 ====
        int leftPos;
        int topPos;

        if (mc.screen instanceof SilentContainerScreen scs) {
            leftPos = scs.nilore$leftPos();
            topPos = scs.nilore$topPos();
        } else {
            int guiHeight = 114 + chestRows * 18;
            leftPos = (screenW - 176) / 2;
            topPos = (screenH - guiHeight) / 2;
        }

        final int userOffsetX = (int) this.silentOffsetX.getValue().floatValue();
        final int userOffsetY = (int) this.silentOffsetY.getValue().floatValue();

        // ==== 颜色 ====
        float bgAlphaRatio = this.silentBgAlpha.getValue().floatValue();
        if (bgAlphaRatio < 0.0f) bgAlphaRatio = 0.0f;
        if (bgAlphaRatio > 1.0f) bgAlphaRatio = 1.0f;
        final int bgAlpha = (int) (bgAlphaRatio * 255.0f);
        final int panelBg = (bgAlpha << 24);

        final int slotBgAlpha = (int) (Math.min(1.0f, bgAlphaRatio + 0.15f) * 255.0f);
        final int slotBg = (slotBgAlpha << 24) | 0x00101010;

        // ==== 1) 面板背景：完全覆盖原版 GUI 区域 ====
        // 原版箱子 GUI 尺寸：176 x (114 + chestRows * 18)
        final int guiWidth = 176;
        final int guiHeight = 114 + chestRows * 18;
        final int pad = 4;

        final int panelX = leftPos - pad + userOffsetX;
        final int panelY = topPos - pad + userOffsetY;
        final int panelW = guiWidth + pad * 2;
        final int panelH = guiHeight + pad * 2;

        if (bgAlpha > 0) {
            gg.fill(panelX, panelY, panelX + panelW, panelY + panelH, panelBg);
        }

        // ==== 2) 标题栏 ====
        final int titleHeight = 14;
        gg.fill(panelX, panelY, panelX + panelW, panelY + titleHeight, 0x60000000);
        gg.drawString(mc.font, "Container Contents", panelX + 6, panelY + 3, 0xFFFFFFFF, true);

        // ==== 3) 每个槽位：直接用原版 Slot 坐标 ====
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int slotX = leftPos + slot.x + userOffsetX;
            int slotY = topPos + slot.y + userOffsetY;

            // 跳过玩家背包以外不需要显示的槽位？这里全都显示，跟原版一致
            // 画槽位背景
            gg.fill(slotX, slotY, slotX + 16, slotY + 16, slotBg);

            // 画物品
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                gg.renderItem(stack, slotX, slotY);
                gg.renderItemDecorations(mc.font, stack, slotX, slotY);
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameMode == null
                || mc.getConnection() == null || KillAura.target != null || Scaffold.INSTANCE.isEnabled()) {
            return;
        }
        if (!openTimer.hasPassed((int) clickDelayMs)
                || !mc.player.isAlive() || mc.player.isDeadOrDying()
                || mc.player.isSpectator() || motionEvent.isPre()) {
            return;
        }
        Screen screen = mc.screen;
        AbstractContainerMenu containerMenu = mc.player.containerMenu;
        this.countBlocks();
        if (screen instanceof ContainerScreen containerScreen) {
            if (screen != this.lastScreen) {
                actionTimer.reset();
                this.openDelayTicks = 0;
                this.queueBuilt = false;
                this.stealTargetQueue.clear();
                this.stealIndex = 0;
            } else {
                ++this.openDelayTicks;
                if (this.openDelayTicks < this.openDelaySetting.getValue().intValue()) {
                    return;
                }
                String title = containerScreen.getTitle().getString();
                String chestTitle = Component.translatable("container.chest").getString();
                String doubleChestTitle = Component.translatable("container.chestDouble").getString();
                String enderChestTitle = Component.translatable("container.enderchest").getString();
                ChestMenu chestMenu = containerScreen.getMenu();
                if (this.chestSetting.getValue() && (title.equals(chestTitle) || title.equals(doubleChestTitle) || title.equals("Chest"))) {
                    if (this.shouldCloseChest(chestMenu)) {
                        this.stealFromChest(chestMenu);
                    }
                } else if (this.enderChestSetting.getValue() && title.equals(enderChestTitle) && this.shouldCloseChest(chestMenu)) {
                    this.stealFromChest(chestMenu);
                }
            }
        } else {
            this.openDelayTicks = 0;
            this.queueBuilt = false;
            this.stealTargetQueue.clear();
            this.stealIndex = 0;
        }
        if (containerMenu instanceof FurnaceMenu furnaceMenu) {
            if (this.furnaceSetting.getValue()) {
                this.stealFromFurnace(furnaceMenu);
            }
        }
        if (containerMenu instanceof BrewingStandMenu brewingMenu) {
            if (this.brewingStandSetting.getValue()) {
                this.stealFromBrewing(brewingMenu);
            }
        }
        this.lastScreen = screen;
    }

    private boolean shouldCloseChest(ChestMenu chestMenu) {
        if (this.isChestDone(chestMenu) && stealTimer.hasPassed(100L)) {
            mc.player.closeContainer();
            return false;
        }
        return true;
    }

    private void stealFromChest(ChestMenu chestMenu) {
        ++this.accessCount;
        if (this.smartStealingSetting.getValue() && this.accessCount > 1) {
            this.stealSmartMode(chestMenu);
        } else {
            this.stealRandomMode(chestMenu);
        }
    }

    private void stealSmartMode(ChestMenu chestMenu) {
        if (!this.queueBuilt) {
            this.buildStealQueue(chestMenu);
            this.queueBuilt = true;
            this.stealIndex = 0;
        }
        if (this.stealIndex < this.stealTargetQueue.size()) {
            ChestStealer.StealTarget target = this.stealTargetQueue.get(this.stealIndex);
            if (!chestMenu.getSlot(target.slotIndex).getItem().isEmpty()) {
                this.schedulePendingClick(chestMenu, target.slotIndex);
                ++this.stealIndex;
            } else {
                ++this.stealIndex;
            }
        } else if (this.isChestComplete(chestMenu) && stealTimer.hasPassed(100L)) {
            mc.player.closeContainer();
        }
    }

    private void buildStealQueue(ChestMenu chestMenu) {
        ArrayList<ChestStealer.StealTarget> candidates = new ArrayList<>();
        for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
            ItemStack itemStack = chestMenu.getSlot(slot).getItem();
            if (itemStack.isEmpty() || !this.shouldStealItem(itemStack)) continue;
            int priority = this.getItemPriority(itemStack);
            double score = this.getItemScore(itemStack);
            candidates.add(new ChestStealer.StealTarget(slot, itemStack, priority, score));
        }
        Map<String, List<ChestStealer.StealTarget>> categoryMap = this.categorizeItems(candidates);
        this.stealTargetQueue.clear();
        List<String> categories = Arrays.asList("god", "helmet", "chestplate", "leggings", "boots", "sword", "bow", "crossbow", "golden_apple", "pickaxe", "axe", "shovel", "special", "utility", "other");
        for (String category : categories) {
            if (!categoryMap.containsKey(category)) continue;
            List<ChestStealer.StealTarget> categoryItems = categoryMap.get(category);
            boolean isBestOnlyCategory = category.equals("god") || category.equals("helmet") || category.equals("chestplate") || category.equals("leggings") || category.equals("boots") || category.equals("sword") || category.equals("bow") || category.equals("crossbow") || category.equals("pickaxe") || category.equals("axe") || category.equals("shovel");
            if (this.onlyBestSetting.getValue() && isBestOnlyCategory) {
                ChestStealer.StealTarget best = categoryItems.stream().max(Comparator.comparingDouble(t -> t.score)).orElse(null);
                if (best == null) continue;
                this.stealTargetQueue.add(best);
                continue;
            }
            categoryItems.sort((a, b) -> Double.compare(b.score, a.score));
            this.stealTargetQueue.addAll(categoryItems);
        }
    }

    private Map<String, List<ChestStealer.StealTarget>> categorizeItems(List<ChestStealer.StealTarget> targets) {
        HashMap<String, List<ChestStealer.StealTarget>> categoryMap = new HashMap<>();
        for (ChestStealer.StealTarget target : targets) {
            String category = this.getItemCategory(target.itemStack);
            categoryMap.computeIfAbsent(category, key -> new ArrayList<>()).add(target);
        }
        return categoryMap;
    }

    private String getItemCategory(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack)) {
            return "god";
        }
        if (item instanceof ArmorItem armorItem) {
            return switch (armorItem.getEquipmentSlot()) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                case FEET -> "boots";
                default -> "other";
            };
        }
        if (item instanceof SwordItem) {
            return "sword";
        }
        if (item instanceof BowItem) {
            return "bow";
        }
        if (item instanceof CrossbowItem) {
            return "crossbow";
        }
        if (item instanceof PickaxeItem) {
            return "pickaxe";
        }
        if (item instanceof AxeItem) {
            return "axe";
        }
        if (item instanceof ShovelItem) {
            return "shovel";
        }
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
            return "golden_apple";
        }
        if (item == Items.COMPASS || item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            return "special";
        }
        if (item == Items.COBWEB) {
            return "utility";
        }
        if (item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG || item == Items.ARROW || item instanceof FishingRodItem || item instanceof BlockItem) {
            return "utility";
        }
        return "other";
    }

    private int getItemPriority(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack)) {
            return 150;
        }
        if (item instanceof ArmorItem armorItem) {
            return switch (armorItem.getEquipmentSlot()) {
                case HEAD -> 100;
                case CHEST -> 99;
                case LEGS -> 98;
                case FEET -> 97;
                default -> 50;
            };
        }
        if (item instanceof SwordItem) {
            return 95;
        }
        if (item instanceof BowItem) {
            return 93;
        }
        if (item instanceof CrossbowItem) {
            return 92;
        }
        if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            return 91;
        }
        if (item == Items.GOLDEN_APPLE) {
            return 90;
        }
        if (item instanceof PickaxeItem) {
            return 89;
        }
        if (item instanceof AxeItem) {
            return 88;
        }
        if (item instanceof ShovelItem) {
            return 87;
        }
        if (item == Items.COMPASS) {
            return 85;
        }
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            return 83;
        }
        if (item == Items.ENDER_PEARL) {
            return 80;
        }
        if (item == Items.ARROW) {
            return 75;
        }
        if (item == Items.COBWEB) {
            return 72;
        }
        if (item == Items.SNOWBALL || item == Items.EGG) {
            return 70;
        }
        if (item instanceof FishingRodItem) {
            return 65;
        }
        if (item instanceof BlockItem) {
            return 60;
        }
        return 50;
    }

    private double getItemScore(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack)) {
            return 10000.0;
        }
        if (item instanceof ArmorItem) {
            return ItemUtil.getArmorScore(itemStack);
        }
        if (item instanceof SwordItem) {
            return ItemUtil.getSwordDamage(itemStack);
        }
        if (item instanceof AxeItem && ItemUtil.isLegitAxe(itemStack)) {
            return ItemUtil.getAxeDamage(itemStack);
        }
        if (item instanceof DiggerItem) {
            return ItemUtil.getDigSpeed(itemStack);
        }
        if (item instanceof BowItem) {
            if (ItemUtil.isGoodBow(itemStack)) {
                return ItemUtil.getBowScore(itemStack);
            }
            if (ItemUtil.isGoodBowAlt(itemStack)) {
                return ItemUtil.getBowScoreAlt(itemStack);
            }
            return 1.0;
        }
        if (item instanceof CrossbowItem) {
            return ItemUtil.getCrossbowScore(itemStack);
        }
        if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            return 50.0 + (double)itemStack.getCount();
        }
        if (item == Items.GOLDEN_APPLE) {
            return 30.0 + (double)itemStack.getCount();
        }
        if (item == Items.ENDER_PEARL) {
            return 10.0 + (double)itemStack.getCount();
        }
        if (item == Items.ARROW) {
            return 5.0 + (double)itemStack.getCount() * 0.1;
        }
        if (item == Items.COBWEB) {
            return 4.0 + (double)itemStack.getCount() * 0.1;
        }
        if (item == Items.SNOWBALL || item == Items.EGG) {
            return 3.0 + (double)itemStack.getCount() * 0.1;
        }
        if (item instanceof FishingRodItem) {
            return ItemUtil.getDigSpeed(itemStack);
        }
        if (item instanceof BlockItem) {
            return 2.0 + (double)itemStack.getCount() * 0.05;
        }
        return 1.0;
    }

    private void stealRandomMode(ChestMenu chestMenu) {
        List<Integer> stealableSlots = this.getStealableChestSlots(chestMenu);
        if (this.randomClickSetting.getValue() && !stealableSlots.isEmpty() && this.accessCount > 1) {
            int randomSlot = stealableSlots.get(this.random.nextInt(stealableSlots.size()));
            this.schedulePendingClick(chestMenu, randomSlot);
        } else {
            for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
                ItemStack itemStack = chestMenu.getSlot(slot).getItem();
                if (itemStack.isEmpty() || this.accessCount <= 1 || !this.tryStealSlot(chestMenu, slot)) continue;
                return;
            }
        }
    }

    private void stealFromFurnace(FurnaceMenu furnaceMenu) {
        ++this.accessCount;
        try {
            Container container = this.getFurnaceContainer(furnaceMenu);
            if (container == null) {
                return;
            }
            if (this.isFurnaceDone(furnaceMenu) && stealTimer.hasPassed(100L)) {
                mc.player.closeContainer();
                return;
            }
            List<Integer> stealableSlots = this.getStealableContainerSlots(container);
            if (this.randomClickSetting.getValue() && !stealableSlots.isEmpty() && this.accessCount > 1) {
                int randomSlot = stealableSlots.get(this.random.nextInt(stealableSlots.size()));
                this.schedulePendingClick(furnaceMenu, randomSlot);
            } else {
                for (int slot = 0; slot < container.getContainerSize(); ++slot) {
                    ItemStack itemStack = container.getItem(slot);
                    if (itemStack.isEmpty() || this.accessCount <= 1 || !this.shouldStealItem(itemStack)) continue;
                    this.schedulePendingClick(furnaceMenu, slot);
                    return;
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void stealFromBrewing(BrewingStandMenu brewingStandMenu) {
        ++this.accessCount;
        Container container = ReflectionUtil.getBrewingStand(brewingStandMenu);
        if (container == null) {
            return;
        }
        if (this.isBrewingDone(brewingStandMenu) && stealTimer.hasPassed(100L)) {
            mc.player.closeContainer();
            return;
        }
        List<Integer> stealableSlots = this.getStealableContainerSlots(container);
        if (this.randomClickSetting.getValue() && !stealableSlots.isEmpty() && this.accessCount > 1) {
            int randomSlot = stealableSlots.get(this.random.nextInt(stealableSlots.size()));
            this.schedulePendingClick(brewingStandMenu, randomSlot);
        } else {
            for (int slot = 0; slot < container.getContainerSize(); ++slot) {
                ItemStack itemStack = container.getItem(slot);
                if (itemStack.isEmpty() || this.accessCount <= 1 || !this.shouldStealItem(itemStack)) continue;
                this.schedulePendingClick(brewingStandMenu, slot);
                return;
            }
        }
    }

    private List<Integer> getStealableChestSlots(ChestMenu chestMenu) {
        ArrayList<Integer> stealableSlots = new ArrayList<>();
        for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
            ItemStack itemStack = chestMenu.getSlot(slot).getItem();
            if (itemStack.isEmpty() || !ChestStealer.isWorthStealing(itemStack) && !this.pickTrashSetting.getValue() || !this.shouldStealItem(itemStack)) continue;
            stealableSlots.add(slot);
        }
        return stealableSlots;
    }

    private List<Integer> getStealableContainerSlots(Container container) {
        ArrayList<Integer> stealableSlots = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack itemStack = container.getItem(slot);
            if (itemStack.isEmpty() || !this.shouldStealItem(itemStack)) continue;
            stealableSlots.add(slot);
        }
        return stealableSlots;
    }

    private Container getFurnaceContainer(AbstractFurnaceMenu furnaceMenu) throws Exception {
        Field[] fields;
        for (Field field : fields = AbstractFurnaceMenu.class.getDeclaredFields()) {
            if (!Container.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            return (Container)field.get(furnaceMenu);
        }
        return null;
    }

    private void schedulePendingClick(AbstractContainerMenu menu, int slot) {
        if (!this.hasPendingClick) {
            this.pendingMenu = menu;
            this.pendingSlot = slot;
            this.hasPendingClick = true;
            this.ticksSinceMenu = 0;
            this.executePendingClick();
            this.resetState();
        }
    }

    private void executePendingClick() {
        if (this.pendingMenu != null && this.pendingSlot >= 0) {
            clickDelayMs = this.clickDelaySetting.getValue().longValue();
            if (this.pendingSlot < this.pendingMenu.slots.size()) {
                if (this.dangerSetting.getValue() && mc.getConnection() != null) {
                    PacketUtil.sendQueued(new ServerboundCustomPayloadPacket(
                            ResourceLocation.tryBuild("minecraft", "test"), new FriendlyByteBuf(Unpooled.buffer())));
                    PacketUtil.sendQueued(new ServerboundPongPacket(Integer.MAX_VALUE));
                }
                mc.gameMode.handleInventoryMouseClick(
                        this.pendingMenu.containerId, this.pendingSlot,
                        0, ClickType.QUICK_MOVE, mc.player);
            }
            openTimer.reset();
            stealTimer.reset();
            actionTimer.reset();
        }
    }

    private boolean tryStealSlot(ChestMenu chestMenu, int slot) {
        ItemStack itemStack = chestMenu.getSlot(slot).getItem();
        if ((ChestStealer.isWorthStealing(itemStack) || this.pickTrashSetting.getValue()) && this.shouldStealItem(itemStack)) {
            this.schedulePendingClick(chestMenu, slot);
            return true;
        }
        return false;
    }

    private void resetAll() {
        this.resetState();
        this.openDelayTicks = 0;
        this.serverPing = -1;
    }

    private void resetState() {
        this.hasPendingClick = false;
        this.pendingSlot = -1;
        this.pendingMenu = null;
        this.ticksSinceMenu = 0;
    }

    private void countBlocks() {
        this.totalBlockCount = 0;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); ++slot) {
            ItemStack itemStack = mc.player.getInventory().getItem(slot);
            if (itemStack.isEmpty() || !(itemStack.getItem() instanceof BlockItem)) continue;
            this.totalBlockCount += itemStack.getCount();
        }
    }

    private boolean shouldStealItem(ItemStack itemStack) {
        int count;
        Item item = itemStack.getItem();
        if (item instanceof FishingRodItem && (count = ItemUtil.countItem(Items.FISHING_ROD)) > 0) {
            return false;
        }
        if (item instanceof BlockItem && item != Items.COBWEB && item != Items.TNT) {
            count = InventoryManager.getMaxBlockSize();
            if (this.totalBlockCount + itemStack.getCount() > count) {
                return false;
            }
        }
        if (this.onlyBestSetting.getValue()) {
            if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack)) {
                return true;
            }
            if (item instanceof SwordItem) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (item instanceof DiggerItem) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (item instanceof ArmorItem) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (item instanceof BowItem) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (item instanceof CrossbowItem) {
                return this.isBetterThanCurrent(itemStack);
            }
            if (!(item instanceof SwordItem || item instanceof DiggerItem || item instanceof ArmorItem || item instanceof BowItem || item instanceof CrossbowItem)) {
                return ChestStealer.isWorthStealing(itemStack) || this.pickTrashSetting.getValue() != false;
            }
        }
        return true;
    }

    private boolean isBetterThanCurrent(ItemStack itemStack) {
        if (itemStack.getItem() instanceof SwordItem) {
            float candidateDamage = ItemUtil.getSwordDamage(itemStack);
            float bestDamage = ItemUtil.getBestSwordDamage();
            return candidateDamage > bestDamage;
        }
        if (itemStack.getItem() instanceof DiggerItem) {
            if (itemStack.getItem() instanceof PickaxeItem) {
                float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
                float bestSpeed = ItemUtil.getBestPickaxeScore();
                return candidateSpeed > bestSpeed;
            }
            if (itemStack.getItem() instanceof AxeItem) {
                if (ItemUtil.isLegitAxe(itemStack)) {
                    float candidateDamage = ItemUtil.getAxeDamage(itemStack);
                    ItemStack bestAxeStack = ItemUtil.getBestSharpAxe();
                    float bestDamage = bestAxeStack != null ? ItemUtil.getAxeDamage(bestAxeStack) : 0.0f;
                    return candidateDamage > bestDamage;
                }
                float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
                float bestSpeed = ItemUtil.getBestAxeScore();
                return candidateSpeed > bestSpeed;
            }
            if (itemStack.getItem() instanceof ShovelItem) {
                float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
                float bestSpeed = ItemUtil.getBestShovelScore();
                return candidateSpeed > bestSpeed;
            }
        } else {
            Item item = itemStack.getItem();
            if (item instanceof ArmorItem armorItem) {
                float candidateScore = ItemUtil.getArmorScore(itemStack);
                float equippedScore = ItemUtil.getEquippedArmorScore(armorItem.getEquipmentSlot());
                return candidateScore > equippedScore + 0.1f;
            }
            if (itemStack.getItem() instanceof BowItem) {
                if (ItemUtil.isGoodBow(itemStack)) {
                    float candidateScore = ItemUtil.getBowScore(itemStack);
                    float bestScore = ItemUtil.getBestBowScore();
                    return candidateScore > bestScore;
                }
                if (ItemUtil.isGoodBowAlt(itemStack)) {
                    float candidateScore = ItemUtil.getBowScoreAlt(itemStack);
                    float bestScore = ItemUtil.getBestBowScoreAlt();
                    return candidateScore > bestScore;
                }
            } else if (itemStack.getItem() instanceof CrossbowItem) {
                float candidateScore = ItemUtil.getCrossbowScore(itemStack);
                float bestScore = ItemUtil.getBestCrossbowScore();
                return candidateScore > bestScore;
            }
        }
        return true;
    }

    private boolean isChestDone(ChestMenu chestMenu) {
        for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
            ItemStack itemStack = chestMenu.getSlot(slot).getItem();
            if (itemStack.isEmpty() || !ChestStealer.isWorthStealing(itemStack) && !this.pickTrashSetting.getValue() || !this.shouldStealItem(itemStack)) continue;
            return false;
        }
        return true;
    }

    private boolean isChestComplete(ChestMenu chestMenu) {
        for (int slot = 0; slot < chestMenu.getRowCount() * 9; ++slot) {
            ItemStack itemStack = chestMenu.getSlot(slot).getItem();
            if (itemStack.isEmpty() || !ChestStealer.isWorthStealing(itemStack) && !this.pickTrashSetting.getValue() || !this.shouldStealItem(itemStack)) continue;
            return false;
        }
        return true;
    }

    private boolean isFurnaceDone(FurnaceMenu furnaceMenu) {
        try {
            Container container = this.getFurnaceContainer(furnaceMenu);
            if (container == null) {
                return false;
            }
            for (int slot = 0; slot < container.getContainerSize(); ++slot) {
                ItemStack itemStack = container.getItem(slot);
                if (itemStack.isEmpty() || !this.shouldStealItem(itemStack)) continue;
                return false;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean isBrewingDone(BrewingStandMenu brewingStandMenu) {
        Container container = ReflectionUtil.getBrewingStand(brewingStandMenu);
        if (container == null) {
            return true;
        }
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack itemStack = container.getItem(slot);
            if (itemStack.isEmpty() || !this.shouldStealItem(itemStack)) continue;
            return false;
        }
        return true;
    }

    public static boolean isWorthStealing(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        if (ItemUtil.isWeaponItem(itemStack) || ItemUtil.isOtherCheat(itemStack) || ItemUtil.isLegitAxe(itemStack)) {
            return true;
        }
        Item item = itemStack.getItem();
        if (item instanceof ArmorItem armorItem) {
            float candidateScore = ItemUtil.getArmorScore(itemStack);
            float bestScore = ItemUtil.getBestArmorScore(armorItem.getEquipmentSlot());
            return !(candidateScore <= bestScore);
        }
        if (itemStack.getItem() instanceof SwordItem) {
            float candidateDamage = ItemUtil.getSwordDamage(itemStack);
            float bestDamage = ItemUtil.getBestSwordDamage();
            return !(candidateDamage <= bestDamage);
        }
        if (itemStack.getItem() instanceof PickaxeItem) {
            float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
            float bestSpeed = ItemUtil.getBestPickaxeScore();
            return !(candidateSpeed <= bestSpeed);
        }
        if (itemStack.getItem() instanceof AxeItem) {
            float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
            float bestSpeed = ItemUtil.getBestAxeScore();
            return !(candidateSpeed <= bestSpeed);
        }
        if (itemStack.getItem() instanceof ShovelItem) {
            float candidateSpeed = ItemUtil.getDigSpeed(itemStack);
            float bestSpeed = ItemUtil.getBestShovelScore();
            return !(candidateSpeed <= bestSpeed);
        }
        if (itemStack.getItem() instanceof CrossbowItem) {
            float candidateScore = ItemUtil.getCrossbowScore(itemStack);
            float bestScore = ItemUtil.getBestCrossbowScore();
            return !(candidateScore <= bestScore);
        }
        if (itemStack.getItem() instanceof BowItem && ItemUtil.isGoodBow(itemStack)) {
            float candidateScore = ItemUtil.getBowScore(itemStack);
            float bestScore = ItemUtil.getBestBowScore();
            return !(candidateScore <= bestScore);
        }
        if (itemStack.getItem() instanceof BowItem && ItemUtil.isGoodBowAlt(itemStack)) {
            float candidateScore = ItemUtil.getBowScoreAlt(itemStack);
            float bestScore = ItemUtil.getBestBowScoreAlt();
            return !(candidateScore <= bestScore);
        }
        if (itemStack.getItem() == Items.GOLDEN_APPLE || itemStack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            return true;
        }
        if (itemStack.getItem() == Items.COBWEB || itemStack.getItem() == Items.TNT) {
            return true;
        }
        if (itemStack.getItem() == Items.COMPASS) {
            return !ItemUtil.hasItem(itemStack.getItem());
        }
        if (itemStack.getItem() == Items.WATER_BUCKET && ItemUtil.countItem(Items.WATER_BUCKET) >= InventoryManager.getMaxWaterBuckets()) {
            return false;
        }
        if (itemStack.getItem() == Items.LAVA_BUCKET && ItemUtil.countItem(Items.LAVA_BUCKET) >= InventoryManager.getMaxLavaBuckets()) {
            return false;
        }
        if (itemStack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(itemStack) && ItemUtil.countBlocks() + itemStack.getCount() >= InventoryManager.getMaxBlockSize()) {
            return false;
        }
        if (itemStack.getItem() == Items.ARROW && ItemUtil.countItem(Items.ARROW) + itemStack.getCount() >= InventoryManager.getMaxArrows()) {
            return false;
        }
        if (itemStack.getItem() instanceof FishingRodItem && ItemUtil.countItem(Items.FISHING_ROD) >= 1) {
            return false;
        }
        if ((itemStack.getItem() == Items.SNOWBALL || itemStack.getItem() == Items.EGG) && ItemUtil.countItem(Items.SNOWBALL) + ItemUtil.countItem(Items.EGG) + itemStack.getCount() >= InventoryManager.getMaxEggsSnowballsSize()) {
            return false;
        }
        if (itemStack.getItem() instanceof ItemNameBlockItem) {
            return false;
        }
        return ItemUtil.isUsableItem(itemStack);
    }

    static {
        actionTimer = new Timer();
        stealTimer = new Timer();
        openTimer = new Timer();
    }
}