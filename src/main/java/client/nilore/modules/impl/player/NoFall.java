package client.nilore.modules.impl.player;

import client.nilore.NiloreClient;
import client.nilore.modules.impl.MoveInputEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.*;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.Setting;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.misc.PacketUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;

import java.util.ArrayList;
import java.util.List;

public
class NoFall extends Module {
    public static NoFall INSTANCE;

    // ========== 添加这两个字段供 JumpResetMode 使用 ==========
    public boolean boostActive = false;
    public boolean jumpLandingBoost = false;

    // ========== 设置（按模式分组，带可见条件） ==========
    private final ModeSetting mode = new ModeSetting("Mode", "GrimAC", "MLG", "Packet").withDefault("GrimAC");

    // Grim 模式设置
    private final NumberSetting grimSkipTicks = new NumberSetting("Grim Skip", 1, 0, 4, 1,
            () -> mode.is("GrimAC"));
    private final BooleanSetting skipTick = new BooleanSetting("Skip Tick", true,
            () -> mode.is("GrimAC"));
    private final NumberSetting distance = new NumberSetting("Dist", 3.3, 0, 5, 0.1,
            () -> mode.is("GrimAC") || mode.is("Packet"));

    // MLG 模式设置
    private final NumberSetting triggerDistance = new NumberSetting("Trigger", 3.0, 1.0, 10.0, 0.1,
            () -> mode.is("MLG"));
    private final NumberSetting predictTicks = new NumberSetting("Predict", 2, 1, 5, 1,
            () -> mode.is("MLG"));
    private final BooleanSetting solidCheck = new BooleanSetting("Solid Check", true,
            () -> mode.is("MLG"));
    private final BooleanSetting autoRecovery = new BooleanSetting("Auto Rec", true,
            () -> mode.is("MLG"));
    private final BooleanSetting swing = new BooleanSetting("Swing", true,
            () -> mode.is("MLG"));

    // ========== 状态变量 ==========
    private boolean o;
    private boolean receivedPositionPacket;
    public boolean isFalling;
    private boolean flagR;
    private boolean flagA;
    private boolean flagL;
    private float fallDistanceTracker;
    private double lastY;
    private Integer previousSlot;
    private boolean isMLGActive;
    private boolean isRecoveringWater;
    private int recoveryTicks;
    private int bucketAttempts;
    private Integer bucketSlot;
    private BlockPos targetWaterPos;
    private boolean gFlag;
    private int delay1, delay2, delay3, delay4, delay5;
    private PendingAction currentAction;

    public NoFall() {
        super("NoFall", Category.PLAYER);
        INSTANCE = this;
    }

    // ========== 关键修复：重写 getSettings() 手动返回设置列表 ==========
    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> list = new ArrayList<>();
        list.add(mode);
        list.add(grimSkipTicks);
        list.add(skipTick);
        list.add(distance);
        list.add(triggerDistance);
        list.add(predictTicks);
        list.add(solidCheck);
        list.add(autoRecovery);
        list.add(swing);
        return list;
    }

    // ================================================================
    // getModuleName - 返回模块名称
    // ================================================================
    @Override
    public String getModuleName() {
        return "NoFall";
    }

    // ================================================================
    // getSuffix - 返回不带颜色代码的后缀（纯文本）
    // ================================================================
    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return null;
        }
        // 返回不带颜色代码的后缀，颜色由 ModuleListHud 控制
        return "[" + modeName + "]";
    }

    @Override
    public void onTick() {

    }

    // ================================================================
    // getDisplayName - 覆盖主题颜色，全部显示为白色
    // ================================================================
    @Override
    public String getDisplayName() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fNoFall";
        }
        return "§fNoFall[" + modeName + "]";
    }

    @Override
    public void onEnable() {
        NiloreClient.getInstance().getEventBus().register(this);
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        NiloreClient.getInstance().getEventBus().unregister(this);
    }

    // ========== 事件监听 ==========

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.isSpectator()) return;

        updateFallTracker();

        if (delay1 > 0) delay1--;
        if (delay2 > 0) delay2--;
        if (delay3 > 0) delay3--;

        if (mode.is("MLG")) {
            onTickMLG();
        }
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mc.player == null || mc.level == null) return;

        String currentMode = mode.getValue();

        if (currentMode.equals("Packet")) {
            handlePacketMode(event);
        }

        if (currentMode.equals("GrimAC")) {
            handleGrimMode(event);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            if (isFalling) {
                receivedPositionPacket = true;
                if (mode.is("GrimAC")) {
                    flagR = true;
                    isFalling = false;
                    receivedPositionPacket = false;
                    PacketUtil.sendQueued(new ServerboundMovePlayerPacket.StatusOnly(false));
                }
            }
        }

        if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
            if (isFalling && o && !receivedPositionPacket) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        if (flagL) {
            if (isScaffoldEnabled()) {
                flagL = false;
                return;
            }
            event.setJumping(true);
            flagL = false;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (mc.player.onGround() && flagR && !mc.options.keyShift.isDown()) {
            mc.player.setShiftKeyDown(false);
            flagR = false;
        }
    }

    // ========== 核心逻辑 ==========

    private void updateFallTracker() {
        if (mc.player == null) return;

        if (mc.player.onGround() || mc.player.getAbilities().flying || mc.player.isFallFlying() || mc.player.isInWater()) {
            fallDistanceTracker = 0.0f;
        } else {
            double yDiff = mc.player.getY() - lastY;
            if (yDiff < 0.0) {
                fallDistanceTracker -= (float) yDiff;
            }
        }
        lastY = mc.player.getY();
    }

    private void handlePacketMode(PreMotionEvent event) {
        if (!isFalling && mc.player.fallDistance > distance.getValue().doubleValue() && !event.isOnGround()) {
            isFalling = true;
            receivedPositionPacket = false;
            o = false;
        }
        if (isFalling && event.isOnGround()) {
            event.setOnGround(false);
            if (!o) {
                PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Pos(
                        event.getX() + 1337.0, event.getY(), event.getZ() + 1337.0, false));
                o = true;
            }
        }
    }

    private void handleGrimMode(PreMotionEvent event) {
        double fallDelta = 0;
        if (mc.player != null) {
            fallDelta = mc.player.fallDistance - mc.player.getDeltaMovement().y;
        }
        if (fallDelta > distance.getValue().doubleValue()) {
            flagA = true;
            flagL = false;
        } else {
            flagA = false;
        }

        if (!isScaffoldEnabled() && flagA && mc.player.onGround()) {
            event.setY(event.getY() + 1.0);
            flagL = true;
        }
    }

    // ========== MLG 逻辑 ==========

    private void onTickMLG() {
        if (mc.player == null || mc.level == null) return;

        if (mc.player.onGround() || fallDistanceTracker <= 0.0f) {
            isMLGActive = false;
            gFlag = false;
        }

        handleRecoveringWater();

        if (isRecoveringWater) return;

        if (!isMLGActive && !isRecoveringWater && targetWaterPos == null && delay1 == 0 && delay2 == 0
                && fallDistanceTracker <= 0.5f && findItemSlot(Items.WATER_BUCKET) == null) {
            Integer emptyBucketSlot = findItemSlot(Items.BUCKET);
            if (emptyBucketSlot != null) {
                BlockPos targetPos = findWaterTarget();
                if (targetPos != null) {
                    RotationTarget target = checkLineOfSightBlock(targetPos, 4.5);
                    if (target != null) {
                        executeUseItem(target, emptyBucketSlot);
                        return;
                    }
                }
            }
        }

        if (isMLGActive && !gFlag && mc.player.getDeltaMovement().y < 0.0) {
            double dist = getDistanceToGround(2.5);
            if (dist > 0.0 && dist <= 1.05) {
                gFlag = true;
            }
        }

        if (isMLGActive) return;

        if (fallDistanceTracker < triggerDistance.getValue().floatValue()) return;

        Integer waterBucketSlot = findItemSlot(Items.WATER_BUCKET);
        if (waterBucketSlot == null) return;

        int predictedTicks = predictTicksToFall();
        if (predictedTicks <= predictTicks.getValue().intValue()) {
            RotationTarget target = predictLandingTarget(5.0);
            if (target != null) {
                executePlaceWater(waterBucketSlot, target);
            }
        }
    }

    private void handleRecoveringWater() {
        if (mc.player == null || mc.level == null) return;

        if (!isRecoveringWater) return;

        if (mc.player.isFallFlying() && !mc.player.onGround()) {
            if (delay4++ < 1) {
                recoveryTicks = Math.max(recoveryTicks, 1);
                bucketAttempts = Math.max(bucketAttempts, 2);
                return;
            }
        } else {
            delay4 = 0;
        }

        if (recoveryTicks > 0) {
            recoveryTicks--;
            return;
        }

        if (bucketAttempts-- <= 0) {
            isRecoveringWater = false;
            return;
        }

        if (bucketSlot == null) {
            bucketSlot = findItemSlot(Items.BUCKET);
            if (bucketSlot == null) {
                isRecoveringWater = false;
                return;
            }
        }

        if (mc.player.getInventory().getItem(bucketSlot).getItem() == Items.WATER_BUCKET) {
            isRecoveringWater = false;
            bucketSlot = null;
            targetWaterPos = null;
            delay1 = Math.max(delay1, 1);
            return;
        }

        if (targetWaterPos == null || !isValidWaterTarget(targetWaterPos)) {
            isRecoveringWater = false;
            bucketSlot = null;
            targetWaterPos = null;
            return;
        }

        RotationTarget target = checkLineOfSightBlock(targetWaterPos, 4.5);
        if (target == null) {
            isRecoveringWater = false;
            bucketSlot = null;
            targetWaterPos = null;
            return;
        }

        executeUseItem(target, bucketSlot);
    }

    private void executePlaceWater(int slot, RotationTarget target) {
        setAction(new PendingAction(ActionType.PLACE_WATER, target, slot));
    }

    private void executeUseItem(RotationTarget target, int slot) {
        setAction(new PendingAction(ActionType.USE_ITEM, target, slot));
    }

    private void setAction(PendingAction action) {
        currentAction = action;
        delay5 = 0;
        setRotationsForAction(action);
    }

    private void setRotationsForAction(PendingAction action) {
        if (action == null || action.getTarget() == null) return;

        Rotation rot = new Rotation(
                action.getTarget().getRotation().getYaw(),
                action.getTarget().getRotation().getPitch()
        );
        RotationHandler.setTargetRotation(rot);
    }

    private void executeAction() {
        if (currentAction == null || mc.player == null) return;

        if (!verifyTargeting(currentAction)) {
            if (++delay5 > 3) {
                currentAction = null;
                return;
            }
            setRotationsForAction(currentAction);
            return;
        }

        PendingAction action = currentAction;
        currentAction = null;
        delay5 = 0;

        if (previousSlot == null) {
            previousSlot = mc.player.getInventory().selected;
        }
        mc.player.getInventory().selected = action.getSlot();

        swingHand();

        if (action.getType() == ActionType.PLACE_WATER) {
            setupMLG(action.getTarget());
        } else {
            delay2 = 1;
            delay1 = Math.max(delay1, 1);
        }
    }

    private void setupMLG(RotationTarget target) {
        isMLGActive = true;
        isRecoveringWater = autoRecovery.getValue();
        recoveryTicks = 1;
        bucketAttempts = isRecoveringWater ? 2 : 0;
        bucketSlot = null;
        delay4 = 0;
        targetWaterPos = target.getBlockPos().relative(target.getDirection());
    }

    private void swingHand() {
        if (mc.gameMode == null || mc.player == null) return;

        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    private boolean verifyTargeting(PendingAction action) {
        if (mc.player == null || mc.level == null) return false;

        Rotation currentRot = RotationHandler.targetRotation;
        if (currentRot == null) return false;

        if (action.getType() == ActionType.PLACE_WATER) {
            BlockHitResult result = rayTraceBlock(
                    new Vector2f(currentRot.getYaw(), currentRot.getPitch()), 5.0
            );
            return result != null && result.getType() == HitResult.Type.BLOCK &&
                    result.getBlockPos().equals(action.getTarget().getBlockPos());
        } else {
            BlockHitResult result = rayTraceBlock(
                    new Vector2f(currentRot.getYaw(), currentRot.getPitch()), 4.5
            );
            return result != null && result.getType() != HitResult.Type.MISS &&
                    result.getBlockPos().equals(action.getTarget().getBlockPos());
        }
    }

    // ========== 辅助方法 ==========

    private BlockPos findWaterTarget() {
        if (mc.player == null || mc.level == null) return null;

        BlockPos playerPos = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        BlockPos bestPos = null;
        double closestDist = Double.POSITIVE_INFINITY;
        int range = 4;

        for (int yOffset = -1; yOffset <= 1; yOffset++) {
            for (int xOffset = -range; xOffset <= range; xOffset++) {
                for (int zOffset = -range; zOffset <= range; zOffset++) {
                    BlockPos checkPos = playerPos.offset(xOffset, yOffset, zOffset);
                    if (!isValidWaterTarget(checkPos)) continue;

                    double dist = mc.player.getEyePosition(1.0f).distanceToSqr(
                            checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5);
                    if (dist >= closestDist) continue;

                    RotationTarget target = checkLineOfSightBlock(checkPos, 4.5);
                    if (target == null) continue;

                    bestPos = checkPos;
                    closestDist = dist;
                }
            }
        }
        return bestPos;
    }

    private boolean isValidWaterTarget(BlockPos pos) {
        if (mc.level == null) return false;
        FluidState state = mc.level.getFluidState(pos);
        return state.getType() == Fluids.WATER && state.isSource();
    }

    private RotationTarget checkLineOfSightBlock(BlockPos pos, double maxDistance) {
        if (mc.player == null || mc.level == null) return null;

        RotationTarget bestTarget = null;
        double minDistance = Double.POSITIVE_INFINITY;

        for (double dx = 0.2; dx <= 0.8; dx += 0.2) {
            for (double dy = 0.2; dy <= 0.8; dy += 0.2) {
                for (double dz = 0.2; dz <= 0.8; dz += 0.2) {
                    Vec3 testVec = new Vec3(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    Rotation rot = RotationUtil.exactRotation(mc.player.getEyePosition(1.0f), testVec);
                    if (rot == null) continue;

                    BlockHitResult result = rayTraceBlock(new Vector2f(rot.getYaw(), rot.getPitch()), maxDistance);
                    if (result.getType() == HitResult.Type.MISS || !result.getBlockPos().equals(pos)) continue;

                    double dist = mc.player.getEyePosition(1.0f).distanceToSqr(testVec);
                    if (dist < minDistance) {
                        bestTarget = new RotationTarget(
                                new Vector2f(rot.getYaw(), rot.getPitch()),
                                result.getBlockPos(),
                                result.getDirection(),
                                testVec
                        );
                        minDistance = dist;
                    }
                }
            }
        }
        return bestTarget;
    }

    private BlockHitResult rayTraceBlock(Vector2f rot, double distance) {
        if (mc.player == null || mc.level == null) return BlockHitResult.miss(new Vec3(0, 0, 0), Direction.UP, BlockPos.ZERO);

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookVec = Vec3.directionFromRotation(rot.getPitch(), rot.getYaw());
        Vec3 targetVec = eyePos.add(lookVec.scale(distance));
        return mc.level.clip(new ClipContext(eyePos, targetVec, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, mc.player));
    }

    private RotationTarget predictLandingTarget(double distance) {
        if (mc.player == null || mc.level == null) return null;

        Vec3 predictedVec = getPredictedPos();
        BlockPos basePos = BlockPos.containing(predictedVec.x, predictedVec.y, predictedVec.z);
        RotationTarget bestTarget = null;
        double closestDist = Double.POSITIVE_INFINITY;

        for (int i = 1; i <= 4; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    BlockPos checkPos = basePos.offset(j, -i, k);
                    if (!isSolidBlock(checkPos)) continue;

                    Vec3 clampedVec = clampVecToBlock(checkPos, predictedVec);
                    RotationTarget target = createTargetIfValid(checkPos, Direction.UP, clampedVec, distance);
                    if (target == null) continue;

                    double dist = getDistanceSqr(predictedVec, checkPos) * 1000.0 + i;
                    if (dist < closestDist) {
                        bestTarget = target;
                        closestDist = dist;
                    }
                }
            }
        }
        return bestTarget;
    }

    private RotationTarget createTargetIfValid(BlockPos pos, Direction dir, Vec3 vec, double distance) {
        if (mc.player == null || mc.level == null) return null;

        Rotation rot = RotationUtil.exactRotation(mc.player.getEyePosition(1.0f), vec);
        if (rot == null) return null;

        BlockHitResult result = rayTraceBlock(new Vector2f(rot.getYaw(), rot.getPitch()), distance);
        if (result.getType() == HitResult.Type.MISS || !result.getBlockPos().equals(pos)) {
            return null;
        }
        return new RotationTarget(new Vector2f(rot.getYaw(), rot.getPitch()), pos, dir, vec);
    }

    private boolean isSolidBlock(BlockPos pos) {
        if (mc.level == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (state.getCollisionShape(mc.level, pos).isEmpty()) {
            return false;
        }
        return !solidCheck.getValue() || state.getFluidState().isEmpty();
    }

    private Vec3 clampVecToBlock(BlockPos pos, Vec3 vec) {
        double x = clampValue(vec.x, pos.getX() + 0.2, pos.getX() + 0.8);
        double z = clampValue(vec.z, pos.getZ() + 0.2, pos.getZ() + 0.8);
        return new Vec3(x, pos.getY() + 1.0, z);
    }

    private double clampValue(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private double getDistanceSqr(Vec3 vec, BlockPos pos) {
        double clampedX = clampValue(vec.x, pos.getX(), pos.getX() + 1.0);
        double clampedZ = clampValue(vec.z, pos.getZ(), pos.getZ() + 1.0);
        double diffX = vec.x - clampedX;
        double diffZ = vec.z - clampedZ;
        return diffX * diffX + diffZ * diffZ;
    }

    private double getDistanceToGround(double maxDistance) {
        if (mc.player == null || mc.level == null) return Double.POSITIVE_INFINITY;

        Vec3 startPos = new Vec3(mc.player.getX(), mc.player.getBoundingBox().minY, mc.player.getZ());
        Vec3 endPos = startPos.add(0.0, -maxDistance, 0.0);
        BlockHitResult result = mc.level.clip(new ClipContext(
                startPos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));

        if (result.getType() == HitResult.Type.MISS) {
            return Double.POSITIVE_INFINITY;
        }
        return startPos.y - result.getLocation().y;
    }

    private int predictTicksToFall() {
        if (mc.player == null) return 999;
        if (mc.player.getDeltaMovement().y >= 0.0) {
            return 999;
        }

        double distToGround = getDistanceToGround(30.0);
        if (distToGround == Double.POSITIVE_INFINITY) {
            return 999;
        }

        double currentDist = 0.0;
        double currentMotionY = mc.player.getDeltaMovement().y;

        for (int i = 1; i <= 20; i++) {
            currentDist += currentMotionY;
            currentMotionY = (currentMotionY - 0.08) * 0.98;
            if (Math.abs(currentDist) >= distToGround) {
                return i;
            }
        }
        return 999;
    }

    private Vec3 getPredictedPos() {
        if (mc.player == null) return Vec3.ZERO;

        int ticks = Math.max(1, Math.min(predictTicksToFall(), predictTicks.getValue().intValue() + 1));
        double predX = mc.player.getX();
        double predZ = mc.player.getZ();
        Vec3 motion = mc.player.getDeltaMovement();

        for (int i = 0; i < ticks; i++) {
            predX += motion.x;
            predZ += motion.z;
        }
        return new Vec3(predX, mc.player.getBoundingBox().minY, predZ);
    }

    private Integer findItemSlot(Item item) {
        if (mc.player == null) return null;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) {
                return i;
            }
        }
        return null;
    }

    private boolean isScaffoldEnabled() {
        try {
            Class<?> scaffoldClass = Class.forName("client.nilore.modules.impl.movement.Scaffold");
            Object instance = scaffoldClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method isEnabled = scaffoldClass.getMethod("isEnabled");
            return (boolean) isEnabled.invoke(instance);
        } catch (Exception e) {
            return false;
        }
    }

    private void resetState() {
        if (mc.player == null) return;

        receivedPositionPacket = false;
        isFalling = false;
        o = false;
        flagA = false;
        flagL = false;

        if (previousSlot != null) {
            mc.player.getInventory().selected = previousSlot;
            previousSlot = null;
        }
        isMLGActive = false;
        isRecoveringWater = false;
        recoveryTicks = 0;
        bucketAttempts = 0;
        bucketSlot = null;
        targetWaterPos = null;
        gFlag = false;
        delay1 = 0;
        delay2 = 0;
        delay3 = 0;
        delay4 = 0;
        delay5 = 0;
        currentAction = null;
        fallDistanceTracker = 0.0f;
        lastY = mc.player.getY();
    }

    public boolean isVisualFlagActive() {
        if (!isEnabled() || !mode.is("GrimAC") || mc.player == null || mc.level == null) {
            return false;
        }
        return isFalling || (!mc.player.onGround() && mc.player.fallDistance > distance.getValue().doubleValue());
    }

    // ========== 内部类 ==========

    private static class Vector2f {
        private final float yaw;
        private final float pitch;

        public Vector2f(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
    }

    private static class RotationTarget {
        private final Vector2f rotation;
        private final BlockPos blockPos;
        private final Direction direction;
        private final Vec3 vec;

        public RotationTarget(Vector2f rotation, BlockPos blockPos, Direction direction, Vec3 vec) {
            this.rotation = rotation;
            this.blockPos = blockPos;
            this.direction = direction;
            this.vec = vec;
        }

        public Vector2f getRotation() { return rotation; }
        public BlockPos getBlockPos() { return blockPos; }
        public Direction getDirection() { return direction; }
        public Vec3 getVec() { return vec; }
    }

    private enum ActionType {
        PLACE_WATER,
        USE_ITEM
    }

    private static class PendingAction {
        private final ActionType type;
        private final RotationTarget target;
        private final int slot;

        public PendingAction(ActionType type, RotationTarget target, int slot) {
            this.type = type;
            this.target = target;
            this.slot = slot;
        }

        public ActionType getType() { return type; }
        public RotationTarget getTarget() { return target; }
        public int getSlot() { return slot; }
    }
}