package client.nilore.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Slice;
import asm.patchify.annotation.WrapInvoke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import client.nilore.ClientBase;
import client.nilore.NiloreClient;
import client.nilore.asm.Invocation;
import client.nilore.event.impl.RenderEntityEvent;
import client.nilore.event.impl.RotationAnimationEvent;
import client.nilore.modules.impl.render.Drep;
import client.nilore.modules.impl.render.YsmModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

@Patch(LivingEntityRenderer.class)
public class LivingEntityRendererPatch {
    /** True only while the local player's vanilla body is being rendered. */
    public static boolean currentRenderingSelf = false;
    /** Vanilla player bone rotations: head/body/leftArm/rightArm/leftLeg/rightLeg, xyz each. */
    public static final float[] SELF_BONE_ROT = new float[18];
    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderPre(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        if (!NiloreClient.isReady()) return;

        currentRenderingSelf = entity == ClientBase.mc.player;
        if (currentRenderingSelf) {
            YsmModel ysm = YsmModel.INSTANCE;
            if (ysm != null && ysm.isEnabled()) {
                if (renderer instanceof PlayerRenderer playerRenderer && entity instanceof Player player) {
                    PlayerModel<?> model = (PlayerModel<?>) playerRenderer.getModel();
                    float limbSwingAmount = player.walkAnimation.speed(partialTick);
                    float limbSwing = player.walkAnimation.position(partialTick);
                    if (player.isBaby()) {
                        limbSwing *= 3.0F;
                    }
                    if (limbSwingAmount > 1.0F) {
                        limbSwingAmount = 1.0F;
                    }
                    model.attackTime = player.getAttackAnim(partialTick);
                    model.crouching = player.isCrouching();
                    model.riding = player.isPassenger();
                    model.young = player.isBaby();
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    PlayerModel rawModel = model;
                    rawModel.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);

                    float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
                    RotationAnimationEvent rotationEvent = new RotationAnimationEvent(
                            player.yHeadRot, player.yHeadRotO, player.getXRot(), player.xRotO);
                    NiloreClient.getInstance().getEventBus().call(rotationEvent);
                    float renderHeadYaw = Mth.rotLerp(partialTick, rotationEvent.getLastYaw(), rotationEvent.getYaw());
                    float netHeadYaw = renderHeadYaw - bodyYaw;
                    float headPitch = Mth.lerp(partialTick, rotationEvent.getLastPitch(), rotationEvent.getPitch());
                    if (player instanceof AbstractClientPlayer abstractPlayer) {
                        @SuppressWarnings("unchecked")
                        PlayerModel<AbstractClientPlayer> typedModel = (PlayerModel<AbstractClientPlayer>) model;
                        typedModel.setupAnim(abstractPlayer, limbSwing, limbSwingAmount,
                                player.tickCount + partialTick, netHeadYaw, headPitch);
                    }
                    ModelPart[] parts = {model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
                    for (int i = 0; i < parts.length; i++) {
                        SELF_BONE_ROT[i * 3] = parts[i].xRot;
                        SELF_BONE_ROT[i * 3 + 1] = parts[i].yRot;
                        SELF_BONE_ROT[i * 3 + 2] = parts[i].zRot;
                    }
                }
                try {
                    if (ysm.renderInto(poseStack, bufferSource, partialTick, packedLight)) {
                        currentRenderingSelf = false;
                        callbackInfo.cancel();
                        return;
                    }
                } catch (Throwable ignored) {
                    // Fall back to vanilla rendering if a model is malformed.
                }
            }
        }

        RenderEntityEvent.Post pre = new RenderEntityEvent.Post(renderer, entity, poseStack, bufferSource, partialTick, packedLight);
        NiloreClient.getInstance().getEventBus().call(pre);
        if (pre.isCancelled()) {
            callbackInfo.cancel();
            return;
        }
        applyFakeAntiAimHead(entity, partialTick);
    }


    private static boolean headPoked = false;
    private static float savedYHeadRot, savedYHeadRotO;

    private static void applyFakeAntiAimHead(LivingEntity entity, float partialTick) {
        headPoked = false;
        Drep faa = Drep.INSTANCE;
        if (faa == null || !faa.isEnabled() || entity != ClientBase.mc.player) return;
        if (!faa.headYawActive()) return;

        int tick = entity.tickCount;
        float time = tick + partialTick;
        savedYHeadRot = entity.yHeadRot;
        savedYHeadRotO = entity.yHeadRotO;
        headPoked = true;

        float off = faa.headYawOffset(time, tick);
        entity.yHeadRot += off;
        entity.yHeadRotO += off;
    }

    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.TAIL)
    )
    public static void onRenderPost(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        if (entity == ClientBase.mc.player) {
            currentRenderingSelf = false;
        }
        if (headPoked && entity == ClientBase.mc.player) {
            entity.yHeadRot = savedYHeadRot;
            entity.yHeadRotO = savedYHeadRotO;
            headPoked = false;
        }
        if (!NiloreClient.isReady()) return;
        RenderEntityEvent.Pre post = new RenderEntityEvent.Pre(renderer, entity, poseStack, bufferSource, partialTick, packedLight);
        NiloreClient.getInstance().getEventBus().call(post);
    }

    // PatchTransformer.wrapInvoke now tries strict owner+name+desc matching first,
    // so this slice index is independent of the other Mth.* wraps in this patch.
    // LivingEntityRenderer.render holds 3 Mth.rotLerp(FFF)F calls in order:
    //   1: body yaw    (Mth.rotLerp(g, yBodyRotO, yBodyRot))
    //   2: head yaw    (Mth.rotLerp(g, yHeadRotO, yHeadRot))      <- we want this
    //   3: head yaw recalc in the conditional branch
    @WrapInvoke(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            target = "net/minecraft/util/Mth/rotLerp",
            targetDesc = "(FFF)F",
            slice = @Slice(startIndex = 2, endIndex = 2)
    )
    public static float onRenderHeadYawLerp(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            Invocation<Object, Float> original) throws Exception {
        float delta = (Float) original.args().get(0);
        float start = (Float) original.args().get(1);
        float end   = (Float) original.args().get(2);
        RotationAnimationEvent event = new RotationAnimationEvent(end, start, 0.0f, 0.0f);
        if (NiloreClient.isReady() && entity == ClientBase.mc.player) {
            NiloreClient.getInstance().getEventBus().call(event);
        }
        return Mth.rotLerp(delta, event.getLastYaw(), event.getYaw());
    }

    // Only one Mth.lerp(FFF)F call site in LivingEntityRenderer.render (pitch
    // lerp: Mth.lerp(g, xRotO, getXRot())). The strict matcher in
    // PatchTransformer.wrapInvoke filters out the three Mth.rotLerp calls, so
    // slice (1,1) is the right pick.
    @WrapInvoke(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            target = "net/minecraft/util/Mth/lerp",
            targetDesc = "(FFF)F",
            slice = @Slice(startIndex = 1, endIndex = 1)
    )
    public static float onRenderPitchLerp(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            Invocation<Object, Float> original) throws Exception {
        float delta = (Float) original.args().get(0);
        float start = (Float) original.args().get(1);
        float end   = (Float) original.args().get(2);
        RotationAnimationEvent event = new RotationAnimationEvent(0.0f, 0.0f, end, start);
        if (NiloreClient.isReady() && entity == ClientBase.mc.player) {
            NiloreClient.getInstance().getEventBus().call(event);
        }
        return Mth.lerp(delta, event.getLastPitch(), event.getPitch());
    }
}
