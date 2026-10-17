package client.nilore.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.SlowdownEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.exploit.FastPlace;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.misc.PacketUtil;

public class NoSlow extends Module {
    public static NoSlow INSTANCE;
    public static boolean releaseItemSent;

    public static boolean isActive() {
        if (INSTANCE == null) return false;
        return INSTANCE.step != Step.NONE
                || INSTANCE.bowActive
                || INSTANCE.foodDelay
                || INSTANCE.foodSwapping
                || INSTANCE.didSwapHand
                || INSTANCE.releaseTicksRemaining > 0;
    }

    public static boolean isHandling() {
        NoSlow noSlow = INSTANCE;
        return noSlow != null
                && (noSlow.step != Step.NONE || noSlow.bowActive || noSlow.foodDelay);
    }

    public static boolean isProcessing() {
        return INSTANCE != null && INSTANCE.step != Step.NONE;
    }

    // ==================== 状态枚举 ====================
    private enum Step { NONE, ARMED, EATING }

    private static final int FOOD_SWAP_BACK_TIMEOUT = 20;

    // ==================== 主模式选择 ====================
    public final ModeSetting mode = new ModeSetting("Mode", "None", "Vanilla", "GrimAC", "Grim", "LeakGrim", "Heypixel", "GrimBlink", "Grim1/2", "Grim1/3").withDefault("GrimAC");

    // ==================== Vanilla / GrimBlink 设置 ====================
    public final BooleanSetting vanillaFood = new BooleanSetting("Food", true, () -> mode.is("Vanilla") || mode.is("GrimBlink"));
    public final BooleanSetting vanillaBow = new BooleanSetting("Bow", true, () -> mode.is("Vanilla") || mode.is("GrimBlink"));
    public final BooleanSetting vanillaCrossbow = new BooleanSetting("Crossbow", true, () -> mode.is("Vanilla") || mode.is("GrimBlink"));
    public final BooleanSetting vanillaCobweb = new BooleanSetting("Cobweb", true, () -> mode.is("Vanilla"));
    private final Queue<Packet<?>> noSlowPackets = new java.util.concurrent.LinkedBlockingQueue<>();
    private int noSlowTicks;
    private boolean noSlowEating;

    // ==================== GrimAC 模式专用设置 ====================
    public final BooleanSetting grimACBowNoSlow = new BooleanSetting("Bow", false, this::isGrimACMode);
    public final BooleanSetting grimACKeepSprinting = new BooleanSetting("Keep Sprinting", true, this::isGrimACMode);
    public final BooleanSetting grimACCrossbowNoSlow = new BooleanSetting("Crossbow", false, this::isGrimACMode);
    public final BooleanSetting grimACFoodNoSlow = new BooleanSetting("Food", true, this::isGrimACMode);
    public final BooleanSetting grimACPotionNoSlow = new BooleanSetting("Potion", true, this::isGrimACMode);
    public final BooleanSetting grimACShieldNoSlow = new BooleanSetting("Shield NoSlow", true, this::isGrimACMode);
    public final NumberSetting grimACUseItemTicks = new NumberSetting("Use Item Ticks", 1, 1, 20, 1,
            () -> this.isGrimACMode() && this.grimACBowNoSlow.getValue());

    // ==================== Grim 模式专用设置 ====================
    public final BooleanSetting grimBowNoSlow = new BooleanSetting("Bow", false, this::isGrimMode);
    public final BooleanSetting grimKeepSprinting = new BooleanSetting("Keep Sprinting", true, this::isGrimMode);
    public final BooleanSetting grimCrossbowNoSlow = new BooleanSetting("Crossbow", false, this::isGrimMode);
    public final BooleanSetting grimFoodNoSlow = new BooleanSetting("Food", true, this::isGrimMode);
    public final BooleanSetting grimPotionNoSlow = new BooleanSetting("Potion", true, this::isGrimMode);
    public final BooleanSetting grimShieldNoSlow = new BooleanSetting("Shield NoSlow", true, this::isGrimMode);
    public final NumberSetting grimUseItemTicks = new NumberSetting("Use Item Ticks", 1, 1, 20, 1,
            () -> this.isGrimMode() && this.grimBowNoSlow.getValue());

    // ==================== LeakGrim 模式专用设置 ====================
    public final BooleanSetting leakBlockSetting = new BooleanSetting("Block", false, this::isLeakGrimMode);
    public final BooleanSetting leakFoodSetting = new BooleanSetting("Food", true, this::isLeakGrimMode);
    public final BooleanSetting leakBowSetting = new BooleanSetting("Bow", false, this::isLeakGrimMode);
    public final BooleanSetting leakKeepSprinting = new BooleanSetting("Keep Sprinting", true, this::isLeakGrimMode);
    public final NumberSetting leakBowTick = new NumberSetting("BowTick", 20, 1, 20, 1, this::isLeakGrimMode);
    public final NumberSetting leakEatTick = new NumberSetting("EatTick", 32, 1, 32, 1, this::isLeakGrimMode);

    // ==================== Heypixel 模式专用设置 ====================
    // 四个开关只作为"该类型是否走 Heypixel NoSlow"的过滤，
    // 行为完全对齐第二个文件的简化逻辑。
    public final BooleanSetting heypixelBowNoSlow =
            new BooleanSetting("Bow", true, this::isHeypixelMode);
    public final BooleanSetting heypixelCrossbowNoSlow =
            new BooleanSetting("Crossbow", true, this::isHeypixelMode);
    public final BooleanSetting heypixelFoodNoSlow =
            new BooleanSetting("Food", true, this::isHeypixelMode);
    public final BooleanSetting heypixelPotionNoSlow =
            new BooleanSetting("Potion", true, this::isHeypixelMode);

    // ==================== 状态变量 ====================
    private final Timer timer = new Timer();
    private final Queue<Packet<ClientGamePacketListener>> inboundQueue = new ArrayDeque<>();
    private final Queue<Packet<?>> cached = new ConcurrentLinkedQueue<>();

    // LeakGrim 模式专用状态
    private InteractionHand leakCurrentHand = InteractionHand.MAIN_HAND;
    private InteractionHand leakOppositeHand = InteractionHand.MAIN_HAND;
    private boolean leakIsBlinking = false;
    private boolean leakNeedsRelease = false;
    private boolean leakSendingPackets = false;
    private boolean leakPendingUse = false;
    private boolean leakJustUsed = false;
    private InteractionHand leakPendingHand = InteractionHand.MAIN_HAND;
    private int leakUseSequence = 0;
    private int leakCooldownTicks = 0;
    private int leakTicksSinceSwap = 0;
    private long leakLastUseTime = 0;
    private int leakDelayedPacketsCount = 0;
    private int leakDelayedPacketsMax = 0;
    private boolean leakDelayingPackets = false;
    private final Timer leakTimer = new Timer();

    // GrimAC / Grim / Heypixel 共享状态
    private InteractionHand useHand = InteractionHand.MAIN_HAND;
    private InteractionHand lastUseHand = InteractionHand.MAIN_HAND;
    private InteractionHand pendingUseHand;
    private boolean didSwapHand;
    private boolean shouldReleaseItem;
    private int swapInitSlot;
    private int releaseTicksRemaining;
    private int pendingUseCount;
    private boolean isBlinking;
    private int blinkTicks;
    private int blinkDuration;
    private Step step = Step.NONE;
    private boolean hasSwapped = false;
    private boolean swapInArmed = false;
    private int noUseTicks = 0;

    // GrimAC-only first-person hand alias
    private boolean grimACVisualLock = false;
    private ItemStack grimACVisualMain = ItemStack.EMPTY;
    private ItemStack grimACVisualOff = ItemStack.EMPTY;
    private InteractionHand grimACVisualUseHand = InteractionHand.MAIN_HAND;
    private int grimACVisualStableTicks = 0;

    // ==================== Heypixel 专用字段 ====================
    private boolean bowActive;
    private boolean bowDelay;
    private boolean foodDelay;
    private boolean foodSwapping;
    private boolean useSuppressed;
    private boolean forwardReleased;
    private int forwardRestoreTicks;

    // ==================== GrimBlink 模式字段 ====================
    private int grimBlinkTicks;
    private boolean grimBlinkEating;
    private int grimBlinkUseDuration = 32;
    private final Queue<Packet<?>> grimBlinkPackets = new java.util.concurrent.LinkedBlockingQueue<>();

    public NoSlow() {
        super("NoSlow", Category.MOVEMENT);
        INSTANCE = this;
    }

    // ================================================================
    // 生命周期
    // ================================================================

    @Override
    public void onEnable() {
        noSlowPackets.clear();
        noSlowEating = false;
        noSlowTicks = 0;
        releaseItemSent = false;
        this.releaseTicksRemaining = 0;
        this.reset();
        this.stopBlink();
        this.leakReset();
        this.heypixelReset();
        this.clearGrimACVisualLock();
        this.grimBlinkReset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (this.mode.is("LeakGrim")) {
            this.leakReleaseItem();
        }
        if (this.isHeypixelMode()) {
            this.heypixelRelease();
        }
        if (this.isGrimBlinkMode()) {
            this.grimBlinkFlush();
            this.grimBlinkEating = false;
            this.grimBlinkTicks = 0;
            this.grimBlinkUseDuration = 32;
        }
        this.release();
        this.stopBlink();
        noSlowPackets.clear();
        noSlowEating = false;
        noSlowTicks = 0;
        releaseItemSent = false;
        this.didSwapHand = false;
        this.shouldReleaseItem = false;
        this.pendingUseHand = null;
        this.releaseTicksRemaining = 0;
        this.bowActive = false;
        this.bowDelay = false;
        this.foodDelay = false;
        this.foodSwapping = false;
        this.useSuppressed = false;
        this.forwardReleased = false;
        this.forwardRestoreTicks = 0;
        this.restoreForwardKeyState();
        this.restoreUseKeyState();
        this.clearGrimACVisualLock();
        super.onDisable();
    }

    // ================================================================
    // 显示名称
    // ================================================================

    @Override
    public String getModuleName() {
        return "NoSlow";
    }

    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) return null;
        return "[" + modeName + "]";
    }

    @Override
    public void onTick() {
    }

    @Override
    public String getDisplayName() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fNoSlow";
        }
        return "§fNoSlow[" + modeName + "]";
    }

    // ================================================================
    // 模式判断
    // ================================================================

    private boolean isGrimACMode() { return this.mode.is("GrimAC"); }
    private boolean isGrimMode() { return this.mode.is("Grim"); }
    private boolean isLeakGrimMode() { return this.mode.is("LeakGrim"); }
    private boolean isHeypixelMode() { return this.mode.is("Heypixel"); }
    private boolean isGrimBlinkMode() { return this.mode.is("GrimBlink"); }
    private boolean isGrim1_2Mode() { return this.mode.is("Grim1/2"); }
    private boolean isGrim1_3Mode() { return this.mode.is("Grim1/3"); }

    // ==================== GrimAC-only first-person render API ====================

    public static boolean isGrimACVisualLockActive() {
        return INSTANCE != null
                && INSTANCE.isEnabled()
                && INSTANCE.isGrimACMode()
                && INSTANCE.grimACVisualLock;
    }

    public static ItemStack getGrimACVisualMainHand() {
        return INSTANCE != null ? INSTANCE.grimACVisualMain : ItemStack.EMPTY;
    }

    public static ItemStack getGrimACVisualOffHand() {
        return INSTANCE != null ? INSTANCE.grimACVisualOff : ItemStack.EMPTY;
    }

    public static InteractionHand getGrimACVisualUseHand() {
        return INSTANCE != null
                ? INSTANCE.grimACVisualUseHand
                : InteractionHand.MAIN_HAND;
    }

    private void beginGrimACVisualLock(InteractionHand originalHand) {
        if (!this.isGrimACMode() || mc.player == null) {
            return;
        }
        if (this.grimACVisualLock) {
            return;
        }
        this.grimACVisualMain = mc.player.getMainHandItem().copy();
        this.grimACVisualOff = mc.player.getOffhandItem().copy();
        this.grimACVisualUseHand = originalHand;
        this.grimACVisualStableTicks = 0;
        this.grimACVisualLock = true;
    }

    private void clearGrimACVisualLock() {
        this.grimACVisualLock = false;
        this.grimACVisualMain = ItemStack.EMPTY;
        this.grimACVisualOff = ItemStack.EMPTY;
        this.grimACVisualUseHand = InteractionHand.MAIN_HAND;
        this.grimACVisualStableTicks = 0;
    }

    private void tickGrimACVisualLock() {
        if (!this.grimACVisualLock) {
            return;
        }
        if (!this.isGrimACMode() || mc.player == null) {
            this.clearGrimACVisualLock();
            return;
        }
        if (this.step != Step.NONE || this.hasSwapped || this.swapInArmed) {
            this.grimACVisualStableTicks = 0;
            return;
        }
        boolean restored =
                ItemStack.matches(mc.player.getMainHandItem(), this.grimACVisualMain)
                        && ItemStack.matches(mc.player.getOffhandItem(), this.grimACVisualOff);
        if (restored) {
            if (++this.grimACVisualStableTicks >= 2) {
                this.clearGrimACVisualLock();
            }
        } else {
            this.grimACVisualStableTicks = 0;
        }
    }

    private boolean areAllOff() {
        if (this.isHeypixelMode()) {
            return !this.heypixelBowNoSlow.getValue()
                    && !this.heypixelCrossbowNoSlow.getValue()
                    && !this.heypixelFoodNoSlow.getValue()
                    && !this.heypixelPotionNoSlow.getValue();
        }
        if (this.isGrimACMode()) {
            return !this.grimACBowNoSlow.getValue() && !this.grimACKeepSprinting.getValue()
                    && !this.grimACCrossbowNoSlow.getValue() && !this.grimACFoodNoSlow.getValue()
                    && !this.grimACPotionNoSlow.getValue() && !this.grimACShieldNoSlow.getValue();
        }
        if (this.isGrimMode()) {
            return !this.grimBowNoSlow.getValue() && !this.grimKeepSprinting.getValue()
                    && !this.grimCrossbowNoSlow.getValue() && !this.grimFoodNoSlow.getValue()
                    && !this.grimPotionNoSlow.getValue() && !this.grimShieldNoSlow.getValue();
        }
        if (this.isLeakGrimMode()) {
            return !this.leakBowSetting.getValue() && !this.leakFoodSetting.getValue() && !this.leakBlockSetting.getValue();
        }
        if (this.isGrimBlinkMode()) {
            return !this.vanillaFood.getValue() && !this.vanillaBow.getValue()
                    && !this.vanillaCrossbow.getValue();
        }
        if (this.isGrim1_2Mode() || this.isGrim1_3Mode()) {
            return false;
        }
        return true;
    }

    // ================================================================
    // GrimBlink 模式核心逻辑
    // ================================================================

    private void grimBlinkReset() {
        this.grimBlinkTicks = 0;
        this.grimBlinkEating = false;
        this.grimBlinkUseDuration = 32;
        this.grimBlinkPackets.clear();
    }

    private void grimBlinkFlush() {
        if (mc.getConnection() == null) {
            this.grimBlinkPackets.clear();
            return;
        }
        Packet<?> packet;
        while ((packet = this.grimBlinkPackets.poll()) != null) {
            try {
                @SuppressWarnings("unchecked")
                Packet<ClientGamePacketListener> typed = (Packet<ClientGamePacketListener>) packet;
                typed.handle(mc.getConnection());
            } catch (Exception e) {
                this.grimBlinkPackets.clear();
                return;
            }
        }
    }

    private void grimBlinkTick() {
        if (mc.player == null) {
            this.grimBlinkFlush();
            this.grimBlinkEating = false;
            this.grimBlinkTicks = 0;
            return;
        }
        if (this.grimBlinkEating) {
            this.grimBlinkTicks++;
            long pingCount = this.grimBlinkPackets.stream()
                    .filter(packet -> packet instanceof ClientboundPingPacket)
                    .count();
            if (pingCount > 150 && this.grimBlinkTicks > 1) {
                mc.options.keyUse.setDown(false);
                this.grimBlinkFlush();
                this.grimBlinkEating = false;
                this.grimBlinkTicks = 0;
                return;
            }
        }
        if (mc.player.isUsingItem()
                && isFoodOrDrink(mc.player.getUseItem())
                && !this.grimBlinkEating) {
            this.grimBlinkEating = true;
            this.grimBlinkTicks = 0;
            ItemStack useItem = mc.player.getUseItem();
            this.grimBlinkUseDuration = useItem.getItem().getUseDuration(useItem);
        }
        if (this.grimBlinkEating) {
            if (this.grimBlinkTicks == 1) {
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO, Direction.DOWN));
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                        BlockPos.ZERO, Direction.DOWN));
            }
            if (this.grimBlinkTicks == 2) {
                InteractionHand hand = mc.player.getUsedItemHand() == InteractionHand.OFF_HAND
                        ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                if (mc.getConnection() != null) {
                    if (mc.level != null) {
                        mc.getConnection().send(new ServerboundUseItemPacket(hand, 0));
                    }
                }
            }
            if (this.grimBlinkTicks > this.grimBlinkUseDuration + 3) {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                            BlockPos.ZERO, Direction.DOWN));
                }
                mc.options.keyUse.setDown(false);
                this.grimBlinkTicks = 0;
                this.grimBlinkEating = false;
            }
        }
    }

    private boolean grimBlinkShouldQueue(Packet<?> packet) {
        if (!this.grimBlinkEating || !this.isGrimBlinkMode()) return false;
        if (mc.player == null || mc.player.tickCount < 30) return false;

        if (packet instanceof ClientboundPlayerPositionPacket) {
            this.grimBlinkFlush();
            return false;
        }
        if (packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundEntityEventPacket
                || packet instanceof ClientboundAddEntityPacket
                || packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundBlockEventPacket) {
            return false;
        }
        return true;
    }

    private boolean isFoodOrDrink(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    // ================================================================
    // LeakGrim 模式核心逻辑
    // ================================================================

    private boolean leakHasDualItems() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        if (mainHand.isEmpty() || offHand.isEmpty()) return false;
        return mainHand.getItem() == offHand.getItem() ||
                (leakIsSlowingItem(mainHand.getUseAnimation()) && leakIsSlowingItem(offHand.getUseAnimation()));
    }

    private boolean leakIsSlowingItem(UseAnim anim) {
        return anim == UseAnim.EAT || anim == UseAnim.DRINK || anim == UseAnim.BOW;
    }

    private boolean leakIsProjectileItem(UseAnim anim) {
        return anim == UseAnim.BOW || anim == UseAnim.CROSSBOW || anim == UseAnim.SPEAR;
    }

    private boolean leakIsCrossbowCharged(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof CrossbowItem)) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("Charged");
    }

    private void leakPerformGrimSwap(InteractionHand hand, int sequence) {
        if (mc.player == null) return;
        leakIsBlinking = true;
        leakNeedsRelease = false;
        leakOppositeHand = hand;
        leakTicksSinceSwap = mc.player.getInventory().selected;
        leakCurrentHand = leakOppositeHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO, Direction.DOWN));
        PacketUtil.sendQueued(new ServerboundUseItemPacket(leakCurrentHand, sequence));

        leakDelayedPacketsMax = ((Number) leakEatTick.getValue()).intValue();
        leakDelayedPacketsCount = 0;
        leakDelayingPackets = true;
    }

    private void leakReleaseItem() {
        leakNeedsRelease = false;
        if (mc.player == null || mc.getConnection() == null) return;

        leakDelayingPackets = false;
        mc.options.keyUse.setDown(false);

        if (leakIsBlinking) {
            if (leakCurrentHand != leakOppositeHand) {
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO, Direction.DOWN));
            }
            leakIsBlinking = false;
            leakCooldownTicks = 3;
            leakTimer.reset();
        }
        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO, Direction.DOWN));
    }

    private void leakSchedulePendingUse(InteractionHand hand, int sequence) {
        leakPendingUse = true;
        leakPendingHand = hand;
        leakUseSequence = sequence;
    }

    private void leakScheduleRelease() {
        leakSendingPackets = true;
    }

    private void leakClearUseKey() {
        mc.options.keyUse.setDown(false);
        while (mc.options.keyUse.consumeClick()) {
        }
    }

    private void leakReset() {
        leakIsBlinking = false;
        leakNeedsRelease = false;
        leakSendingPackets = false;
        leakPendingUse = false;
        leakJustUsed = false;
        leakCooldownTicks = 0;
        leakDelayingPackets = false;
        leakDelayedPacketsCount = 0;
        leakCurrentHand = InteractionHand.MAIN_HAND;
        leakOppositeHand = InteractionHand.MAIN_HAND;
        leakPendingHand = InteractionHand.MAIN_HAND;
        leakTimer.reset();
        mc.options.keyUse.setDown(false);
    }

    private void leakTick() {
        if (mc.player == null) return;

        if (leakCooldownTicks > 0) {
            leakClearUseKey();
            leakCooldownTicks--;
        }

        int maxTicks = leakIsProjectileItem(mc.player.getUseItem().getUseAnimation()) ?
                ((Number) leakBowTick.getValue()).intValue() :
                ((Number) leakEatTick.getValue()).intValue();

        if (leakDelayingPackets) {
            leakDelayedPacketsCount++;
            if (leakDelayedPacketsCount >= maxTicks) {
                leakScheduleRelease();
                leakDelayingPackets = false;
            }
        }

        if (leakNeedsRelease && mc.player.isUsingItem() && !leakHasDualItems() &&
                !leakIsCrossbowCharged(mc.player.getUseItem())) {
            leakPerformGrimSwap(mc.player.getUsedItemHand(), 0);
        }
    }

    // ================================================================
    // 原方法
    // ================================================================

    private boolean canSwapHands() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        if (mainHand.isEmpty() || offHand.isEmpty()) return true;
        if (mainHand.getItem() == Items.ENCHANTED_GOLDEN_APPLE && offHand.getItem() == Items.GOLDEN_APPLE) return false;
        if (offHand.getItem() == Items.ENCHANTED_GOLDEN_APPLE && mainHand.getItem() == Items.GOLDEN_APPLE) return false;
        return mainHand.getItem() != offHand.getItem();
    }

    public static boolean isBlocking(Minecraft minecraft) {
        return INSTANCE != null && INSTANCE.isBlockingInternal(minecraft);
    }

    private boolean isBlockingInternal(Minecraft minecraft) {
        if (!this.isEnabled() || !this.isGrimACMode()) return false;
        if (minecraft == null || minecraft.player == null || minecraft.hitResult == null) return false;
        if (minecraft.hitResult.getType() != HitResult.Type.BLOCK) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            if (isUsable(minecraft.player.getItemInHand(hand).getUseAnimation())) return true;
        }
        return false;
    }

    private boolean isUsable(UseAnim action) {
        return action == UseAnim.EAT || action == UseAnim.DRINK || action == UseAnim.SPEAR;
    }

    private boolean isLookingAtInteractableBlock() {
        return isLookingAtInteractableBlock(mc);
    }

    public static boolean isLookingAtInteractableBlock(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return false;
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) return false;

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);

        if (state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.SHULKER_BOXES) || state.is(BlockTags.ANVIL)
                || state.is(BlockTags.BEDS) || state.is(BlockTags.CAMPFIRES)) {
            return true;
        }

        Block block = state.getBlock();
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER || block == Blocks.BARREL
                || block == Blocks.ENDER_CHEST || block == Blocks.BREWING_STAND || block == Blocks.HOPPER
                || block == Blocks.DISPENSER || block == Blocks.DROPPER || block == Blocks.JUKEBOX
                || block == Blocks.NOTE_BLOCK || block == Blocks.LEVER || block == Blocks.REPEATER
                || block == Blocks.COMPARATOR || block == Blocks.DAYLIGHT_DETECTOR || block == Blocks.CAKE
                || block == Blocks.COMPOSTER || block == Blocks.BEEHIVE || block == Blocks.BEE_NEST
                || block == Blocks.RESPAWN_ANCHOR || block == Blocks.GRINDSTONE || block == Blocks.STONECUTTER
                || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.LOOM || block == Blocks.SMITHING_TABLE
                || block == Blocks.LECTERN || block == Blocks.BELL || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.CRAFTING_TABLE || block == Blocks.ENCHANTING_TABLE;
    }

    private boolean isEatOrDrink(ItemStack stack) {
        if (stack.isEmpty()) return false;
        UseAnim anim = stack.getUseAnimation();
        Item item = stack.getItem();
        return (anim == UseAnim.EAT || anim == UseAnim.DRINK)
                || item instanceof PotionItem;
    }

    private void sendSwapOffhand() {
        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
    }

    private void startBlink(int duration) {
        if (!this.isBlinking) this.blinkTicks = 0;
        this.isBlinking = true;
        this.blinkDuration = duration;
    }

    private void stopBlink() {
        this.flushInboundQueue();
        this.isBlinking = false;
        this.blinkTicks = 0;
        this.blinkDuration = 0;
    }

    private void finishBlink() {
        this.shouldReleaseItem = false;
        if (!this.isBlinking || !this.didSwapHand || mc.player == null) return;
        if (this.useHand != this.lastUseHand) {
            this.sendSwapOffhand();
        }
        releaseItemSent = true;
        this.didSwapHand = false;
        this.timer.reset();
        this.releaseTicksRemaining = this.grimACUseItemTicks.getValue().intValue();
        this.releaseUseKey();
        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
    }

    private void startUseItem(InteractionHand hand, int count) {
        if (mc.player == null) return;
        if (isLookingAtInteractableBlock()) return;
        this.didSwapHand = true;
        this.lastUseHand = hand;
        this.swapInitSlot = mc.player.getInventory().selected;
        this.useHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        this.sendSwapOffhand();
        if (count > 0) {
            PacketUtil.sendQueued(new ServerboundUseItemPacket(this.useHand, count));
        } else {
            PacketUtil.sendPredictiveDirect(seq -> new ServerboundUseItemPacket(this.useHand, seq));
        }
        this.startBlink(2);
    }

    private void startUseItemDefault(InteractionHand hand) {
        this.startUseItem(hand, 0);
    }

    private void releaseUseKey() {
        mc.options.keyUse.setDown(false);
        while (mc.options.keyUse.consumeClick()) {
        }
    }

    private void restoreUseKeyState() {
        if (mc == null || mc.options == null || mc.getWindow() == null) return;
        mc.options.keyUse.setDown(!this.useSuppressed && this.isKeyPhysicallyDown(mc.options.keyUse));
    }

    private void releaseForwardKey() {
        if (mc == null || mc.options == null) return;
        mc.options.keyUp.setDown(false);
    }

    private void restoreForwardKeyState() {
        if (mc == null || mc.options == null || mc.getWindow() == null) return;
        mc.options.keyUp.setDown(this.isKeyPhysicallyDown(mc.options.keyUp));
    }

    private boolean isKeyPhysicallyDown(KeyMapping key) {
        if (mc == null || mc.getWindow() == null || key == null) return false;
        InputConstants.Key bound = InputConstants.getKey(key.saveString());
        long window = mc.getWindow().getWindow();
        return bound.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(window, bound.getValue()) == 1
                : InputConstants.isKeyDown(window, bound.getValue());
    }

    private void reset() {
        step = Step.NONE;
        hasSwapped = false;
        swapInArmed = false;
        noUseTicks = 0;
        foodDelay = false;
        foodSwapping = false;
        cached.clear();
    }

    private void release() {
        if (step == Step.NONE && cached.isEmpty() && !hasSwapped && !foodDelay) return;
        boolean ate = step == Step.EATING;
        step = Step.NONE;
        noUseTicks = 0;
        swapInArmed = false;
        foodDelay = false;
        foodSwapping = false;
        if (mc.player == null || mc.getConnection() == null) {
            cached.clear();
            inboundQueue.clear();
            return;
        }
        while (!cached.isEmpty()) {
            mc.getConnection().send(cached.poll());
        }
        flushInboundQueue();
        if (hasSwapped) {
            this.sendSwapOffhand();
            hasSwapped = false;
            if (ate) {
                this.useSuppressed = true;
                this.releaseUseKey();
            }
        }
    }

    private void queueInboundPacket(Packet<?> packet) {
        @SuppressWarnings("unchecked")
        Packet<ClientGamePacketListener> typed = (Packet<ClientGamePacketListener>) packet;
        this.inboundQueue.add(typed);
    }

    private void flushInboundQueue() {
        if (mc == null || mc.getConnection() == null) {
            this.inboundQueue.clear();
            return;
        }
        while (!this.inboundQueue.isEmpty()) {
            Packet<ClientGamePacketListener> packet = this.inboundQueue.poll();
            try {
                packet.handle(mc.getConnection());
            } catch (Exception e) {
                this.inboundQueue.clear();
                return;
            }
        }
    }

    private boolean isBlinkablePacket(Packet<?> packet) {
        if (packet instanceof ClientboundKeepAlivePacket || packet instanceof ClientboundPingPacket) {
            return true;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            return mc.player != null && motion.getId() == mc.player.getId();
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            return slotPacket.getSlot() == 45 || slotPacket.getContainerId() == 0;
        }
        if (packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            for (Pair<EquipmentSlot, ItemStack> slot : equipmentPacket.getSlots()) {
                if (slot.getFirst() == EquipmentSlot.OFFHAND) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldQueuePacket(Packet<?> packet) {
        if ((!this.isBlinking && !this.bowDelay && !this.foodDelay) || packet == null || mc.level == null || mc.getConnection() == null) return false;
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundLoginPacket
                || packet instanceof ClientboundRespawnPacket) {
            boolean foodEat = this.foodDelay || this.foodSwapping || this.step == Step.EATING;
            if (!foodEat || !(packet instanceof ClientboundPlayerPositionPacket)) {
                this.stopBlink();
            }
            return false;
        }
        if (packet instanceof ClientboundEntityEventPacket evt) {
            Entity entity = evt.getEntity(mc.level);
            if (entity != null && (entity != mc.player || evt.getEventId() != 2)) return false;
        }
        if (!this.isBlinkablePacket(packet)) return false;
        this.queueInboundPacket(packet);
        return true;
    }

    // ================================================================
    // 主事件监听 - 入口分发
    // ================================================================

    private boolean noSlowUseFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof PotionItem) return false;
        return stack.isEdible() || stack.getUseAnimation() == UseAnim.EAT;
    }

    private boolean noSlowUseBow(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    private boolean noSlowUseCrossbow(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }

    public boolean noWeb() {
        return isEnabled() && mode.is("Vanilla") && vanillaCobweb.getValue();
    }

    @EventTarget
    public void onSlowdown(SlowdownEvent event) {
        if (areAllOff()) return;
        if (mc.player == null || !mc.player.isUsingItem()) return;
        ItemStack stack = mc.player.getUseItem();
        if (stack.isEmpty()) return;

        // ===== Vanilla 模式 =====
        if (mode.is("Vanilla")) {
            if (noSlowUseFood(stack) && vanillaFood.getValue()) {
                event.setSlowDown(false);
                mc.player.setSprinting(true);
                return;
            }

            if ((noSlowUseBow(stack) && vanillaBow.getValue())
                    || (noSlowUseCrossbow(stack) && vanillaCrossbow.getValue())) {
                event.setSlowDown(false);
                mc.player.setSprinting(true);
            }
            return;
        }

        // ===== Grim1/2 模式 =====
        if (mode.is("Grim1/2")) {
            UseAnim anim12 = stack.getUseAnimation();
            if (anim12 != UseAnim.EAT && anim12 != UseAnim.DRINK) {
                return;
            }
            int remaining12 = mc.player.getUseItemRemainingTicks();
            if (remaining12 <= 30 && remaining12 % 2 == 0) {
                event.setSlowDown(false);
            }
            return;
        }

        // ===== Grim1/3 模式 =====
        if (mode.is("Grim1/3")) {
            UseAnim anim13 = stack.getUseAnimation();
            if (anim13 != UseAnim.EAT && anim13 != UseAnim.DRINK) {
                return;
            }
            int remaining13 = mc.player.getUseItemRemainingTicks();
            if (remaining13 <= 30 && remaining13 % 3 == 0) {
                event.setSlowDown(false);
            }
            return;
        }

        // ===== GrimBlink 模式 =====
        if (mode.is("GrimBlink")) {
            if (mc.player.isUsingItem()
                    && mc.player.getUseItemRemainingTicks() < 30
                    && !(isFoodOrDrink(mc.player.getMainHandItem())
                    && isFoodOrDrink(mc.player.getOffhandItem()))
                    && isFoodOrDrink(mc.player.getUseItem())) {
                event.setSlowDown(false);
                mc.player.setSprinting(true);
            }
            return;
        }

        // ===== Heypixel 模式（第二个文件的简化逻辑 + 四开关过滤） =====
        if (this.isHeypixelMode()) {
            // 食品 / 药水：主手使用、副手空、非炖菜 → 取消减速 + 挂起 inbound 队列
            if (this.isEatOrDrink(stack) && !this.isStew(stack)) {
                boolean isPotion = stack.getItem() instanceof PotionItem;
                boolean enabled = isPotion
                        ? this.heypixelPotionNoSlow.getValue()
                        : this.heypixelFoodNoSlow.getValue();
                if (!enabled) return;

                if (mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND
                        || !mc.player.getOffhandItem().isEmpty()) {
                    return;
                }
                event.setSlowDown(false);
                mc.player.setSprinting(true);
                if (!this.foodDelay) {
                    this.foodDelay = true;
                    this.inboundQueue.clear();
                }
                return;
            }

            // 弓 / 弩：仅当 bowActive 时取消减速
            if (this.bowActive) {
                boolean isCrossbow = stack.getItem() instanceof CrossbowItem;
                boolean enabled = isCrossbow
                        ? this.heypixelCrossbowNoSlow.getValue()
                        : this.heypixelBowNoSlow.getValue();
                if (!enabled) return;
                event.setSlowDown(false);
                mc.player.setSprinting(true);
            }
            return;
        }

        // ===== LeakGrim 模式 =====
        if (this.isLeakGrimMode()) {
            if (leakNeedsRelease) return;
            UseAnim useAnim = stack.getUseAnimation();
            boolean shouldCancel = false;
            switch (useAnim) {
                case BOW: case CROSSBOW: case SPEAR:
                    if (leakBowSetting.getValue()) shouldCancel = true;
                    break;
                case EAT: case DRINK:
                    if (leakFoodSetting.getValue()) shouldCancel = true;
                    break;
                case BLOCK:
                    if (leakBlockSetting.getValue()) shouldCancel = true;
                    break;
                default: break;
            }
            if (shouldCancel) {
                event.setSlowDown(false);
                mc.player.setSprinting(true);
            }
            return;
        }

        // ===== GrimAC 模式 =====
        if (this.isGrimACMode()) {
            if (step == Step.EATING) {
                event.setSlowDown(false);
                if (this.grimACKeepSprinting.getValue() && mc.player != null) {
                    mc.player.setSprinting(true);
                }
                return;
            }
            if (!(Boolean) this.grimACBowNoSlow.getValue()) return;
            if (!this.canSwapHands()) return;
            UseAnim anim = stack.getUseAnimation();
            if (anim == UseAnim.BOW && this.grimACCrossbowNoSlow.getValue()
                    || anim == UseAnim.CROSSBOW && this.grimACFoodNoSlow.getValue()
                    || this.isEatOrDrink(stack) && this.grimACPotionNoSlow.getValue()
                    || anim == UseAnim.BLOCK && this.grimACShieldNoSlow.getValue()) {
                event.setSlowDown(false);
            }
            if (this.grimACKeepSprinting.getValue()) {
                mc.player.setSprinting(true);
            }
            return;
        }

        // ===== Grim 模式 =====
        if (this.isGrimMode()) {
            Item item = stack.getItem();
            boolean isBow = item instanceof BowItem;
            boolean isCrossbow = item instanceof CrossbowItem;
            boolean isEdible = stack.isEdible();
            boolean isPotion = item instanceof PotionItem;
            if (isBow && this.grimBowNoSlow.getValue()) {
                event.setSlowDown(mc.player.tickCount % 3 != 0);
            } else if (isCrossbow && this.grimBowNoSlow.getValue()) {
                event.setSlowDown(mc.player.tickCount % 3 != 0);
            } else if ((isEdible && this.grimFoodNoSlow.getValue())
                    || (isPotion && this.grimFoodNoSlow.getValue())) {
                event.setSlowDown(mc.player.getUseItemRemainingTicks() >= 1
                        || mc.player.tickCount % 3 != 0);
            }
            if (this.grimKeepSprinting.getValue()) {
                mc.player.setSprinting(true);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) {
            this.release();
            this.stopBlink();
            if (isLeakGrimMode()) {
                leakReleaseItem();
                leakReset();
            }
            if (isHeypixelMode()) {
                heypixelRelease();
                heypixelReset();
            }
            if (isGrimBlinkMode()) {
                this.grimBlinkFlush();
                this.grimBlinkEating = false;
                this.grimBlinkTicks = 0;
            }
            return;
        }

        // ===== Heypixel 模式 Tick（简化逻辑） =====
        if (isHeypixelMode()) {
            heypixelTick();
            return;
        }

        // ===== Grim1/2 / Grim1/3 只需要 onSlowdown =====
        if (isGrim1_2Mode() || isGrim1_3Mode()) {
            return;
        }

        // ===== GrimBlink 模式 Tick =====
        if (isGrimBlinkMode()) {
            this.grimBlinkTick();
            return;
        }

        // ===== LeakGrim 模式 Tick =====
        if (isLeakGrimMode()) {
            leakTick();
            return;
        }

        this.tickGrimACVisualLock();

        // ===== GrimAC 模式 Tick =====
        if (this.isGrimACMode()) {
            if (this.isBlinking) ++this.blinkTicks;
            if (this.step != Step.NONE && !this.isGrimACMode()) this.release();
            if (this.isGrimACMode()) {
                if (step != Step.NONE && step != Step.EATING) mc.options.keyUse.setDown(false);
                if (step == Step.NONE) {
                    if (mc.player.isUsingItem()
                            && mc.options.keyUse.isDown()
                            && isUsable(mc.player.getUseItem().getUseAnimation())) {
                        if (isLookingAtInteractableBlock()) return;
                        InteractionHand hand = mc.player.getUsedItemHand();
                        if (hand == InteractionHand.OFF_HAND
                                || (hand == InteractionHand.MAIN_HAND && mc.player.getOffhandItem().isEmpty())) {
                            this.beginGrimACVisualLock(hand);
                            step = Step.ARMED;
                            mc.options.keyUse.setDown(false);
                            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                            }
                        }
                    }
                } else if (step == Step.EATING) {
                    if (mc.player.isUsingItem()) {
                        noUseTicks = 0;
                    } else {
                        noUseTicks++;
                        if (noUseTicks >= 5) release();
                    }
                } else {
                    noUseTicks = 0;
                }
            }
            if (this.releaseTicksRemaining > 0) {
                this.releaseUseKey();
                --this.releaseTicksRemaining;
                if (this.releaseTicksRemaining == 0) this.restoreUseKeyState();
            }
            if (this.pendingUseHand != null) {
                this.startUseItem(this.pendingUseHand, this.pendingUseCount);
                this.pendingUseHand = null;
                this.pendingUseCount = 0;
            }
            if (this.isBlinking && this.blinkTicks >= this.blinkDuration) {
                this.finishBlink();
                return;
            }
            if (this.isGrimACMode() && this.grimACBowNoSlow.getValue() && this.didSwapHand && !this.isBlinking) {
                if (this.useHand != this.lastUseHand) this.sendSwapOffhand();
                releaseItemSent = true;
                this.didSwapHand = false;
                this.shouldReleaseItem = false;
                this.timer.reset();
                this.releaseTicksRemaining = this.grimACUseItemTicks.getValue().intValue();
                this.releaseUseKey();
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
                return;
            }
            if (this.isGrimACMode() && this.grimACBowNoSlow.getValue() && this.shouldReleaseItem
                    && mc.player.isUsingItem() && this.canSwapHands()) {
                this.shouldReleaseItem = false;
                this.startUseItemDefault(mc.player.getUsedItemHand());
            }
            return;
        }

        // ===== Grim 模式 Tick =====
        if (this.isGrimMode()) {
            if (this.isBlinking) ++this.blinkTicks;
            if (this.releaseTicksRemaining > 0) {
                this.releaseUseKey();
                --this.releaseTicksRemaining;
                if (this.releaseTicksRemaining == 0) this.restoreUseKeyState();
            }
            if (this.pendingUseHand != null) {
                this.startUseItem(this.pendingUseHand, this.pendingUseCount);
                this.pendingUseHand = null;
                this.pendingUseCount = 0;
            }
            if (this.isBlinking && this.blinkTicks >= this.blinkDuration) {
                this.finishBlink();
                return;
            }
            if (this.isGrimMode() && this.grimBowNoSlow.getValue() && this.didSwapHand && !this.isBlinking) {
                if (this.useHand != this.lastUseHand) this.sendSwapOffhand();
                releaseItemSent = true;
                this.didSwapHand = false;
                this.shouldReleaseItem = false;
                this.timer.reset();
                this.releaseTicksRemaining = this.grimUseItemTicks.getValue().intValue();
                this.releaseUseKey();
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
                return;
            }
            if (this.isGrimMode() && this.grimBowNoSlow.getValue() && this.shouldReleaseItem
                    && mc.player.isUsingItem() && this.canSwapHands()) {
                this.shouldReleaseItem = false;
                this.startUseItemDefault(mc.player.getUsedItemHand());
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (isHeypixelMode()) return;
        if (isLeakGrimMode()) return;
        if (isGrimBlinkMode()) return;
        if (isGrim1_2Mode() || isGrim1_3Mode()) return;
        if (event.isPre() && this.isBlinking && this.blinkTicks >= this.blinkDuration && !this.didSwapHand) {
            this.stopBlink();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (areAllOff()) return;
        if (FastPlace.INSTANCE != null && FastPlace.INSTANCE.isEnabled()) return;
        if (mc.player == null) return;

        // ===== Grim1/2 / Grim1/3 不处理包 =====
        if (isGrim1_2Mode() || isGrim1_3Mode()) {
            return;
        }

        // ===== GrimBlink 模式包处理 =====
        if (isGrimBlinkMode()) {
            Packet<?> p = event.getPacket();

            if (event.isIncoming()) {
                if (this.grimBlinkShouldQueue(p)) {
                    event.setCancelled(true);
                    this.grimBlinkPackets.offer(p);
                    return;
                }
                return;
            }

            if (p instanceof ServerboundPlayerActionPacket action
                    && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                    && this.grimBlinkEating) {
                this.grimBlinkEating = false;
                this.grimBlinkTicks = 0;
                this.grimBlinkFlush();
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO, Direction.DOWN));
            }
            return;
        }

        // ===== Heypixel 模式包处理（简化逻辑） =====
        if (isHeypixelMode()) {
            heypixelOnPacket(event);
            return;
        }

        // ===== LeakGrim 模式包处理 =====
        if (isLeakGrimMode()) {
            leakOnPacket(event);
            return;
        }

        // ===== GrimAC 模式包处理 =====
        if (isGrimACMode()) {
            if (event.isIncoming() && this.shouldQueuePacket(event.getPacket())) {
                event.setCancelled(true);
                return;
            }
            if (this.isGrimACMode()) {
                Packet<?> p = event.getPacket();
                if (!event.isIncoming()) {
                    if (step == Step.EATING
                            && p instanceof ServerboundPlayerActionPacket action
                            && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                        release();
                    }
                } else {
                    if (step != Step.NONE && p instanceof ClientboundPingPacket) {
                        event.setCancelled(true);
                        queueInboundPacket(p);
                        if (step == Step.ARMED && !swapInArmed) {
                            if (!this.grimACVisualLock && mc.player != null) {
                                InteractionHand originalHand = mc.player.isUsingItem()
                                        ? mc.player.getUsedItemHand()
                                        : InteractionHand.MAIN_HAND;
                                this.beginGrimACVisualLock(originalHand);
                            }
                            swapInArmed = true;
                            hasSwapped = true;
                            mc.getConnection().send(new ServerboundPlayerActionPacket(
                                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                        }
                    }
                    if (step != Step.NONE && p instanceof ClientboundSetEntityMotionPacket motion
                            && mc.player != null && motion.getId() == mc.player.getId()) {
                        event.setCancelled(true);
                        queueInboundPacket(p);
                    }
                    if (step == Step.ARMED && swapInArmed && p instanceof ClientboundContainerSetSlotPacket) {
                        swapInArmed = false;
                        mc.options.keyUse.setDown(true);
                        step = Step.EATING;
                    }
                    if (step != Step.NONE && p instanceof ClientboundPlayerPositionPacket) {
                        release();
                    }
                }
            }
            if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket
                    && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                this.blinkTicks = Math.max(this.blinkTicks, 1);
            }
            if (event.getPacket() instanceof ServerboundUseItemOnPacket useOnPacket
                    && this.didSwapHand
                    && useOnPacket.getHand() == this.useHand
                    && mc.player.getInventory().selected == this.swapInitSlot) {
                InteractionHand other = useOnPacket.getHand() == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                PacketUtil.sendQueued(new ServerboundUseItemOnPacket(other, useOnPacket.getHitResult(), useOnPacket.getSequence()));
            }
            if (event.getPacket() instanceof ServerboundUseItemPacket usePacket) {
                if (this.didSwapHand || this.releaseTicksRemaining > 0) {
                    event.setCancelled(true);
                } else if (this.isGrimACMode() && this.grimACBowNoSlow.getValue()) {
                    if (!this.timer.hasPassed(150.0f) && this.releaseTicksRemaining <= 0) {
                        event.setCancelled(true);
                    } else if (!this.canSwapHands()) {
                        this.shouldReleaseItem = true;
                    } else {
                        ItemStack handStack = mc.player.getItemInHand(usePacket.getHand());
                        UseAnim anim = handStack.getUseAnimation();
                        if ((anim == UseAnim.BOW && this.grimACCrossbowNoSlow.getValue())
                                || (anim == UseAnim.CROSSBOW && !CrossbowItem.isCharged(handStack) && this.grimACFoodNoSlow.getValue())) {
                            this.shouldReleaseItem = false;
                            this.startBlink(1);
                        } else if (this.isEatOrDrink(handStack)) {
                            this.shouldReleaseItem = false;
                            event.setCancelled(true);
                            this.pendingUseHand = usePacket.getHand();
                            this.pendingUseCount = usePacket.getSequence();
                        }
                    }
                }
            }
            return;
        }

        // ===== Grim 模式包处理 =====
        if (isGrimMode()) {
            if (event.isIncoming() && this.shouldQueuePacket(event.getPacket())) {
                event.setCancelled(true);
                return;
            }
            if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket
                    && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                this.blinkTicks = Math.max(this.blinkTicks, 1);
            }
            if (event.getPacket() instanceof ServerboundUseItemOnPacket useOnPacket
                    && this.didSwapHand
                    && useOnPacket.getHand() == this.useHand
                    && mc.player.getInventory().selected == this.swapInitSlot) {
                InteractionHand other = useOnPacket.getHand() == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                PacketUtil.sendQueued(new ServerboundUseItemOnPacket(other, useOnPacket.getHitResult(), useOnPacket.getSequence()));
            }
            if (event.getPacket() instanceof ServerboundUseItemPacket usePacket) {
                if (this.didSwapHand || this.releaseTicksRemaining > 0) {
                    event.setCancelled(true);
                } else if (this.isGrimMode() && this.grimBowNoSlow.getValue()) {
                    if (!this.timer.hasPassed(150.0f) && this.releaseTicksRemaining <= 0) {
                        event.setCancelled(true);
                    } else if (!this.canSwapHands()) {
                        this.shouldReleaseItem = true;
                    } else {
                        ItemStack handStack = mc.player.getItemInHand(usePacket.getHand());
                        UseAnim anim = handStack.getUseAnimation();
                        if ((anim == UseAnim.BOW && this.grimBowNoSlow.getValue())
                                || (anim == UseAnim.CROSSBOW && !CrossbowItem.isCharged(handStack) && this.grimBowNoSlow.getValue())) {
                            this.shouldReleaseItem = false;
                            this.startBlink(1);
                        } else if (this.isEatOrDrink(handStack)) {
                            this.shouldReleaseItem = false;
                            event.setCancelled(true);
                            this.pendingUseHand = usePacket.getHand();
                            this.pendingUseCount = usePacket.getSequence();
                        }
                    }
                }
            }
        }
    }

    // ================================================================
    // Heypixel 模式核心（第二个文件的逻辑 + 四开关过滤）
    // ================================================================

    private void heypixelReset() {
        this.reset();
        this.bowActive = false;
        this.bowDelay = false;
        this.foodDelay = false;
        this.foodSwapping = false;
        this.useSuppressed = false;
        this.forwardReleased = false;
        this.forwardRestoreTicks = 0;
    }

    private void heypixelRelease() {
        this.release();
        this.stopBlink();
        this.bowActive = false;
        this.bowDelay = false;
        this.foodDelay = false;
        this.foodSwapping = false;
        this.useSuppressed = false;
        this.forwardReleased = false;
        this.forwardRestoreTicks = 0;
        this.restoreForwardKeyState();
    }

    /**
     * Heypixel 的 tick 逻辑：直接复用第二个文件的实现（简化版）。
     * 没有 Heypixel 专属状态机，只有 vanilla 风格的食物/弓处理。
     */
    private void heypixelTick() {
        if (mc.player == null) {
            this.release();
            this.stopBlink();
            return;
        }
        if (this.useSuppressed) {
            if (this.isKeyPhysicallyDown(mc.options.keyUse)) {
                this.releaseUseKey();
            } else {
                this.useSuppressed = false;
            }
        }
        if (this.step == Step.EATING && mc.player.isUsingItem()
                && mc.player.getUseItemRemainingTicks() <= 1) {
            this.forwardReleased = true;
        }
        if (this.forwardReleased) {
            boolean hold = mc.player.isUsingItem()
                    || (this.hasSwapped && !mc.player.isDeadOrDying());
            if (hold) {
                this.forwardRestoreTicks = 0;
                this.releaseForwardKey();
            } else if (++this.forwardRestoreTicks >= 1) {
                this.forwardReleased = false;
                this.forwardRestoreTicks = 0;
                this.restoreForwardKeyState();
            }
        }
        if ((step != Step.NONE || this.bowActive || this.foodDelay)
                && ((Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled())
                || mc.player.getMainHandItem().is(Items.ENDER_PEARL))) {
            this.release();
            return;
        }
        if (this.isBlinking) {
            ++this.blinkTicks;
        }
        if (this.bowActive && (mc.player.getUseItem().isEmpty()
                || !this.isBowLike(mc.player.getUseItem().getUseAnimation()))) {
            this.bowActive = false;
            this.bowDelay = false;
            this.flushInboundQueue();
        }
        if (this.foodDelay && !mc.player.isUsingItem()) {
            this.foodDelay = false;
            this.flushInboundQueue();
        }
        if (step != Step.NONE && step != Step.EATING) {
            mc.options.keyUse.setDown(false);
        }

        if (step == Step.NONE) {
            if (mc.player.isUsingItem()
                    && mc.options.keyUse.isDown()
                    && this.drivesHandSwap(mc.player.getUseItem().getUseAnimation())) {
                if (isLookingAtInteractableBlock()) {
                    return;
                }
                InteractionHand hand = mc.player.getUsedItemHand();
                if (hand == InteractionHand.OFF_HAND
                        || (hand == InteractionHand.MAIN_HAND && mc.player.getOffhandItem().isEmpty())) {
                    step = Step.ARMED;
                    mc.options.keyUse.setDown(false);

                    if (mc.player.containerMenu != mc.player.inventoryMenu) {
                        mc.getConnection().send(
                                new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                    }
                }
            }
        } else if (step == Step.EATING) {
            if (mc.player.isUsingItem()) {
                noUseTicks = 0;
            } else if (++noUseTicks >= FOOD_SWAP_BACK_TIMEOUT) {
                release();
            }
        } else {
            noUseTicks = 0;
        }
        if (this.releaseTicksRemaining > 0) {
            this.releaseUseKey();
            --this.releaseTicksRemaining;
            if (this.releaseTicksRemaining == 0) {
                this.restoreUseKeyState();
            }
        }
        if (this.pendingUseHand != null) {
            this.startUseItem(this.pendingUseHand, this.pendingUseCount);
            this.pendingUseHand = null;
            this.pendingUseCount = 0;
        }
        if (this.isBlinking && this.blinkTicks >= this.blinkDuration) {
            this.finishBlink();
            return;
        }
        if (this.didSwapHand && !this.isBlinking) {
            if (this.useHand != this.lastUseHand) {
                this.sendSwapOffhand();
            }
            releaseItemSent = true;
            this.didSwapHand = false;
            this.shouldReleaseItem = false;
            this.releaseTicksRemaining = 1;
            this.releaseUseKey();
            PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
            return;
        }
        if (this.shouldReleaseItem
                && mc.player.isUsingItem() && this.canSwapHands()) {
            this.shouldReleaseItem = false;
            this.startUseItemDefault(mc.player.getUsedItemHand());
        }
    }

    /**
     * Heypixel 的收包逻辑：直接复用第二个文件的实现（简化版）。
     */
    private void heypixelOnPacket(PacketEvent event) {
        if (mc.player == null) {
            return;
        }
        if (event.isIncoming() && this.shouldQueuePacket(event.getPacket())) {
            event.setCancelled(true);
            return;
        }
        Packet<?> p = event.getPacket();

        if (!event.isIncoming()) {
            if (p instanceof ServerboundMovePlayerPacket
                    && this.step == Step.EATING && !mc.player.isUsingItem()) {
                this.release();
                return;
            }
            if (p instanceof ServerboundPlayerActionPacket action
                    && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                if (this.bowActive) {
                    this.bowActive = false;
                    this.bowDelay = false;
                    this.flushInboundQueue();
                }
            }
        } else {
            if (step != Step.NONE && p instanceof ClientboundPingPacket) {
                event.setCancelled(true);
                queueInboundPacket(p);
                if (step == Step.ARMED && !swapInArmed) {
                    swapInArmed = true;
                    hasSwapped = true;
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                }
            }

            if (step != Step.NONE && p instanceof ClientboundSetEntityMotionPacket motion
                    && mc.player != null && motion.getId() == mc.player.getId()) {
                event.setCancelled(true);
                queueInboundPacket(p);
            }

            if (step == Step.ARMED && swapInArmed && p instanceof ClientboundContainerSetSlotPacket) {
                swapInArmed = false;
                mc.options.keyUse.setDown(true);
                step = Step.EATING;
            }

            if (step != Step.NONE && !this.foodSwapping && step != Step.EATING
                    && p instanceof ClientboundPlayerPositionPacket) {
                release();
            }
        }
        if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket
                && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            this.blinkTicks = Math.max(this.blinkTicks, 1);
        }
        if (event.getPacket() instanceof ServerboundUseItemOnPacket useOnPacket
                && this.didSwapHand
                && useOnPacket.getHand() == this.useHand
                && mc.player.getInventory().selected == this.swapInitSlot) {
            InteractionHand other = useOnPacket.getHand() == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            PacketUtil.sendQueued(new ServerboundUseItemOnPacket(other, useOnPacket.getHitResult(), useOnPacket.getSequence()));
        }
        if (event.getPacket() instanceof ServerboundUseItemPacket usePacket) {
            if (this.didSwapHand || this.releaseTicksRemaining > 0 || this.foodSwapping) {
                event.setCancelled(true);
            } else {
                ItemStack handStack = mc.player.getItemInHand(usePacket.getHand());

                // 四开关过滤：非启用的物品一律跳过
                if (this.isHeypixelMode() && !this.heypixelItemEnabled(handStack)) {
                    return;
                }

                if (this.shouldFoodSwap(usePacket.getHand(), handStack)) {
                    this.foodSwapping = true;
                    event.setCancelled(true);
                    this.sendSwapOffhand();
                    PacketUtil.sendQueued(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, usePacket.getSequence()));
                    this.step = Step.EATING;
                    this.hasSwapped = true;
                    this.noUseTicks = 0;
                    return;
                }
                if (this.isBowLike(handStack.getUseAnimation())) {
                    this.handleBowUseItem(handStack);
                }
            }
        }
    }

    // ================================================================
    // Heypixel 模式辅助方法
    // ================================================================

    /** 判断给定物品是否被 Heypixel 的四个开关之一覆盖。 */
    private boolean heypixelItemEnabled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (stack.getItem() instanceof BowItem) {
            return this.heypixelBowNoSlow.getValue();
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return this.heypixelCrossbowNoSlow.getValue();
        }
        if (stack.getItem() instanceof PotionItem) {
            return this.heypixelPotionNoSlow.getValue();
        }
        if (this.isEatOrDrink(stack)) {
            return this.heypixelFoodNoSlow.getValue();
        }
        return false;
    }

    private boolean isBowLike(UseAnim anim) {
        return anim == UseAnim.BOW || anim == UseAnim.CROSSBOW || anim == UseAnim.SPEAR;
    }

    private boolean isStew(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.MUSHROOM_STEW || item == Items.RABBIT_STEW
                || item == Items.BEETROOT_SOUP || item == Items.SUSPICIOUS_STEW;
    }

    private boolean isConsumable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    /** 换手路径仍然只为 SPEAR 服务（与第二个文件一致）。 */
    private boolean drivesHandSwap(UseAnim action) {
        return action == UseAnim.SPEAR;
    }

    private boolean shouldFoodSwap(InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND) return false;
        if (this.foodSwapping) return false;
        if (!mc.player.getOffhandItem().isEmpty()) return false;
        if (stack == null || stack.isEmpty()) return false;
        if (!this.isEatOrDrink(stack)) return false;

        // Heypixel 模式下按 Food / Potion 开关过滤
        if (this.isHeypixelMode()) {
            boolean isPotion = stack.getItem() instanceof PotionItem;
            boolean enabled = isPotion
                    ? this.heypixelPotionNoSlow.getValue()
                    : this.heypixelFoodNoSlow.getValue();
            if (!enabled) return false;
        }

        UseAnim anim = stack.getUseAnimation();
        if (anim == UseAnim.BLOCK || this.isBowLike(anim)) return false;
        if (this.isStew(stack)) return false;
        if (this.isLookingAtInteractableBlock()) return false;
        if (this.isConsumable(mc.player.getMainHandItem())
                && this.isConsumable(mc.player.getOffhandItem())) {
            return false;
        }
        return this.canSwapHands();
    }

    private void handleBowUseItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        if (this.isHeypixelMode()) {
            if (stack.getItem() instanceof BowItem && !this.heypixelBowNoSlow.getValue()) {
                return;
            }
            if (stack.getItem() instanceof CrossbowItem && !this.heypixelCrossbowNoSlow.getValue()) {
                return;
            }
        }

        if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            this.shouldReleaseItem = false;
            return;
        }

        UseAnim anim = stack.getUseAnimation();
        if (anim == UseAnim.BOW && !this.isHeypixelMode()) {
            this.shouldReleaseItem = false;
            return;
        }
        if (this.isStew(stack)) {
            this.shouldReleaseItem = false;
            return;
        }
        if (this.isLookingAtInteractableBlock()) {
            this.shouldReleaseItem = false;
            return;
        }

        this.shouldReleaseItem = false;
        this.bowActive = true;
        this.bowDelay = true;
    }

    // ===== LeakGrim 模式包处理 =====
    private void leakOnPacket(PacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ServerboundMovePlayerPacket) {
            if (leakJustUsed) return;
            if (leakSendingPackets || leakPendingUse) {
                event.setCancelled(true);
                leakJustUsed = true;
                try {
                    PacketUtil.sendQueued((Packet<ServerGamePacketListener>) packet);
                } finally {
                    leakJustUsed = false;
                }
                if (leakSendingPackets) {
                    leakReleaseItem();
                    leakSendingPackets = false;
                }
                if (leakPendingUse) {
                    leakPerformGrimSwap(leakPendingHand, leakUseSequence);
                    leakPendingUse = false;
                }
                return;
            }
        }

        if (packet instanceof ServerboundPlayerActionPacket action) {
            if (action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                leakScheduleRelease();
            }
        }

        if (packet instanceof ServerboundUseItemPacket useItem) {
            ItemStack heldItem = mc.player.getItemInHand(useItem.getHand());
            int sequence = useItem.getSequence();

            if (leakIsCrossbowCharged(heldItem)) {
                leakNeedsRelease = false;
                return;
            }

            UseAnim useAnim = heldItem.getUseAnimation();

            if (leakPendingUse) {
                event.setCancelled(true);
                return;
            }

            if (leakIsBlinking && (useAnim == UseAnim.EAT || useAnim == UseAnim.DRINK)) {
                event.setCancelled(true);
                PacketUtil.sendQueued(new ServerboundUseItemPacket(useItem.getHand(), sequence));
                return;
            }

            if (!leakTimer.hasPassed(50L)) {
                event.setCancelled(true);
                PacketUtil.sendQueued(new ServerboundUseItemPacket(useItem.getHand(), sequence));
                return;
            }

            if (leakHasDualItems()) {
                leakNeedsRelease = true;
                return;
            }

            switch (useAnim) {
                case BOW: case CROSSBOW: case SPEAR:
                    if ((Boolean) this.leakBowSetting.getValue()) {
                        leakNeedsRelease = false;
                        leakDelayedPacketsMax = ((Number) leakBowTick.getValue()).intValue();
                        leakDelayedPacketsCount = 0;
                        leakDelayingPackets = true;
                    }
                    break;
                case EAT: case DRINK:
                    if ((Boolean) this.leakFoodSetting.getValue()) {
                        event.setCancelled(true);
                        leakSchedulePendingUse(useItem.getHand(), sequence);
                    }
                    break;
                default: break;
            }
        }
    }
}