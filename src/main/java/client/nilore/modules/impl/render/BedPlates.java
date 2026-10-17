package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.WorldChangeEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.math.Vector2f;
import client.nilore.utils.render.ProjectionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BedPlates extends Module {

    private static final Direction[] CHECK_DIRECTIONS = {
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private final BooleanSetting minimal =
            new BooleanSetting("Minimal", false);

    private final BooleanSetting showDistance =
            new BooleanSetting("Show Distance", true);

    /*
     * 板子整体透明度：0 = 全透明，255 = 不透明。
     * 文字会取 alpha 与 255 之间的较大值，保证可读性。
     */
    private final NumberSetting alpha =
            new NumberSetting("Alpha", 160, 0, 255, 5);

    private final NumberSetting range =
            new NumberSetting("Range", 32, 12, 64, 4);

    private final NumberSetting refreshTicks =
            new NumberSetting("Refresh Ticks", 20, 5, 60, 1);

    private final List<BedEntry> beds =
            new ArrayList<>();

    private int tickCounter;

    private boolean projectionReady;

    public BedPlates() {
        super("BedPlates", Category.RENDER);
    }

    @Override
    public String getDisplayName() {
        return "BedPlates";
    }

    @Override
    public String getModuleName() {
        return "BedPlates";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onEnable() {
        beds.clear();
        tickCounter = 0;
        projectionReady = false;
    }

    @Override
    protected void onDisable() {
        beds.clear();
        projectionReady = false;
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        beds.clear();
        tickCounter = 0;
        projectionReady = false;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) {
            beds.clear();
            return;
        }

        tickCounter++;

        if (tickCounter >= refreshTicks.getValue().intValue() || beds.isEmpty()) {
            tickCounter = 0;
            scanBeds();
        }
    }

    @EventTarget
    public void onRender3D(RenderEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        ProjectionUtil.updateMatrices();
        projectionReady = true;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null || !projectionReady) {
            return;
        }

        int a = alpha.getValue().intValue();
        int panelAlpha = a;
        // 色条稍微更不透明一点，让它更醒目
        int barAlpha = Math.min(255, a + 40);
        // 文字保持高可读性，最低 200
        int textAlpha = Math.max(200, a);

        for (BedEntry entry : beds) {

            BlockState current = mc.level.getBlockState(entry.head);
            if (!(current.getBlock() instanceof BedBlock)) {
                continue;
            }

            Vector2f screen = ProjectionUtil.project(
                    entry.center.x,
                    entry.center.y + 1.15,
                    entry.center.z
            );
            if (screen == null) {
                continue;
            }

            int x = Math.round(screen.getX());
            int y = Math.round(screen.getY());

            int distance = (int) Math.round(
                    mc.player.position().distanceTo(entry.center)
            );

            ItemStack stack = entry.displayStack;

            String label = entry.open
                    ? "床"
                    : "床  " + entry.coverCount;

            String distanceText = distance + "m";

            if (minimal.getValue()) {

                String text = label
                        + (showDistance.getValue() ? "  " + distanceText : "");

                int width = mc.font.width(text) + 10;

                int bgColor = (panelAlpha << 24) | 0x000000;
                event.guiGraphics().fill(
                        x - width / 2,
                        y - 9,
                        x + width / 2,
                        y + 9,
                        bgColor
                );

                int textColor;
                if (entry.open) {
                    textColor = (textAlpha << 24) | 0x55FF55;
                } else {
                    textColor = (textAlpha << 24) | 0xFFFFFF;
                }

                event.guiGraphics().drawString(
                        mc.font,
                        text,
                        x - mc.font.width(text) / 2,
                        y - 4,
                        textColor,
                        true
                );

                continue;
            }

            int textWidth = Math.max(
                    mc.font.width(label),
                    showDistance.getValue() ? mc.font.width(distanceText) : 0
            );

            int width = 26 + textWidth + 10;

            int height = showDistance.getValue() ? 28 : 23;

            int left = x - width / 2;
            int top = y - height / 2;

            int bgColor = (panelAlpha << 24) | 0x101010;
            int barColor;
            if (entry.open) {
                barColor = (barAlpha << 24) | 0x55FF55;
            } else {
                barColor = (barAlpha << 24) | 0xFF5555;
            }

            event.guiGraphics().fill(
                    left,
                    top,
                    left + width,
                    top + height,
                    bgColor
            );

            event.guiGraphics().fill(
                    left,
                    top,
                    left + 2,
                    top + height,
                    barColor
            );

            if (!stack.isEmpty()) {
                event.guiGraphics().renderItem(
                        stack,
                        left + 5,
                        top + 6
                );
            }

            int labelColor;
            if (entry.open) {
                labelColor = (textAlpha << 24) | 0x55FF55;
            } else {
                labelColor = (textAlpha << 24) | 0xFFFFFF;
            }

            event.guiGraphics().drawString(
                    mc.font,
                    label,
                    left + 25,
                    top + 5,
                    labelColor,
                    true
            );

            if (showDistance.getValue()) {
                int distanceColor = (textAlpha << 24) | 0xB8B8B8;

                event.guiGraphics().drawString(
                        mc.font,
                        distanceText,
                        left + 25,
                        top + 16,
                        distanceColor,
                        true
                );
            }
        }
    }

    private void scanBeds() {

        beds.clear();

        if (mc.player == null || mc.level == null) {
            return;
        }

        int radius = range.getValue().intValue();

        int verticalRadius = Math.min(
                16,
                Math.max(8, radius / 3)
        );

        double rangeSq = (double) radius * radius;

        BlockPos origin = mc.player.blockPosition();
        Vec3 eye = mc.player.getEyePosition();

        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {

                    double distanceSq = (double) x * x
                            + (double) y * y
                            + (double) z * z;

                    if (distanceSq > rangeSq) {
                        continue;
                    }

                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);

                    if (!(state.getBlock() instanceof BedBlock)) {
                        continue;
                    }

                    if (state.getValue(BedBlock.PART) != BedPart.HEAD) {
                        continue;
                    }

                    Direction facing = state.getValue(BedBlock.FACING);
                    BlockPos foot = pos.relative(facing.getOpposite());

                    Vec3 center = new Vec3(
                            (pos.getX() + foot.getX() + 1.0) / 2.0,
                            pos.getY() + 0.5,
                            (pos.getZ() + foot.getZ() + 1.0) / 2.0
                    );

                    if (eye.distanceToSqr(center) > rangeSq) {
                        continue;
                    }

                    beds.add(analyzeBed(pos, foot, center));
                }
            }
        }

        beds.sort(
                Comparator.comparingDouble(
                        entry -> mc.player.position().distanceToSqr(entry.center)
                )
        );
    }

    private BedEntry analyzeBed(BlockPos head, BlockPos foot, Vec3 center) {

        int coverCount = 0;
        BlockState weakestState = null;
        float weakestHardness = Float.MAX_VALUE;

        BlockPos[] parts = { head, foot };

        for (BlockPos part : parts) {
            for (Direction direction : CHECK_DIRECTIONS) {

                BlockPos around = part.relative(direction);

                if (around.equals(head) || around.equals(foot)) {
                    continue;
                }

                BlockState state = mc.level.getBlockState(around);

                if (state.isAir()) {
                    continue;
                }

                if (state.getBlock() instanceof BedBlock) {
                    continue;
                }

                if (!state.blocksMotion()) {
                    continue;
                }

                float hardness = state.getDestroySpeed(mc.level, around);

                if (hardness < 0.0F) {
                    continue;
                }

                coverCount++;

                if (hardness < weakestHardness) {
                    weakestHardness = hardness;
                    weakestState = state;
                }
            }
        }

        boolean open = coverCount == 0;

        ItemStack display;

        if (open || weakestState == null) {
            display = new ItemStack(Items.RED_BED);
        } else {
            Item item = weakestState.getBlock().asItem();
            if (item == Items.AIR) {
                display = new ItemStack(Items.RED_BED);
            } else {
                display = new ItemStack(item);
            }
        }

        return new BedEntry(
                head.immutable(),
                center,
                display,
                coverCount,
                open
        );
    }

    private record BedEntry(
            BlockPos head,
            Vec3 center,
            ItemStack displayStack,
            int coverCount,
            boolean open
    ) {
    }
}