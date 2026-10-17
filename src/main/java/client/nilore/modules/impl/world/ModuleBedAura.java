package client.nilore.modules.impl.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.movement.FireballBlink;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;

/**
 * BedAura module
 *
 * Destroys beds around you.
 *
 * Modes:
 * - OnlyBed: directly destroys the bed.
 * - NearBlock: destroys one block next to the bed (making an entrance) before destroying the
 *   bed itself. When the bed is not fully enclosed, it is destroyed directly.
 *
 * Adapted from LiquidBounce's ModuleBedAura (Kotlin) to the OpenNilore client (Java).
 */
public class ModuleBedAura extends Module {
    public static ModuleBedAura INSTANCE;
    public static Rotation targetRotation;

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    private static final Direction[] DIRECTIONS_EXCLUDING_DOWN = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final ModeSetting mode = new ModeSetting("Mode", "OnlyBed", "OnlyBed", "NearBlock");
    private final NumberSetting range = new NumberSetting("Range", 5, 1, 6, 0.1);
    private final NumberSetting wallRange = new NumberSetting("WallRange", 0, 0, 6, 0.1);
    private final NumberSetting delay = new NumberSetting("Delay", 0, 0, 20, 1);
    private final BooleanSetting ignoreOpenInventory = new BooleanSetting("IgnoreOpenInventory", true);
    private final BooleanSetting ignoreUsingItem = new BooleanSetting("IgnoreUsingItem", true);
    private final BooleanSetting prioritizeOverKillAura = new BooleanSetting("PrioritizeOverKillAura", false);

    private DestroyerTarget currentTarget;
    private DestroyerTarget oldTarget;
    private int delayTicks;

    public ModuleBedAura() {
        super("BedAura", Category.WORLD);
        INSTANCE = this;
    }

    public boolean isPrioritized() {
        return this.prioritizeOverKillAura.getValue();
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
    protected void onEnable() {
        this.currentTarget = null;
        this.oldTarget = null;
        targetRotation = null;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.clearCurrentTarget();
        this.oldTarget = null;
        targetRotation = null;
        super.onDisable();
    }

    @Override
    public void onTick() {

    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        if (!this.ignoreOpenInventory.getValue() && mc.screen instanceof AbstractContainerScreen) {
            return;
        }
        if (!this.ignoreUsingItem.getValue() && mc.player.isUsingItem()) {
            return;
        }

        this.oldTarget = this.currentTarget;
        this.updateCurrentTarget();

        // If we don't have any new target, and we had one before, stop breaking.
        if (this.oldTarget != null && this.currentTarget == null) {
            this.stopDestroy();
            return;
        }

        if (this.oldTarget != this.currentTarget && this.delay.getValue().intValue() > 0) {
            this.stopDestroy();
            this.delayTicks = this.delay.getValue().intValue();
        }

        if (this.delayTicks > 0) {
            this.delayTicks--;
            return;
        }

        // Check if blink is enabled - if so, we don't want to do anything.
        if (FireballBlink.INSTANCE != null && FireballBlink.INSTANCE.isEnabled()) {
            return;
        }

        DestroyerTarget destroyerTarget = this.currentTarget;
        if (destroyerTarget == null) {
            return;
        }

        BlockPos pos = destroyerTarget.pos;

        // ================================================================
        // 转头距离：使用 Range 控制（和搜索范围一致）
        // ================================================================
        double rangeValue = this.range.getValue().doubleValue();
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 targetVec = Vec3.atCenterOf(pos);

        // 检查目标是否在转头距离内
        if (eyePos.distanceToSqr(targetVec) <= rangeValue * rangeValue) {
            targetRotation = RotationUtil.exactRotation(eyePos, targetVec);
            RotationHandler.targetRotation = targetRotation;
        }

        if (!this.canSee(pos)) {
            this.clearCurrentTarget();
            return;
        }

        this.doBreak(pos);
    }

    private void updateCurrentTarget() {
        List<BlockPos> possibleBlocks = this.searchBedPositions();

        this.validateCurrentTarget(possibleBlocks);

        if (possibleBlocks.isEmpty()) {
            return;
        }

        double rangeValue = this.range.getValue().doubleValue();
        double wallRangeValue = this.wallRange.getValue().doubleValue();

        // Keep the current target if it is still valid.
        if (this.currentTarget != null) {
            return;
        }

        if (this.mode.is("OnlyBed")) {
            // Only the bed itself is destroyed, nothing else.
            for (BlockPos pos : possibleBlocks) {
                if (this.considerAsTarget(pos, true, rangeValue, wallRangeValue)) {
                    return;
                }
            }
        } else if (this.mode.is("NearBlock")) {
            // Break the bed directly as soon as it is not fully covered anymore.
            // Mining a bed while it is completely enclosed (blocks on every horizontal
            // side) is flagged by the server, so we first destroy the weakest block
            // next to the bed to get it out of the covered state.
            for (BlockPos pos : possibleBlocks) {
                if (!this.isFullyCovered(pos)) {
                    if (this.considerAsTarget(pos, true, rangeValue, wallRangeValue)) {
                        return;
                    }
                } else {
                    BlockPos neighbor = this.weakestNeighbor(pos);
                    if (neighbor != null && this.considerAsTarget(neighbor, false, rangeValue, rangeValue)) {
                        return;
                    }
                }
            }

            // Every bed is fully covered but no entrance could be broken - fall back
            // to breaking the weakest bed directly.
            for (BlockPos pos : possibleBlocks) {
                if (this.considerAsTarget(pos, true, rangeValue, wallRangeValue)) {
                    return;
                }
            }
        }
    }

    /**
     * @return true if it is the best target, false if it's invalid.
     */
    private boolean considerAsTarget(BlockPos pos, boolean isBed, double range, double throughWallsRange) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        DestroyerTarget currentTarget = this.currentTarget;
        if (currentTarget != null && isBed && !currentTarget.isBed) {
            return false;
        }

        targetRotation = RotationUtil.exactRotation(mc.player.getEyePosition(), Vec3.atCenterOf(pos));

        this.clearCurrentTarget();
        this.currentTarget = new DestroyerTarget(pos, isBed);
        return true;
    }

    private void validateCurrentTarget(List<BlockPos> possibleBlocks) {
        DestroyerTarget target = this.currentTarget;
        if (target == null) {
            return;
        }

        boolean removed = false;
        if (!target.isBed) {
            // The block next to the bed (the entrance) is no longer a valid target once it
            // is gone, is air or became unbreakable.
            BlockState state = mc.level.getBlockState(target.pos);
            if (state.isAir() || state.getDestroySpeed(mc.level, target.pos) < 0.0F) {
                removed = true;
            }
        } else if (!possibleBlocks.contains(target.pos)) {
            removed = true;
        }

        if (removed) {
            this.clearCurrentTarget();
        }
    }

    private void clearCurrentTarget() {
        this.stopDestroy();
        this.currentTarget = null;
        targetRotation = null;
    }

    private void stopDestroy() {
        if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    private List<BlockPos> searchBedPositions() {
        List<BlockPos> result = new ArrayList<>();
        double rangeValue = this.range.getValue().doubleValue();
        double rangeSqr = rangeValue * rangeValue;
        int radius = (int) Math.ceil(rangeValue);
        BlockPos playerPos = mc.player.blockPosition();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() instanceof BedBlock
                            && mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= rangeSqr) {
                        result.add(pos);
                    }
                }
            }
        }

        result.sort(Comparator.comparingDouble(p -> mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))));
        return result;
    }

    private boolean canSee(BlockPos pos) {
        // 修复：performRaycast 现在需要 (Rotation, boolean ignoreBlocks)。
        // 这里需要的是方块命中（BLOCK），因此传 false（不忽略方块）。
        HitResult hit = RotationUtil.performRaycast(targetRotation, false);
        return hit != null && hit.getType() == HitResult.Type.BLOCK && ((BlockHitResult) hit).getBlockPos().equals(pos);
    }

    private void doBreak(BlockPos pos) {
        // 修复：同上，需要方块命中，传 false。
        HitResult hit = RotationUtil.performRaycast(targetRotation, false);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        if (!blockHit.getBlockPos().equals(pos)) {
            return;
        }

        if (!mc.gameMode.isDestroying()) {
            mc.gameMode.startDestroyBlock(pos, blockHit.getDirection());
        } else {
            mc.gameMode.continueDestroyBlock(pos, blockHit.getDirection());
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Whether the bed is completely enclosed: a solid block on every horizontal side
     * (front, back, left and right). Mining a bed in this state is flagged by the server
     * ("you cannot mine a covered bed"), so an entrance has to be broken first.
     *
     * The second bed block (the bed is a two-block block) does not count as coverage.
     */
    private boolean isFullyCovered(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return false;
        }

        BlockPos otherPart = pos.relative(BedBlock.getConnectedDirection(state));
        BlockPos[] coveredPositions = {pos, otherPart};

        for (BlockPos bedPos : coveredPositions) {
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos neighbor = bedPos.relative(direction);
                if (neighbor.equals(pos) || neighbor.equals(otherPart)) {
                    continue;
                }

                BlockState neighborState = mc.level.getBlockState(neighbor);
                if (neighborState.isAir() || neighborState.getBlock() instanceof BedBlock) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The block next to the bed with the lowest mining duration, used to make an entrance
     * in NEAR_BLOCK mode.
     */
    private BlockPos weakestNeighbor(BlockPos pos) {
        Block block = mc.level.getBlockState(pos).getBlock();
        List<BlockPos> neighbours = new ArrayList<>();

        for (Direction direction : DIRECTIONS_EXCLUDING_DOWN) {
            BlockPos neighbor = pos.relative(direction);
            BlockState state = mc.level.getBlockState(neighbor);
            if (state.isAir() || state.getBlock() == block) {
                continue;
            }
            if (state.getDestroySpeed(mc.level, neighbor) < 0.0F) {
                continue;
            }
            neighbours.add(neighbor);
        }

        if (neighbours.isEmpty()) {
            return null;
        }

        neighbours.sort(Comparator.comparingDouble(n -> this.miningDuration(n, mc.level.getBlockState(n))));
        return neighbours.get(0);
    }

    private double miningDuration(BlockPos pos, BlockState state) {
        double bestMiningSpeed = 1.0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            bestMiningSpeed = Math.max(bestMiningSpeed, stack.getDestroySpeed(state));
        }
        return state.getDestroySpeed(mc.level, pos) / bestMiningSpeed;
    }

    private static final class DestroyerTarget {
        private final BlockPos pos;
        private final boolean isBed;

        private DestroyerTarget(BlockPos pos, boolean isBed) {
            this.pos = pos;
            this.isBed = isBed;
        }
    }
}