package client.nilore.modules.impl.render;

import client.nilore.ClientBase;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.UseAnim;

public class Animations extends Module {

    public static Animations INSTANCE;

    /*
     * =========================
     * Block Animation
     * =========================
     */

    public final ModeSetting blockMode =
            new ModeSetting(
                    "Block Mode",
                    "None",
                    "1.7",
                    "Push",
                    "XinXin",
                    "Vanilla",
                    "Leaked",
                    "Slide",
                    "Sigma",
                    "Exhibition",
                    "Spin",
                    "Down",
                    "Up",
                    "Astolfo",
                    "Moon",
                    "Dance",
                    "Reverse",
                    "Shield",
                    "Twerk",
                    "Bobbing"
            ).withDefault("1.7");

    public final BooleanSetting blockOnlySword =
            new BooleanSetting(
                    "Block Only Sword",
                    true
            );

    public final BooleanSetting killAuraAutoBlock =
            new BooleanSetting(
                    "KillAura Auto Block",
                    true
            );

    public final BooleanSetting axeBlock =
            new BooleanSetting(
                    "Axe Block",
                    true
            );

    /*
     * =========================
     * Item Position
     * =========================
     */

    public final BooleanSetting globalApply =
            new BooleanSetting(
                    "Global Apply",
                    false
            );

    public final NumberSetting itemX =
            new NumberSetting(
                    "Item X",
                    0.0,
                    -2.0,
                    2.0,
                    0.01
            );

    public final NumberSetting itemY =
            new NumberSetting(
                    "Item Y",
                    0.0,
                    -2.0,
                    2.0,
                    0.01
            );

    public final NumberSetting itemZ =
            new NumberSetting(
                    "Item Z",
                    0.0,
                    -2.0,
                    2.0,
                    0.01
            );

    /*
     * =========================
     * Small Item
     * =========================
     */

    public final BooleanSetting smallItem =
            new BooleanSetting(
                    "Small Item",
                    false
            );

    public final NumberSetting smallItemSize =
            new NumberSetting(
                    "Small Item Size",
                    0.75,
                    0.1,
                    1.5,
                    0.05,
                    () -> smallItem.getValue()
            );


    /**
     * Third person visual block state.
     * Only used by HumanoidModelPatch to change the model pose.
     */
    public final BooleanSetting thirdPersonBlock =
            new BooleanSetting(
                    "Third Person Block",
                    true
            );

    /**
     * Used by HumanoidModelPatch for third person visual block.
     */
    public boolean shouldThirdPersonBlock() {
        return isEnabled()
                && thirdPersonBlock.getValue()
                && isKillAuraBlocking()
                && ClientBase.mc.player != null;
    }

    /*
     * =========================
     * Swing
     * =========================
     */

    public final BooleanSetting customSwingSpeed =
            new BooleanSetting(
                    "Custom Swing Speed",
                    false
            );

    public final NumberSetting swingSpeed =
            new NumberSetting(
                    "Swing Speed",
                    1.0,
                    0.1,
                    3.0,
                    0.1,
                    () -> customSwingSpeed.getValue()
            );

    public Animations() {
        super(
                "Animations",
                Category.RENDER
        );

        INSTANCE = this;
    }

    /*
     * =========================
     * Item Modifier
     * =========================
     */

    public boolean shouldApplyItemModifiers(
            ItemStack stack
    ) {
        if (!isEnabled()) {
            return false;
        }

        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // 鍓墜姝ｅ湪浣跨敤鐗╁搧鏃讹紝Animations 鏆傛椂瀹屽叏涓嶅奖鍝嶄富鎵嬨€
        if (isUsingOffhandItem()) {
            return false;
        }

        if (globalApply.getValue()) {
            return true;
        }

        return stack.getItem()
                instanceof SwordItem;
    }

    /**
     * 鐜╁鏄惁姝ｅ湪瀹為檯浣跨敤鍓墜鐗╁搧銆
     *
     * 渚嬪鍚冮噾鑻规灉銆佸枬鑽按銆佹媺寮撱€佷妇鐩剧瓑銆
     * 鍙湁杩涘叆 Minecraft 鐨勨€滄鍦ㄤ娇鐢ㄧ墿鍝佲€濈姸鎬佹墠杩斿洖 true锛
     * 鍗曠函鎶婄墿鍝佹斁鍦ㄥ壇鎵嬩笉浼氬奖鍝  Animations銆
     */
    public boolean isUsingOffhandItem() {
        return ClientBase.mc.player != null
                && ClientBase.mc.player.isUsingItem()
                && ClientBase.mc.player.getUsedItemHand() == InteractionHand.OFF_HAND;
    }

    public void applyItemModifiers(
            PoseStack poseStack,
            ItemStack stack
    ) {
        if (!shouldApplyItemModifiers(stack)) {
            return;
        }

        float x =
                itemX.getValue()
                        .floatValue();

        float y =
                itemY.getValue()
                        .floatValue();

        float z =
                itemZ.getValue()
                        .floatValue();

        poseStack.translate(
                x,
                y,
                z
        );

        if (smallItem.getValue()) {
            float size =
                    smallItemSize
                            .getValue()
                            .floatValue();

            poseStack.scale(
                    size,
                    size,
                    size
            );
        }
    }

    /*
     * =========================
     * Blocking
     * =========================
     */

    public boolean isKillAuraBlocking() {
        return killAuraAutoBlock.getValue()
                && KillAura.INSTANCE != null
                && KillAura.INSTANCE.isEnabled()
                && KillAura.INSTANCE.fakeAutoBlock.getValue()
                && KillAura.aimingTarget != null;
    }

    public boolean isValidBlockItem(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.getItem()
                instanceof SwordItem) {
            return true;
        }

        return axeBlock.getValue()
                && stack.getItem()
                instanceof AxeItem;
    }

    public boolean shouldBlock(
            ItemStack stack
    ) {
        if (!isEnabled()) {
            return false;
        }

        if (blockMode.is("None")) {
            return false;
        }

        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // 鍓墜姝ｅ湪浣跨敤涓滆タ鏃朵紭鍏堜氦缁  Minecraft 鍘熺増娓叉煋銆
        // 浣跨敤缁撴潫鍚  Animations 浼氳嚜鍔ㄦ仮澶嶃€
        if (isUsingOffhandItem()) {
            return false;
        }

        if (blockOnlySword.getValue()
                && !isValidBlockItem(stack)) {
            return false;
        }

        if (isKillAuraBlocking()) {
            return true;
        }

        if (ClientBase.mc.player == null) {
            return false;
        }

        return ClientBase.mc.options
                .keyUse
                .isDown();
    }

    /*
     * =========================
     * Swing Speed
     * =========================
     */

    public float modifySwingProgress(
            float swingProgress
    ) {
        if (!customSwingSpeed.getValue()) {
            return swingProgress;
        }

        return Mth.clamp(
                swingProgress
                        * swingSpeed
                        .getValue()
                        .floatValue(),
                0.0F,
                1.0F
        );
    }

    /*
     * =========================
     * Block Animation Router
     * =========================
     */

    public void applyBlockAnimation(
            PoseStack poseStack,
            float swingProgress,
            HumanoidArm arm,
            float equipProgress
    ) {
        int side =
                arm == HumanoidArm.RIGHT
                        ? 1
                        : -1;

        float progress =
                modifySwingProgress(
                        swingProgress
                );

        if (blockMode.is("1.7")) {
            apply17(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Push")) {
            applyPush(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("XinXin")) {
            applyXinXin(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Vanilla")) {
            applyVanilla(
                    poseStack,
                    progress,
                    arm,
                    equipProgress
            );
            return;
        }

        if (blockMode.is("Leaked")) {
            applyLeaked(
                    poseStack,
                    progress,
                    arm,
                    equipProgress
            );
            return;
        }

        if (blockMode.is("Slide")) {
            applySlide(
                    poseStack,
                    progress,
                    arm,
                    equipProgress
            );
            return;
        }

        if (blockMode.is("Sigma")) {
            applySigma(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Exhibition")) {
            applyExhibition(
                    poseStack,
                    progress,
                    arm,
                    equipProgress
            );
            return;
        }

        if (blockMode.is("Spin")) {
            applySpin(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Down")) {
            applyDown(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Up")) {
            applyUp(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Astolfo")) {
            applyAstolfo(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Moon")) {
            applyMoon(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Dance")) {
            applyDance(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Reverse")) {
            applyReverse(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Shield")) {
            applyShield(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Twerk")) {
            applyTwerk(
                    poseStack,
                    progress,
                    side
            );
            return;
        }

        if (blockMode.is("Bobbing")) {
            applyBobbing(
                    poseStack,
                    progress,
                    side
            );
        }
    }

    /*
     * =========================
     * 1.7
     * =========================
     */

    private void apply17(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float swing1 =
                Mth.sin(
                        progress
                                * progress
                                * (float) Math.PI
                );

        float swing2 =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * (
                                45.0F
                                        + swing1
                                        * -20.0F
                        )
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * swing2
                                * -20.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        swing2
                                * -80.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * -45.0F
                )
        );

        poseStack.scale(
                0.9F,
                0.9F,
                0.9F
        );

        poseStack.translate(
                -0.2F,
                0.126F,
                0.2F
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -102.25F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * 15.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * 80.0F
                )
        );
    }

    /*
     * =========================
     * Push
     * =========================
     */

    private void applyPush(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        poseStack.translate(
                side
                        * -0.1414214F,
                0.08F,
                0.1414214F
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -102.25F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * 13.365F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * 78.05F
                )
        );

        float swing1 =
                Mth.sin(
                        progress
                                * progress
                                * (float) Math.PI
                );

        float swing2 =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        swing1 * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        swing1 * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        swing1 * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        swing2 * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        swing2 * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        swing2 * -10.0F
                )
        );
    }

    /*
     * =========================
     * XinXin
     * =========================
     */

    private void applyXinXin(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float rotationTime =
                (float) (
                        System.currentTimeMillis()
                                % 800L
                )
                        / 800.0F
                        * 360.0F;

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        rotationTime
                )
        );

        float wobble =
                Mth.sin(
                        progress
                                * (float) Math.PI
                                * 4.0F
                )
                        * 0.1F;

        poseStack.mulPose(
                Axis.XP.rotation(
                        wobble
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotation(
                        wobble
                                * side
                )
        );
    }

    /*
     * =========================
     * Vanilla
     * =========================
     */

    private void applyVanilla(
            PoseStack poseStack,
            float progress,
            HumanoidArm arm,
            float equipProgress
    ) {
        int side =
                arm == HumanoidArm.RIGHT
                        ? 1
                        : -1;

        poseStack.translate(
                side
                        * 0.56F,
                -0.52F
                        + equipProgress
                        * -0.6F,
                -0.72F
        );

        poseStack.translate(
                side
                        * -0.1414214F,
                0.08F,
                0.1414213925600052F
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -102.25F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * 13.365F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * 78.05F
                )
        );

        float sinSquared =
                Mth.sin(
                        progress
                                * progress
                                * (float) Math.PI
                );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        sinSquared
                                * -20.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * sinSqrt
                                * -20.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        sinSqrt
                                * -80.0F
                )
        );
    }

    /*
     * =========================
     * Leaked
     * =========================
     */

    private void applyLeaked(
            PoseStack poseStack,
            float progress,
            HumanoidArm arm,
            float equipProgress
    ) {
        int side =
                arm == HumanoidArm.RIGHT
                        ? 1
                        : -1;

        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.71999997F
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * 45.0F
                )
        );

        float sinSquared =
                Mth.sin(
                        progress
                                * progress
                                * (float) Math.PI
                );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        sinSquared
                                * -20.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * sinSqrt
                                * -20.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        sinSqrt
                                * -80.0F
                )
        );

        poseStack.scale(
                0.4F,
                0.4F,
                0.4F
        );

        poseStack.translate(
                side
                        * -0.5F,
                0.2F,
                0.0F
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * 30.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -80.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * 60.0F
                )
        );

        float pulse =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                )
                        / 8.0F;

        poseStack.translate(
                side
                        * 0.008F,
                0.24F,
                0.03F
        );

        poseStack.translate(
                side
                        * -0.16F,
                -0.25F,
                0.0F
        );

        float scale =
                0.8F
                        + pulse;

        poseStack.scale(
                scale,
                scale,
                scale
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * -Mth.sin(
                                Mth.sqrt(progress)
                                        * (float) Math.PI
                        )
                                * 20.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -Mth.sin(
                                Mth.sqrt(progress)
                                        * (float) Math.PI
                        )
                                * 30.0F
                )
        );

        poseStack.scale(
                2.4F,
                2.4F,
                2.4F
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * -38.4F
                )
        );
    }

    /*
     * =========================
     * Slide
     * =========================
     */

    private void applySlide(
            PoseStack poseStack,
            float progress,
            HumanoidArm arm,
            float equipProgress
    ) {
        int side =
                arm == HumanoidArm.RIGHT
                        ? 1
                        : -1;

        float slideSwing =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.translate(
                side
                        * 0.648F,
                -0.55F,
                -0.7199999690055847F
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * 77.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -80.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -slideSwing
                                * 20.0F
                )
        );

        poseStack.scale(
                1.2F,
                1.2F,
                1.2F
        );
    }

    /*
     * =========================
     * Sigma
     * =========================
     */

    private void applySigma(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float swing1 =
                Mth.sin(
                        progress
                                * progress
                                * (float) Math.PI
                );

        float swing2 =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * (45.0F + swing1 * -10.0F)
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * swing2 * -30.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        swing2 * -60.0F
                )
        );

        poseStack.scale(
                0.85F,
                0.85F,
                0.85F
        );

        poseStack.translate(
                -0.15F,
                0.1F,
                0.15F
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -90.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * 20.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 70.0F
                )
        );
    }

    /*
     * =========================
     * Exhibition
     * =========================
     */

    private void applyExhibition(
            PoseStack poseStack,
            float progress,
            HumanoidArm arm,
            float equipProgress
    ) {
        int side =
                arm == HumanoidArm.RIGHT
                        ? 1
                        : -1;

        poseStack.translate(
                side * 0.56F,
                -0.52F + equipProgress * -0.4F,
                -0.72F
        );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        float sinSquared =
                Mth.sin(
                        progress
                                * progress
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -20.0F + sinSqrt * 30.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * 30.0F + sinSquared * -15.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 10.0F
                )
        );

        poseStack.scale(
                1.1F + sinSqrt * 0.05F,
                1.1F + sinSqrt * 0.05F,
                1.1F + sinSqrt * 0.05F
        );
    }

    /*
     * =========================
     * Spin
     * =========================
     */

    private void applySpin(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float time =
                (float) (
                        System.currentTimeMillis()
                                % 1200L
                )
                        / 1200.0F
                        * 360.0F;

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        time * side
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -85.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 75.0F
                )
        );

        float bob =
                Mth.sin(
                        progress
                                * (float) Math.PI
                                * 2.0F
                )
                        * 5.0F;

        poseStack.translate(
                0.0F,
                bob * 0.01F,
                0.0F
        );
    }

    /*
     * =========================
     * Down
     * =========================
     */

    private void applyDown(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -10.0F + sinSqrt * -70.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * 25.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 20.0F
                )
        );

        poseStack.translate(
                0.0F,
                0.15F - sinSqrt * 0.1F,
                0.0F
        );
    }

    /*
     * =========================
     * Up
     * =========================
     */

    private void applyUp(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -120.0F + sinSqrt * 80.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * -15.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 10.0F
                )
        );

        poseStack.translate(
                0.0F,
                -0.1F + sinSqrt * 0.05F,
                0.05F
        );
    }

    /*
     * =========================
     * Astolfo
     * =========================
     */

    private void applyAstolfo(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        float sinSquared =
                Mth.sin(
                        progress
                                * progress
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * (30.0F + sinSquared * -15.0F)
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -75.0F + sinSqrt * -15.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 60.0F
                )
        );

        float wave =
                Mth.sin(
                        (float) System.currentTimeMillis()
                                / 500.0F
                )
                        * 2.0F;

        poseStack.translate(
                side * 0.02F * wave,
                0.02F * wave,
                0.0F
        );

        poseStack.scale(
                0.9F,
                0.9F,
                0.9F
        );
    }

    /*
     * =========================
     * Moon
     * =========================
     */

    private void applyMoon(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        float arcX =
                Mth.sin(
                        progress
                                * (float) Math.PI
                                * 2.0F
                )
                        * 15.0F;

        float arcY =
                Mth.cos(
                        progress
                                * (float) Math.PI
                                * 2.0F
                )
                        * 10.0F
                        - 10.0F;

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -70.0F + arcX * 0.5F + sinSqrt * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * (40.0F + arcY * 0.3F)
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 50.0F
                )
        );

        poseStack.translate(
                side * 0.02F * arcX * 0.01F,
                0.02F * arcY * 0.01F,
                0.0F
        );
    }

    /*
     * =========================
     * Dance
     * =========================
     */

    private void applyDance(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        long time = System.currentTimeMillis();

        float rotX =
                Mth.sin(
                        time / 300.0F
                )
                        * 10.0F;

        float rotY =
                Mth.sin(
                        time / 400.0F + 1.0F
                )
                        * 15.0F;

        float rotZ =
                Mth.cos(
                        time / 350.0F + 0.5F
                )
                        * 8.0F;

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -80.0F + rotX + sinSqrt * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * (30.0F + rotY)
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * (60.0F + rotZ)
                )
        );

        float pulse =
                0.9F
                        + Mth.sin(time / 250.0F)
                        * 0.05F;

        poseStack.scale(
                pulse,
                pulse,
                pulse
        );
    }

    /*
     * =========================
     * Reverse
     * =========================
     */

    private void applyReverse(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        float sinSquared =
                Mth.sin(
                        progress
                                * progress
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * (-45.0F + sinSquared * 20.0F)
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * sinSqrt
                                * 20.0F
                )
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        sinSqrt
                                * 80.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * 45.0F
                )
        );

        poseStack.scale(
                0.9F,
                0.9F,
                0.9F
        );

        poseStack.translate(
                0.2F,
                0.126F,
                -0.2F
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        102.25F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side
                                * -15.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side
                                * -80.0F
                )
        );
    }

    /*
     * =========================
     * Shield
     * =========================
     */

    private void applyShield(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -90.0F + sinSqrt * -20.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * 80.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 10.0F
                )
        );

        poseStack.translate(
                0.0F,
                0.0F,
                0.1F
        );

        poseStack.scale(
                1.3F,
                1.3F,
                1.3F
        );
    }

    /*
     * =========================
     * Twerk
     * =========================
     */

    private void applyTwerk(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        long time = System.currentTimeMillis();

        float shakeX =
                Mth.sin(time / 100.0F) * 3.0F;
        float shakeY =
                Mth.sin(time / 120.0F + 0.5F) * 3.0F;
        float shakeZ =
                Mth.sin(time / 80.0F + 1.0F) * 2.0F;

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -75.0F + shakeX + sinSqrt * -15.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * 35.0F + shakeY
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 55.0F + shakeZ
                )
        );

        float twerkScale =
                0.9F
                        + Mth.sin(time / 50.0F)
                        * 0.08F;

        poseStack.scale(
                twerkScale,
                twerkScale,
                twerkScale
        );
    }

    /*
     * =========================
     * Bobbing
     * =========================
     */

    private void applyBobbing(
            PoseStack poseStack,
            float progress,
            int side
    ) {
        poseStack.translate(
                side * 0.56F,
                -0.52F,
                -0.72F
        );

        float sinSqrt =
                Mth.sin(
                        Mth.sqrt(progress)
                                * (float) Math.PI
                );

        float bobY =
                Mth.sin(
                        progress
                                * (float) Math.PI
                                * 3.0F
                )
                        * 0.05F;

        poseStack.translate(
                0.0F,
                bobY,
                0.0F
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(
                        -85.0F + sinSqrt * -10.0F
                )
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(
                        side * 35.0F
                )
        );

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        side * 65.0F
                )
        );

        float bobScale =
                0.95F
                        + Mth.sin(
                        progress
                                * (float) Math.PI
                                * 3.0F
                )
                        * 0.05F;

        poseStack.scale(
                bobScale,
                bobScale,
                bobScale
        );
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
    public String getSuffix() {
        return blockMode.getValue();
    }

    @Override
    public void onTick() {
    }
}