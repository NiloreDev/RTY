package client.nilore.modules.impl.world;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import client.nilore.utils.render.BlockInESP;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;

public class BlockIn extends Module {
    public static BlockIn INSTANCE;

    public final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", true);
    public final ModeSetting placeOrder = new ModeSetting("Place Order", "Normal", "Normal", "Random", "BottomTop", "TopBottom").withDefault("Normal");
    public final BooleanSetting autoBlock = new BooleanSetting("Auto Block", true);
    public final BooleanSetting switchBack = new BooleanSetting("Switch Back", true);
    public final BooleanSetting useOffhand = new BooleanSetting("Use Offhand", false);
    public final NumberSetting range = new NumberSetting("Range", 4.5, 1.0, 6.0, 0.1);
    public final NumberSetting cooldown = new NumberSetting("Cooldown", 1, 0, 20, 1);
    public final BooleanSetting rotation = new BooleanSetting("Rotation", true);
    public final BooleanSetting smooth = new BooleanSetting("Smooth", false, () -> this.rotation.getValue());
    public final NumberSetting rotationTicks = new NumberSetting("Rotation Ticks", 3, 1, 6, 1, () -> this.rotation.getValue() && this.smooth.getValue());
    public final BooleanSetting roofSupport = new BooleanSetting("Roof Support", true);
    public final BooleanSetting diagonalSupport = new BooleanSetting("Diagonal Support", true);
    public final BooleanSetting pillarSupport = new BooleanSetting("Pillar Support", true);
    public final BooleanSetting useJump = new BooleanSetting("Use Jump", true);
    public final NumberSetting jumpTicks = new NumberSetting("Jump Ticks", 2, 1, 8, 1);
    public final BooleanSetting debug = new BooleanSetting("Debug", false);

    // ==================== ESP 相关开关 ====================
    public final BooleanSetting espEnabled = new BooleanSetting("ESP", true);
    public final NumberSetting espDuration = new NumberSetting("ESP Duration", 2.0, 0.1, 10.0, 0.1);

    public Rotation targetRotation = null;
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

    // ESP 数据存储
    private final Map<BlockPos, Long> espBlocks = new ConcurrentHashMap<>();

    public BlockIn() {
        super("BlockIn", Category.WORLD);
        INSTANCE = this;
        // 自动注册 Forge 渲染事件
        BlockInESP.bootstrap();
    }

    // ==================== ESP 公共接口 ====================
    public boolean isEspEnabled() {
        return this.espEnabled.getValue();
    }

    public List<BlockPos> getEspBlocks() {
        long now = System.currentTimeMillis();
        long durationMs = (long) (this.espDuration.getValue().doubleValue() * 1000.0);
        // 清理过期方块
        this.espBlocks.entrySet().removeIf(entry -> now - entry.getValue() > durationMs);
        return new ArrayList<>(this.espBlocks.keySet());
    }

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

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || this.startPos == null) {
            return;
        }

        BlockPos currentPos = mc.player.blockPosition();
        if (!currentPos.equals(this.startPos) && !currentPos.equals(this.startPos.above())) {
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Player moved away, disabling.");
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
                    ChatUtil.print("[BlockIn] Structure complete, disabling.");
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
                ChatUtil.print("[BlockIn] Structure complete, disabling.");
            }
            this.setEnabled(false);
        }
    }

    private List<BlockPos> generatePositions() {
        if (mc.player == null || this.startPos == null) {
            return List.of();
        }

        int playerHeight = Mth.ceil(mc.player.getBbHeight());
        int totalHeight = playerHeight;
        this.roofPos = this.startPos.above(totalHeight);

        List<BlockPos> baseBlocks = new ArrayList<>();
        baseBlocks.add(this.startPos.below());

        for (Direction dir : this.getOrderedDirections()) {
            BlockPos wallBase = this.startPos.relative(dir);
            for (int i = 0; i < totalHeight; i++) {
                baseBlocks.add(wallBase.above(i));
            }
        }

        List<BlockPos> ordered = this.orderPositions(baseBlocks);

        BlockPos support = this.updateRoofSupport(totalHeight);
        if (support != null) {
            ordered.add(support);
        }
        ordered.add(this.roofPos);

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

        if (isRoof && this.roofSupportPos != null && BlockUtil.isSolid(this.roofSupportPos)) {
            for (Direction d : Direction.values()) {
                if (this.roofSupportPos.relative(d).equals(targetPos)) {
                    face = d;
                    againstPos = this.roofSupportPos;
                    break;
                }
            }
        }

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

            if (RotationHandler.targetRotation != null) {
                boolean canRayTrace = RayTraceUtil.canRayTrace(RotationHandler.targetRotation, face, againstPos, false);
                if (!canRayTrace) {
                    if (this.debug.getValue()) {
                        ChatUtil.print("[BlockIn] Failed at " + fmtPos(targetPos) + ": Raytrace blocked.");
                    }
                    return false;
                }
            }
        } else {
            this.targetRotation = null;
        }

        int placeSlot = this.findPlaceableSlot();
        if (placeSlot == -1) {
            if (this.debug.getValue()) ChatUtil.print("[BlockIn] No block item.");
            return false;
        }

        if (placeSlot != 40 && mc.player.getInventory().selected != placeSlot) {
            mc.player.getInventory().selected = placeSlot;
        }

        BlockHitResult hit = new BlockHitResult(hitVec, face, againstPos, false);

        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            // 记录到 ESP
            this.espBlocks.put(targetPos.immutable(), System.currentTimeMillis());
            if (this.debug.getValue()) {
                ChatUtil.print("[BlockIn] Placed at " + this.fmtPos(targetPos));
            }
            return true;
        }
        return false;
    }

    /**
     * BlockPlacer style target search.
     * Does not rotate like BlockPlacer. It only borrows its placement analysis:
     * search reachable support blocks and choose a valid hit point.
     */
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

            // Sample several points on the support face instead of only center.
            // This avoids the "turns but cannot place" case.
            for (double ox : new double[]{0.25, 0.5, 0.75}) {
                for (double oy : new double[]{0.25, 0.5, 0.75}) {
                    for (double oz : new double[]{0.25, 0.5, 0.75}) {
                        Vec3 hit = new Vec3(
                                support.getX() + ox,
                                support.getY() + oy,
                                support.getZ() + oz
                        );

                        if (!isValidPlaceHit(support, hit)) {
                            continue;
                        }

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

    /**
     * Check if the hit vector is actually inside the support block face.
     * This is intentionally separate from rotation logic.
     */
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
                return 40;
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

    private BlockPos updateRoofSupport(int totalHeight) {
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

        // 新的 roof 路径搜索：模拟 BlockPlacer 的思路，不直接转头硬放。
        // 在 range 内寻找已有方块 -> 可放辅助方块 -> roof 的合法链。
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
            BlockPos diag = this.findDiagonalSupport(totalHeight);
            if (diag != null) {
                this.roofPhase = RoofPhase.DIAGONAL;
                this.roofSupportPos = diag;
                if (this.debug.getValue()) ChatUtil.print("[BlockIn] Found diagonal support at " + fmtPos(diag));
                return diag;
            }
        }

        BlockPos nearby = this.findNearbyRoofSupport(totalHeight);
        if (nearby != null) {
            this.roofPhase = RoofPhase.DIAGONAL;
            this.roofSupportPos = nearby;
            if (this.debug.getValue()) ChatUtil.print("[BlockIn] Found nearby roof support at " + fmtPos(nearby));
            return nearby;
        }

        if (this.pillarSupport.getValue() && this.useJump.getValue()) {
            BlockPos jump = this.findPillarSupport(totalHeight);
            if (jump != null) {
                this.roofPhase = RoofPhase.JUMP_PILLAR;
                this.roofSupportPos = jump;
                if (this.debug.getValue()) ChatUtil.print("[BlockIn] Found jump pillar support at " + fmtPos(jump));
                return jump;
            }
        }

        if (this.pillarSupport.getValue()) {
            BlockPos pillar = this.findPillarSupport(totalHeight);
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



    /**
     * Range based placement path search.
     *
     * 不是复制 BlockPlacer 的 rotation，而是分析它的核心：
     * 先找到可点击的已有方块，再寻找能连接到目标的位置。
     */
    /**
     * Searches the configured range for a block that can actually be used
     * to complete the roof. Supports:
     * 1. existing block -> roof
     * 2. existing block -> bridge -> roof
     */
    private BlockPos findRoofPlacementPath() {
        if (this.roofPos == null || mc.level == null) return null;

        int r = (int)Math.ceil(this.range.getValue().doubleValue());
        List<BlockPos> solids = new ArrayList<>();

        for (int x=-r;x<=r;x++) {
            for (int y=-r;y<=r;y++) {
                for (int z=-r;z<=r;z++) {
                    BlockPos p = this.roofPos.offset(x,y,z);
                    if (BlockUtil.isSolid(p)) solids.add(p);
                }
            }
        }

        solids.sort((a,b)->Double.compare(a.distSqr(this.roofPos), b.distSqr(this.roofPos)));

        // direct support
        for (BlockPos support : solids) {
            for (Direction d : Direction.values()) {
                if (support.relative(d).equals(this.roofPos)
                        && findPlacementFace(this.roofPos) != null) {
                    return support;
                }
            }
        }

        // bridge search
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

    private BlockPos findDiagonalSupport(int totalHeight) {
        int[] offsets = {-1, 1};
        for (int dx : offsets) {
            for (int dz : offsets) {
                BlockPos diagonal = this.startPos.offset(dx, totalHeight, dz);
                if (!BlockUtil.isSolid(diagonal)) continue;
                BlockPos xSupport = this.startPos.offset(dx, totalHeight, 0);
                if (!BlockUtil.isSolid(xSupport) && this.findPlacementFace(xSupport) != null) {
                    return xSupport;
                }
                BlockPos zSupport = this.startPos.offset(0, totalHeight, dz);
                if (!BlockUtil.isSolid(zSupport) && this.findPlacementFace(zSupport) != null) {
                    return zSupport;
                }
            }
        }
        return null;
    }

    /**
     * Range based roof helper scan.
     * Scans the whole configured range around the roof (including above and below)
     * to find a legal helper position instead of only checking a few blocks.
     */
    private BlockPos findNearbyRoofSupport(int totalHeight) {
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

    private BlockPos findPillarSupport(int totalHeight) {
        for (Direction dir : this.getOrderedDirections()) {
            BlockPos support = this.startPos.relative(dir).above(totalHeight);
            if (!BlockUtil.isSolid(support) && BlockUtil.isSolid(support.below()) && this.findPlacementFace(support) != null) {
                return support;
            }
        }
        return null;
    }

    private boolean isComplete() {
        if (mc.player == null || this.startPos == null) return false;
        int playerHeight = Mth.ceil(mc.player.getBbHeight());
        int totalHeight = playerHeight;

        if (!BlockUtil.isSolid(this.startPos.below())) return false;

        for (Direction dir : this.getOrderedDirections()) {
            BlockPos wallBase = this.startPos.relative(dir);
            for (int i = 0; i < totalHeight; i++) {
                if (!BlockUtil.isSolid(wallBase.above(i))) return false;
            }
        }
        if (!BlockUtil.isSolid(this.roofPos)) return false;
        return true;
    }

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