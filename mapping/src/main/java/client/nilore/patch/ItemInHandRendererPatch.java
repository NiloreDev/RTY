package client.nilore.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;

import client.nilore.ClientBase;
import client.nilore.NiloreClient;
import client.nilore.asm.Invocation;
import client.nilore.event.impl.UpdateHeldItemEvent;
import client.nilore.modules.impl.render.Animations;
import client.nilore.modules.impl.render.YsmModel;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Patch(ItemInHandRenderer.class)
public class ItemInHandRendererPatch {

    /*
     * ============================================================
     * Held Item Event
     * ============================================================
     */

    @WrapInvoke(
            method = "tick",
            desc = "()V",
            target = "net/minecraft/world/entity/LivingEntity/getMainHandItem",
            targetDesc = "()Lnet/minecraft/world/item/ItemStack;"
    )
    public static ItemStack onGetMainHandItem(
            ItemInHandRenderer renderer,
            Invocation<ItemInHandRenderer, ItemStack> original
    ) {

        if (ClientBase.mc.player == null) {
            return original.call(renderer);
        }

        UpdateHeldItemEvent event =
                new UpdateHeldItemEvent(
                        InteractionHand.MAIN_HAND,
                        ClientBase.mc.player.getMainHandItem()
                );

        if (NiloreClient.isReady()) {
            NiloreClient.getInstance()
                    .getEventBus()
                    .call(event);
        }

        return event.getItemStack();
    }

    @Inject(
            method = "renderPlayerArm",
            desc = "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFFLnet/minecraft/world/entity/HumanoidArm;)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderYsmPlayerArm(
            ItemInHandRenderer renderer, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, float equippedProgress, float swingProgress, HumanoidArm arm,
            CallbackInfo callbackInfo) {
        YsmModel ysm = YsmModel.INSTANCE;
        if (!NiloreClient.isReady() || ysm == null || !ysm.isEnabled() || ClientBase.mc.player == null) {
            return;
        }
        try {
            if (ysm.renderFirstPersonArm(arm, poseStack, bufferSource, packedLight, ClientBase.mc.getFrameTime())) {
                callbackInfo.cancel();
            }
        } catch (Throwable ignored) {
            // Malformed optional arm geometry falls back to vanilla hands.
        }
    }

    /*
     * ============================================================
     * First Person Item Render
     * ============================================================
     */

    @Inject(
            method = "renderArmWithItem",
            desc = "(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderArmWithItem(
            ItemInHandRenderer renderer,
            AbstractClientPlayer player,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callbackInfo
    ) {

        if (!NiloreClient.isReady()) {
            return;
        }

        if (player == null) {
            return;
        }

        if (Animations.INSTANCE == null) {
            return;
        }

        if (!Animations.INSTANCE.isEnabled()) {
            return;
        }

        /*
         * 鍓墜姝ｅ湪瀹為檯浣跨敤鐗╁搧鏃讹紝Animations 鏆傛椂瀹屽叏璁╄矾銆�
         *
         * 杩欐牱鍚冮噾鑻规灉銆佸枬鑽按銆佹媺寮撱€佷妇鐩剧瓑鍔ㄤ綔閮界敱
         * Minecraft 鍘熺増姝ｅ父娓叉煋锛涘仠姝娇鐢ㄥ悗 Animations 鑷姩鎭㈠銆�
         *
         * 娉ㄦ剰锛氬彧鏄壇鎵嬫嬁鐫€鐗╁搧浣嗘病鏈夆€滀娇鐢ㄢ€濇椂涓嶄細杩涘叆杩欓噷銆�
         */
        if (Animations.INSTANCE.isUsingOffhandItem()) {
            return;
        }

        /*
         * ========================================================
         * 鍓墜
         * ========================================================
         *
         * 杩欓噷涓€瀹氫笉鑳� cancel銆�
         *
         * Minecraft 浼氳嚜宸辩户缁墽琛� OFF_HAND 鐨勫師鐗堟覆鏌擄紝
         * 鍥犳鍓墜鐨勶細
         *
         * 閲戣嫻鏋�
         * 鍥捐吘
         * 椋熺墿
         * 鏂瑰潡
         * 鍏朵粬鐗╁搧
         *
         * 閮藉彲浠ユ甯告樉绀恒€�
         *
         * 涓绘墜鐨勬牸鎸″姩鐢讳笉浼氭帴绠″壇鎵嬨€�
         */
        if (hand == InteractionHand.OFF_HAND) {
            return;
        }

        /*
         * 浠庤繖閲屽紑濮嬪彧澶勭悊 MAIN_HAND銆�
         */

        if (stack == null || stack.isEmpty()) {
            return;
        }

        /*
         * ========================================================
         * 鏄惁闇€瑕佹牸鎸�
         * ========================================================
         *
         * Animations.shouldBlock() 宸茬粡鍖呭惈锛�
         *
         * - 妯″潡鏄惁寮€鍚�
         * - Block Mode 鏄惁涓� None
         * - Sword / Axe 鍒ゆ柇
         * - KillAura FakeAutoBlock
         * - 榧犳爣鍙抽敭
         */
        boolean blocking =
                Animations.INSTANCE.shouldBlock(stack);

        /*
         * ========================================================
         * 鏅€氱姸鎬�
         * ========================================================
         *
         * 娌℃湁鏍兼尅鐨勬椂鍊欙紝涓嶆浛鎹� Minecraft 鍘熺増娓叉煋銆�
         *
         * 鍙簲鐢� Item X/Y/Z 鍜� Small Item銆�
         */
        if (!blocking) {

            if (Animations.INSTANCE
                    .shouldApplyItemModifiers(stack)) {

                Animations.INSTANCE.applyItemModifiers(
                        poseStack,
                        stack
                );
            }

            return;
        }

        /*
         * ========================================================
         * 鑷畾涔夋牸鎸�
         * ========================================================
         *
         * 鍙彇娑� MAIN_HAND 杩欎竴杞殑鍘熺増娓叉煋銆�
         *
         * OFF_HAND 鍓嶉潰宸茬粡 return锛�
         * 鎵€浠ヤ笉浼氳 cancel銆�
         */
        callbackInfo.cancel();

        /*
         * 蹇呴』淇濇姢 PoseStack銆�
         *
         * 鍚﹀垯涓绘墜鏍兼尅浜х敓鐨� translate / rotate / scale
         * 鏈夊彲鑳藉奖鍝嶅悗闈㈢殑鍓墜娓叉煋銆�
         */
        poseStack.pushPose();

        try {

            /*
             * 鑷畾涔� Swing Speed
             */
            float modifiedSwing =
                    Animations.INSTANCE.modifySwingProgress(
                            swingProgress
                    );

            /*
             * 鍏堝簲鐢ㄧ敤鎴疯缃殑鐗╁搧浣嶇疆銆�
             */
            Animations.INSTANCE.applyItemModifiers(
                    poseStack,
                    stack
            );

            /*
             * 鑾峰彇鐜╁涓绘墜鏂瑰悜銆�
             */
            HumanoidArm arm =
                    player.getMainArm();

            /*
             * ====================================================
             * 搴旂敤 Animations 鏍兼尅濮垮娍
             * ====================================================
             */
            Animations.INSTANCE.applyBlockAnimation(
                    poseStack,
                    modifiedSwing,
                    arm,
                    equippedProgress
            );

            /*
             * 鍒ゆ柇娓叉煋妯″瀷浣跨敤宸﹀彸鎵� context銆�
             */
            boolean rightHand =
                    arm == HumanoidArm.RIGHT;

            /*
             * ====================================================
             * 鎵嬪姩缁樺埗涓绘墜鐗╁搧
             * ====================================================
             */
            renderer.renderItem(
                    player,
                    stack,
                    rightHand
                            ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                            : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !rightHand,
                    poseStack,
                    bufferSource,
                    packedLight
            );

        } finally {

            /*
             * 鎭㈠鐭╅樀銆�
             *
             * 闈炲父閲嶈锛�
             *
             * 鍚庣画 OFF_HAND 娓叉煋鏃跺皢浣跨敤骞插噣鐨� PoseStack锛�
             * 涓嶄細缁ф壙涓绘墜鍓戠殑鏍兼尅鏃嬭浆銆�
             */
            poseStack.popPose();
        }
    }
}