package client.nilore.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import java.awt.Color;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import client.nilore.ClientBase;
import client.nilore.NiloreClient;
import client.nilore.event.impl.JumpEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.event.impl.UpdateHeldItemEvent;
import client.nilore.hud.ModuleListHud;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.GlHelper;
import client.nilore.render.Paint;
import client.nilore.render.Rectangle;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.BlockUtil;
import client.nilore.utils.game.MotionSimulator;
import client.nilore.utils.game.MovementUtil;
import client.nilore.utils.game.RayTraceUtil;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.misc.PacketUtil;
import client.nilore.utils.render.RenderUtil;

import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;
import client.nilore.utils.rotation.RotationSmoother;
import client.nilore.event.EventTarget;

public class Scaffold extends Module {
    public static Scaffold INSTANCE;

    public final ModeSetting mode = new ModeSetting("Mode", "Normal", "Telly", "KeepY").withDefault("Normal");
    public final NumberSetting tellyAirTicks = new NumberSetting("AirTicks", 1, 0, 3, 0.1, () -> this.mode.is("Telly"));
    public final NumberSetting tellyPlaceDelay = new NumberSetting("PlaceDelay", 1, 0, 20, 1, () -> this.mode.is("Telly"));
    public final BooleanSetting eagle = new BooleanSetting("Eagle", false, () -> this.mode.is("Normal"));
    public final BooleanSetting renderItemSpoof = new BooleanSetting("Render Item Spoof", true);
    public final BooleanSetting clutch = new BooleanSetting("Clutch", false);
    public final NumberSetting clutchDepth = new NumberSetting("Clutch Depth", 4, 2, 5, 1, this.clutch::getValue);
    public final ModeSetting swingMode = new ModeSetting("Swing", "Both", "Server").withDefault("Both");
    public final BooleanSetting blockCounter = new BooleanSetting("Block Counter", true);
    public final ModeSetting blockCounterStyle = new ModeSetting("Block Counter Style", "Amunix", "Modern", "Naven", "Nitro").withDefault("Nitro");
    public final NumberSetting nitroOffsetY = new NumberSetting("Nitro Offset", 90, 0, 220, 5, () -> this.blockCounterStyle.is("Nitro"));
    public final BooleanSetting onTickRot = new BooleanSetting("OnTickRot", false);
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 153.9, 0, 360, 5, () -> !this.syncRotSpeed.getValue());
    public final NumberSetting jitter = new NumberSetting("Jitter", 2.5, 0.0, 5.0, 0.1);
    public final BooleanSetting syncRotSpeed = new BooleanSetting("Sync RotSpeed", false);
    public final NumberSetting turnSpeed = new NumberSetting("Turn Speed", 73.4, 0, 360, 5, this.syncRotSpeed::getValue);
    public final NumberSetting returnSpeed = new NumberSetting("Return Speed", 101.8, 0, 360, 5, this.syncRotSpeed::getValue);
    public final ModeSetting switchMode = new ModeSetting("Switch Mode", "Normal", "Hotbar", "Full").withDefault("Hotbar");
    public final BooleanSetting bpsLimit = new BooleanSetting("Limit BPS", true);
    public final NumberSetting bps = new NumberSetting("BPS", 3.5, 2.0, 10.0, 0.1, this.bpsLimit::getValue);
    public final BooleanSetting mark = new BooleanSetting("Mark", true);
    public final NumberSetting markRed = new NumberSetting("Mark Red", 255, 0, 255, 1, () -> this.mark.getValue());
    public final NumberSetting markGreen = new NumberSetting("Mark Green", 55, 0, 255, 1, () -> this.mark.getValue());
    public final NumberSetting markBlue = new NumberSetting("Mark Blue", 100, 0, 255, 1, () -> this.mark.getValue());
    public final NumberSetting markAlpha = new NumberSetting("Mark Alpha", 130, 0, 255, 1, () -> this.mark.getValue());

    // ======================================================================
    // NEW SETTINGS (从文件2合并)
    // ======================================================================
    public final BooleanSetting supportOffhand = new BooleanSetting("Support Offhand", true);
    public final ModeSetting blockSwitchMode = new ModeSetting("Block Switch", "First", "Most Blocks", "Farthest").withDefault("Most Blocks");
    public final BooleanSetting autoRefill = new BooleanSetting("Auto Refill", true);
    public final BooleanSetting refillFromInventory = new BooleanSetting("Refill From Inventory", true, () -> this.autoRefill.getValue());
    public final BooleanSetting refillBeforeEmpty = new BooleanSetting("Refill Before Empty", false, () -> this.autoRefill.getValue() && this.refillFromInventory.getValue());
    public final NumberSetting refillAt = new NumberSetting("Refill At", 4, 1, 64, 1, () -> this.autoRefill.getValue() && this.refillFromInventory.getValue() && this.refillBeforeEmpty.getValue());

    public final NumberSetting clutchTrigger = new NumberSetting("Clutch Trigger", 0.3, 0.1, 2.0, 0.1);
    public final NumberSetting clutchSimFrames = new NumberSetting("Clutch Sim Frames", 3, 1, 5, 1);

    public final BooleanSetting autoContinent = new BooleanSetting("Auto Continent", false);
    public final NumberSetting continentSpeed = new NumberSetting("Continent Speed", 5, 1, 20, 1, () -> this.autoContinent.getValue());
    public final BooleanSetting continentAutoJump = new BooleanSetting("Continent AutoJump", false, () -> this.autoContinent.getValue());
    public final NumberSetting continentJumpInterval = new NumberSetting("Continent JumpInterval", 10, 5, 40, 1, () -> this.autoContinent.getValue());

    public final NumberSetting offsetY = new NumberSetting("Offset Y", 20, 0, 220, 5);
    public final NumberSetting blockCounterOffsetX = new NumberSetting("Block Counter X", 0, -2000, 2000, 1, this.blockCounter::getValue);
    public final NumberSetting blockCounterOffsetY = new NumberSetting("Block Counter Y", 0, -2000, 2000, 1, this.blockCounter::getValue);

    public final BooleanSetting nitroHorizontal = new BooleanSetting("Nitro Horizontal", false, () -> this.blockCounterStyle.is("Nitro"));
    public final BooleanSetting nitroHideBlockName = new BooleanSetting("Nitro Hide Block Name", false, () -> this.blockCounterStyle.is("Nitro"));
    public final NumberSetting nitroSize = new NumberSetting("Nitro Size", 1.0, 0.5, 2.0, 0.05, () -> this.blockCounterStyle.is("Nitro"));
    public final NumberSetting nitroOffsetX = new NumberSetting("Nitro Offset X", 0, -2000, 2000, 1, () -> this.blockCounterStyle.is("Nitro"));
    public final NumberSetting nitroOffsetZ = new NumberSetting("Nitro Offset Z", 0, -220, 220, 5, () -> this.blockCounterStyle.is("Nitro"));
    public final BooleanSetting nitroCustomAlpha = new BooleanSetting("Nitro Custom Alpha", false, () -> this.blockCounterStyle.is("Nitro"));
    public final NumberSetting nitroAlpha = new NumberSetting("Nitro Alpha", 210, 0, 255, 5, () -> this.blockCounterStyle.is("Nitro") && this.nitroCustomAlpha.getValue());

    public final BooleanSetting esp = new BooleanSetting("Esp", true);
    public final ModeSetting espColor = new ModeSetting("Esp Color", "White", "Rainbow", "Cyan", "Red", "Green", "Blue").withDefault("White");

    public Rotation rots = new Rotation();
    public Rotation lastRots = new Rotation();
    /** 转头平滑(惯性+波动), 与 KillAura 共用同一实现。 */
    private final RotationSmoother rotSmoother = new RotationSmoother(new Random());
    /** 抖动独立随机源: 不能和 smoother 共用, 否则平滑器的白噪声会与抖动相关。 */
    private final Random jitterRandom = new Random();
    public int targetYLevel = -1;
    public int velocityDelay = 0;

    private int oldSlot;
    private int altSlot = -1;
    private boolean slotActive;
    private int slotActiveTick;
    private int slotTicks;
    private PlacementTarget currentPlacement;
    private int airTicks = 0;
    private int rotationDelay = 0;
    private final CopyOnWriteArrayList<CopyOnWriteArrayList<Packet<?>>> packetBatches = new CopyOnWriteArrayList<>();
    private boolean canBuildNow;
    private BlockPos lastC05Position;
    private int tellyPlaceDelayTimer;

    // ======================================================================
    // NEW STATE (从文件2合并)
    // ======================================================================
    private float continentYaw = 0;
    private float continentPitch = 0;
    private int continentJumpCounter = 0;
    private int lastOffhandCount = 0;

    private boolean blockCounterDragging;
    private boolean blockCounterMouseWasDown;
    private double blockCounterDragStartMouseX;
    private double blockCounterDragStartMouseY;
    private float blockCounterDragStartOffsetX;
    private float blockCounterDragStartOffsetY;

    private static final FontRenderer shelfLabelFont = FontPresets.axiformaBold(12);
    private static final FontRenderer shelfBpsFont = FontPresets.axiformaBold(13);
    private static final FontRenderer shelfBlocksFont = FontPresets.axiformaBold(13);
    private final client.nilore.utils.animation.SpringAnimation shelfProgress =
            new client.nilore.utils.animation.SpringAnimation(300.0f, 1.0f, 25.0f, 1.0f);
    private int shelfInitialBlocks;
    private boolean shelfHudInitialized;
    private long hudLastFrameTime;


    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            this.oldSlot = mc.player.getInventory().selected;
            this.altSlot = this.oldSlot;
            this.slotActive = false;
            this.slotActiveTick = 0;
            this.slotTicks = 0;
            this.rots.setYawPitch(mc.player.getYRot() - 180.0f, mc.player.getXRot());
            this.lastRots.setYawPitch(mc.player.yRotO - 180.0f, mc.player.xRotO);
            this.currentPlacement = null;
            this.targetYLevel = 10000;
            this.velocityDelay = 0;
            this.canBuildNow = true;
            this.lastC05Position = null;
            this.packetBatches.clear();
            this.packetBatches.add(new CopyOnWriteArrayList<>());
            this.continentYaw = mc.player.getYRot();
            this.continentPitch = mc.player.getXRot();
            this.continentJumpCounter = 0;
            this.lastOffhandCount = getOffhandBlockCount();
            this.rotSmoother.reset();
        }
        this.shelfHudInitialized = mc.player != null;
        this.shelfInitialBlocks = this.shelfHudInitialized ? this.getBlockSlot() : 0;
        this.shelfProgress.reset(0.0f);
        this.hudLastFrameTime = 0L;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc != null && mc.player != null) {
            this.packetBatches.forEach(this::processBatchedPackets);
            this.packetBatches.clear();
            boolean jumpDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
            boolean shiftDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyShift.getKey().getValue());
            mc.options.keyJump.setDown(jumpDown);
            mc.options.keyShift.setDown(shiftDown);
            mc.options.keyUse.setDown(false);
            if (this.slotActive) {
                mc.player.getInventory().selected = this.altSlot;
                this.slotActive = false;
            }
            this.canBuildNow = true;
            this.lastC05Position = null;
            this.rotSmoother.reset();
            ClientBase.delayPackets.clear();
        }
        super.onDisable();
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

    private void processBatchedPackets(List<Packet<?>> batch) {
        batch.forEach(packet -> {
            batch.remove(packet);
            PacketUtil.sendQueued((Packet<ServerGamePacketListener>) packet);
        });
    }

    // ======================================================================
    // OFFHAND / BLOCK HELPERS (从文件2合并)
    // ======================================================================
    private int getOffhandBlockCount() {
        if (mc.player == null) return 0;
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
            return offhand.getCount();
        }
        return 0;
    }

    private boolean hasOffhandBlock() {
        if (mc.player == null) return false;
        if (!this.supportOffhand.getValue()) return false;
        return BlockUtil.isPlaceable(mc.player.getOffhandItem());
    }

    private boolean hasMainhandBlock() {
        if (mc.player == null) return false;
        return BlockUtil.isPlaceable(mc.player.getMainHandItem());
    }

    private boolean hasPlaceableBlock() {
        if (mc.player == null) return false;
        if (this.supportOffhand.getValue()) {
            return hasOffhandBlock() || hasMainhandBlock();
        }
        return hasMainhandBlock();
    }

    private InteractionHand getAvailableHand() {
        if (mc.player == null) return InteractionHand.MAIN_HAND;
        if (this.supportOffhand.getValue() && hasOffhandBlock()) {
            return InteractionHand.OFF_HAND;
        }
        if (hasMainhandBlock()) {
            return InteractionHand.MAIN_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    private int findPlaceableSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int getSmartBlockSlot() {
        if (mc.player == null) return -1;
        if (this.blockSwitchMode.is("Farthest")) {
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                    slot = i;
                }
            }
            return slot;
        }
        int best = -1;
        int amount = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                if (this.blockSwitchMode.is("Most Blocks")) {
                    if (stack.getCount() > amount) {
                        amount = stack.getCount();
                        best = i;
                    }
                } else if (best == -1) {
                    best = i;
                }
            }
        }
        return best;
    }

    private boolean autoRefillBlock() {
        if (mc.player == null || mc.gameMode == null || !this.autoRefill.getValue() || !this.refillFromInventory.getValue()) return false;
        int targetHotbarSlot = mc.player.getInventory().selected;
        if (targetHotbarSlot < 0 || targetHotbarSlot > 8) return false;
        ItemStack targetStack = mc.player.getInventory().getItem(targetHotbarSlot);
        boolean targetIsPlaceable = !targetStack.isEmpty() && targetStack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(targetStack);

        if (!targetIsPlaceable) {
            int smartSlot = this.getSmartBlockSlot();
            if (smartSlot >= 0 && smartSlot < 9) {
                targetHotbarSlot = smartSlot;
                targetStack = mc.player.getInventory().getItem(targetHotbarSlot);
                targetIsPlaceable = !targetStack.isEmpty() && targetStack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(targetStack);
            }
        }

        if (this.refillBeforeEmpty.getValue()) {
            if (targetIsPlaceable) {
                int threshold = this.refillAt.getValue().intValue();
                if (targetStack.getCount() > threshold) return false;
            } else {
                targetHotbarSlot = mc.player.getInventory().selected;
                if (targetHotbarSlot < 0 || targetHotbarSlot > 8) return false;
                targetStack = ItemStack.EMPTY;
            }
        } else {
            if (this.getSmartBlockSlot() >= 0) return false;
            targetHotbarSlot = mc.player.getInventory().selected;
            if (targetHotbarSlot < 0 || targetHotbarSlot > 8) return false;
            targetStack = ItemStack.EMPTY;
        }

        if (!targetStack.isEmpty()) {
            for (int i = 9; i < 36; i++) {
                ItemStack inventoryStack = mc.player.getInventory().getItem(i);
                if (inventoryStack.isEmpty() || !(inventoryStack.getItem() instanceof BlockItem) || !BlockUtil.isPlaceable(inventoryStack) || inventoryStack.getItem() != targetStack.getItem()) {
                    continue;
                }
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, targetHotbarSlot, net.minecraft.world.inventory.ClickType.SWAP, mc.player);
                return true;
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack inventoryStack = mc.player.getInventory().getItem(i);
            if (inventoryStack.isEmpty() || !(inventoryStack.getItem() instanceof BlockItem) || !BlockUtil.isPlaceable(inventoryStack)) {
                continue;
            }
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, targetHotbarSlot, net.minecraft.world.inventory.ClickType.SWAP, mc.player);
            return true;
        }
        return false;
    }

    /**
     * 通用的"可替换"判断: 空气、草/水/雪等可替换方块、含流体(水面/岩浆)位置都算可放置。
     * 从文件2合并, 用于台阶、半砖、水中脚手架的放置。
     */
    private boolean isReplaceableAt(BlockPos pos) {
        if (mc.level == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.canBeReplaced()) return true;
        if (!state.getFluidState().isEmpty()) return true;
        return false;
    }

    /**
     * 判断某个方块能否作为"朝空气方向"的放置支撑。
     * 除了常规 entityCanStandOnFace 外, 台阶(半砖)被视为可支撑 —— 台阶顶面/侧面都能贴。
     * 从文件2合并。
     */
    private boolean canSupportAirPlacement(BlockState state, BlockPos pos, Direction faceTowardAir) {
        if (mc.level == null || mc.player == null) return false;
        if (state.entityCanStandOnFace(mc.level, pos, mc.player, faceTowardAir)) return true;
        if (state.entityCanStandOnFace(mc.level, pos, mc.player, faceTowardAir.getOpposite())) return true;
        if (state.getBlock() instanceof SlabBlock) return true;
        return false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket motion
                && motion.getId() == mc.player.getId()) {
            double length = new Vec3(motion.getXa() / 8000.0, 0.0, motion.getZa() / 8000.0).length();
            if (length >= 1.5) {
                this.velocityDelay = 60;
            }
        }
    }

    @EventTarget
    public void onUpdateHeldItem(UpdateHeldItemEvent event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && mc.player != null) {
            if (!this.supportOffhand.getValue()) {
                boolean silenceActive = this.slotActive && !this.switchMode.is("Normal");
                if ((silenceActive || this.renderItemSpoof.getValue()) && this.altSlot >= 0) {
                    event.setItemStack(mc.player.getInventory().getItem(this.altSlot));
                }
                return;
            }
            if (hasOffhandBlock()) {
                if (this.renderItemSpoof.getValue() && this.altSlot == -1) {
                    this.altSlot = mc.player.getInventory().selected;
                }
                return;
            }
            boolean silenceActive = this.slotActive && !this.switchMode.is("Normal");
            if ((silenceActive || this.renderItemSpoof.getValue()) && this.altSlot >= 0) {
                event.setItemStack(mc.player.getInventory().getItem(this.altSlot));
            }
        }
    }

    @EventTarget
    public void onJump(JumpEvent event) {
        if (!this.canBuildNow && this.currentPlacement != null && this.rotationDelay > 0) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost() && mc.player != null) {
            if (mc.player.onGround()) {
                this.airTicks = 0;
            } else {
                this.airTicks++;
            }
        }
    }

    // 限制 BPS = 限制玩家移动速度: 超速时停止前进输入, 让玩家减速回限制内
    @EventTarget
    public void onStrafe(StrafeEvent strafeEvent) {
        if (mc.player == null) return;
        if (!this.bpsLimit.getValue()) return;
        double limit = this.bps.getValue().doubleValue();
        if (limit >= 10.0) return;
        double maxSpeed = limit / 20.0;
        if (MovementUtil.getSpeed() > maxSpeed) {
            strafeEvent.setForward(0.0f);
            strafeEvent.setStrafe(0.0f);
        }
    }

    private void handleAutoContinent() {
        if (mc.player == null) return;
        if (!hasPlaceableBlock()) return;

        float speed = this.continentSpeed.getValue().floatValue();
        this.continentYaw += speed;
        if (this.continentYaw >= 360) {
            this.continentYaw -= 360;
        }
        this.continentPitch = (float) Math.sin(this.continentYaw * 0.02) * 10;

        boolean rotationsActive = false;
        try {
            Class<?> rotationsClass = Class.forName("client.nilore.modules.impl.render.Rotations");
            Object rotationsInstance = rotationsClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method isEnabledMethod = rotationsClass.getMethod("isEnabled");
            rotationsActive = (boolean) isEnabledMethod.invoke(rotationsInstance);
        } catch (Exception ignored) {}

        Rotation targetRot = new Rotation(this.continentYaw, this.continentPitch);
        RotationHandler.setTargetRotation(targetRot);

        if (!rotationsActive) {
            mc.player.setYRot(this.continentYaw);
            mc.player.setXRot(this.continentPitch);
            ServerboundMovePlayerPacket.Rot rotPacket = new ServerboundMovePlayerPacket.Rot(this.continentYaw, this.continentPitch, mc.player.onGround());
            PacketUtil.sendQueued(rotPacket);
        }

        if (this.continentAutoJump.getValue()) {
            this.continentJumpCounter++;
            int interval = this.continentJumpInterval.getValue().intValue();
            if (this.continentJumpCounter >= interval) {
                this.continentJumpCounter = 0;
                if (mc.player.onGround()) {
                    mc.player.setJumping(true);
                }
            }
        }

        if (this.supportOffhand.getValue()) {
            ItemStack mainHand = mc.player.getMainHandItem();
            ItemStack offhand = mc.player.getOffhandItem();
            boolean mainHasBlock = BlockUtil.isPlaceable(mainHand);
            boolean offHasBlock = BlockUtil.isPlaceable(offhand);

            if (!mainHasBlock && offHasBlock) {
                if (this.renderItemSpoof.getValue() && this.altSlot == -1) {
                    this.altSlot = mc.player.getInventory().selected;
                }
            } else if (!offHasBlock && mainHasBlock) {
                int slot = findPlaceableSlot();
                if (slot != -1 && slot != mc.player.getInventory().selected) {
                    if (!this.slotActive) {
                        this.altSlot = mc.player.getInventory().selected;
                    }
                    mc.player.getInventory().selected = slot;
                    this.slotActive = true;
                    this.slotActiveTick = this.slotTicks;
                }
            }
        }
    }

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        int currentOffhandCount = getOffhandBlockCount();
        boolean offhandDepleted = this.lastOffhandCount > 0 && currentOffhandCount == 0;
        this.lastOffhandCount = currentOffhandCount;

        if (this.autoContinent.getValue()) {
            handleAutoContinent();
        }

        if (this.slotActive && this.slotActiveTick != this.slotTicks) {
            mc.player.getInventory().selected = this.altSlot;
            this.slotActive = false;
        }
        this.slotTicks++;

        this.packetBatches.add(new CopyOnWriteArrayList<>());
        if (this.velocityDelay > 0) this.velocityDelay--;
        if (mc.player.onGround() && this.velocityDelay <= 30) this.velocityDelay = 0;

        if (this.autoRefill.getValue()) {
            this.autoRefillBlock();
        }

        boolean hasOffhand = hasOffhandBlock();
        int placeableSlot = getSmartBlockSlot();
        boolean hasMainhand = placeableSlot != -1;
        int totalBlocks = getBlockSlot();
        boolean hasAnyBlocks = totalBlocks > 0;

        if (!this.supportOffhand.getValue()) {
            if (placeableSlot != -1 && placeableSlot != mc.player.getInventory().selected) {
                if (!this.slotActive) {
                    this.altSlot = mc.player.getInventory().selected;
                }
                mc.player.getInventory().selected = placeableSlot;
                this.slotActive = true;
                this.slotActiveTick = this.slotTicks;
            }
        } else {
            if (hasOffhand) {
                if (this.slotActive) {
                    this.slotActive = false;
                }
                if (this.altSlot == -1) {
                    this.altSlot = mc.player.getInventory().selected;
                }
            } else if (hasAnyBlocks) {
                boolean shouldSwitch = false;
                if (hasMainhand && placeableSlot != mc.player.getInventory().selected) {
                    shouldSwitch = true;
                }
                if (shouldSwitch && placeableSlot != -1) {
                    if (!this.slotActive || offhandDepleted) {
                        this.altSlot = mc.player.getInventory().selected;
                    }
                    mc.player.getInventory().selected = placeableSlot;
                    this.slotActive = true;
                    this.slotActiveTick = this.slotTicks;
                }
            } else {
                if (this.slotActive) {
                    this.slotActive = false;
                }
            }
        }
        boolean hasBlocks = hasOffhand || hasMainhand;

        boolean jumpHeld = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
        if (this.targetYLevel == -1
                || this.targetYLevel > (int) Math.floor(mc.player.getY()) - 1
                || mc.player.onGround()
                || !MovementUtil.isMoving()
                || jumpHeld
                || this.mode.is("Normal")) {
            this.targetYLevel = (int) Math.floor(mc.player.getY()) - 1;
        }

        this.applyRotations();
        if (this.currentPlacement == null) {
            this.tellyPlaceDelayTimer = 0;
        } else {
            this.tellyPlaceDelayTimer++;
        }
        this.canBuildNow = true;
        if (this.currentPlacement != null && hasBlocks) {
            if (this.clutch.getValue() && mc.player.getDeltaMovement().y < -this.clutchTrigger.getValue().doubleValue()) {
                MotionSimulator sim = new MotionSimulator(mc.player);
                int frames = this.clutchSimFrames.getValue().intValue();
                sim.simulateWithFriction(frames);
                // 预测位置需低于放置层超过 clutchDepth 才进入 clutch, 避免正常搭路时轻微下落误触发
                if (this.currentPlacement.position.getY() > sim.y + this.clutchDepth.getValue().doubleValue()) {
                    this.canBuildNow = false;
                }
            }
        }
        if (mc.player.onGround()) {
            this.canBuildNow = true;
        }
        if (this.currentPlacement == null) {
            ClientBase.delayPackets.clear();
        } else if (this.clutch.getValue() && (!this.canBuildNow || this.velocityDelay > 0) && this.rotationDelay <= 8) {
            boolean useC06 = this.onTickRot.getValue() || !this.canBuildNow;
            if (useC06) {
                // C06 模式：不转头，让 onPreMotion 中的 c06Place 处理放置
                this.rots.setYawPitch(mc.player.getYRot(), mc.player.getXRot());
                RotationHandler.setTargetRotation(this.rots);
                this.rotationDelay++;
            } else {
                Vec3 predictedEye = new Vec3(mc.player.getX() + mc.player.getDeltaMovement().x, mc.player.getY() + mc.player.getEyeHeight() + mc.player.getDeltaMovement().y, mc.player.getZ() + mc.player.getDeltaMovement().z);
                Vec3 targetVec = faceAimPoint(this.currentPlacement.position, this.currentPlacement.facing, predictedEye);
                Rotation rotationToBlock = RotationUtil.rotationFromPoints(targetVec.x, targetVec.y, targetVec.z, predictedEye.x, predictedEye.y, predictedEye.z);
                Rotation previousTarget = RotationHandler.targetRotation;
                this.rots.setYawPitch(rotationToBlock.getYaw(), rotationToBlock.getPitch());
                RotationHandler.setTargetRotation(this.rots);
                this.rotationDelay++;
                ClientBase.delayPackets.add(() -> {});
                ClientBase.delayPackets.add(() -> {
                    RotationHandler.prevSentRotation.setYawPitch(rotationToBlock.getYaw(), rotationToBlock.getPitch());
                    float yaw = rotationToBlock.getYaw();
                    if (yaw > -360.0f && yaw < 360.0f) {
                        yaw += 720.0f;
                    }
                    if (previousTarget != null) {
                        if (previousTarget.getYaw() != rotationToBlock.getYaw()) {
                            PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(yaw, rotationToBlock.getPitch(), mc.player.onGround()));
                        }
                    } else {
                        PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(yaw, rotationToBlock.getPitch(), mc.player.onGround()));
                    }
                    this.doSnap();
                    this.onTick(event);
                });
            }
        } else {
            this.canBuildNow = true;
            boolean wasInRescue = this.rotationDelay > 0;
            ClientBase.delayPackets.clear();
            this.rotationDelay = 0;

            // SkipTicks 结束后无论 onTickRot 设置如何，都发 C06 恢复服务端旋转追踪
            if (wasInRescue) {
                Rotation resumeRot = new Rotation(mc.player.getYRot() + rotationJitter(), mc.player.getXRot() + rotationJitter());
                mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        resumeRot.getYaw(), resumeRot.getPitch(),
                        mc.player.onGround()
                ));
            }

            if (this.onTickRot.getValue()) {
                // OnTickRot: 不转头, 保持当前视角, 只靠 C06 发包欺骗
                this.rots.setYawPitch(mc.player.getYRot(), mc.player.getXRot());
            } else {
                this.calculateTargetRotation();
            }

            if (this.mode.is("Telly")) {
                mc.options.keyJump.setDown(MovementUtil.isMoving() || jumpHeld);
                if (this.airTicks < this.tellyAirTicks.getValue().intValue() && MovementUtil.isMoving()) {
                    this.rots.setYaw(mc.player.getYRot());
                    this.lastRots.setYawPitch(this.rots.getYaw(), this.rots.getPitch());
                    return;
                }
            } else if (this.mode.is("KeepY")) {
                mc.options.keyJump.setDown(MovementUtil.isMoving() || jumpHeld);
            } else {
                if (this.eagle.getValue()) {
                    mc.options.keyShift.setDown(mc.player.onGround() && isOnBlockEdge(0.3f));
                }
            }
        }
        this.lastRots.setYawPitch(this.rots.getYaw(), this.rots.getPitch());
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        event.setCancelled(true);
        if (mc.screen != null || mc.player == null || this.currentPlacement == null) return;

        // C06 旋转欺骗模式: OnTickRot 开启或 SkipTicks 期间强制启用
        if (this.onTickRot.getValue() || (!this.canBuildNow && this.rotationDelay > 0)) {
            if (this.mode.is("Telly") && this.airTicks < this.tellyAirTicks.getValue().intValue()) return;
            if (this.mode.is("Telly") && this.tellyPlaceDelay.getValue().intValue() > 0 && this.tellyPlaceDelayTimer < this.tellyPlaceDelay.getValue().intValue()) return;
            this.c06Place();
            return;
        }

        if (this.mode.is("Telly") && this.airTicks < this.tellyAirTicks.getValue().intValue()) return;
        if (this.mode.is("Telly") && this.tellyPlaceDelay.getValue().intValue() > 0 && this.tellyPlaceDelayTimer < this.tellyPlaceDelay.getValue().intValue()) return;
        boolean canRayTrace = RayTraceUtil.canRayTrace(RotationHandler.targetRotation, this.currentPlacement.facing, this.currentPlacement.position, true);
        if (!this.canBuildNow && !this.isPlacementReachable(this.currentPlacement)) return;
        if (this.rotationDelay <= 0 && !this.mode.is("Telly") && !canRayTrace) return;

        this.doSnap();
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (this.currentPlacement == null) return;

        PoseStack poseStack = event.poseStack();
        if (poseStack == null) return;

        AABB targetBox = new AABB(this.currentPlacement.position.relative(this.currentPlacement.facing));

        if (this.mark.getValue()) {
            poseStack.pushPose();
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionShader);
            Tesselator tesselator = RenderSystem.renderThreadTesselator();
            BufferBuilder bufferBuilder = tesselator.getBuilder();
            RenderSystem.setShaderColor(
                    this.markRed.getValue().floatValue() / 255.0f,
                    this.markGreen.getValue().floatValue() / 255.0f,
                    this.markBlue.getValue().floatValue() / 255.0f,
                    this.markAlpha.getValue().floatValue() / 255.0f);
            RenderUtil.drawBoxVerts(bufferBuilder, poseStack.last().pose(), targetBox);
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            poseStack.popPose();
        }

        if (this.esp.getValue()) {
            BlockPos espPos = this.currentPlacement.position.relative(this.currentPlacement.facing);
            this.renderColoredScaffoldEsp(poseStack, espPos);
        }
    }

    private void renderColoredScaffoldEsp(PoseStack poseStack, BlockPos pos) {
        if (mc.level == null || mc.player == null || pos == null || poseStack == null) return;
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        AABB box = new AABB(pos).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        int color = this.getScaffoldEspColor();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        poseStack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(r, g, b, 1.0F);
        RenderSystem.lineWidth(2.0F);
        RenderUtil.drawOutlineBox(box.inflate(0.002D), poseStack);
        RenderSystem.lineWidth(1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private int getScaffoldEspColor() {
        String selected = this.espColor.getValue();
        if (selected == null) return 0xFFFFFF;
        switch (selected) {
            case "Rainbow": {
                float hue = (System.currentTimeMillis() % 3000L) / 3000.0F;
                return Color.HSBtoRGB(hue, 0.90F, 1.0F) & 0xFFFFFF;
            }
            case "Cyan": return 0x00FFFF;
            case "Red": return 0xFF4040;
            case "Green": return 0x40FF60;
            case "Blue": return 0x4080FF;
            case "White":
            default: return 0xFFFFFF;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!this.blockCounter.getValue()) return;
        this.renderShelfHud(event);
    }

    private void renderShelfHud(Render2DEvent event) {
        if (mc.player == null) return;

        if (!this.shelfHudInitialized) {
            this.shelfInitialBlocks = this.getBlockSlot();
            this.shelfHudInitialized = true;
            this.shelfProgress.reset(0.0f);
            this.hudLastFrameTime = 0L;
        }

        int totalBlocks = this.getBlockSlot();
        float target = Mth.clamp((float) totalBlocks / Math.max(this.shelfInitialBlocks, 64), 0.0f, 1.0f);
        long now = System.currentTimeMillis();
        if (this.hudLastFrameTime == 0L) this.hudLastFrameTime = now;
        float deltaSec = Math.min((now - this.hudLastFrameTime) / 1000.0f, 0.05f);
        this.hudLastFrameTime = now;
        this.shelfProgress.setTargetValue(target);
        this.shelfProgress.update(deltaSec);
        float animProg = Mth.clamp(this.shelfProgress.getValue(), 0.0f, 1.0f);

        switch (this.blockCounterStyle.getValue()) {
            case "Modern" -> this.renderModernShelfHud(event, totalBlocks, animProg);
            case "Amunix" -> this.renderSimpleShelfHud(event, totalBlocks, animProg);
            case "Naven" -> this.renderNavenBlockHud(event, totalBlocks);
            case "Nitro" -> this.renderNitroBlockHud(event, totalBlocks, animProg);
        }
    }

    private void renderSimpleShelfHud(Render2DEvent event, int totalBlocks, float animProg) {
        String labelStr = "Scaffold";
        String bpsStr = String.format("%.1f b/s", MovementUtil.getSpeedBps());
        String blocksStr = totalBlocks + "blocks";
        float bpsWidth = shelfBpsFont.getWidth(bpsStr);
        float blocksWidth = shelfBlocksFont.getWidth(blocksStr);
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float barWidth = 115.0f;
        float barHeight = 2.0f;
        float offX = this.blockCounterOffsetX.getValue().floatValue();
        float offY = this.blockCounterOffsetY.getValue().floatValue();
        float barX = (screenWidth - barWidth) / 2.0f + offX;
        float barY = screenHeight / 2.0f + 12.0f + offY;

        this.updateBlockCounterDragging(barX, barY - 15.0f, barWidth, 20.0f);
        offX = this.blockCounterOffsetX.getValue().floatValue();
        offY = this.blockCounterOffsetY.getValue().floatValue();
        barX = (screenWidth - barWidth) / 2.0f + offX;
        barY = screenHeight / 2.0f + 12.0f + offY;

        float blocksX = barX + barWidth - blocksWidth;
        float bpsX = blocksX - 10.0f - bpsWidth;
        float textY = barY - 3.0f;
        final float fBarX = barX, fBarY = barY, fBlocksX = blocksX, fBpsX = bpsX, fTextY = textY;

        Renderer.render(event.guiGraphics(), drawContext -> {
            try (Paint paint = new Paint()) {
                paint.setColor(0xFFFFFFFF);
                drawContext.drawString(labelStr, fBarX, fTextY + 1.0f, shelfLabelFont, paint);
                paint.setColor(totalBlocks < 40 ? 0xFFFF4D4D : 0xFFFFFFFF);
                drawContext.drawString(blocksStr, fBlocksX, fTextY, shelfBlocksFont, paint);
                paint.setColor(0xFFFFFFFF);
                drawContext.drawString(bpsStr, fBpsX, fTextY, shelfBpsFont, paint);
                paint.setColor(0x80333333);
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(fBarX, fBarY, barWidth, barHeight, 2.0f), paint);
                if (animProg > 0.01f) {
                    drawContext.save();
                    drawContext.clip(Rectangle.ofXYWH(fBarX, fBarY, barWidth * animProg, barHeight));
                    paint.setColor(0xFFFFFFFF);
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(fBarX, fBarY, barWidth, barHeight, 1.0f), paint);
                    drawContext.restore();
                }
            }
        });
    }

    private void renderModernShelfHud(Render2DEvent event, int totalBlocks, float animProg) {
        String blocksStr = totalBlocks + " blocks";
        float textWidth = shelfBlocksFont.getWidth(blocksStr);
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float hudHeight = 30.0f;
        float ringDiameter = hudHeight - 10.0f;
        float hudWidth = 5.0f + ringDiameter + 8.0f + textWidth + 12.0f;
        float offX = this.blockCounterOffsetX.getValue().floatValue();
        float offY = this.blockCounterOffsetY.getValue().floatValue();
        float hudX = (screenWidth - hudWidth) / 2.0f + offX;
        float hudY = screenHeight / 2.0f + 12.0f + offY;

        this.updateBlockCounterDragging(hudX, hudY, hudWidth, hudHeight);
        offX = this.blockCounterOffsetX.getValue().floatValue();
        offY = this.blockCounterOffsetY.getValue().floatValue();
        hudX = (screenWidth - hudWidth) / 2.0f + offX;
        hudY = screenHeight / 2.0f + 12.0f + offY;

        float ringX = hudX + 5.0f;
        float ringY = hudY + (hudHeight - ringDiameter) / 2.0f;
        float textX = ringX + ringDiameter + 8.0f;
        float textY = hudY + hudHeight / 2.0f - shelfBlocksFont.getMetrics().capHeight() / 2.0f + 4.0f;
        final float fHudX = hudX, fHudY = hudY, fRingX = ringX, fRingY = ringY, fTextX = textX, fTextY = textY;

        Renderer.render(event.guiGraphics(), drawContext -> {
            try (Paint paint = new Paint()) {
                paint.setColor(0xCC10151C);
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(fHudX, fHudY, hudWidth, hudHeight, hudHeight / 2.0f), paint);

                float strokeWidth = 2.25f;
                float centerX = fRingX + ringDiameter / 2.0f;
                float centerY = fRingY + ringDiameter / 2.0f;
                float radius = ringDiameter / 2.0f;
                paint.setAntialias(true);
                paint.setStrokeWidth(strokeWidth);
                paint.setStrokeCap(Paint.StrokeCap.STROKE);
                paint.setStrokeJoin(Paint.StrokeJoin.ROUND);
                paint.setColor(0x5538D9FF);
                drawContext.drawArc(fRingX, fRingY, fRingX + ringDiameter, fRingY + ringDiameter, -90.0f, 360.0f, false, 64, paint);
                if (animProg > 0.001f) {
                    float sweepAngle = 360.0f * animProg;
                    paint.setColor(0xFF24D8FF);
                    drawContext.drawArc(fRingX, fRingY, fRingX + ringDiameter, fRingY + ringDiameter, -90.0f, sweepAngle, false, 64, paint);
                    if (animProg < 0.999f) {
                        float capRadius = strokeWidth / 2.0f;
                        float endAngle = (float) Math.toRadians(-90.0f + sweepAngle);
                        float endX = centerX + (float) Math.cos(endAngle) * radius;
                        float endY = centerY + (float) Math.sin(endAngle) * radius;
                        paint.setStrokeCap(Paint.StrokeCap.FILL);
                        drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(centerX - capRadius, centerY - radius - capRadius, strokeWidth, strokeWidth, capRadius), paint);
                        drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(endX - capRadius, endY - capRadius, strokeWidth, strokeWidth, capRadius), paint);
                    }
                }

                paint.setStrokeCap(Paint.StrokeCap.FILL);
                paint.setColor(totalBlocks < 40 ? 0xFFFF4D4D : 0xFFFFFFFF);
                drawContext.drawString(blocksStr, fTextX, fTextY, shelfBlocksFont, paint);
            }
        });
    }

    private void renderNavenBlockHud(Render2DEvent event, int totalBlocks) {
        String label = "Blocks: ";
        String count = String.valueOf(totalBlocks);
        FontRenderer navenFont = FontPresets.openSans(15f);
        float textWidth = navenFont.getWidth(label + count);
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float boxW = textWidth + 12f;
        float boxH = 18f;
        float offX = this.blockCounterOffsetX.getValue().floatValue();
        float offY = this.blockCounterOffsetY.getValue().floatValue();
        float x = screenWidth / 2f - boxW / 2f + offX;
        float y = screenHeight / 2f + 30f + offY;

        this.updateBlockCounterDragging(x, y, boxW, boxH);
        offX = this.blockCounterOffsetX.getValue().floatValue();
        offY = this.blockCounterOffsetY.getValue().floatValue();
        x = screenWidth / 2f - boxW / 2f + offX;
        y = screenHeight / 2f + 30f + offY;

        int bgAlpha = 190;
        int bodyColor = (bgAlpha << 24) | 0x000000;
        int headerColor = (bgAlpha << 24) | 0x962D2D;
        final float fx = x, fy = y;
        Renderer.render(event.guiGraphics(), drawContext -> {
            try (Paint paint = new Paint()) {
                paint.setColor(bodyColor);
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(fx, fy, boxW, boxH, 4f), paint);
                paint.setColor(headerColor);
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(fx, fy, boxW, 3f, 2f), paint);
                float ty = fy + 13f;
                paint.setColor(0xFFFFFFFF);
                drawContext.drawString(label, fx + 6f, ty, navenFont, paint);
                paint.setColor(totalBlocks < 40 ? 0xFFFF4D4D : 0xFFFFFFFF);
                drawContext.drawString(count, fx + 6f + navenFont.getWidth(label), ty, navenFont, paint);
            }
        });
    }

    private void renderNitroBlockHud(Render2DEvent event, int totalBlocks, float animProg) {
        if (mc.player == null || event == null) return;
        ItemStack displayStack = this.getNitroDisplayBlock();
        if (displayStack.isEmpty()) return;

        final ItemStack iconStack = displayStack;
        final String itemName = displayStack.getHoverName().getString();
        final String blocksText = totalBlocks + " blocks";
        final String bpsText = String.format("%.1f BPS", MovementUtil.getSpeedBps());

        int initialBlocks = Math.max(Math.max(this.shelfInitialBlocks, totalBlocks), 1);
        int usedBlocks = Math.max(0, initialBlocks - totalBlocks);
        final String usedText = "Used " + usedBlocks + " / " + initialBlocks;

        FontRenderer nameFont = FontPresets.pingfang(14.0f);
        FontRenderer mainFont = FontPresets.openSans(13.0f);
        FontRenderer smallFont = FontPresets.openSans(12.0f);

        boolean horizontal = this.nitroHorizontal.getValue();
        boolean hideName = this.nitroHideBlockName.getValue();

        float scale = Mth.clamp(this.nitroSize.getValue().floatValue(), 0.5f, 2.0f);
        float extraX = this.blockCounterOffsetX.getValue().floatValue() + this.nitroOffsetX.getValue().floatValue();
        float extraY = this.blockCounterOffsetY.getValue().floatValue() + this.nitroOffsetY.getValue().floatValue() + this.nitroOffsetZ.getValue().floatValue();

        float baseIconX = 8.0f;
        float baseIconY;
        float baseTextX = 31.0f;
        float baseRightPad = 9.0f;
        float baseCardW;
        float baseCardH;

        if (horizontal) {
            baseCardH = 30.0f;
            baseIconY = 7.0f;
            float contentW = 0.0f;
            if (!hideName) contentW += nameFont.getWidth(itemName) + 13.0f;
            contentW += mainFont.getWidth(blocksText) + 13.0f;
            contentW += mainFont.getWidth(bpsText) + 13.0f;
            contentW += smallFont.getWidth(usedText);
            baseCardW = Math.max(150.0f, baseTextX + contentW + baseRightPad);
        } else {
            baseCardH = hideName ? 40.0f : 52.0f;
            baseIconY = (baseCardH - 16.0f) / 2.0f;
            float secondRowW = mainFont.getWidth(blocksText) + 18.0f + mainFont.getWidth(bpsText);
            float required = Math.max(secondRowW, smallFont.getWidth(usedText));
            if (!hideName) required = Math.max(required, nameFont.getWidth(itemName));
            baseCardW = Math.max(142.0f, baseTextX + required + baseRightPad);
        }

        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float cardW = baseCardW * scale;
        float cardH = baseCardH * scale;
        float cardX = (screenWidth - cardW) / 2.0f + extraX;
        float cardY = screenHeight / 2.0f + extraY;

        this.updateBlockCounterDragging(cardX, cardY, cardW, cardH);
        extraX = this.blockCounterOffsetX.getValue().floatValue() + this.nitroOffsetX.getValue().floatValue();
        extraY = this.blockCounterOffsetY.getValue().floatValue() + this.nitroOffsetY.getValue().floatValue() + this.nitroOffsetZ.getValue().floatValue();
        cardX = (screenWidth - cardW) / 2.0f + extraX;
        cardY = screenHeight / 2.0f + extraY;

        int backgroundAlpha = this.nitroCustomAlpha.getValue() ? Mth.clamp(this.nitroAlpha.getValue().intValue(), 0, 255) : 0xCC;
        final int cardColor = (backgroundAlpha << 24) | 0x00121820;
        final int normalColor = 0xFFFFFFFF;
        final int countColor = totalBlocks < 40 ? 0xFFFF4D4D : 0xFFFFFFFF;

        ModuleListHud moduleList = NiloreClient.getInstance().getHudManager() != null
                ? NiloreClient.getInstance().getHudManager().getHudElement(ModuleListHud.class)
                : null;

        final float fScale = scale, fCardX = cardX, fCardY = cardY, fBaseCardW = baseCardW, fBaseCardH = baseCardH;
        final boolean fHorizontal = horizontal, fHideName = hideName;

        Renderer.render(event.guiGraphics(), drawContext -> {
            drawContext.save();
            drawContext.translate(fCardX, fCardY);
            drawContext.scale(fScale, fScale);
            try (Paint paint = new Paint()) {
                paint.setAntialias(true);
                paint.setColor(cardColor);
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(0.0f, 0.0f, fBaseCardW, fBaseCardH, 8.0f), paint);

                if (fHorizontal) {
                    float rowY = 18.5f;
                    float cursor = baseTextX;
                    if (!fHideName) {
                        cursor = this.drawNitroThemeText(drawContext, paint, itemName, cursor, rowY, nameFont, moduleList);
                        cursor += 13.0f;
                    }
                    paint.setColor(countColor);
                    drawContext.drawString(blocksText, cursor, rowY, mainFont, paint);
                    cursor += mainFont.getWidth(blocksText) + 13.0f;
                    paint.setColor(normalColor);
                    drawContext.drawString(bpsText, cursor, rowY, mainFont, paint);
                    cursor += mainFont.getWidth(bpsText) + 13.0f;
                    paint.setColor(normalColor);
                    drawContext.drawString(usedText, cursor, rowY, smallFont, paint);
                } else {
                    float nameY = 9.0f;
                    float blocksY = fHideName ? 14.0f : 25.0f;
                    float usedY = fHideName ? 28.0f : 40.0f;
                    if (!fHideName) {
                        this.drawNitroThemeText(drawContext, paint, itemName, baseTextX, nameY, nameFont, moduleList);
                    }
                    paint.setColor(countColor);
                    drawContext.drawString(blocksText, baseTextX, blocksY, mainFont, paint);
                    float bpsX = fBaseCardW - baseRightPad - mainFont.getWidth(bpsText);
                    paint.setColor(normalColor);
                    drawContext.drawString(bpsText, bpsX, blocksY, mainFont, paint);
                    paint.setColor(normalColor);
                    drawContext.drawString(usedText, baseTextX, usedY, smallFont, paint);
                }

                float realProgress = Mth.clamp((float) totalBlocks / (float) Math.max(1, initialBlocks), 0.0f, 1.0f);
                float visibleProgress = Mth.clamp(animProg, 0.0f, 1.0f);
                if (visibleProgress <= 0.001f && realProgress > 0.001f) {
                    visibleProgress = realProgress;
                }

                float progressX = 7.0f;
                float progressW = Math.max(1.0f, fBaseCardW - 14.0f);
                float progressH = 3.0f;
                float progressY = fBaseCardH - progressH - 2.0f;

                paint.setColor(0x55FFFFFF);
                drawContext.drawRect(Rectangle.ofXYWH(progressX, progressY, progressW, progressH), paint);

                float filledW = Math.max(0.0f, progressW * visibleProgress);
                if (filledW > 0.05f) {
                    int progressColor = totalBlocks < 40 ? 0xFFFF4040 : (moduleList != null ? moduleList.getThemeColor(0, visibleProgress, 1) : 0xFFFFFFFF);
                    paint.setColor(progressColor);
                    drawContext.drawRect(Rectangle.ofXYWH(progressX, progressY, filledW, progressH), paint);
                }
            } finally {
                drawContext.restore();
            }
        });

        var guiPose = event.guiGraphics().pose();
        guiPose.pushPose();
        guiPose.translate(cardX + baseIconX * scale, cardY + baseIconY * scale, 200.0f);
        guiPose.scale(scale, scale, 1.0f);
        event.guiGraphics().renderItem(iconStack, 0, 0);
        guiPose.popPose();
    }

    private void updateBlockCounterDragging(float hudX, float hudY, float hudWidth, float hudHeight) {
        if (!(mc.screen instanceof ChatScreen)) {
            this.blockCounterDragging = false;
            this.blockCounterMouseWasDown = false;
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double[] rawX = new double[1];
        double[] rawY = new double[1];
        GLFW.glfwGetCursorPos(window, rawX, rawY);

        double mouseX = rawX[0] * mc.getWindow().getGuiScaledWidth() / Math.max(1.0, mc.getWindow().getScreenWidth());
        double mouseY = rawY[0] * mc.getWindow().getGuiScaledHeight() / Math.max(1.0, mc.getWindow().getScreenHeight());

        boolean hovered = mouseX >= hudX && mouseX <= hudX + hudWidth && mouseY >= hudY && mouseY <= hudY + hudHeight;

        if (leftDown && !this.blockCounterMouseWasDown && hovered) {
            this.blockCounterDragging = true;
            this.blockCounterDragStartMouseX = mouseX;
            this.blockCounterDragStartMouseY = mouseY;
            this.blockCounterDragStartOffsetX = this.blockCounterOffsetX.getValue().floatValue();
            this.blockCounterDragStartOffsetY = this.blockCounterOffsetY.getValue().floatValue();
        }

        if (this.blockCounterDragging) {
            if (!leftDown) {
                this.blockCounterDragging = false;
            } else {
                float newX = this.blockCounterDragStartOffsetX + (float)(mouseX - this.blockCounterDragStartMouseX);
                float newY = this.blockCounterDragStartOffsetY + (float)(mouseY - this.blockCounterDragStartMouseY);
                this.blockCounterOffsetX.setValue(Mth.clamp(newX, -2000.0f, 2000.0f));
                this.blockCounterOffsetY.setValue(Mth.clamp(newY, -2000.0f, 2000.0f));
            }
        }
        this.blockCounterMouseWasDown = leftDown;
    }

    private float drawNitroThemeText(client.nilore.render.DrawContext drawContext, Paint paint, String text, float x, float y, FontRenderer font, ModuleListHud moduleList) {
        float cursor = x;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            String ch = String.valueOf(text.charAt(i));
            float progress = len <= 1 ? 0.0f : i / (float)(len - 1);
            int color = moduleList != null ? moduleList.getThemeColor(i, progress, Math.max(1, len - 1)) : 0xFFFFFFFF;
            paint.setColor(color);
            drawContext.drawString(ch, cursor, y, font, paint);
            cursor += font.getWidth(ch);
        }
        return cursor;
    }

    private ItemStack getNitroDisplayBlock() {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(mainHand)) return mainHand;
        int selected = mc.player.getInventory().selected;
        if (selected >= 0 && selected < 9) {
            ItemStack selectedStack = mc.player.getInventory().getItem(selected);
            if (!selectedStack.isEmpty() && selectedStack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(selectedStack)) return selectedStack;
        }
        int slot = this.getSmartBlockSlot();
        if (slot >= 0 && slot < 9) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) return stack;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (this.supportOffhand.getValue() && !offhand.isEmpty() && offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) return offhand;
        return ItemStack.EMPTY;
    }

    private int getBlockSlot() {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                total += stack.getCount();
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
            total += offhand.getCount();
        }
        return total;
    }

    private boolean isPlacementReachable(PlacementTarget target) {
        if (target == null || mc.player == null) return false;
        Vec3 blockCenter = new Vec3(target.position.getX() + 0.5, target.position.getY() + 0.5f, target.position.getZ() + 0.5);
        Vec3 hitPoint = blockCenter.add(new Vec3(
                target.facing.getNormal().getX() * 0.5,
                target.facing.getNormal().getY() * 0.5,
                target.facing.getNormal().getZ() * 0.5));
        Rotation currentTarget = RotationHandler.targetRotation;
        if (currentTarget == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 toBlock = hitPoint.subtract(eye);
        return toBlock.lengthSqr() <= 20.25
                && toBlock.normalize().dot(Vec3.atLowerCornerOf(target.facing.getNormal().multiply(-1)).normalize()) >= 0.0;
    }

    private void calculateTargetRotation() {
        if (this.currentPlacement == null || mc.player == null) return;

        Rotation held = RotationHandler.targetRotation;
        if (held != null && RayTraceUtil.canRayTrace(
                held, this.currentPlacement.facing, this.currentPlacement.position, true)) {
            return;
        }

        this.raycastBudget = ROTATION_RAYCAST_BUDGET;

        float realYaw = mc.player.getYRot();
        if (mc.options.keyDown.isDown()) {
            realYaw += 180.0f;
            if (mc.options.keyLeft.isDown()) realYaw += 45.0f;
            else if (mc.options.keyRight.isDown()) realYaw -= 45.0f;
        } else if (mc.options.keyUp.isDown()) {
            if (mc.options.keyLeft.isDown()) realYaw -= 45.0f;
            else if (mc.options.keyRight.isDown()) realYaw += 45.0f;
        } else if (mc.options.keyRight.isDown()) {
            realYaw += 90.0f;
        } else if (mc.options.keyLeft.isDown()) {
            realYaw -= 90.0f;
        }

        float yaw = realYaw - 180.0f;
        float idealPitch = 75.5f;
        float currentPitch = this.rots.getPitch();

        if (isRotationValidForPlacement(new Rotation(yaw, idealPitch))) {
            applyRotationWithSpeed(yaw, idealPitch);
            return;
        }
        if (isRotationValidForPlacement(new Rotation(yaw, currentPitch))) {
            applyRotationWithSpeed(yaw, currentPitch);
            return;
        }
        if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            Rotation direct = RotationUtil.exactRotation(eye,
                    faceAimPoint(this.currentPlacement.position, this.currentPlacement.facing, eye));
            if (isRotationValidForPlacement(direct)) {
                applyRotationWithSpeed(direct.getYaw(), direct.getPitch());
                return;
            }
        }
        Float nearest = findValidPitchNear(yaw, currentPitch);
        if (nearest != null) {
            applyRotationWithSpeed(yaw, nearest);
            return;
        }
        Float towardIdeal = findValidPitchNear(yaw, idealPitch);
        if (towardIdeal != null) {
            applyRotationWithSpeed(yaw, towardIdeal);
            return;
        }
        Rotation optimal = findOptimalRotation(yaw);
        if (optimal != null) {
            applyRotationWithSpeed(optimal.getYaw(), optimal.getPitch());
            return;
        }
        applyRotationWithSpeed(yaw, currentPitch);
    }

    private int raycastBudget;
    private static final int ROTATION_RAYCAST_BUDGET = 72;

    private boolean isRotationValidForPlacement(Rotation rotation) {
        if (this.currentPlacement == null) return false;
        if (this.raycastBudget <= 0) return false;
        this.raycastBudget--;
        return RayTraceUtil.canRayTrace(rotation, this.currentPlacement.facing, this.currentPlacement.position, true);
    }

    private Float findValidPitchNear(float yaw, float center) {
        if (this.currentPlacement == null) return null;

        for (float offset = 0.0f; offset <= 30.0f; offset += 3.0f) {
            float down = Mth.clamp(center - offset, -90.0f, 90.0f);
            if (this.probePitch(yaw, down)) {
                return this.refinePitch(yaw, center, offset, true);
            }
            float up = center + offset;
            if (up <= 90.0f && this.probePitch(yaw, up)) {
                return this.refinePitch(yaw, center, offset, false);
            }
        }
        return null;
    }

    private boolean probePitch(float yaw, float pitch) {
        if (this.raycastBudget <= 0) return false;
        return this.isRotationValidForPlacement(new Rotation(yaw, pitch));
    }

    private Float refinePitch(float yaw, float center, float coarseOffset, boolean downward) {
        float start = Math.max(0.0f, coarseOffset - 3.0f);
        for (float offset = start; offset < coarseOffset; offset += 0.3f) {
            float test = downward ? Mth.clamp(center - offset, -90.0f, 90.0f) : center + offset;
            if (test > 90.0f) break;
            if (this.probePitch(yaw, test)) {
                return test;
            }
        }
        return downward ? Mth.clamp(center - coarseOffset, -90.0f, 90.0f) : center + coarseOffset;
    }

    private Rotation findOptimalRotation(float baseYaw) {
        if (this.currentPlacement == null) return null;

        float currentPitch = this.rots.getPitch();
        for (float yawOffset = 0.0f; yawOffset < 180.0f; yawOffset += 6.0f) {
            for (float pitchOffset = 0.0f; pitchOffset <= 25.0f; pitchOffset += 5.0f) {
                for (int i = 0; i < 2; i++) {
                    if (this.raycastBudget <= 0) return null;
                    float testPitch = Mth.clamp(currentPitch + (i == 0 ? -pitchOffset : pitchOffset), -90.0f, 90.0f);

                    Rotation test1 = new Rotation(baseYaw + yawOffset, testPitch);
                    if (this.isRotationValidForPlacement(test1)) return test1;
                    if (yawOffset == 0.0f) continue;

                    Rotation test2 = new Rotation(baseYaw - yawOffset, testPitch);
                    if (this.isRotationValidForPlacement(test2)) return test2;
                }
            }
        }
        return null;
    }

    private void applyRotationWithSpeed(float yaw, float pitch) {
        if (this.currentPlacement == null || mc.player == null) return;

        float speed;
        if (this.syncRotSpeed.getValue()) {
            Rotation current = RotationHandler.targetRotation;
            if (current != null) {
                float realYaw = mc.player.getYRot();
                float currentDiffToReal = Math.abs(Mth.wrapDegrees(current.getYaw() - realYaw));
                float newDiffToReal = Math.abs(Mth.wrapDegrees(yaw - realYaw));
                if (newDiffToReal < currentDiffToReal) {
                    speed = this.returnSpeed.getValue().floatValue();
                } else {
                    speed = this.turnSpeed.getValue().floatValue();
                }
            } else {
                speed = this.turnSpeed.getValue().floatValue();
            }
        } else {
            speed = this.rotationSpeed.getValue().floatValue();
        }

        Rotation desired = new Rotation(yaw, pitch);
        float jitterScale = this.jitter.getValue().floatValue();
        if (jitterScale > 0.0f) {
            desired = new Rotation(yaw + jitterOffset() * jitterScale,
                    Mth.clamp(pitch + jitterOffset() * jitterScale, -90.0f, 90.0f));
        }

        Rotation rotation = desired;
        if (speed < 360f) {
            Rotation current = RotationHandler.targetRotation;
            if (current == null) {
                current = new Rotation(mc.player.getYRot(), mc.player.getXRot());
            }
            rotation = this.rotSmoother.tick(current, desired, speed);
        }
        Rotation anchor = RotationHandler.prevRotation != null
                ? RotationHandler.prevRotation
                : new Rotation(mc.player.getYRot(), mc.player.getXRot());
        rotation = RotationSmoother.patchConstantRotation(rotation, anchor);
        rotation = this.rotSmoother.breakDuplicateDelta(rotation, anchor);

        this.rots.setYawPitch(rotation.getYaw(), rotation.getPitch());
        RotationHandler.setTargetRotation(this.rots);
    }

    private void applyRotations() {
        if (mc.player == null || mc.level == null) return;
        Vec3 eye = mc.player.getEyePosition();
        if (!this.canBuildNow) {
            eye = mc.player.getEyePosition().add(mc.player.getDeltaMovement().multiply(2.0, 2.0, 2.0));
        }
        if (this.clutch.getValue() && mc.player.getDeltaMovement().y < 0.01) {
            MotionSimulator sim = new MotionSimulator(mc.player);
            sim.simulateWithFriction(2);
            eye = new Vec3(eye.x, Math.max(sim.y + mc.player.getEyeHeight(), eye.y), eye.z);
        }
        BlockPos belowFeet = BlockPos.containing(eye.x, this.targetYLevel + 0.1f, eye.z);
        int feetX = belowFeet.getX();
        int feetZ = belowFeet.getZ();
        if (mc.level.getBlockState(belowFeet).entityCanStandOn(mc.level, belowFeet, mc.player)) return;
        if (this.currentPlacement != null && this.isPlacementStillValid(this.currentPlacement, eye)) {
            return;
        }
        this.currentPlacement = null;
        if (this.isAbovePlaceable(eye, belowFeet)) return;
        for (int radius = 1; radius <= 6; radius++) {
            if (this.isAbovePlaceable(eye, new BlockPos(feetX, this.targetYLevel - radius, feetZ))) return;
            for (int x = 1; x <= radius; x++) {
                for (int z = 0; z <= radius - x; z++) {
                    int yOff = radius - x - z;
                    for (int signX = 0; signX <= 1; signX++) {
                        for (int signZ = 0; signZ <= 1; signZ++) {
                            BlockPos test = new BlockPos(
                                    feetX + (signX == 0 ? x : -x),
                                    this.targetYLevel - yOff,
                                    feetZ + (signZ == 0 ? z : -z));
                            if (!this.isWithinSearchReach(eye, test)) continue;
                            if (this.isAbovePlaceable(eye, test)) return;
                        }
                    }
                }
            }
        }
    }

    private boolean isWithinSearchReach(Vec3 eye, BlockPos pos) {
        double dx = pos.getX() + 0.5 - eye.x;
        double dy = pos.getY() + 0.5 - eye.y;
        double dz = pos.getZ() + 0.5 - eye.z;
        return dx * dx + dy * dy + dz * dz <= 20.25;
    }

    private boolean isPlacementStillValid(PlacementTarget target, Vec3 eye) {
        if (target == null || target.facing == null) return false;
        BlockPos support = target.position;
        // 支撑面现在也接受台阶(半砖)及朝空气方向可站立的方块
        if (!this.canSupportAirPlacement(mc.level.getBlockState(support), support, target.facing)) {
            return false;
        }
        // 待放置位置: 空气 / 可替换方块 / 含流体(水面/岩浆) 都算有效
        if (!this.isReplaceableAt(support.relative(target.facing))) {
            return false;
        }
        return faceAimPoint(support, target.facing, eye).distanceToSqr(eye) <= 20.25;
    }

    private float jitterOffset() {
        float offset = 0.03f + this.jitterRandom.nextFloat() * 0.04f;
        return this.jitterRandom.nextBoolean() ? offset : -offset;
    }

    private boolean isAbovePlaceable(Vec3 from, BlockPos pos) {
        if (mc.level == null || mc.player == null) return false;
        // 待放置位置: 空气 / 可替换方块 / 含流体(水面/岩浆) 都可放置
        if (!this.isReplaceableAt(pos)) return false;
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5f, pos.getZ() + 0.5);
        BlockPos bestSupport = null;
        Direction bestFace = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            Vec3 offsetCenter = center.add(new Vec3(
                    direction.getNormal().getX() * 0.5,
                    direction.getNormal().getY() * 0.5,
                    direction.getNormal().getZ() * 0.5));
            BlockPos offset = pos.offset(direction.getNormal());
            Direction face = direction.getOpposite();
            // 支撑面判定: 允许台阶(半砖)朝任意方向作为支撑
            if (!this.canSupportAirPlacement(mc.level.getBlockState(offset), offset, face)) {
                continue;
            }
            Vec3 delta = offsetCenter.subtract(from);
            if (delta.lengthSqr() > 20.25
                    || delta.normalize().dot(Vec3.atLowerCornerOf(direction.getNormal()).normalize()) < 0.0) {
                continue;
            }
            // Rank by the face point the view will actually aim at rather than by the block
            // centre: the nearest face means the shortest rotation and the cleanest click ray.
            double distance = faceAimPoint(offset, face, from).distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSupport = offset;
                bestFace = face;
            }
        }
        if (bestSupport == null) {
            return false;
        }
        this.currentPlacement = new PlacementTarget(
                new BlockPos(bestSupport.getX(), bestSupport.getY(), bestSupport.getZ()), bestFace);
        return true;
    }

    private boolean hasPlaceableInHand() {
        if (mc.player == null) return false;
        if (this.supportOffhand.getValue()) {
            return BlockUtil.isPlaceable(mc.player.getMainHandItem())
                    || BlockUtil.isPlaceable(mc.player.getOffhandItem());
        }
        return BlockUtil.isPlaceable(mc.player.getMainHandItem());
    }

    private InteractionHand placementHand() {
        if (mc.player == null) return InteractionHand.MAIN_HAND;
        if (this.supportOffhand.getValue() && hasOffhandBlock()) {
            return InteractionHand.OFF_HAND;
        }
        if (BlockUtil.isPlaceable(mc.player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (BlockUtil.isPlaceable(mc.player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    private boolean shouldBuild() {
        if (mc.player == null || mc.level == null) return false;
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        // 使用 isReplaceableAt: 空气/可替换方块/含流体(水面)都算可放
        return this.isReplaceableAt(below) && this.hasPlaceableInHand();
    }

    private float rotationJitter() {
        float offset = (float) (Math.random() * 0.04 + 0.03);
        return Math.random() > 0.5 ? offset : -offset;
    }

    private void c06Place() {
        if (this.currentPlacement == null || mc.player == null || mc.gameMode == null) return;

        Direction facing = this.currentPlacement.facing;
        if (facing == null) return;
        if (facing == Direction.UP && !mc.player.onGround() && MovementUtil.isMoving()
                && !mc.options.keyJump.isDown() && !this.mode.is("Normal")) return;

        if (!this.hasPlaceableInHand()) return;
        InteractionHand hand = this.placementHand();

        Vec3 aim = faceAimPoint(this.currentPlacement.position, facing, mc.player.getEyePosition());
        Rotation targetRot = RotationUtil.exactRotation(mc.player.getEyePosition(), aim);

        if (!RayTraceUtil.canRayTrace(targetRot, facing, this.currentPlacement.position, true)) {
            return;
        }

        float origYaw = mc.player.getYRot();
        float origPitch = mc.player.getXRot();

        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                targetRot.getYaw() + rotationJitter(),
                targetRot.getPitch() + rotationJitter(),
                mc.player.onGround()
        ));

        BlockHitResult hit = new BlockHitResult(aim, facing, this.currentPlacement.position, false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);

        if (result == InteractionResult.SUCCESS) {
            if (!this.swingMode.is("Server")) {
                mc.player.swing(hand);
            }
        }

        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                origYaw + rotationJitter(),
                origPitch + rotationJitter(),
                mc.player.onGround()
        ));
    }

    private void doSnap() {
        if (this.currentPlacement == null || mc.player == null || mc.gameMode == null) return;

        if (!this.hasPlaceableInHand()) return;
        InteractionHand hand = this.placementHand();

        if (!this.canBuildNow && this.clutch.getValue() && !this.currentPlacement.position.equals(this.lastC05Position)) {
            this.lastC05Position = this.currentPlacement.position;
            Vec3 c05Eye = new Vec3(mc.player.getX() + mc.player.getDeltaMovement().x, mc.player.getY() + mc.player.getEyeHeight() + mc.player.getDeltaMovement().y, mc.player.getZ() + mc.player.getDeltaMovement().z);
            Vec3 c05Target = faceAimPoint(this.currentPlacement.position, this.currentPlacement.facing, c05Eye);
            Rotation c05Rotation = RotationUtil.rotationFromPoints(c05Target.x, c05Target.y, c05Target.z, c05Eye.x, c05Eye.y, c05Eye.z);
            float yaw = c05Rotation.getYaw();
            if (yaw > -360.0f && yaw < 360.0f) {
                yaw += 720.0f;
            }
            PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(yaw, c05Rotation.getPitch(), mc.player.onGround()));
        }

        Direction facing = this.currentPlacement.facing;
        boolean jumpHeld = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
        if (facing == null) return;
        if (facing == Direction.UP && !mc.player.onGround() && MovementUtil.isMoving() && !jumpHeld && !this.mode.is("Normal")) {
            return;
        }
        if (!this.shouldBuild()) return;
        if (!this.isAimingAtPlacementFace()) return;
        BlockHitResult hit = new BlockHitResult(
                faceAimPoint(this.currentPlacement.position, facing, mc.player.getEyePosition()), facing,
                this.currentPlacement.position, false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (result == InteractionResult.SUCCESS && !this.swingMode.is("Server")) {
            mc.player.swing(hand);
        }
    }

    public static boolean isOnBlockEdge(float inflate) {
        if (mc.level == null || mc.player == null) return false;
        double halfWidth = mc.player.getBbWidth() / 2.0;
        if (inflate >= halfWidth) return true;
        double margin = Math.min(inflate, halfWidth * 0.99);
        Vec3 pos = mc.player.position();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos corner = BlockPos.containing(
                        pos.x + sx * margin,
                        pos.y - 1.0,
                        pos.z + sz * margin
                );
                BlockState state = mc.level.getBlockState(corner);
                if (!state.entityCanStandOn(mc.level, corner, mc.player)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Vec3 faceAimPoint(BlockPos pos, Direction face, Vec3 eye) {
        double inset = 0.15;
        double x = face.getAxis() == Direction.Axis.X ? pos.getX() + 0.5 + face.getStepX() * 0.5
                : Mth.clamp(eye.x, pos.getX() + inset, pos.getX() + 1.0 - inset);
        double y = face.getAxis() == Direction.Axis.Y ? pos.getY() + 0.5 + face.getStepY() * 0.5
                : Mth.clamp(eye.y, pos.getY() + inset, pos.getY() + 1.0 - inset);
        double z = face.getAxis() == Direction.Axis.Z ? pos.getZ() + 0.5 + face.getStepZ() * 0.5
                : Mth.clamp(eye.z, pos.getZ() + inset, pos.getZ() + 1.0 - inset);
        return new Vec3(x, y, z);
    }

    public static Vec3 getFaceCenter(BlockPos pos, Direction direction) {
        return new Vec3(
                pos.getX() + 0.5 + direction.getNormal().getX() * 0.5,
                pos.getY() + 0.5 + direction.getNormal().getY() * 0.5,
                pos.getZ() + 0.5 + direction.getNormal().getZ() * 0.5);
    }

    public static Vec3 getHitVec(BlockPos pos, Direction direction) {
        return getFaceCenter(pos, direction);
    }

    private boolean isAimingAtPlacementFace() {
        if (this.currentPlacement == null || mc.player == null) return false;
        Rotation rotation = RotationHandler.targetRotation;
        if (rotation == null) {
            rotation = this.rots;
        }
        if (rotation == null) return false;
        return RayTraceUtil.canRayTrace(rotation, this.currentPlacement.facing, this.currentPlacement.position, true);
    }

    private record PlacementTarget(BlockPos position, Direction facing) {
    }
}