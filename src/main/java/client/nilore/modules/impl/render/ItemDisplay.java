package client.nilore.modules.impl.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * ItemDisplay
 *
 * 掉落物标签显示：地上每个掉落物上方显示 "数量x 物品名"。
 *
 * 特性：
 *   - NORMAL 深度遮挡，被地形遮挡时正确隐藏（不会穿透墙壁）
 *   - 多个靠得很近的掉落物会自动垂直错开标签，避免重叠
 *   - 平滑跟随物品移动
 */
public class ItemDisplay extends Module {

    // =========================================================
    // 设置
    // =========================================================
    /** 是否显示数量前缀，例如 "2x "。 */
    private final BooleanSetting showCount =
            new BooleanSetting("Show Count", true);

    /** 是否显示文字阴影。 */
    private final BooleanSetting dropShadow =
            new BooleanSetting("Shadow", true);

    /** 文字大小（世界单位），0.045 接近参考图。 */
    private final NumberSetting textScale =
            new NumberSetting("Text Scale", 0.045f, 0.01f, 0.15f, 0.001f);

    /** 文字离掉落物的垂直偏移。 */
    private final NumberSetting yOffset =
            new NumberSetting("Y Offset", 0.5f, 0.0f, 3.0f, 0.1f);

    /** 最大显示距离，超出不渲染。 */
    private final NumberSetting maxDistance =
            new NumberSetting("Max Distance", 32.0f, 4.0f, 128.0f, 1.0f);

    /**
     * 堆叠检测半径（世界单位）。
     * 两个掉落物水平距离小于这个值时，标签会自动上下错开。
     * 1.5 ~ 2.0 是比较舒服的值。
     */
    private final NumberSetting stackRadius =
            new NumberSetting("Stack Radius", 1.6f, 0.0f, 5.0f, 0.1f);

    public ItemDisplay() {
        super("ItemDisplay", Category.RENDER);

        this.registerSetting(showCount);
        this.registerSetting(dropShadow);
        this.registerSetting(textScale);
        this.registerSetting(yOffset);
        this.registerSetting(maxDistance);
        this.registerSetting(stackRadius);
    }

    @Override
    public String getDisplayName() {
        return "ItemDisplay";
    }

    @Override
    public String getModuleName() {
        return "ItemDisplay";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    // =========================================================
    // 渲染
    // =========================================================
    @EventTarget
    public void onRender(RenderEvent event) {
        if (!this.isEnabled()) return;
        if (mc.player == null || mc.level == null) return;

        final float scale = this.textScale.getValue().floatValue();
        final float baseYOff = this.yOffset.getValue().floatValue();
        final boolean shadow = this.dropShadow.getValue();
        final boolean count = this.showCount.getValue();
        final double maxDistSq = Math.pow(this.maxDistance.getValue().floatValue(), 2.0);
        final double stackRadiusSq = Math.pow(this.stackRadius.getValue().floatValue(), 2.0);
        final float partialTick = event.partialTick();

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.poseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // --------------------------------------------------
        // 1) 收集所有可见的掉落物
        // --------------------------------------------------
        List<ItemEntity> items = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity ie)) continue;
            if (ie.getItem().isEmpty()) continue;

            double dx = ie.getX() - cameraPos.x;
            double dy = ie.getY() - cameraPos.y;
            double dz = ie.getZ() - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

            items.add(ie);
        }

        // 从近到远排序：近的掉落物优先保留原始标签位置，远的往上堆
        items.sort(Comparator.comparingDouble(ie ->
                ie.distanceToSqr(cameraPos.x, cameraPos.y, cameraPos.z)));

        // --------------------------------------------------
        // 2) 逐个计算标签的最终位置（防重叠堆叠）
        // --------------------------------------------------
        // 每个元素 {worldX, worldY, worldZ}，表示已经放好的标签位置
        List<double[]> placed = new ArrayList<>();

        // 每个标签向上错开的垂直步长（世界单位）
        double stepY = Math.max(0.35, scale * 9.0);

        boolean hasRendered = false;

        for (ItemEntity ie : items) {
            ItemStack stack = ie.getItem();
            String name = stack.getHoverName().getString();
            String text = count ? (stack.getCount() + "x " + name) : name;

            // 插值坐标，让标签平滑跟随
            double rx = Mth.lerp(partialTick, ie.xo, ie.getX()) - cameraPos.x;
            double ry = Mth.lerp(partialTick, ie.yo, ie.getY()) - cameraPos.y;
            double rz = Mth.lerp(partialTick, ie.zo, ie.getZ()) - cameraPos.z;

            // 初始标签 Y 位置
            double labelY = ry + baseYOff;

            // ---- 防重叠：如果与已放置的标签水平距离太近，就向上移动 ----
            boolean adjusted = true;
            int safety = 0;
            while (adjusted && safety++ < 30) {
                adjusted = false;
                for (double[] p : placed) {
                    double ddx = rx - p[0];
                    double ddz = rz - p[2];
                    double horizSq = ddx * ddx + ddz * ddz;

                    // 水平方向太近 + 垂直方向不够远 = 需要错开
                    if (horizSq < stackRadiusSq
                            && Math.abs(labelY - p[1]) < stepY * 0.9) {
                        labelY = p[1] + stepY;
                        adjusted = true;
                        break;
                    }
                }
            }
            placed.add(new double[]{rx, labelY, rz});

            // --------------------------------------------------
            // 3) 渲染文字
            // --------------------------------------------------
            poseStack.pushPose();
            poseStack.translate(rx, labelY, rz);
            // billboard：面向摄像机
            poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            // 缩放（负的 X/Y 让文字正向）
            poseStack.scale(-scale, -scale, scale);

            Matrix4f matrix = poseStack.last().pose();
            float textWidth = mc.font.width(text);
            float textX = -textWidth / 2.0f;

            // Font.DisplayMode.NORMAL：有深度遮挡，被地形正确遮挡
            mc.font.drawInBatch(
                    text,
                    textX,
                    0,
                    0xFFFFFFFF,             // 白色
                    shadow,                 // 阴影
                    matrix,
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0,
                    0xF000F0
            );

            poseStack.popPose();
            hasRendered = true;
        }

        if (hasRendered) {
            bufferSource.endBatch();
        }
    }
}