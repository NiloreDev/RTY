package client.nilore.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import java.util.Map;

import client.nilore.NiloreClient;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import client.nilore.ClientBase;
import client.nilore.asm.Invocation;
import client.nilore.event.impl.*;
import client.nilore.modules.impl.movement.NoJumpDelay;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.modules.impl.render.FullBright;
import client.nilore.utils.game.PlayerUtil;
import client.nilore.utils.misc.ReflectionUtil;

@Patch(LivingEntity.class)
public class LivingEntityPatch {
    @Inject(method = "aiStep", desc = "()V", at = @At(At.Type.HEAD))
    public static void onAiStep(LivingEntity entity, CallbackInfo callbackInfo) {
        if (shouldFastDig(entity)) {
            ReflectionUtil.setJumpDelay(entity, 0);
        }
    }

    // ========== 修复这里 ==========
    private static boolean shouldFastDig(LivingEntity entity) {
        if (!NiloreClient.isReady() || entity != ClientBase.mc.player) return false;
        NoJumpDelay noDelay = NoJumpDelay.INSTANCE;
        return Scaffold.INSTANCE == null || !Scaffold.INSTANCE.isEnabled();
    }

    @Overwrite(method = "hasEffect", desc = "(Lnet/minecraft/world/effect/MobEffect;)Z")
    @SuppressWarnings("unchecked")
    public static boolean overwriteHasEffect(LivingEntity entity, MobEffect effect) throws Exception {
        if (ClientBase.mc != null
                && entity == ClientBase.mc.player
                && effect == MobEffects.NIGHT_VISION
                && FullBright.INSTANCE != null
                && FullBright.INSTANCE.isEnabled()) {
            return true;
        }
        Map<MobEffect, MobEffectInstance> activeEffects =
                (Map<MobEffect, MobEffectInstance>) ReflectionUtil.getStaticField(entity, "activeEffects", "net/minecraft/world/entity/LivingEntity");
        return activeEffects.containsKey(effect);
    }

    @WrapInvoke(method = "tickHeadTurn", desc = "(FF)F", target = "net/minecraft/world/entity/Entity/getYRot", targetDesc = "()F")
    public static float onTickHeadTurn(LivingEntity entity, float yaw, float partial, Invocation<LivingEntity, Float> original) throws Exception {
        float currentYaw = original.call();
        RotationAnimationEvent event = new RotationAnimationEvent(currentYaw, 0, 0, 0);
        if (NiloreClient.isReady() && entity == ClientBase.mc.player) {
            NiloreClient.getInstance().getEventBus().call(event);
        }
        return event.getYaw();
    }

    @WrapInvoke(method = "tick", desc = "()V", target = "net/minecraft/world/entity/Entity/getYRot", targetDesc = "()F")
    public static float onTickGetYRot(LivingEntity entity, Invocation<LivingEntity, Float> original) throws Exception {
        if (NiloreClient.isReady() && entity == ClientBase.mc.player) {
            return ClientBase.yaw;
        }
        return original.call();
    }

    @WrapInvoke(method = "jumpFromGround", desc = "()V", target = "net/minecraft/world/entity/Entity/getYRot", targetDesc = "()F")
    public static float onJumpGetYRot(LivingEntity entity, Invocation<LivingEntity, Float> original) throws Exception {
        float yaw = original.call();
        JumpMarkerEvent event = new JumpMarkerEvent(yaw);
        if (NiloreClient.isReady()) {
            NiloreClient.getInstance().getEventBus().call(event);
        }
        ClientBase.yaw = event.getYaw();
        return event.getYaw();
    }

    @Inject(method = "travel", desc = "(Lnet/minecraft/world/phys/Vec3;)V", at = @At(At.Type.HEAD))
    public static void onTravel(LivingEntity entity, Vec3 movement, CallbackInfo callbackInfo) throws Exception {
        if (entity == null || entity != ClientBase.mc.player || !NiloreClient.isReady()) return;

        // 鞘翅飞行必须保留原版 travel 逻辑，否则会导致 Elytra 无法滑翔
        if (entity.isFallFlying()) {
            return;
        }

        JumpEvent event = new JumpEvent();
        NiloreClient.getInstance().getEventBus().call(event);

        if (event.isCancelled()) {
            PlayerUtil.updateWalkAnim();
            callbackInfo.cancelled = true;
        }
    }

    @WrapInvoke(method = "travel", desc = "(Lnet/minecraft/world/phys/Vec3;)V", target = "net/minecraft/world/entity/Entity/getXRot", targetDesc = "()F")
    public static float onTravelGetXRot(LivingEntity entity, Vec3 movement, Invocation<LivingEntity, Float> original) throws Exception {
        float pitch = original.call();
        FallFlyingEvent event = new FallFlyingEvent(pitch);
        if (NiloreClient.isReady()) {
            NiloreClient.getInstance().getEventBus().call(event);
        }
        return event.getPitch();
    }

    @Inject(method = "hurt", desc = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(At.Type.HEAD))
    public static void onHurt(LivingEntity entity, DamageSource source, float amount, CallbackInfo callbackInfo) {
        if (NiloreClient.isReady()) {
            NiloreClient.getInstance().getEventBus().call(new EntityHurtEvent(entity, source, amount));
        }
    }
}