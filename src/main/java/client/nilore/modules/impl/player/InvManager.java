package client.nilore.modules.impl.player;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.epsilon.math.MathUtils;
import client.nilore.utils.epsilon.player.ClickSlotUtils;
import client.nilore.utils.epsilon.player.InvHelper;
import client.nilore.utils.epsilon.player.PlayerUtils;
import client.nilore.utils.epsilon.timer.TimerUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Epsilon InvManager port for Nilore.
 *
 * Anti-BadPackets0 加固：
 *   - 严格检查当前 containerMenu 必须 == inventoryMenu
 *   - Silent 模式每次点击间隔强制 >= 2 tick
 *   - 玩家打开任何 GUI（聊天/箱子/工作台）时停手
 *   - 换装第二步（穿新装备）额外等一拍再发
 */
public class InvManager extends Module {
    public static InvManager INSTANCE;

    /** Silent 模式两次点击之间的最小 tick 间隔，防止一 tick 一发触发 BadPackets */
    private static final int SILENT_MIN_TICK_GAP = 2;

    public final ModeSetting mode = new ModeSetting("Mode", "Inventory", "Silent").withDefault("Silent");
    public final BooleanSetting pauseOnEat = new BooleanSetting("Pause On Eat", true);
    public final BooleanSetting pauseOnUseItem = new BooleanSetting("Pause On Use Item", true);
    public final BooleanSetting pauseOnKillAura = new BooleanSetting("Pause On KillAura", true);
    public final BooleanSetting pauseOnScaffold = new BooleanSetting("Pause On Scaffold", true);
    public final NumberSetting minDelay = new NumberSetting("Min Delay", 95, 0, 1000, 5);
    public final NumberSetting maxDelay = new NumberSetting("Max Delay", 135, 0, 1000, 5);

    public final ModeSetting offhandItem = new ModeSetting("Offhand Item", "None", "GoldenApple", "Projectile", "FishingRod", "Block").withDefault("None");
    public final BooleanSetting autoArmor = new BooleanSetting("Auto Armor", true);

    // Hotbar slot management: same configuration model as InventoryManager.
    // 0 = disabled; 1-9 = target hotbar slot. Duplicate enabled slots are rejected.
    private final NumberSetting swordSlotSetting = new NumberSetting("Sword Slot", 1, 0, 9, 1);
    private final NumberSetting blockSlotSetting = new NumberSetting("Block Slot", 2, 0, 9, 1);
    private final NumberSetting axeSlotSetting = new NumberSetting("Axe Slot", 4, 0, 9, 1);
    private final NumberSetting pickaxeSlotSetting = new NumberSetting("Pickaxe Slot", 0, 0, 9, 1);
    private final NumberSetting bowSlotSetting = new NumberSetting("Bow Slot", 3, 0, 9, 1);
    private final NumberSetting waterBucketSlotSetting = new NumberSetting("Water Bucket Slot", 5, 0, 9, 1);
    private final NumberSetting pearlSlotSetting = new NumberSetting("Ender Pearl Slot", 7, 0, 9, 1);
    private final NumberSetting goldenAppleSlotSetting = new NumberSetting("Golden Apple Slot", 6, 0, 9, 1);
    private final NumberSetting eggsSnowballsSlotSetting = new NumberSetting("Eggs & Snowballs Slot", 0, 0, 9, 1);
    private final NumberSetting slimeBallSlotSetting = new NumberSetting("Slime Ball Slot", 0, 0, 9, 1);
    private final NumberSetting crystalSlotSetting = new NumberSetting("Crystal Slot", 0, 0, 9, 1);

    // Everything below remains InvManager's original behavior/settings.
    public final NumberSetting maxBlockSize = new NumberSetting("Max Block Size", 256, 64, 512, 64);
    public final ModeSetting preferBow = new ModeSetting("Bow Priority", "Crossbow", "PowerBow", "PunchBow").withDefault("Crossbow");
    public final NumberSetting maxArrowSize = new NumberSetting("Max Arrow Size", 256, 64, 512, 64);

    public final BooleanSetting throwItems = new BooleanSetting("Throw Items", true);
    public final NumberSetting waterBucketCount = new NumberSetting("Keep Water Buckets", 1, 0, 5, 1, () -> throwItems.getValue());
    public final NumberSetting lavaBucketCount = new NumberSetting("Keep Lava Buckets", 1, 0, 5, 1, () -> throwItems.getValue());
    public final BooleanSetting keepProjectile = new BooleanSetting("Keep Eggs & Snowballs", true);
    public final NumberSetting maxProjectileSize = new NumberSetting("Max Eggs & Snowballs Size", 64, 16, 512, 16,
            () -> keepProjectile.getValue());

    public final BooleanSetting keepSpiderWeb = new BooleanSetting("Keep Spider Web", false);
    public final NumberSetting maxFoodSize = new NumberSetting("Max Food Size", 64, 1, 512, 1);

    private boolean clickOffHand;
    private boolean actionConsumed;
    private boolean waitingSprintStop;
    private int delayMs;
    private final TimerUtils timer = new TimerUtils();

    /** 最近一次点击动作的 tick，用于 Silent 模式强制间隔 */
    private int lastClickTick = -100;
    private int tickCounter = 0;

    public InvManager() {
        super("InvManager", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        clickOffHand = false;
        actionConsumed = false;
        waitingSprintStop = false;
        lastClickTick = -100;
        tickCounter = 0;
        resetDelay();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        clickOffHand = false;
        actionConsumed = false;
        waitingSprintStop = false;
        timer.reset();
        super.onDisable();
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() { return "InvManager"; }

    @Override
    public String getSuffix() { return mode.getValue(); }

    @Override
    public void onTick() { }

    private boolean validateSlotConfig() {
        List<Pair<Boolean, NumberSetting>> entries = new ArrayList<>();
        entries.add(Pair.of(this.swordSlotSetting.getValue().intValue() != 0, this.swordSlotSetting));
        entries.add(Pair.of(this.axeSlotSetting.getValue().intValue() != 0, this.axeSlotSetting));
        entries.add(Pair.of(this.pickaxeSlotSetting.getValue().intValue() != 0, this.pickaxeSlotSetting));
        entries.add(Pair.of(this.bowSlotSetting.getValue().intValue() != 0, this.bowSlotSetting));
        entries.add(Pair.of(this.waterBucketSlotSetting.getValue().intValue() != 0, this.waterBucketSlotSetting));
        entries.add(Pair.of(this.pearlSlotSetting.getValue().intValue() != 0, this.pearlSlotSetting));
        entries.add(Pair.of(this.slimeBallSlotSetting.getValue().intValue() != 0, this.slimeBallSlotSetting));
        entries.add(Pair.of(this.crystalSlotSetting.getValue().intValue() != 0, this.crystalSlotSetting));
        entries.add(Pair.of(this.eggsSnowballsSlotSetting.getValue().intValue() != 0, this.eggsSnowballsSlotSetting));
        if (!offhandItem.is("GoldenApple")) {
            entries.add(Pair.of(this.goldenAppleSlotSetting.getValue().intValue() != 0, this.goldenAppleSlotSetting));
        }
        if (!offhandItem.is("Block")) {
            entries.add(Pair.of(this.blockSlotSetting.getValue().intValue() != 0, this.blockSlotSetting));
        }

        HashSet<Integer> usedSlots = new HashSet<>();
        for (Pair<Boolean, NumberSetting> entry : entries) {
            if (!entry.getKey()) continue;
            int slot = entry.getValue().getValue().intValue() - 1;
            if (!usedSlots.add(slot)) return false;
        }
        return true;
    }

    @EventTarget
    public void onInventoryTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        actionConsumed = false;
        tickCounter++;

        if (!validateSlotConfig()) {
            this.setEnabled(false);
            return;
        }

        if (!canWork()) {
            waitingSprintStop = false;
            return;
        }

        if (mode.is("Silent") && waitingSprintStop) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
                return;
            }
            waitingSprintStop = false;
        }

        if (!timer.passedMillise(delayMs)) return;

        // ============================================================
        // Auto Armor：同保护等级 + 同附魔时，优先穿剩余耐久更高的
        // ============================================================
        if (autoArmor.getValue()) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (EquipmentSlot armorSlot : armorSlots) {
                if (processArmorSlot(armorSlot)) return;
            }
        }

        if (clickOffHand) {
            if (click(45)) clickOffHand = false;
            return;
        }

        if (manageOffhand()) return;

        // Hotbar slot assignments: InventoryManager-style slot management.
        if (!offhandItem.is("GoldenApple") && goldenAppleSlotSetting.getValue().intValue() != 0) {
            int target = goldenAppleSlotSetting.getValue().intValue() - 1;
            int egap = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gap = InvHelper.getItemSlot(Items.GOLDEN_APPLE);
            int best = egap != -1 ? egap : gap;
            if (best != -1) {
                ItemStack current = InvHelper.getInventoryStack(target);
                ItemStack bestStack = InvHelper.getInventoryStack(best);
                if (current.getItem() != bestStack.getItem()
                        || (current.getItem() == bestStack.getItem() && current.getCount() < bestStack.getCount())) {
                    if (swapItem(target, bestStack)) return;
                }
            }
        }

        if (blockSlotSetting.getValue().intValue() != 0 && !offhandItem.is("Block")) {
            int target = blockSlotSetting.getValue().intValue() - 1;
            ItemStack current = InvHelper.getInventoryStack(target);
            ItemStack best = InvHelper.getBestBlock();
            if (best != null && (best.getCount() > current.getCount() || !InvHelper.isValidStack(current))) {
                if (swapItem(target, best)) return;
            }
        }
        if (InvHelper.getBlockCountInInventory() > maxBlockSize.getValue().intValue()) {
            if (throwItem(InvHelper.getWorstBlock())) return;
        }

        if (swordSlotSetting.getValue().intValue() != 0) {
            int target = swordSlotSetting.getValue().intValue() - 1;
            ItemStack current = InvHelper.getInventoryStack(target);
            ItemStack best = InvHelper.getBestSword();
            ItemStack sharpAxe = InvHelper.getBestShapeAxe();
            if (InvHelper.getAxeDamage(sharpAxe) > InvHelper.getSwordDamage(best)) best = sharpAxe;
            if (best != null) {
                float currentDamage = InvHelper.isSword(current) ? InvHelper.getSwordDamage(current) : InvHelper.getAxeDamage(current);
                float bestDamage = InvHelper.isSword(best) ? InvHelper.getSwordDamage(best) : InvHelper.getAxeDamage(best);
                if (bestDamage > currentDamage && swapItem(target, best)) return;
            }
        }

        if (pickaxeSlotSetting.getValue().intValue() != 0) {
            int target = pickaxeSlotSetting.getValue().intValue() - 1;
            ItemStack best = InvHelper.getBestPickaxe();
            ItemStack current = InvHelper.getInventoryStack(target);
            if (InvHelper.isPickaxe(best)
                    && (InvHelper.getToolScore(best) > InvHelper.getToolScore(current) || !InvHelper.isPickaxe(current))) {
                if (swapItem(target, best)) return;
            }
        }

        if (axeSlotSetting.getValue().intValue() != 0) {
            int target = axeSlotSetting.getValue().intValue() - 1;
            ItemStack best = InvHelper.getBestAxe();
            ItemStack current = InvHelper.getInventoryStack(target);
            if (best != null && InvHelper.isAxe(best)
                    && (InvHelper.getToolScore(best) > InvHelper.getToolScore(current) || !InvHelper.isAxe(current))) {
                if (swapItem(target, best)) return;
            }
        }

        if (bowSlotSetting.getValue().intValue() != 0) {
            int target = bowSlotSetting.getValue().intValue() - 1;
            ItemStack current = InvHelper.getInventoryStack(target);
            ItemStack best = getPreferredBow();
            if (best != null && bowScore(best) > bowScore(current)) {
                if (swapItem(target, best)) return;
            }
            if (InvHelper.getItemCount(Items.ARROW) > maxArrowSize.getValue().intValue()) {
                if (throwItem(InvHelper.getWorstArrow())) return;
            }
        }

        if (eggsSnowballsSlotSetting.getValue().intValue() != 0 && !offhandItem.is("Projectile")) {
            int target = eggsSnowballsSlotSetting.getValue().intValue() - 1;
            ItemStack bestProjectile = InvHelper.getBestProjectile();
            if (bestProjectile != null && swapItem(target, bestProjectile)) return;
        }
        if (keepProjectile.getValue()
                && InvHelper.getItemCount(Items.EGG) + InvHelper.getItemCount(Items.SNOWBALL) > maxProjectileSize.getValue().intValue()) {
            if (throwItem(InvHelper.getWorstProjectile())) return;
        }

        if (pearlSlotSetting.getValue().intValue() != 0
                && swapItem(pearlSlotSetting.getValue().intValue() - 1, Items.ENDER_PEARL)) return;
        if (waterBucketSlotSetting.getValue().intValue() != 0
                && swapItem(waterBucketSlotSetting.getValue().intValue() - 1, Items.WATER_BUCKET)) return;
        if (slimeBallSlotSetting.getValue().intValue() != 0
                && swapItem(slimeBallSlotSetting.getValue().intValue() - 1, Items.SLIME_BALL)) return;
        if (crystalSlotSetting.getValue().intValue() != 0
                && swapItem(crystalSlotSetting.getValue().intValue() - 1, Items.END_CRYSTAL)) return;

        if (throwItems.getValue()) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < 36; i++) slots.add(i);
            Collections.shuffle(slots);
            for (int slot : slots) {
                ItemStack stack = InvHelper.getInventoryStack(slot);
                if (!stack.isEmpty() && !isItemUseful(stack) && throwItem(stack)) return;
            }
        }
    }

    // ================================================================
    // Auto Armor
    // ================================================================

    private boolean processArmorSlot(EquipmentSlot armorSlot) {
        ItemStack equipped = InvHelper.getArmorStack(armorSlot);
        boolean hasEquipped = !equipped.isEmpty() && InvHelper.isArmor(equipped);

        float equippedScore = hasEquipped ? InvHelper.getProtection(equipped) : -1.0f;
        int equippedDurability = hasEquipped ? getRemainingDurability(equipped) : -1;

        float bestScore = InvHelper.getBestArmorScore(armorSlot);

        ItemStack bestCandidate = null;
        int bestCandidateSlot = -1;
        int bestCandidateDurability = -1;

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = InvHelper.getInventoryStack(slot);
            if (stack.isEmpty() || !InvHelper.isArmor(stack)) continue;
            if (InvHelper.getArmorSlot(stack) != armorSlot) continue;

            float score = InvHelper.getProtection(stack);
            if (score < bestScore) continue;

            int durability = getRemainingDurability(stack);
            if (bestCandidate == null || durability > bestCandidateDurability) {
                bestCandidate = stack;
                bestCandidateSlot = slot;
                bestCandidateDurability = durability;
            }
        }

        if (bestCandidate == null || bestCandidateSlot < 0) {
            return false;
        }

        boolean needReplace;
        if (!hasEquipped) {
            needReplace = true;
        } else if (equippedScore < bestScore) {
            needReplace = true;
        } else if (equippedScore == bestScore) {
            needReplace = bestCandidateDurability > equippedDurability;
        } else {
            needReplace = false;
        }

        if (!needReplace) {
            return false;
        }

        // 身上没装备：直接穿
        if (!hasEquipped) {
            return shiftClick(toMenuSlot(bestCandidateSlot));
        }

        // 身上有装备，需要替换
        // 背包有空位：shiftClick 盔甲栏把旧装备换回背包
        if (hasEmptyInventorySlot()) {
            if (shiftClick(armorMenuSlot(armorSlot))) return true;
        }

        // 背包满：先丢一件杂物腾位
        if (dropOneJunkItem()) {
            return true;
        }

        // 无杂物可丢：保底直接丢弃旧装备
        return dropAll(armorMenuSlot(armorSlot));
    }

    private boolean hasEmptyInventorySlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = InvHelper.getInventoryStack(i);
            if (stack == null || stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean dropOneJunkItem() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) slots.add(i);
        Collections.shuffle(slots);

        for (int slot : slots) {
            ItemStack stack = InvHelper.getInventoryStack(slot);
            if (stack.isEmpty()) continue;
            if (isItemUseful(stack)) continue;
            return dropAll(toMenuSlot(slot));
        }
        return false;
    }

    private static int getRemainingDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return Integer.MAX_VALUE;
        }
        int damage = stack.getDamageValue();
        return Math.max(0, maxDamage - damage);
    }

    private boolean isUsingItem() {
        return mc.player != null && mc.player.isUsingItem();
    }

    /**
     * ★ Anti-BadPackets0 关键检查：
     *   1) 玩家自己的 containerMenu 必须是 inventoryMenu
     *      （一旦打开过箱子/工作台，inventoryMenu.containerId 就会变，
     *       此时再对 inventoryMenu 发包，服务器会判定为窗口 ID 不匹配）
     *   2) 玩家不能打开任何 GUI（聊天、箱子、工作台等）
     *      —— 除非是 Inventory 模式且打开的是自己的背包
     */
    private boolean canWork() {
        if (InvHelper.shouldDisableFeatures()) return false;
        if (pauseOnUseItem.getValue() && isUsingItem()) return false;
        if (pauseOnEat.getValue() && PlayerUtils.isEating()) return false;
        if (pauseOnKillAura.getValue() && KillAura.INSTANCE != null && KillAura.INSTANCE.isEnabled() && KillAura.target != null) return false;
        if (pauseOnScaffold.getValue() && Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) return false;

        if (mc.player == null || mc.player.inventoryMenu == null) return false;
        if (mc.player.containerMenu == null) return false;

        // ============================================================
        // ★ 关键：当前操作的窗口必须就是玩家自己的背包窗口
        //   如果 containerMenu 不是 inventoryMenu，说明玩家打开了别的容器，
        //   此时再发窗口点击包会让服务器判定为窗口 ID 不匹配 → BadPackets0
        // ============================================================
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            return false;
        }

        // Inventory 模式必须实际打开背包界面
        if (mode.is("Inventory") && !(mc.screen instanceof InventoryScreen)) return false;

        // Silent 模式：除了背包界面以外，任何 GUI 打开都停手
        if (mode.is("Silent") && mc.screen != null) {
            return false;
        }

        return true;
    }

    private boolean manageOffhand() {
        if (offhandItem.is("None")) return false;
        ItemStack offhand = InvHelper.getOffhandStack();

        if (offhandItem.is("GoldenApple")) {
            int egap = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gap = InvHelper.getItemSlot(Items.GOLDEN_APPLE);
            if (offhand.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return false;
            int source = egap != -1 ? egap : gap;
            return source != -1 && swapOffHand(source);
        }
        if (offhandItem.is("Projectile")) {
            ItemStack best = InvHelper.getBestProjectile();
            if (best == null) return false;
            boolean replace = (offhand.getItem() != Items.EGG && offhand.getItem() != Items.SNOWBALL) || offhand.getCount() < best.getCount();
            return replace && swapOffHand(InvHelper.getItemStackSlot(best));
        }
        if (offhandItem.is("FishingRod")) {
            int source = InvHelper.getItemSlot(Items.FISHING_ROD);
            return source != -1 && offhand.getItem() != Items.FISHING_ROD && swapOffHand(source);
        }
        if (offhandItem.is("Block")) {
            ItemStack best = InvHelper.getBestBlock();
            if (best == null) return false;
            boolean replace = !InvHelper.isValidStack(offhand) || offhand.getCount() < best.getCount();
            return replace && swapOffHand(InvHelper.getItemStackSlot(best));
        }
        return false;
    }

    private ItemStack getPreferredBow() {
        ItemStack best;
        if (preferBow.is("PowerBow")) best = InvHelper.getBestPowerBow();
        else if (preferBow.is("PunchBow")) best = InvHelper.getBestPunchBow();
        else best = InvHelper.getBestCrossbow();
        if (best == null) best = InvHelper.getBestCrossbow();
        if (best == null) best = InvHelper.getBestPowerBow();
        if (best == null) best = InvHelper.getBestPunchBow();
        return best;
    }

    private float bowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0f;
        if (preferBow.is("PowerBow")) return InvHelper.getPowerBowScore(stack);
        if (preferBow.is("PunchBow")) return InvHelper.getPunchBowScore(stack);
        return InvHelper.getCrossbowScore(stack);
    }

    private boolean isItemUseful(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (isMace(stack)) return true;

        if (stack.getItem() == Items.COBWEB) return keepSpiderWeb.getValue();

        if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof SlabBlock
                    || blockItem.getBlock() instanceof StairBlock) {
                return false;
            }
        }

        if (stack.getItem().getFoodProperties() != null
                && InvHelper.getItemCount(stack.getItem()) > maxFoodSize.getValue().intValue()) {
            return false;
        }

        if (InvHelper.isGodItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;
        if (InvHelper.isArmor(stack)) {
            EquipmentSlot slot = InvHelper.getArmorSlot(stack);
            float protection = InvHelper.getProtection(stack);
            if (slot == null || InvHelper.getCurrentArmorScore(slot) >= protection) return false;
            return protection >= InvHelper.getBestArmorScore(slot);
        }
        if (InvHelper.isSword(stack)) return InvHelper.getBestSword() == stack;
        if (InvHelper.isPickaxe(stack)) return InvHelper.getBestPickaxe() == stack;
        if (InvHelper.isAxe(stack) && !InvHelper.isSharpnessAxe(stack)) return InvHelper.getBestAxe() == stack;
        if (InvHelper.isShovel(stack)) return InvHelper.getBestShovel() == stack;
        if (stack.getItem() instanceof CrossbowItem) return InvHelper.getBestCrossbow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.isPunchBow(stack)) return InvHelper.getBestPunchBow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.isPowerBow(stack)) return InvHelper.getBestPowerBow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.getItemCount(Items.BOW) > 1) return false;
        if (stack.getItem() == Items.WATER_BUCKET && InvHelper.getItemCount(Items.WATER_BUCKET) > waterBucketCount.getValue().intValue()) return false;
        if (stack.getItem() == Items.LAVA_BUCKET && InvHelper.getItemCount(Items.LAVA_BUCKET) > lavaBucketCount.getValue().intValue()) return false;
        if (stack.getItem() instanceof FishingRodItem && InvHelper.getItemCount(Items.FISHING_ROD) > 1) return false;
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !keepProjectile.getValue()) return false;
        if (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return true;
        return !(stack.getItem() instanceof StandingAndWallBlockItem) && InvHelper.isCommonItemUseful(stack);
    }

    private boolean swapOffHand(int inventorySlot) {
        if (inventorySlot < 0) return false;
        return swap(toMenuSlot(inventorySlot), 40);
    }

    private boolean throwItem(ItemStack item) {
        if (item == null || !InvHelper.isItemValid(item)) return false;
        int slot = InvHelper.getItemStackSlot(item);
        return slot >= 0 && slot < 36 && dropAll(toMenuSlot(slot));
    }

    private boolean swapItem(int targetHotbarSlot, ItemStack bestItem) {
        if (bestItem == null || bestItem.isEmpty()) return false;
        ItemStack current = InvHelper.getInventoryStack(targetHotbarSlot);
        if (!InvHelper.isItemValid(current) || bestItem == current) return false;
        int source = InvHelper.getItemStackSlot(bestItem);
        return source >= 0 && source < 36 && source != targetHotbarSlot && swap(toMenuSlot(source), targetHotbarSlot);
    }

    private boolean swapItem(int targetHotbarSlot, Item item) {
        ItemStack current = InvHelper.getInventoryStack(targetHotbarSlot);
        if (!InvHelper.isItemValid(current)) return false;
        int source = InvHelper.getItemSlot(item);
        if (source < 0 || source == targetHotbarSlot) return false;
        ItemStack sourceStack = InvHelper.getInventoryStack(source);
        if (current.getItem() == item && current.getCount() >= sourceStack.getCount()) return false;
        return swap(toMenuSlot(source), targetHotbarSlot);
    }

    private boolean prepareInventoryAction() {
        if (!mode.is("Silent")) return true;

        // ★ Silent 模式：两次点击之间强制 >= SILENT_MIN_TICK_GAP tick
        if (tickCounter - lastClickTick < SILENT_MIN_TICK_GAP) {
            return false;
        }

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            waitingSprintStop = true;
            return false;
        }
        return !waitingSprintStop;
    }

    private void completeInventoryAction() {
        timer.reset();
        delayMs = MathUtils.getRandom(minDelay.getValue().intValue(), maxDelay.getValue().intValue());
        actionConsumed = true;
        lastClickTick = tickCounter;
    }

    private boolean canExecuteInventoryAction() {
        return !actionConsumed && timer.passedMillise(delayMs);
    }

    private boolean click(int slot) {
        if (!canExecuteInventoryAction() || !prepareInventoryAction()) return false;
        ClickSlotUtils.click(slot);
        completeInventoryAction();
        return true;
    }

    private boolean swap(int slot, int hotbarSlot) {
        if (!canExecuteInventoryAction() || !prepareInventoryAction()) return false;
        ClickSlotUtils.swap(slot, hotbarSlot);
        completeInventoryAction();
        return true;
    }

    private boolean shiftClick(int slot) {
        if (!canExecuteInventoryAction() || !prepareInventoryAction()) return false;
        ClickSlotUtils.shiftClick(slot);
        completeInventoryAction();
        return true;
    }

    private boolean dropAll(int slot) {
        if (!canExecuteInventoryAction() || !prepareInventoryAction()) return false;
        ClickSlotUtils.dropAll(slot);
        completeInventoryAction();
        return true;
    }

    private void resetDelay() {
        int min = minDelay.getValue().intValue();
        int max = maxDelay.getValue().intValue();
        if (min > max) { int t = min; min = max; max = t; }
        delayMs = MathUtils.getRandom(min, max);
        timer.reset();
    }

    private static int toMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private static int armorMenuSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> -1;
        };
    }

    private boolean isMace(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String descriptionId = stack.getItem().getDescriptionId();
        if (descriptionId != null
                && descriptionId.toLowerCase().contains("mace")) {
            return true;
        }

        String displayName = stack.getHoverName().getString();
        if (displayName == null) {
            return false;
        }

        String name = displayName.toLowerCase();
        return name.contains("mace")
                || name.contains("重锤")
                || name.contains("锤");
    }

}