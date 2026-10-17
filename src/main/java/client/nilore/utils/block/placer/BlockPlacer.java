package client.nilore.utils.block.placer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import client.nilore.ClientBase;
import client.nilore.modules.impl.world.BlockIn2;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;

public final class BlockPlacer extends ClientBase {

    public record Slot(
            int slot,
            boolean offhand,
            ItemStack stack,
            float destroyTime
    ) {
        public InteractionHand hand() {
            return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        }
    }

    private record Queued(boolean support) {}

    private record PlacementTarget(
            BlockPos placePos,
            BlockPos interactedPos,
            Direction face,
            Vec3 hitVec,
            Rotation rotation,
            BlockHitResult constructedHit
    ) {}

    private final BlockIn2 module;
    private final Function<BlockPos, Slot> slotFinder;
    private final Function<BlockPos, Slot> supportSlotFinder;

    private final LinkedHashMap<BlockPos, Queued> blocks = new LinkedHashMap<>();
    private final Set<BlockPos> inaccessible = new HashSet<>();
    private final Map<BlockPos, Integer> failedTargetTicks = new HashMap<>();

    private int ticksToWait;
    private boolean ranAction;
    // Prevent far/through-wall scan-only holes from keeping BlockIn alive forever.
    private int noActionTicks;

    private long lastSupportSearchMs;

    private int clientOldSlot = -1;
    private int slotResetTicks;

    private PlacementTarget pendingRotationTarget;
    private boolean pendingRotationIsSupport;
    private int pendingRotationTicks;
    private int pendingNoProgressTicks;
    private double pendingLastRotationDistance = Double.NaN;

    // Placement ESP history uses wall-clock expiry so the outline can remain
    // visible for a short time even when BlockIn auto-disables after finishing.
    private final LinkedHashMap<BlockPos, Long> placedEspUntil = new LinkedHashMap<>();

    // Roof fallback state.  Normal visible placements are always attempted
    // first; jumping is only used when the roof is still missing and no
    // currently visible legal target can be placed.
    private enum RoofJumpState { IDLE, RISING, FALLING, COOLDOWN }
    private RoofJumpState roofJumpState = RoofJumpState.IDLE;
    private int roofJumpCooldownTicks;
    private boolean supportPlacedDuringJump;
    private double jumpStartY;
    private boolean jumpKeyInjected;
    private boolean jumpKeyWasDown;

    public BlockPlacer(
            BlockIn2 module,
            Function<BlockPos, Slot> slotFinder,
            Function<BlockPos, Slot> supportSlotFinder
    ) {
        this.module = module;
        this.slotFinder = slotFinder;
        this.supportSlotFinder = supportSlotFinder;
    }

    public void update(Collection<BlockPos> positions) {
        Set<BlockPos> requested = new LinkedHashSet<>(positions);

        blocks.entrySet().removeIf(entry ->
                !entry.getValue().support()
                        && !requested.contains(entry.getKey()));

        blocks.entrySet().removeIf(entry ->
                entry.getValue().support()
                        && !isReplaceable(entry.getKey()));

        for (BlockPos pos : requested) {
            if (isReplaceable(pos)) {
                blocks.put(pos.immutable(), new Queued(false));
            } else {
                blocks.remove(pos);
            }
        }
    }

    public void tick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        tickSlotReset();
        tickFailedTargetCooldowns();
        tickRoofJumpState();
        ensureDynamicJumpSupportCandidate();

        // Airborne helpers are timing-sensitive.  As soon as a real visible
        // support face exists, place it in the same tick with a synchronized
        // position+rotation packet instead of waiting several client ticks for
        // the normal visual rotation pipeline.
        if (tryImmediateAirSupportPlacement()) {
            noActionTicks = 0;
            return;
        }

        // OnTickRot must cover the entire jump fallback, not just the first
        // auxiliary block.  During descent the roof window is very short, so
        // close it immediately with the same one-tick rotation path instead of
        // sending it back through the normal interpolated scheduler.
        if (tryImmediateFallingRoofPlacement()) {
            noActionTicks = 0;
            return;
        }

        if (ticksToWait > 0) {
            ticksToWait--;
        } else if (ranAction) {
            ranAction = false;
            ticksToWait = module.getPlaceDelayTicks();
        }

        if (mc.screen instanceof AbstractContainerScreen<?>) {
            return;
        }

        if (mc.player.isUsingItem()) {
            return;
        }

        if (blocks.isEmpty()) {
            clearRotation();
            noActionTicks = Math.min(2, noActionTicks + 1);
            return;
        }

        Slot availabilitySlot = slotFinder.apply(null);
        if (availabilitySlot == null) {
            return;
        }

        inaccessible.clear();

        if (module.isOnTickRot()) {
            module.targetRotation = null;
            if (pendingRotationTarget != null) {
                pendingRotationTarget = null;
                pendingRotationIsSupport = false;
                pendingRotationTicks = 0;
            }
        }

        if (!module.isOnTickRot()
                && module.isNormalRotationMode()
                && pendingRotationTarget != null) {

            pendingRotationTicks++;

            // Do not use a fixed 4-tick timeout here.  With a low Rotation
            // Speed a perfectly valid target can legitimately need many ticks
            // to converge.  Only abandon it when the sent rotation stops making
            // measurable progress for several consecutive ticks.
            Rotation current = currentRotation();
            double rotationLeft = rotationDistance(
                    current,
                    pendingRotationTarget.rotation()
            );

            if (!Double.isNaN(pendingLastRotationDistance)
                    && rotationLeft >= pendingLastRotationDistance - 0.025D) {
                pendingNoProgressTicks++;
            } else {
                pendingNoProgressTicks = 0;
            }
            pendingLastRotationDistance = rotationLeft;

            if (!isTargetStillPlaceable(pendingRotationTarget)
                    || pendingNoProgressTicks > 12) {
                BlockPos failed = pendingRotationTarget.placePos();
                boolean failedSupport = pendingRotationIsSupport;
                failTarget(failed, failedSupport ? 5 : 12);
                clearRotation();
            } else {
                module.targetRotation = stepToward(
                        current,
                        pendingRotationTarget.rotation(),
                        effectiveRotationSpeed(
                                pendingRotationIsSupport,
                                pendingRotationTarget.placePos()
                        )
                );

                if (ticksToWait <= 0
                        && rotationReached(pendingRotationTarget)) {

                    PlacementTarget attemptedTarget = pendingRotationTarget;
                    boolean wasSupport = pendingRotationIsSupport;

                    boolean placed = doPlacement(
                            wasSupport,
                            attemptedTarget
                    );

                    pendingRotationTarget = null;
                    pendingRotationIsSupport = false;
                    pendingRotationTicks = 0;
                    // Keep last rotation target until next placement is queued.
                    // This makes BlockIn behave like Scaffold: smooth continuous aim.
                    if (placed) {
                        module.targetRotation = attemptedTarget.rotation();
                    } else {
                        module.targetRotation = null;
                    }

                    if (placed) {
                        ranAction = true;
                        scheduleCurrentPlacements(availabilitySlot.stack());
                    } else {
                        // A useItemOn rejection means the candidate was only
                        // geometrically plausible, not actually placeable.
                        // Back it off long enough to let another target win.
                        failTarget(attemptedTarget.placePos(), wasSupport ? 5 : 12);
                        clearRotation();
                    }
                }
                noActionTicks = 0;
                return;
            }
        }

        if (scheduleCurrentPlacements(availabilitySlot.stack())) {
            noActionTicks = 0;
            return;
        }

        if (module.isSupportEnabled()
                && System.currentTimeMillis() - lastSupportSearchMs >= module.getSupportDelayMs()) {
            findSupportPath();
            lastSupportSearchMs = System.currentTimeMillis();
            if (scheduleCurrentPlacements(availabilitySlot.stack())) {
                noActionTicks = 0;
                return;
            }
        }

        // Only after every normal/direct/support option has failed do we use
        // the jump fallback. Discovery can see through walls, but placement
        // still requires a real ray hit and <= 3 blocks.
        RoofJumpState beforeJump = roofJumpState;
        tryStartRoofJumpFallback();
        if (roofJumpState != RoofJumpState.IDLE || roofJumpState != beforeJump) {
            noActionTicks = 0;
        } else {
            noActionTicks = Math.min(20, noActionTicks + 1);
        }
    }

    private boolean scheduleCurrentPlacements(ItemStack stackToPlaceWith) {
        List<Map.Entry<BlockPos, Queued>> entries =
                new ArrayList<>(blocks.entrySet());

        PlacementTarget bestTarget = null;
        boolean bestSupport = false;
        double bestScore = Double.MAX_VALUE;

        Rotation reference = currentRotation();

        for (Map.Entry<BlockPos, Queued> entry : entries) {
            BlockPos pos = entry.getKey();

            if (failedTargetTicks.containsKey(pos)
                    || inaccessible.contains(pos)
                    || isBlocked(pos)) {
                continue;
            }

            // The jump fallback is intentionally limited to one auxiliary
            // support block. After that, wait for the descending phase and
            // concentrate on the actual roof target.
            if (entry.getValue().support()
                    && supportPlacedDuringJump
                    && (roofJumpState == RoofJumpState.RISING
                    || roofJumpState == RoofJumpState.FALLING)) {
                continue;
            }

            PlacementTarget target =
                    findBestPlacementTarget(pos, stackToPlaceWith);

            if (target == null) {
                inaccessible.add(pos);
                continue;
            }

            if (!canReachTarget(target, target.rotation())) {
                inaccessible.add(pos);
                continue;
            }

            // Nearest legal click point is the primary ordering rule.
            // Rotation is only a tiny tie-breaker so a farther, easy-to-turn-to
            // target can never beat a genuinely closer one.
            double distance = mc.player.getEyePosition(1.0f).distanceTo(target.hitVec());
            double angle = rotationDistance(reference, target.rotation());
            double score = distance * 1000.0 + angle * 0.01;

            // While descending from the roof fallback, close the actual roof
            // as soon as it becomes legally reachable.
            BlockPos roof = module.getRoofPos();
            if (roofJumpState == RoofJumpState.FALLING
                    && roof != null
                    && roof.equals(target.placePos())
                    && !entry.getValue().support()) {
                score -= 100000.0;
            }

            if (bestTarget == null || score < bestScore) {
                bestTarget = target;
                bestSupport = entry.getValue().support();
                bestScore = score;
            }
        }

        if (bestTarget == null) {
            if (!module.isOnTickRot()
                    && module.isNormalRotationMode()) {
                clearRotation();
            }
            return false;
        }

        if (module.isOnTickRot()) {
            if (ticksToWait <= 0) {
                if (doOnTickRotPlacement(bestSupport, bestTarget)) {
                    ranAction = true;
                } else {
                    failTarget(
                            bestTarget.placePos(),
                            bestSupport ? 1 : 3
                    );
                }
            }
            return true;
        }

        if (module.isNormalRotationMode()) {
            if (pendingRotationTarget == null
                    || !pendingRotationTarget.placePos().equals(bestTarget.placePos())
                    || !pendingRotationTarget.interactedPos().equals(bestTarget.interactedPos())
                    || pendingRotationTarget.face() != bestTarget.face()) {
                pendingRotationTicks = 0;
            }
            pendingRotationTarget = bestTarget;
            pendingRotationIsSupport = bestSupport;

            double turnDistance = rotationDistance(reference, bestTarget.rotation());
            float effectiveSpeed = effectiveRotationSpeed(bestSupport, bestTarget.placePos());
            module.targetRotation = turnDistance <= effectiveSpeed
                    ? bestTarget.rotation()
                    : stepToward(
                    reference,
                    bestTarget.rotation(),
                    effectiveSpeed
            );

            return true;
        }

        if (ticksToWait <= 0) {
            if (doPlacement(bestSupport, bestTarget)) {
                ranAction = true;
            } else {
                failTarget(
                        bestTarget.placePos(),
                        bestSupport ? 1 : 3
                );
            }
        }

        return true;
    }

    private PlacementTarget findBestPlacementTarget(
            BlockPos placePos,
            ItemStack stackToPlaceWith
    ) {
        if (!isReplaceable(placePos)) {
            return null;
        }

        PlacementTarget best = null;
        double bestScore = Double.MAX_VALUE;

        Rotation reference = currentRotation();

        final double[] samples = {-0.28, 0.0, 0.28};

        for (Direction neighborDirection : Direction.values()) {
            BlockPos interacted =
                    placePos.relative(neighborDirection);

            BlockState state =
                    mc.level.getBlockState(interacted);

            if (state.canBeReplaced()) {
                continue;
            }

            Direction clickedFace =
                    neighborDirection.getOpposite();

            for (double a : samples) {
                for (double b : samples) {
                    Vec3 hitVec = facePoint(
                            interacted,
                            clickedFace,
                            a,
                            b
                    );

                    Rotation rotation = rotationTo(
                            mc.player.getEyePosition(1.0f),
                            hitVec
                    );

                    BlockHitResult constructed =
                            new BlockHitResult(
                                    hitVec,
                                    clickedFace,
                                    interacted,
                                    false
                            );

                    PlacementTarget candidate =
                            new PlacementTarget(
                                    placePos.immutable(),
                                    interacted.immutable(),
                                    clickedFace,
                                    hitVec,
                                    rotation,
                                    constructed
                            );

                    if (!canReachTarget(candidate, rotation)) {
                        continue;
                    }

                    // A candidate is not placeable unless the real client
                    // ray reaches exactly the support block face.  This is what
                    // permits through-wall scanning without through-wall use.
                    if (!rayMatchesTarget(candidate, rotation)) {
                        continue;
                    }

                    double angle =
                            rotationDistance(reference, rotation);

                    double distance =
                            mc.player
                                    .getEyePosition(1.0f)
                                    .distanceTo(hitVec);

                    double score =
                            distance * 1000.0
                                    + angle * 0.01
                                    + (Math.abs(a) + Math.abs(b)) * 0.001;

                    if (best == null || score < bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }

        return best;
    }

    private Vec3 facePoint(
            BlockPos block,
            Direction face,
            double offsetA,
            double offsetB
    ) {
        double x =
                block.getX() + 0.5
                        + face.getStepX() * 0.5001;

        double y =
                block.getY() + 0.5
                        + face.getStepY() * 0.5001;

        double z =
                block.getZ() + 0.5
                        + face.getStepZ() * 0.5001;

        if (face.getAxis() == Direction.Axis.Y) {
            x += offsetA;
            z += offsetB;
        } else if (face.getAxis() == Direction.Axis.X) {
            y += offsetA;
            z += offsetB;
        } else {
            x += offsetA;
            y += offsetB;
        }

        return new Vec3(x, y, z);
    }

    private Rotation rotationTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double horizontal =
                Math.sqrt(dx * dx + dz * dz);

        float yaw =
                (float) (
                        Math.toDegrees(Math.atan2(dz, dx))
                                - 90.0
                );

        float pitch =
                (float) (
                        -Math.toDegrees(
                                Math.atan2(dy, horizontal)
                        )
                );

        return new Rotation(
                Mth.wrapDegrees(yaw),
                Mth.clamp(pitch, -90.0F, 90.0F)
        );
    }

    private boolean canReach(
            BlockPos interactedPos,
            Rotation rotation
    ) {
        Vec3 eye = mc.player.getEyePosition(1.0f);

        double distanceSq = eye.distanceToSqr(
                interactedPos.getX() + 0.5,
                interactedPos.getY() + 0.5,
                interactedPos.getZ() + 0.5
        );

        double max = Math.max(
                module.getPlacerRange(),
                module.getPlacerWallRange()
        );

        if (distanceSq > max * max) {
            return false;
        }

        HitResult trace =
                traceFromPlayer(
                        module.getPlacerRange(),
                        rotation
                );

        if (trace instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit
                .getBlockPos()
                .equals(interactedPos)) {
            return true;
        }

        double wall =
                module.getPlacerWallRange();

        return module.isConstructFailResult()
                && distanceSq <= wall * wall;
    }

    private boolean canReachTarget(
            PlacementTarget target,
            Rotation rotation
    ) {
        if (mc.player == null || target == null) {
            return false;
        }

        Vec3 eye = mc.player.getEyePosition(1.0f);
        double max = module.getPlacerRange(); // hard 3.0 blocks

        if (eye.distanceToSqr(target.hitVec()) > max * max) {
            return false;
        }

        // Never allow ConstructFailResult / WallRange to turn a blocked ray
        // into a placement.  The support face must really be visible.
        return rayMatchesTarget(target, rotation);
    }

    private boolean rayMatchesTarget(
            PlacementTarget target,
            Rotation rotation
    ) {
        HitResult trace =
                traceFromPlayer(
                        module.getPlacerRange(),
                        rotation
                );

        return trace instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(target.interactedPos())
                && blockHit.getDirection() == target.face();
    }

    private boolean rotationReached(
            PlacementTarget target
    ) {
        Rotation verification =
                RotationHandler.sentRotation;

        if (verification == null) {
            return false;
        }

        float yawDiff = Math.abs(
                Mth.wrapDegrees(
                        verification.getYaw()
                                - target.rotation().getYaw()
                )
        );

        float pitchDiff = Math.abs(
                verification.getPitch()
                        - target.rotation().getPitch()
        );

        return yawDiff <= 2.0F
                && pitchDiff <= 2.0F
                && canReachTarget(
                target,
                verification
        );
    }

    private boolean isTargetStillPlaceable(
            PlacementTarget target
    ) {
        if (target == null
                || mc.level == null
                || !isReplaceable(target.placePos())) {
            return false;
        }

        if (mc.level
                .getBlockState(target.interactedPos())
                .canBeReplaced()) {
            return false;
        }

        return canReachTarget(
                target,
                target.rotation()
        );
    }

    private boolean doOnTickRotPlacement(boolean isSupport, PlacementTarget target) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) {
            return false;
        }

        Slot slot = isSupport
                ? supportSlotFinder.apply(target.placePos())
                : slotFinder.apply(target.placePos());

        if (slot == null
                || slot.stack() == null
                || slot.stack().isEmpty()
                || !(slot.stack().getItem() instanceof BlockItem)) {
            return false;
        }

        Rotation targetRot = target.rotation();

        // Match the same reach/ray requirement used by the normal placer,
        // but evaluate it against the spoofed rotation.
        if (!canReachTarget(target, targetRot)) {
            return false;
        }

        // OneTickRot is a real one-tick aim/use, so never fall back to a
        // constructed hit here.  The simulated ray must genuinely hit the
        // intended support block AND face.  This also guarantees that scan may
        // see through walls while OnTickRot placement still cannot.
        if (!rayMatchesTarget(target, targetRot)) {
            return false;
        }

        HitResult rawHit = traceFromPlayer(module.getPlacerRange(), targetRot);
        if (!(rawHit instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(target.interactedPos())
                || hit.getDirection() != target.face()) {
            return false;
        }

        if (!isReplaceable(target.placePos())) {
            blocks.remove(target.placePos());
            return false;
        }

        if (!selectSlot(slot)) {
            return false;
        }

        float originalYaw = mc.player.getYRot();
        float originalPitch = mc.player.getXRot();

        try {
            // Equivalent purpose to Scaffold's C06/PosRot placement path.
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    targetRot.getYaw(),
                    targetRot.getPitch(),
                    mc.player.onGround()
            ));

            var result = mc.gameMode.useItemOn(mc.player, slot.hand(), hit);
            if (!result.consumesAction()) {
                return false;
            }

            mc.player.swing(slot.hand());
            blocks.remove(target.placePos());
            onPlacementSucceeded(isSupport, target.placePos());
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    originalYaw,
                    originalPitch,
                    mc.player.onGround()
            ));
        }
    }

    private boolean doPlacement(boolean isSupport, PlacementTarget target) {
        Slot slot = isSupport
                ? supportSlotFinder.apply(target.placePos())
                : slotFinder.apply(target.placePos());

        if (slot == null
                || slot.stack() == null
                || slot.stack().isEmpty()
                || !(slot.stack().getItem() instanceof BlockItem)) {
            return false;
        }

        Rotation verification = module.isNormalRotationMode()
                ? (RotationHandler.sentRotation != null
                ? RotationHandler.sentRotation
                : target.rotation())
                : target.rotation();

        if (!canReachTarget(target, verification)) {
            return false;
        }

        BlockHitResult hit = raytraceTarget(target, verification);
        if (hit == null) {
            return false;
        }

        if (!isReplaceable(target.placePos())) {
            blocks.remove(target.placePos());
            return false;
        }

        if (!selectSlot(slot)) {
            return false;
        }

        try {
            var result = mc.gameMode.useItemOn(mc.player, slot.hand(), hit);
            if (!result.consumesAction()) {
                return false;
            }

            mc.player.swing(slot.hand());
            blocks.remove(target.placePos());
            onPlacementSucceeded(isSupport, target.placePos());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private BlockHitResult raytraceTarget(
            PlacementTarget target,
            Rotation verificationRotation
    ) {
        HitResult ray = traceFromPlayer(
                module.getPlacerRange(),
                verificationRotation
        );

        if (ray instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(target.interactedPos())
                && blockHit.getDirection() == target.face()) {
            return blockHit;
        }

        return null;
    }

    private HitResult traceFromPlayer(double range, Rotation rotation) {
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 direction = Vec3.directionFromRotation(
                rotation.getPitch(),
                rotation.getYaw()
        );
        Vec3 end = eye.add(direction.scale(range));

        return mc.level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
    }

    private boolean selectSlot(Slot slot) {
        if (slot.offhand()) {
            return true;
        }

        if (slot.slot() < 0 || slot.slot() > 8) {
            return false;
        }

        if (clientOldSlot == -1) {
            clientOldSlot = mc.player.getInventory().selected;
        }

        mc.player.getInventory().selected = slot.slot();
        slotResetTicks = randomInclusive(
                module.getSlotResetMin(),
                module.getSlotResetMax()
        );
        return true;
    }

    private void onPlacementSucceeded(boolean support, BlockPos placePos) {
        if (placePos != null && module.isEspEnabled()) {
            placedEspUntil.put(
                    placePos.immutable(),
                    System.currentTimeMillis() + module.getEspKeepMs()
            );
        }

        if (support && (roofJumpState == RoofJumpState.RISING
                || roofJumpState == RoofJumpState.FALLING)) {
            supportPlacedDuringJump = true;
        }
    }

    private void tickRoofJumpState() {
        if (jumpKeyInjected && mc.options != null) {
            mc.options.keyJump.setDown(jumpKeyWasDown);
            jumpKeyInjected = false;
        }

        if (mc.player == null) {
            roofJumpState = RoofJumpState.IDLE;
            return;
        }

        if (roofJumpCooldownTicks > 0) {
            roofJumpCooldownTicks--;
            if (roofJumpCooldownTicks == 0 && roofJumpState == RoofJumpState.COOLDOWN) {
                roofJumpState = RoofJumpState.IDLE;
            }
        }

        if (roofJumpState == RoofJumpState.RISING
                && mc.player.getDeltaMovement().y <= 0.0D) {
            roofJumpState = RoofJumpState.FALLING;
        }

        if ((roofJumpState == RoofJumpState.RISING
                || roofJumpState == RoofJumpState.FALLING)
                && mc.player.onGround()
                && Math.abs(mc.player.getY() - jumpStartY) < 0.20D) {
            roofJumpState = RoofJumpState.COOLDOWN;
            roofJumpCooldownTicks = 2;
            supportPlacedDuringJump = false;
        }
    }

    private void tryStartRoofJumpFallback() {
        if (!module.isAutoJumpRoofEnabled()
                || mc.player == null
                || mc.level == null
                || roofJumpState != RoofJumpState.IDLE
                || !mc.player.onGround()) {
            return;
        }

        BlockPos roof = module.getRoofPos();
        if (roof == null || !blocks.containsKey(roof) || !isReplaceable(roof)) {
            return;
        }

        // One final direct check before jumping.  If standing still can reach a
        // side/top support face, normal rotation must handle it instead.
        Slot slot = slotFinder.apply(roof);
        if (slot != null) {
            PlacementTarget direct = findBestPlacementTarget(roof, slot.stack());
            if (direct != null && canReachTarget(direct, direct.rotation())) {
                return;
            }
        }

        // Do not carry unreachable ground-state bridge nodes into the jump.
        // Airborne support candidates are rebuilt every tick from the new eye
        // position, otherwise the module can keep rotating toward a support
        // that was only theoretically discovered through the scan.
        blocks.entrySet().removeIf(entry -> entry.getValue().support());
        clearRotation();

        jumpStartY = mc.player.getY();
        supportPlacedDuringJump = false;
        roofJumpState = RoofJumpState.RISING;

        // Use the normal client jump key path instead of calling the protected
        // LivingEntity jump implementation directly. It is held for one tick
        // and then restored to the user's previous key state.
        if (mc.options != null) {
            jumpKeyWasDown = mc.options.keyJump.isDown();
            mc.options.keyJump.setDown(true);
            jumpKeyInjected = true;
        }
    }

    /**
     * While airborne, continuously re-evaluate the four blocks beside the roof.
     * A support that was impossible while standing may become legally clickable
     * as the eye position rises. Only a real ray-hit candidate is queued.
     */
    private void ensureDynamicJumpSupportCandidate() {
        if (mc.player == null
                || mc.level == null
                || !module.isSupportEnabled()
                || supportPlacedDuringJump
                || (roofJumpState != RoofJumpState.RISING
                && roofJumpState != RoofJumpState.FALLING)) {
            return;
        }

        BlockPos roof = module.getRoofPos();
        if (roof == null || !isReplaceable(roof)) {
            return;
        }

        Slot supportSlot = supportSlotFinder.apply(roof);
        if (supportSlot == null || supportSlot.stack() == null || supportSlot.stack().isEmpty()) {
            return;
        }

        PlacementTarget best = null;
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 eye = mc.player.getEyePosition(1.0f);

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidatePos = roof.relative(dir).immutable();

            if (!isReplaceable(candidatePos)
                    || isBlockedByEntities(candidatePos)
                    || failedTargetTicks.containsKey(candidatePos)) {
                continue;
            }

            PlacementTarget candidate = findBestPlacementTarget(
                    candidatePos,
                    supportSlot.stack()
            );

            if (candidate == null
                    || !canReachTarget(candidate, candidate.rotation())) {
                continue;
            }

            double distance = eye.distanceTo(candidate.hitVec());
            if (best == null || distance < bestDistance) {
                best = candidate;
                bestPos = candidatePos;
                bestDistance = distance;
            }
        }

        if (bestPos != null) {
            // Keep exactly the useful airborne helper; stale support bridge
            // nodes make the placer look at unreachable blocks.
            BlockPos selectedSupportPos = bestPos;
            blocks.entrySet().removeIf(e -> e.getValue().support()
                    && !e.getKey().equals(selectedSupportPos));
            blocks.put(selectedSupportPos, new Queued(true));
        }
    }

    /**
     * Fast path used only during the jump fallback.  It still obeys the same
     * <=3 block reach and exact ray-hit checks as normal placement, but avoids
     * losing the short airborne placement window to rotation interpolation.
     */
    private boolean tryImmediateAirSupportPlacement() {
        if (mc.player == null
                || mc.level == null
                || mc.gameMode == null
                || mc.getConnection() == null
                || supportPlacedDuringJump
                || (roofJumpState != RoofJumpState.RISING
                && roofJumpState != RoofJumpState.FALLING)) {
            return false;
        }

        Slot supportSlot = supportSlotFinder.apply(module.getRoofPos());
        if (supportSlot == null || supportSlot.stack() == null || supportSlot.stack().isEmpty()) {
            return false;
        }

        PlacementTarget best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 eye = mc.player.getEyePosition(1.0f);

        for (Map.Entry<BlockPos, Queued> entry : new ArrayList<>(blocks.entrySet())) {
            if (!entry.getValue().support()
                    || failedTargetTicks.containsKey(entry.getKey())
                    || !isReplaceable(entry.getKey())
                    || isBlockedByEntities(entry.getKey())) {
                continue;
            }

            PlacementTarget candidate = findBestPlacementTarget(entry.getKey(), supportSlot.stack());
            if (candidate == null || !canReachTarget(candidate, candidate.rotation())) {
                continue;
            }

            double distance = eye.distanceTo(candidate.hitVec());
            if (best == null || distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }

        if (best == null) {
            return false;
        }

        // In OnTickRot mode the jump helper must use exactly the same spoofed
        // rotation path as ordinary placements.  In normal rotation mode we
        // leave the target queued so the smooth visual rotation pipeline owns
        // the placement instead of silently switching modes mid-jump.
        if (!module.isOnTickRot()) {
            return false;
        }

        clearRotation();
        boolean placed = doOnTickRotPlacement(true, best);
        if (placed) {
            ranAction = true;
            // Airborne windows are shorter than a normal place cooldown. Do not
            // carry the previous ground cooldown into the jump sequence.
            ticksToWait = 0;
            return true;
        }

        failTarget(best.placePos(), 2);
        return false;
    }

    /**
     * OnTickRot-specific roof finisher. Once the helper block exists and the
     * player starts descending, recompute the roof hit from the CURRENT eye
     * position and place it immediately. This keeps auto-jump and OnTickRot on
     * one consistent placement path.
     */
    private boolean tryImmediateFallingRoofPlacement() {
        if (!module.isOnTickRot()
                || mc.player == null
                || mc.level == null
                || mc.gameMode == null
                || mc.getConnection() == null
                || roofJumpState != RoofJumpState.FALLING) {
            return false;
        }

        BlockPos roof = module.getRoofPos();
        if (roof == null
                || !blocks.containsKey(roof)
                || !isReplaceable(roof)
                || failedTargetTicks.containsKey(roof)) {
            return false;
        }

        Slot slot = slotFinder.apply(roof);
        if (slot == null || slot.stack() == null || slot.stack().isEmpty()) {
            return false;
        }

        PlacementTarget roofTarget = findBestPlacementTarget(roof, slot.stack());
        if (roofTarget == null
                || !canReachTarget(roofTarget, roofTarget.rotation())
                || !rayMatchesTarget(roofTarget, roofTarget.rotation())) {
            return false;
        }

        clearRotation();
        boolean placed = doOnTickRotPlacement(false, roofTarget);
        if (placed) {
            ranAction = true;
            ticksToWait = 0;
            return true;
        }

        failTarget(roof, 2);
        return false;
    }

    private void tickSlotReset() {
        if (clientOldSlot == -1) {
            return;
        }

        if (slotResetTicks > 0) {
            slotResetTicks--;
            return;
        }

        if (mc.player != null) {
            mc.player.getInventory().selected = clientOldSlot;
        }

        clientOldSlot = -1;
    }

    private void findSupportPath() {
        blocks.entrySet().removeIf(entry ->
                entry.getValue().support()
                        && !isReplaceable(entry.getKey()));

        List<BlockPos> requestedNeedingSupport =
                new ArrayList<>();

        Slot probeSlot = slotFinder.apply(null);

        ItemStack probeStack =
                probeSlot != null
                        ? probeSlot.stack()
                        : ItemStack.EMPTY;

        for (Map.Entry<BlockPos, Queued> entry :
                new ArrayList<>(blocks.entrySet())) {

            if (entry.getValue().support()) {
                continue;
            }

            BlockPos structurePos =
                    entry.getKey();

            PlacementTarget direct =
                    findBestPlacementTarget(
                            structurePos,
                            probeStack
                    );

            if (direct != null
                    && canReachTarget(
                    direct,
                    direct.rotation()
            )) {
                continue;
            }

            requestedNeedingSupport.add(
                    structurePos
            );
        }

        for (BlockPos target :
                requestedNeedingSupport) {

            List<BlockPos> roofBridge =
                    findRoofBridge(target);

            if (roofBridge != null
                    && !roofBridge.isEmpty()) {

                for (BlockPos supportPos :
                        roofBridge) {

                    if (!blocks.containsKey(supportPos)
                            && isReplaceable(supportPos)) {

                        blocks.put(
                                supportPos.immutable(),
                                new Queued(true)
                        );
                    }
                }

                return;
            }
        }

        List<BlockPos> bestPath = null;

        for (BlockPos target :
                requestedNeedingSupport) {

            List<BlockPos> path =
                    findSupport(target);

            if (path == null) {
                continue;
            }

            if (bestPath == null
                    || path.size() < bestPath.size()) {
                bestPath = path;
            }
        }

        if (bestPath == null) {
            return;
        }

        for (BlockPos pos : bestPath) {
            if (!blocks.containsKey(pos)
                    && isReplaceable(pos)) {

                blocks.put(
                        pos.immutable(),
                        new Queued(true)
                );
            }
        }
    }

    private List<BlockPos> findRoofBridge(
            BlockPos target
    ) {
        Slot supportSlot =
                supportSlotFinder.apply(target);

        if (supportSlot == null
                || mc.player == null
                || mc.level == null) {
            return null;
        }

        // Scan Range is the single discovery radius. Placement reach remains
        // hard-capped separately at 3 blocks.
        int radius =
                Math.max(1, (int) Math.ceil(module.getScanRange()));

        double maxRange = module.getScanRange();

        double maxRangeSq =
                maxRange * maxRange;

        Rotation reference =
                currentRotation();

        List<BlockPos> bestPath = null;
        double bestScore = Double.MAX_VALUE;

        Vec3 eye =
                mc.player.getEyePosition(1.0f);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {

                if (dx == 0 && dz == 0) {
                    continue;
                }

                int manhattan =
                        Math.abs(dx) + Math.abs(dz);

                if (manhattan > radius) {
                    continue;
                }

                BlockPos anchor =
                        target.offset(dx, 0, dz)
                                .immutable();

                if (!isReplaceable(anchor)
                        || isBlockedByEntities(anchor)) {
                    continue;
                }

                if (eye.distanceToSqr(
                        Vec3.atCenterOf(anchor)
                ) > maxRangeSq) {
                    continue;
                }

                PlacementTarget anchorTarget =
                        findBestPlacementTarget(
                                anchor,
                                supportSlot.stack()
                        );

                if (anchorTarget == null
                        || !canReachTarget(
                        anchorTarget,
                        anchorTarget.rotation()
                )) {
                    continue;
                }

                List<BlockPos> path =
                        buildRoofPlanePath(
                                anchor,
                                target
                        );

                if (path == null
                        || path.isEmpty()) {
                    continue;
                }

                boolean inRange = true;

                for (BlockPos node : path) {
                    if (eye.distanceToSqr(
                            Vec3.atCenterOf(node)
                    ) > maxRangeSq) {
                        inRange = false;
                        break;
                    }
                }

                if (!inRange) {
                    continue;
                }

                double angle =
                        rotationDistance(
                                reference,
                                anchorTarget.rotation()
                        );

                double score =
                        path.size() * 12.0 + angle;

                if (bestPath == null
                        || score < bestScore) {
                    bestPath = path;
                    bestScore = score;
                }
            }
        }

        return bestPath;
    }

    private List<BlockPos> buildRoofPlanePath(
            BlockPos anchor,
            BlockPos target
    ) {
        // Scan Range is the single discovery radius. Placement reach remains
        // hard-capped separately at 3 blocks.
        int radius =
                Math.max(1, (int) Math.ceil(module.getScanRange()));

        ArrayDeque<BlockPos> queue =
                new ArrayDeque<>();

        Map<BlockPos, BlockPos> parent =
                new HashMap<>();

        queue.add(anchor);
        parent.put(anchor, null);

        BlockPos goal = null;

        while (!queue.isEmpty()) {
            BlockPos current =
                    queue.removeFirst();

            if (isHorizontalNeighbor(
                    current,
                    target
            )) {
                goal = current;
                break;
            }

            for (Direction direction :
                    Direction.Plane.HORIZONTAL) {

                BlockPos next =
                        current.relative(direction)
                                .immutable();

                if (parent.containsKey(next)) {
                    continue;
                }

                if (next.getY()
                        != target.getY()) {
                    continue;
                }

                if (next.distManhattan(target)
                        > radius) {
                    continue;
                }

                if (next.equals(target)) {
                    continue;
                }

                if (!isReplaceable(next)
                        || isBlockedByEntities(next)) {
                    continue;
                }

                parent.put(next, current);
                queue.addLast(next);
            }
        }

        if (goal == null) {
            return null;
        }

        List<BlockPos> reversed =
                new ArrayList<>();

        BlockPos cursor = goal;

        while (cursor != null) {
            reversed.add(cursor);
            cursor = parent.get(cursor);
        }

        Collections.reverse(reversed);
        return reversed;
    }

    private boolean isHorizontalNeighbor(
            BlockPos a,
            BlockPos b
    ) {
        return a.getY() == b.getY()
                && Math.abs(a.getX() - b.getX())
                + Math.abs(a.getZ() - b.getZ()) == 1;
    }

    private List<BlockPos> findSupport(BlockPos targetPos) {
        int depth = module.getSupportDepth();
        double rangeSq = module.getScanRange() * module.getScanRange();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Map<BlockPos, Integer> distance = new HashMap<>();

        BlockPos start = targetPos.immutable();
        queue.add(start);
        parent.put(start, null);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            int currentDepth = distance.get(current);

            if (!current.equals(start)
                    && isReplaceable(current)
                    && hasAnySolidPlacementNeighbor(current)) {
                return reconstructPath(parent, current, start);
            }

            if (currentDepth >= depth) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction).immutable();

                if (parent.containsKey(neighbor)) {
                    continue;
                }

                if (neighbor.distManhattan(targetPos) > depth) {
                    continue;
                }

                if (blocks.containsKey(neighbor)) {
                    continue;
                }

                if (!isReplaceable(neighbor)) {
                    continue;
                }

                if (isBlockedByEntities(neighbor)) {
                    continue;
                }

                Vec3 eye = mc.player.getEyePosition(1.0f);
                double dx = neighbor.getX() + 0.5 - eye.x;
                double dy = neighbor.getY() + 0.5 - eye.y;
                double dz = neighbor.getZ() + 0.5 - eye.z;
                if (dx * dx + dy * dy + dz * dz > rangeSq) {
                    continue;
                }

                parent.put(neighbor, current);
                distance.put(neighbor, currentDepth + 1);
                queue.addLast(neighbor);
            }
        }

        return null;
    }

    private List<BlockPos> reconstructPath(
            Map<BlockPos, BlockPos> parent,
            BlockPos goal,
            BlockPos start
    ) {
        List<BlockPos> reversed = new ArrayList<>();
        BlockPos cursor = goal;

        while (cursor != null && !cursor.equals(start)) {
            reversed.add(cursor);
            cursor = parent.get(cursor);
        }

        return reversed;
    }

    private boolean hasAnySolidPlacementNeighbor(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!mc.level.getBlockState(neighbor).canBeReplaced()) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlocked(BlockPos pos) {
        if (!isReplaceable(pos)) {
            blocks.remove(pos);
            inaccessible.add(pos);
            return true;
        }

        if (isBlockedByEntities(pos)) {
            inaccessible.add(pos);
            return true;
        }

        return false;
    }

    private boolean isBlockedByEntities(BlockPos pos) {
        AABB box = new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + 1.0,
                pos.getY() + 1.0,
                pos.getZ() + 1.0
        );

        List<Entity> entities = mc.level.getEntities(
                (Entity) null,
                box,
                entity -> !entity.isSpectator() && entity.isAlive()
        );

        for (Entity entity : entities) {
            if (entity != mc.player) {
                return true;
            }
        }

        return false;
    }

    private boolean isReplaceable(BlockPos pos) {
        return mc.level != null
                && mc.level.getBlockState(pos).canBeReplaced();
    }

    private Rotation currentRotation() {
        if (RotationHandler.sentRotation != null) {
            return RotationHandler.sentRotation;
        }

        if (module.targetRotation != null) {
            return module.targetRotation;
        }

        return new Rotation(
                mc.player.getYRot(),
                mc.player.getXRot()
        );
    }

    /**
     * Smooth adaptive rotation.
     *
     * Large turns start quickly, then ease out as the crosshair approaches the
     * placement point.  This avoids the old constant-speed / one-tick snap while
     * still keeping the placer responsive.
     */
    private Rotation stepToward(
            Rotation current,
            Rotation target,
            float maxDegrees
    ) {
        float yawDelta = Mth.wrapDegrees(
                target.getYaw() - current.getYaw()
        );
        float pitchDelta = target.getPitch() - current.getPitch();

        double distance = Math.sqrt(
                yawDelta * yawDelta + pitchDelta * pitchDelta
        );

        // Tiny dead-zone: snap the last fraction of a degree to prevent jitter.
        if (distance <= 0.65D) {
            return target;
        }

        // Smoothstep-style adaptive speed. Far away = fast, near target = gentle.
        double normalized = Mth.clamp(distance / 90.0D, 0.0D, 1.0D);
        double eased = normalized * normalized * (3.0D - 2.0D * normalized);

        // Keep a speed-scaled minimum step.  A small user speed therefore
        // remains genuinely smooth instead of being forced to jump 1.35° each
        // tick, while still guaranteeing convergence.
        double minStep = Mth.clamp(maxDegrees * 0.10D, 0.30D, 1.20D);
        double desiredStep = minStep + (Math.max(1.0F, maxDegrees) - minStep) * eased;
        double step = Math.min(distance, desiredStep);
        double scale = step / distance;

        float nextYaw = Mth.wrapDegrees(
                current.getYaw() + (float) (yawDelta * scale)
        );
        float nextPitch = Mth.clamp(
                current.getPitch() + (float) (pitchDelta * scale),
                -90.0F,
                90.0F
        );

        return new Rotation(nextYaw, nextPitch);
    }

    /**
     * Ground placements obey the user's configured smooth speed. During the
     * very short airborne roof window we keep a conservative minimum so low
     * Rotation Speed values do not make the jump fallback physically miss its
     * chance to place. It is still interpolated/smooth; only OnTickRot snaps.
     */
    private float effectiveRotationSpeed(boolean supportTarget, BlockPos placePos) {
        float configured = module.getRotationSpeed();
        if (roofJumpState == RoofJumpState.RISING
                || roofJumpState == RoofJumpState.FALLING) {
            BlockPos roof = module.getRoofPos();
            if (supportTarget || (roof != null && roof.equals(placePos))) {
                return Math.max(configured, 35.0F);
            }
        }
        return configured;
    }

    private double rotationDistance(
            Rotation a,
            Rotation b
    ) {
        double yaw = Math.abs(
                Mth.wrapDegrees(
                        b.getYaw() - a.getYaw()
                )
        );

        double pitch = Math.abs(
                b.getPitch() - a.getPitch()
        );

        return Math.sqrt(
                yaw * yaw + pitch * pitch
        );
    }

    private void failTarget(BlockPos pos, int ticks) {
        if (pos != null) {
            failedTargetTicks.put(
                    pos.immutable(),
                    Math.max(1, ticks)
            );
        }
    }

    private void tickFailedTargetCooldowns() {
        failedTargetTicks.replaceAll(
                (pos, ticks) -> ticks - 1
        );

        failedTargetTicks.entrySet()
                .removeIf(entry -> entry.getValue() <= 0);
    }

    private int randomInclusive(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);

        if (min == max) {
            return min;
        }

        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void clearRotation() {
        pendingRotationTarget = null;
        pendingRotationIsSupport = false;
        pendingRotationTicks = 0;
        pendingNoProgressTicks = 0;
        pendingLastRotationDistance = Double.NaN;
        module.targetRotation = null;
    }

    public java.util.Set<BlockPos> getEspBlocks() {
        long now = System.currentTimeMillis();
        // Do not delete an entry just because the local world still reports
        // air. Immediately after useItemOn there can be a short client/server
        // update delay; removing it here was why the previous ESP appeared to
        // do nothing. Expiry is the authoritative cleanup.
        placedEspUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        return new LinkedHashSet<>(placedEspUntil.keySet());
    }

    public boolean isDone() {
        if (pendingRotationTarget != null
                || roofJumpState == RoofJumpState.RISING
                || roofJumpState == RoofJumpState.FALLING) {
            return false;
        }

        // Repair Scan is allowed to discover targets that are intentionally
        // not placeable (too far or behind a wall). Those scan-only entries
        // must not prevent AutoDisable forever after all currently actionable
        // work has been exhausted.
        return blocks.isEmpty() || noActionTicks >= 2;
    }

    public void reset() {
        blocks.clear();
        inaccessible.clear();
        failedTargetTicks.clear();
        ticksToWait = 0;
        ranAction = false;
        noActionTicks = 0;
        lastSupportSearchMs = 0L;
        pendingRotationTarget = null;
        pendingRotationIsSupport = false;
        if (jumpKeyInjected && mc.options != null) {
            mc.options.keyJump.setDown(jumpKeyWasDown);
        }
        jumpKeyInjected = false;
        jumpKeyWasDown = false;
        roofJumpState = RoofJumpState.IDLE;
        roofJumpCooldownTicks = 0;
        supportPlacedDuringJump = false;
        jumpStartY = 0.0D;

        if (mc.player != null && clientOldSlot >= 0) {
            mc.player.getInventory().selected = clientOldSlot;
        }

        clientOldSlot = -1;
        slotResetTicks = 0;
        clearRotation();
    }

    public void disable() {
        reset();
    }
}