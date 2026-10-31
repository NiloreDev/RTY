package client.nilore.modules.impl.world;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.modules.impl.player.AutoWebPlace;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.BlockUtil;
import client.nilore.utils.game.RayTraceUtil;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.utils.misc.PacketUtil;
import client.nilore.utils.render.BlockInESP;
import client.nilore.utils.rotation.Rotation;

public class BlockIn extends Module {
    public static BlockIn INSTANCE;

    // ==================== 基础设置 ====================
    public final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", true);
    public final ModeSetting placeOrder = new ModeSetting("Place Order", "Normal", "Normal", "Random", "BottomTop", "TopBottom").withDefault("Normal");
    public final BooleanSetting autoBlock = new BooleanSetting("Auto Block", true);
    public final BooleanSetting switchBack = new BooleanSetting("Switch Back", true);
    public final BooleanSetting useOffhand = new BooleanSetting("Use Offhand", false);
    public final NumberSetting range = new NumberSetting("Range", 4.5, 1.0, 6.0, 0.1);
    public final NumberSetting cooldown = new NumberSetting("Cooldown", 1, 0, 20, 1);
    public final BooleanSetting rotation = new BooleanSetting("Rotation", true);
    public final BooleanSetting smooth = new BooleanSetting("Smooth", true, () -> this.rotation.getValue());
    public final NumberSetting rotationTicks = new NumberSetting("Rotation Ticks", 2, 1, 6, 1, () -> this.rotation.getValue() && this.smooth.getValue());

    // ==================== Roof Support（含第 2 版逻辑增强）====================
    public final BooleanSetting roofSupport = new BooleanSetting("Roof Support", true);
    public final BooleanSetting diagonalSupport = new BooleanSetting("Diagonal Support", true);
    public final BooleanSetting pillarSupport = new BooleanSetting("Pillar Support", true);
    public final BooleanSetting useJump = new BooleanSetting("Use Jump", true);
    public final NumberSetting jumpTicks = new NumberSetting("Jump Ticks", 1, 1, 8, 1);

    public final BooleanSetting debug = new BooleanSetting("Debug", false);

    // ==================== ESP ====================
    public final BooleanSetting espEnabled = new BooleanSetting("ESP", true);
    public final NumberSetting espDuration = new NumberSetting("ESP Duration", 2.0, 0.1, 10.0, 0.1);
    // 5 种颜色。BlockInESP 通过 getEspColorARGB() / getEspColorR/G/B 读取，
    // 切换模式时 ESP 立即变色（前提是 BlockInESP 用的是 getter，见文末）。
    public final ModeSetting espColor = new ModeSetting("ESP Color", "Purple",
            "Red", "Green", "Blue", "Yellow", "Purple").withDefault("Purple");

    // ==================== 运行时状态 ====================
    public Rotation targetRotation = null;
    private static final int OFFHAND_SLOT = 40;
    private BlockPos startPos;
    private boolean rotateClockwise;
    private int oldSlot = -1;

    private int cooldownTimer;

    private RoofPhase roofPhase = RoofPhase.NORMAL;
    private BlockPos roofPos;
    private BlockPos roofSupportPos;
    private int jumpTicksRemaining;
    private boolean moduleJumpDown;
    private boolean waitingLanding;

    // ESP：位置 -> 放置时间戳（毫秒）
    private final Map<BlockPos, Long> espBlocks = new ConcurrentHashMap<>();

    public BlockIn() {
        super("BlockIn", Category.WORLD);
        INSTANCE = this;
        BlockInESP.bootstrap();
    }

    // ==================== ESP 对外接口 ====================
    public boolean isEspEnabled() {
        return this.espEnabled.getValue();
    }

    public List<BlockPos> getEspBlocks() {
        long now = System.currentTimeMillis();
        long durationMs = (long) (this.espDuration.getValue().doubleValue() * 1000.0);
        this.espBlocks.entrySet().removeIf(entry -> now - entry.getValue() > durationMs);
        return new ArrayList<>(this.espBlocks.keySet());
    }

    /** 当前 ESP 颜色的 ARGB 值（5 选 1）。 */
    public int getEspColorARGB() {
        String c = this.espColor.getValue();
        if ("Red".equals(c))    return 0xFFFF4D4D;
        if ("Green".equals(c))  return 0xFF4DFF88;
        if ("Blue".equals(c))   return 0xFF4D9EFF;
        if ("Yellow".equals(c)) return 0xFFFFD54D;
        /* Purple */            return 0xFFB84DFF;
    }

    /** 0f~1f 分量，方便直接传 GL。 */
    public float getEspColorR() { return ((getEspColorARGB() >> 16) & 0xFF) / 255.0f; }
    public float getEspColorG() { return ((getEspColorARGB() >> 8)  & 0xFF) / 255.0f; }
    public float getEspColorB() { return ( getEspColorARGB()        & 0xFF) / 255.0f; }
    public float getEspColorA() { return ((getEspColorARGB() >> 24) & 0xFF) / 255.0f; }

    // ==================== 生命周期 ====================
    @Override
    protected void onEnable() {
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            Scaffold.INSTANCE.setEnabled(false);
        }
        if (AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled()) {
            AutoWebPlace.INSTANCE.setEnabled(false);
        }
        if (mc.player != null) {
            this.startPos = mc.player.blockPosition();
            this.rotateClockwise = ThreadLocalRandom.current().nextBoolean();
            this.oldSlot = mc.player.getInventory().selected;
            this.resetRoofState();
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Enabled. StartPos: " + this.fmtPos(startPos)
                        + " | Range: " + range.getValue().doubleValue());
            }
        }
        this.targetRotation = null;
        this.cooldownTimer = 0;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.releaseJumpKey();
        this.restoreSlot();
        this.resetRoofState();
        this.espBlocks.clear();
        if (this.debug.getValue()) {
            ChatUtil.print("[BlockIn] Disabled.");
        }
        this.startPos = null;
        this.targetRotation = null;
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

    // ==================== 主 Tick ====================
    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || this.startPos == null) {
            return;
        }

        BlockPos currentPos = mc.player.blockPosition();
        if (!currentPos.equals(this.startPos) && !currentPos.equals(this.startPos.above())) {
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Moved away, disabling");
            }
            this.setEnabled(false);
            return;
        }

        this.updateJumpControl();

        if (this.cooldownTimer > 0) {
            this.cooldownTimer--;
            return;
        }

        List<BlockPos> positions = this.generatePositions();
        if (positions.isEmpty()) {
            if (this.autoDisable.getValue() && this.isComplete()) {
                if (this.debug.getValue()) {
                    ChatUtil.print("[BlockIn] Complete, disabling");
                }
                this.setEnabled(false);
            }
            return;
        }

        for (BlockPos pos : positions) {
            if (this.tryPlaceBlock(pos)) {
                this.cooldownTimer = this.cooldown.getValue().intValue();
                break;
            }
        }

        if (this.autoDisable.getValue() && this.isComplete()) {
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Complete, disabling");
            }
            this.setEnabled(false);
        }
    }

    // ==================== 位置生成 ====================
    private List<BlockPos> generatePositions() {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        if (mc.player == null || this.startPos == null) {
            return List.of();
        }

        int playerHeight = Mth.ceil(mc.player.getBbHeight());
        this.roofPos = this.startPos.above(playerHeight);

        if (this.waitingLanding && !mc.player.onGround()) {
            return List.of();
        }

        boolean baseComplete = this.isBaseComplete(playerHeight);

        List<BlockPos> baseBlocks = new ArrayList<>();
        baseBlocks.add(this.startPos.below());
        for (Direction dir : this.getOrderedDirections()) {
            BlockPos wallBase = this.startPos.relative(dir);
            for (int i = 0; i < playerHeight; i++) {
                baseBlocks.add(wallBase.above(i));
            }
        }

        List<BlockPos> ordered = this.orderPositions(baseBlocks);

        if (baseComplete) {
            BlockPos support = this.updateRoofSupport(playerHeight);
            if (support != null) {
                ordered.add(support);
            }
            ordered.add(this.roofPos);
        }

        return ordered;
    }

    private List<BlockPos> orderPositions(List<BlockPos> positions) {
        String mode = this.placeOrder.getValue();
        if ("Random".equals(mode)) {
            Collections.shuffle(positions);
        } else if ("BottomTop".equals(mode)) {
            positions.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        } else if ("TopBottom".equals(mode)) {
            positions.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        }
        return positions;
    }

    // ==================== 放置 ====================
    private boolean tryPlaceBlock(BlockPos targetPos) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return false;
        }

        if (!mc.level.getBlockState(targetPos).canBeReplaced()) {
            return false;
        }

        boolean isRoof = this.roofPos != null && targetPos.equals(this.roofPos);
        Direction face = null;
        BlockPos againstPos = null;

        // Roof 特殊路径：如果 roof support 已放置，直接从它上面搭 roof
        if (isRoof && this.roofSupportPos != null && BlockUtil.isSolid(this.roofSupportPos)) {
            for (Direction d : Direction.values()) {
                if (this.roofSupportPos.relative(d).equals(targetPos)) {
                    face = d;
                    againstPos = this.roofSupportPos;
                    break;
                }
            }
        }

        // 通用路径：靠周围最近的实心方块
        if (face == null) {
            face = this.findPlacementFace(targetPos);
            if (face != null) {
                againstPos = targetPos.relative(face.getOpposite());
            }
        }

        if (face == null || againstPos == null) {
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Failed at " + fmtPos(targetPos) + ": No valid placement face.");
            }
            return false;
        }

        Vec3 hitVec = new Vec3(
                againstPos.getX() + 0.5 + face.getStepX() * 0.5,
                againstPos.getY() + 0.5 + face.getStepY() * 0.5,
                againstPos.getZ() + 0.5 + face.getStepZ() * 0.5);

        // Range 检查
        double distSq = mc.player.getEyePosition().distanceToSqr(hitVec);
        double maxRange = this.range.getValue().doubleValue();
        if (distSq > maxRange * maxRange) {
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Failed at " + fmtPos(targetPos) + ": Out of range ("
                        + String.format("%.2f", Math.sqrt(distSq)) + " > " + maxRange + ")");
            }
            return false;
        }

        if (this.rotation.getValue()) {
            Rotation targetRot = RotationUtil.rotationFromVec(hitVec);
            if (this.smooth.getValue() && mc.player != null) {
                Rotation currentRot = this.targetRotation != null
                        ? this.targetRotation
                        : new Rotation(mc.player.getYRot(), mc.player.getXRot());
                double speed = 180.0 / this.rotationTicks.getValue().doubleValue();
                this.targetRotation = RotationUtil.smoothRotation(currentRot, targetRot, speed);
            } else {
                this.targetRotation = targetRot;
            }

            if (this.targetRotation == null
                    || !RayTraceUtil.canRayTrace(this.targetRotation, face, againstPos, false)) {
                if (this.debug.getValue()) {
                    ChatUtil.print("[BlockIn] Failed at " + fmtPos(targetPos) + ": Raytrace blocked.");
                }
                return false;
            }

            PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(
                    this.targetRotation.getYaw(), this.targetRotation.getPitch(), mc.player.onGround()));
        } else {
            this.targetRotation = null;
        }

        int placeSlot = this.findPlaceableSlot();
        if (placeSlot == -1) {
            if (this.debug.getValue()) ChatUtil.print("[BlockIn] No block item.");
            return false;
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (placeSlot == OFFHAND_SLOT) {
            hand = InteractionHand.OFF_HAND;
        } else if (placeSlot != mc.player.getInventory().selected) {
            mc.player.getInventory().selected = placeSlot;
        }

        BlockHitResult hit = new BlockHitResult(hitVec, face, againstPos, false);

        var result = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (result.consumesAction()) {
            mc.player.swing(hand);
            // ESP 记录
            this.espBlocks.put(targetPos.immutable(), System.currentTimeMillis());
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Placed at " + this.fmtPos(targetPos));
            }
            return true;
        }
        return false;
    }

    // 增强版：遍历 6 个面 + 27 个采样点，选最近可用的放置面
    private Direction findPlacementFace(BlockPos targetPos) {
        if (mc.level == null || mc.player == null) return null;

        Direction best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye = mc.player.getEyePosition(1.0f);

        for (Direction face : Direction.values()) {
            BlockPos support = targetPos.relative(face);
            if (mc.level.getBlockState(support).canBeReplaced()) {
                continue;
            }

            for (double ox : new double[]{0.25, 0.5, 0.75}) {
                for (double oy : new double[]{0.25, 0.5, 0.75}) {
                    for (double oz : new double[]{0.25, 0.5, 0.75}) {
                        Vec3 hit = new Vec3(
                                support.getX() + ox,
                                support.getY() + oy,
                                support.getZ() + oz
                        );
                        if (!isValidPlaceHit(support, hit)) continue;
                        double score = eye.distanceToSqr(hit);
                        if (score < bestScore) {
                            bestScore = score;
                            best = face.getOpposite();
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean isValidPlaceHit(BlockPos support, Vec3 hit) {
        if (mc.player == null || mc.level == null) return false;
        double range = this.range.getValue().doubleValue();
        if (mc.player.getEyePosition(1.0f).distanceToSqr(hit) > range * range) {
            return false;
        }
        return BlockUtil.isSolid(support);
    }

    private int findPlaceableSlot() {
        if (mc.player == null) return -1;

        int selected = mc.player.getInventory().selected;
        if (this.isPlaceableSlot(selected)) {
            return selected;
        }

        if (this.autoBlock.getValue()) {
            int best = -1;
            int bestCount = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack) && stack.getCount() > bestCount) {
                    best = i;
                    bestCount = stack.getCount();
                }
            }
            if (best != -1) return best;
        }

        if (this.useOffhand.getValue()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
                return OFFHAND_SLOT;
            }
        }

        return -1;
    }

    private boolean isPlaceableSlot(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) return false;
        ItemStack stack = mc.player.getInventory().getItem(slot);
        return stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack);
    }

    private void restoreSlot() {
        if (mc.player != null && this.oldSlot >= 0 && this.switchBack.getValue()) {
            mc.player.getInventory().selected = this.oldSlot;
        }
        this.oldSlot = -1;
    }

    // ==================== Roof Support ====================
    private BlockPos updateRoofSupport(int playerHeight) {
        if (mc.player == null || mc.level == null || this.startPos == null || this.roofPos == null) {
            this.clearRoofSupport("no-player");
            return null;
        }
        if (!this.roofSupport.getValue()) {
            this.clearRoofSupport("disabled");
            return null;
        }
        if (BlockUtil.isSolid(this.roofPos)) {
            this.clearRoofSupport("roof-done");
            return null;
        }
        if (this.findPlacementFace(this.roofPos) != null) {
            this.clearRoofSupport("roof-direct");
            return null;
        }
        if (!this.isBaseComplete(playerHeight)) {
            this.clearRoofSupport("base-pending");
            return null;
        }

        // 增强 1：从周围实心方块搭桥通往 roofPos
        BlockPos pathSupport = this.findRoofPlacementPath();
        if (pathSupport != null) {
            this.roofSupportPos = pathSupport;
            this.roofPhase = RoofPhase.DIAGONAL;
            if (this.debug.getValue()) ChatUtil.print("[BlockIn] Found placement path support at " + fmtPos(pathSupport));
            return pathSupport;
        }

        if (this.roofSupportPos != null && !BlockUtil.isSolid(this.roofSupportPos)) {
            return this.roofSupportPos;
        }

        if (this.diagonalSupport.getValue()) {
            BlockPos diag = this.findDiagonalSupport(playerHeight);
            if (diag != null) {
                this.roofPhase = RoofPhase.DIAGONAL;
                this.roofSupportPos = diag;
                if (this.debug.getValue()) ChatUtil.print("[BlockIn] Found diagonal support at " + fmtPos(diag));
                return diag;
            }
        }

        // 增强 2：从 roofPos 附近找可替换方块作为放置起点
        BlockPos nearby = this.findNearbyRoofSupport(playerHeight);
        if (nearby != null) {
            this.roofPhase = RoofPhase.DIAGONAL;
            this.roofSupportPos = nearby;
            if (this.debug.getValue()) ChatUtil.print("[BlockIn] Found nearby roof support at " + fmtPos(nearby));
            return nearby;
        }

        if (this.pillarSupport.getValue() && this.useJump.getValue()) {
            BlockPos jump = this.findPillarSupport(playerHeight);
            if (jump != null) {
                this.roofPhase = RoofPhase.JUMP_PILLAR;
                this.roofSupportPos = jump;
                if (this.debug.getValue()) ChatUtil.print("[BlockIn] Found jump pillar support at " + fmtPos(jump));
                return jump;
            }
        }

        if (this.pillarSupport.getValue()) {
            BlockPos pillar = this.findPillarSupport(playerHeight);
            if (pillar != null) {
                this.roofPhase = RoofPhase.PILLAR;
                this.roofSupportPos = pillar;
                if (this.debug.getValue()) ChatUtil.print("[BlockIn] Found pillar support at " + fmtPos(pillar));
                return pillar;
            }
        }

        this.clearRoofSupport("no-support");
        return null;
    }

    // 在 roofPos 周围的实心方块里找一条到 roofPos 的搭桥路径
    private BlockPos findRoofPlacementPath() {
        if (this.roofPos == null || mc.level == null) return null;

        int r = (int) Math.ceil(this.range.getValue().doubleValue());
        List<BlockPos> solids = new ArrayList<>();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = this.roofPos.offset(x, y, z);
                    if (BlockUtil.isSolid(p)) solids.add(p);
                }
            }
        }
        solids.sort((a, b) -> Double.compare(a.distSqr(this.roofPos), b.distSqr(this.roofPos)));

        // 直接相邻
        for (BlockPos support : solids) {
            for (Direction d : Direction.values()) {
                if (support.relative(d).equals(this.roofPos)
                        && findPlacementFace(this.roofPos) != null) {
                    return support;
                }
            }
        }

        // 桥接（放一块中间方块，再从它上面放到 roofPos）
        for (BlockPos support : solids) {
            for (Direction d : Direction.values()) {
                BlockPos bridge = support.relative(d);

                if (!mc.level.getBlockState(bridge).canBeReplaced()) continue;
                if (!bridge.relative(Direction.UP).equals(this.roofPos)
                        && !bridge.relative(Direction.DOWN).equals(this.roofPos)
                        && !bridge.relative(Direction.NORTH).equals(this.roofPos)
                        && !bridge.relative(Direction.SOUTH).equals(this.roofPos)
                        && !bridge.relative(Direction.EAST).equals(this.roofPos)
                        && !bridge.relative(Direction.WEST).equals(this.roofPos)) {
                    continue;
                }
                if (findPlacementFace(bridge) != null) {
                    return bridge;
                }
            }
        }
        return null;
    }

    // 在 roofPos 附近扫描可替换方块，作为 roof 起始支撑
    private BlockPos findNearbyRoofSupport(int playerHeight) {
        if (this.roofPos == null || mc.level == null || mc.player == null) return null;

        int radius = Math.max(1, (int) Math.ceil(this.range.getValue().doubleValue()));
        List<BlockPos> candidates = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = this.roofPos.offset(x, y, z);
                    if (!pos.equals(this.roofPos)) candidates.add(pos);
                }
            }
        }
        candidates.sort((a, b) -> Double.compare(a.distSqr(this.roofPos), b.distSqr(this.roofPos)));

        for (BlockPos candidate : candidates) {
            if (!mc.level.getBlockState(candidate).canBeReplaced()) continue;

            Vec3 center = new Vec3(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            if (mc.player.getEyePosition().distanceTo(center) > this.range.getValue().doubleValue()) continue;

            if (this.findPlacementFace(candidate) != null) return candidate;

            for (Direction direction : Direction.values()) {
                BlockPos neighbour = candidate.relative(direction);
                if (!mc.level.getBlockState(neighbour).canBeReplaced()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private BlockPos findDiagonalSupport(int playerHeight) {
        int[] offsets = {-1, 1};
        for (int dx : offsets) {
            for (int dz : offsets) {
                BlockPos diagonal = this.startPos.offset(dx, playerHeight, dz);
                if (!BlockUtil.isSolid(diagonal)) continue;
                BlockPos xSupport = this.startPos.offset(dx, playerHeight, 0);
                if (!BlockUtil.isSolid(xSupport) && this.findPlacementFace(xSupport) != null) {
                    return xSupport;
                }
                BlockPos zSupport = this.startPos.offset(0, playerHeight, dz);
                if (!BlockUtil.isSolid(zSupport) && this.findPlacementFace(zSupport) != null) {
                    return zSupport;
                }
            }
        }
        return null;
    }

    private BlockPos findPillarSupport(int playerHeight) {
        for (Direction dir : this.getOrderedDirections()) {
            BlockPos support = this.startPos.relative(dir).above(playerHeight);
            if (!BlockUtil.isSolid(support) && BlockUtil.isSolid(support.below()) && this.findPlacementFace(support) != null) {
                return support;
            }
        }
        return null;
    }

    private boolean isBaseComplete(int playerHeight) {
        if (mc.player == null || this.startPos == null) return false;
        if (!BlockUtil.isSolid(this.startPos.below())) return false;
        for (Direction dir : this.getOrderedDirections()) {
            BlockPos wallBase = this.startPos.relative(dir);
            for (int i = 0; i < playerHeight; i++) {
                if (!BlockUtil.isSolid(wallBase.above(i))) return false;
            }
        }
        return true;
    }

    private boolean isComplete() {
        if (mc.player == null || this.startPos == null) return false;
        int playerHeight = Mth.ceil(mc.player.getBbHeight());
        if (!BlockUtil.isSolid(this.startPos.below())) return false;
        for (Direction dir : this.getOrderedDirections()) {
            BlockPos wallBase = this.startPos.relative(dir);
            for (int i = 0; i < playerHeight; i++) {
                if (!BlockUtil.isSolid(wallBase.above(i))) return false;
            }
        }
        if (!BlockUtil.isSolid(this.roofPos)) return false;
        return true;
    }

    // ==================== 跳跃控制 ====================
    private void updateJumpControl() {
        if (mc.player == null || mc.options == null) return;

        if (this.roofPhase == RoofPhase.WAIT_LANDING) {
            this.releaseJumpKey();
            if (mc.player.onGround()) {
                this.waitingLanding = false;
                this.clearRoofSupport("landed");
            }
            return;
        }

        if (this.roofPhase != RoofPhase.JUMP_PILLAR) {
            this.releaseJumpKey();
            return;
        }

        if (this.roofSupportPos != null && BlockUtil.isSolid(this.roofSupportPos)) {
            this.releaseJumpKey();
            this.roofPhase = RoofPhase.WAIT_LANDING;
            this.waitingLanding = true;
            return;
        }

        if (!this.moduleJumpDown && mc.player.onGround()) {
            this.jumpTicksRemaining = Math.max(1, this.jumpTicks.getValue().intValue());
            this.setKeyDown(mc.options.keyJump, true);
            this.moduleJumpDown = true;
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Jumping to place pillar support.");
            }
        }

        if (this.moduleJumpDown) {
            this.jumpTicksRemaining--;
            if (this.jumpTicksRemaining <= 0) {
                this.releaseJumpKey();
            }
        }
    }

    private void releaseJumpKey() {
        if (this.moduleJumpDown && mc != null && mc.options != null) {
            boolean physical = mc.getWindow() != null
                    && InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
            this.setKeyDown(mc.options.keyJump, physical);
            this.moduleJumpDown = false;
        }
        this.jumpTicksRemaining = 0;
    }

    private void setKeyDown(KeyMapping key, boolean down) {
        KeyMapping.set(key.getKey(), down);
        key.setDown(down);
    }

    private Direction[] getOrderedDirections() {
        Direction[] result = new Direction[4];
        Direction dir = mc.player == null ? Direction.NORTH : mc.player.getDirection();
        for (int i = 0; i < 4; i++) {
            result[i] = dir;
            dir = this.rotateClockwise ? dir.getClockWise() : dir.getCounterClockWise();
        }
        return result;
    }

    private void clearRoofSupport(String reason) {
        this.roofPhase = RoofPhase.NORMAL;
        this.roofSupportPos = null;
        this.waitingLanding = false;
        this.jumpTicksRemaining = 0;
        this.releaseJumpKey();
        if (this.debug.getValue() && !reason.equals("roof-direct") && !reason.equals("base-pending")) {
            ChatUtil.print("[BlockIn] Clear roof support: " + reason);
        }
    }

    private void resetRoofState() {
        this.roofPhase = RoofPhase.NORMAL;
        this.roofPos = null;
        this.roofSupportPos = null;
        this.jumpTicksRemaining = 0;
        this.moduleJumpDown = false;
        this.waitingLanding = false;
    }

    private String fmtPos(BlockPos pos) {
        if (pos == null) return "null";
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private enum RoofPhase {
        NORMAL,
        DIAGONAL,
        PILLAR,
        JUMP_PILLAR,
        WAIT_LANDING
    }
}