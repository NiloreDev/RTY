package client.nilore.modules.impl.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import client.nilore.utils.rotation.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.settings.impl.TextSetting;
import client.nilore.utils.block.placer.BlockPlacer;
import client.nilore.utils.render.BlockInESP;

/**
 * LiquidBounce ModuleBlockIn -> Open Nilore 1.20.1 compatibility port.
 */
public class BlockIn2 extends Module {

    public static BlockIn2 INSTANCE;

    public final BooleanSetting autoDisable =
            new BooleanSetting("AutoDisable", true);

    public final ModeSetting placeOrder =
            new ModeSetting("PlaceOrder", "Normal", "Random", "BottomTop", "TopBottom")
                    .withDefault("Normal");

    public final ModeSetting filter =
            new ModeSetting("Filter", "Blacklist", "Whitelist")
                    .withDefault("Blacklist");

    public final TextSetting blocks =
            new TextSetting("Blocks", "");

    // Scan radius only.  The actual interaction reach is intentionally hard
    // capped at 3.0 blocks in BlockPlacer.  This lets BlockIn discover
    // anchors through walls / farther away without ever placing through them.
    public final NumberSetting placerRange =
            new NumberSetting("Scan Range", 4.5, 1.0, 12.0, 0.1);

    // Kept for config compatibility with older profiles.  It no longer grants
    // through-wall placement; visibility is mandatory before every useItemOn.
    public final NumberSetting placerWallRange =
            new NumberSetting("Placer WallRange", 0.0, 0.0, 6.0, 0.1);

    public final BooleanSetting autoJumpRoof =
            new BooleanSetting("Auto Jump Roof", true);

    public final BooleanSetting repairScan =
            new BooleanSetting("Repair Scan", true);

    public final BooleanSetting esp =
            new BooleanSetting("ESP", true);

    public final NumberSetting espKeepSeconds =
            new NumberSetting("ESP Keep Seconds", 6.0, 0.5, 30.0, 0.5);

    public final NumberSetting cooldownMin =
            new NumberSetting("Placer Cooldown Min", 0, 0, 40, 1);

    public final NumberSetting cooldownMax =
            new NumberSetting("Placer Cooldown Max", 0, 0, 40, 1);

    public final NumberSetting slotResetMin =
            new NumberSetting("Placer SlotReset Min", 4, 0, 40, 1);

    public final NumberSetting slotResetMax =
            new NumberSetting("Placer SlotReset Max", 6, 0, 40, 1);

    public final BooleanSetting constructFailResult =
            new BooleanSetting("Placer ConstructFailResult", true);

    // Same switch idea as Scaffold.OnTickRot:
    // spoof the exact placement rotation for the placement tick, then restore.
    public final BooleanSetting onTickRot =
            new BooleanSetting("OnTickRot", false);

    public final NumberSetting rotationSpeed =
            new NumberSetting("Rotation Speed", 55.0, 5.0, 180.0, 1.0);

    public final NumberSetting placeSpeed =
            new NumberSetting("Place Delay", 0, 0, 500, 10);

    public final ModeSetting rotationMode =
            new ModeSetting("Rotation Mode", "Normal", "None")
                    .withDefault("Normal");

    public final BooleanSetting support =
            new BooleanSetting("Placer Support", true);

    public final NumberSetting supportDepth =
            new NumberSetting("Support Depth", 4, 1, 12, 1);

    public final NumberSetting roofSearchRadius =
            new NumberSetting("Roof Search Radius", 4, 1, 6, 1);

    public final NumberSetting supportDelay =
            new NumberSetting("Support Delay", 0, 0, 1000, 10);

    public final ModeSetting supportFilter =
            new ModeSetting("Support Filter", "Blacklist", "Whitelist")
                    .withDefault("Blacklist");

    public final TextSetting supportBlocks =
            new TextSetting("Support Blocks", "");

    private final BlockPlacer blockPlacer;
    public Rotation targetRotation;

    private BlockPos startPos;
    private boolean rotateClockwise;
    private List<BlockPos> blockList = Collections.emptyList();

    public BlockIn2() {
        super("BlockIn2", Category.WORLD);
        INSTANCE = this;
        this.blockPlacer = new BlockPlacer(this, this::slotFinder, this::supportSlotFinder);
        // v9: self-register the world-render ESP. No external render hook is required.
        BlockInESP.bootstrap();
    }

    @Override
    public String getDisplayName() {
        String order = placeOrder.getValue();
        if (order == null || order.isEmpty()) {
            return "§fBlockIn";
        }
        return "§fBlockIn[" + order + "]";
    }

    @Override
    public String getModuleName() {
        return "BlockIn";
    }

    @Override
    public String getSuffix() {
        String order = placeOrder.getValue();
        if (order == null || order.isEmpty()) {
            return null;
        }
        return "[" + order + "]";
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        startPos = mc.player.blockPosition();
        rotateClockwise = ThreadLocalRandom.current().nextBoolean();
        targetRotation = null;
        getPositions();
        blockPlacer.reset();

        super.onEnable();
    }

    @Override
    protected void onDisable() {
        startPos = null;
        blockList = Collections.emptyList();
        targetRotation = null;
        blockPlacer.disable();
        super.onDisable();
    }

    @Override
    public void onTick() {
    }

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || startPos == null) {
            return;
        }

        BlockPos currentPos = mc.player.blockPosition();
        if (!currentPos.equals(startPos) && !currentPos.equals(startPos.above())) {
            setEnabled(false);
            return;
        }

        // Re-scan every tick. Scan Range is discovery only; BlockPlacer still
        // enforces the fixed 3-block real-ray interaction distance.
        getPositions();
        blockPlacer.update(blockList);
        blockPlacer.tick();

        if (!blockPlacer.isDone()) {
            return;
        }

        if (autoDisable.getValue()) {
            setEnabled(false);
            return;
        }

        getPositions();
    }

    private void getPositions() {
        blockList = positionsForOrder();
    }

    private List<BlockPos> positionsForOrder() {
        List<BlockPos> positions = normalPositions();

        if (placeOrder.is("Random")) {
            Collections.shuffle(positions);
        } else if (placeOrder.is("BottomTop")) {
            positions.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        } else if (placeOrder.is("TopBottom")) {
            positions.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        }

        return positions;
    }

    private List<BlockPos> normalPositions() {
        if (mc.player == null || mc.level == null || startPos == null) {
            return Collections.emptyList();
        }

        int playerHeight = Mth.ceil(mc.player.getBbHeight());
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();

        // Core enclosure around the activation position.
        result.add(startPos.below());

        Direction direction = mc.player.getDirection();
        for (int side = 0; side < 4; side++) {
            BlockPos value = startPos.relative(direction);
            for (int i = 0; i < playerHeight; i++) {
                result.add(value.above(i));
            }

            direction = rotateClockwise
                    ? direction.getClockWise()
                    : direction.getCounterClockWise();
        }

        result.add(startPos.above(playerHeight));

        // Full 3D repair discovery. This is intentionally visibility-agnostic:
        // it may discover a hole through a wall, but BlockPlacer will refuse
        // useItemOn unless a real <=3-block ray hits the exact support face.
        if (repairScan.getValue()) {
            addRepairHoles(result);
        }

        return new ArrayList<>(result);
    }

    private void addRepairHoles(LinkedHashSet<BlockPos> result) {
        BlockPos center = mc.player.blockPosition();
        int range = Mth.ceil(getScanRange());
        double rangeSq = getScanRange() * getScanRange();
        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    // Center-to-center sphere check; the small allowance prevents
                    // edge cells from flickering in/out at fractional ranges.
                    double cx = dx + 0.5D;
                    double cy = dy + 0.5D;
                    double cz = dz + 0.5D;
                    if (cx * cx + cy * cy + cz * cz > rangeSq + 0.75D) {
                        continue;
                    }

                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(pos).canBeReplaced()) {
                        continue;
                    }
                    if (!looksLikeRepairHole(pos)) {
                        continue;
                    }
                    candidates.add(pos.immutable());
                }
            }
        }

        // Nearest discovered hole first. Final placement ordering is even more
        // precise: BlockPlacer ranks by eye -> actual hitVec distance.
        candidates.sort((a, b) -> Double.compare(
                distanceSqToCenter(a),
                distanceSqToCenter(b)
        ));
        result.addAll(candidates);
    }

    private boolean looksLikeRepairHole(BlockPos pos) {
        int solidCount = 0;
        boolean up = false, down = false, north = false, south = false, west = false, east = false;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!mc.level.getBlockState(neighbor).canBeReplaced()) {
                solidCount++;
                switch (direction) {
                    case UP -> up = true;
                    case DOWN -> down = true;
                    case NORTH -> north = true;
                    case SOUTH -> south = true;
                    case WEST -> west = true;
                    case EAST -> east = true;
                }
            }
        }

        // Strong cavity / corner evidence. Avoid treating ordinary open air as
        // something that BlockIn should fill.
        if (solidCount >= 3) {
            return true;
        }
        if ((up && down) || (north && south) || (west && east)) {
            return true;
        }
        if (solidCount == 2) {
            int axes = 0;
            if (up || down) axes++;
            if (north || south) axes++;
            if (west || east) axes++;
            return axes >= 2;
        }
        return false;
    }

    private double distanceSqToCenter(BlockPos pos) {
        double dx = pos.getX() + 0.5D - mc.player.getX();
        double dy = pos.getY() + 0.5D - mc.player.getEyeY();
        double dz = pos.getZ() + 0.5D - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private BlockPlacer.Slot slotFinder(BlockPos pos) {
        if (mc.player == null) {
            return null;
        }

        List<BlockPlacer.Slot> candidates = collectSlots(false);
        if (candidates.isEmpty()) {
            return null;
        }

        boolean requested = pos != null && blockList.contains(pos);
        BlockPlacer.Slot best = candidates.get(0);

        for (int i = 1; i < candidates.size(); i++) {
            BlockPlacer.Slot candidate = candidates.get(i);
            if (requested) {
                if (candidate.destroyTime() > best.destroyTime()) {
                    best = candidate;
                }
            } else {
                if (candidate.destroyTime() < best.destroyTime()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private BlockPlacer.Slot supportSlotFinder(BlockPos ignored) {
        if (mc.player == null) {
            return null;
        }

        List<BlockPlacer.Slot> candidates = collectSlots(true);
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(0);
    }

    private List<BlockPlacer.Slot> collectSlots(boolean forSupport) {
        List<BlockPlacer.Slot> result = new ArrayList<>(10);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            BlockPlacer.Slot candidate = toSlot(stack, slot, false, forSupport);
            if (candidate != null) {
                result.add(candidate);
            }
        }

        BlockPlacer.Slot offhand =
                toSlot(mc.player.getOffhandItem(), -1, true, forSupport);
        if (offhand != null) {
            result.add(offhand);
        }

        return result;
    }

    private BlockPlacer.Slot toSlot(
            ItemStack stack,
            int slot,
            boolean offhand,
            boolean forSupport
    ) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        Block block = blockItem.getBlock();
        if (!passesFilter(block, forSupport)) {
            return null;
        }

        return new BlockPlacer.Slot(slot, offhand, stack, block.defaultDestroyTime());
    }

    private boolean passesFilter(Block block, boolean forSupport) {
        String id = BuiltInRegistries.BLOCK.getKey(block)
                .toString()
                .toLowerCase(Locale.ROOT);

        String mode = forSupport ? supportFilter.getValue() : filter.getValue();
        String raw = forSupport ? supportBlocks.getValue() : blocks.getValue();

        boolean contains = parseIds(raw).contains(id);
        return "Whitelist".equals(mode) ? contains : !contains;
    }

    private java.util.Set<String> parseIds(String raw) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }

        for (String part : raw.split(",")) {
            String id = part.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            if (!id.contains(":")) {
                id = "minecraft:" + id;
            }
            ids.add(id);
        }
        return ids;
    }

    /** User controlled discovery / support scan radius. */
    public double getScanRange() {
        return placerRange.getValue().doubleValue();
    }

    /**
     * Hard interaction distance requested for BlockIn.  Do not tie this to the
     * scan slider: scanning may be much farther than legal placement.
     */
    public double getPlacerRange() {
        return 3.0D;
    }

    /** Compatibility getter. Through-wall placement is disabled in BlockPlacer. */
    public double getPlacerWallRange() {
        return 0.0D;
    }

    public boolean isAutoJumpRoofEnabled() {
        return autoJumpRoof.getValue();
    }

    public BlockPos getStartPos() {
        return startPos;
    }

    public BlockPos getRoofPos() {
        if (startPos == null || mc.player == null) {
            return null;
        }
        return startPos.above(Mth.ceil(mc.player.getBbHeight()));
    }

    public boolean isEspEnabled() {
        return esp.getValue();
    }

    public long getEspKeepMs() {
        return Math.max(100L, Math.round(espKeepSeconds.getValue().doubleValue() * 1000.0D));
    }

    public java.util.Set<BlockPos> getEspBlocks() {
        return blockPlacer.getEspBlocks();
    }

    public int getCooldownMin() {
        return cooldownMin.getValue().intValue();
    }

    public int getCooldownMax() {
        return cooldownMax.getValue().intValue();
    }

    public int getSlotResetMin() {
        return slotResetMin.getValue().intValue();
    }

    public int getSlotResetMax() {
        return slotResetMax.getValue().intValue();
    }

    public boolean isConstructFailResult() {
        return constructFailResult.getValue();
    }

    public boolean isOnTickRot() {
        return onTickRot.getValue();
    }

    public float getRotationSpeed() {
        return rotationSpeed.getValue().floatValue();
    }

    public int getPlaceDelayTicks() {
        return Math.max(0, placeSpeed.getValue().intValue() / 50);
    }

    public boolean isNormalRotationMode() {
        return rotationMode.is("Normal");
    }

    public boolean isSupportEnabled() {
        return support.getValue();
    }

    public int getSupportDepth() {
        return supportDepth.getValue().intValue();
    }

    public int getRoofSearchRadius() {
        return roofSearchRadius.getValue().intValue();
    }

    public long getSupportDelayMs() {
        return supportDelay.getValue().longValue();
    }
}