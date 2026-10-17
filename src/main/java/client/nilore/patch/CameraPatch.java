package client.nilore.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import client.nilore.NiloreClient;
import client.nilore.modules.impl.render.Camera;
import client.nilore.utils.misc.ReflectionUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime hooks required by the Naven Camera port.
 */
@Patch(net.minecraft.client.Camera.class)
public class CameraPatch {

    @Inject(
            method = "getMaxZoom",
            desc = "(D)D",
            at = @At(At.Type.HEAD)
    )
    public static void onGetMaxZoom(net.minecraft.client.Camera camera, double startingDistance, CallbackInfo callbackInfo) {
        Camera module = Camera.INSTANCE;
        if (!NiloreClient.isReady() || module == null || !module.shouldApplyViewClip()) {
            return;
        }

        callbackInfo.result = module.getViewClipDistance(startingDistance);
        callbackInfo.cancel();
    }

    @Inject(
            method = "setup",
            desc = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(At.Type.TAIL)
    )
    public static void onCameraSetup(
            net.minecraft.client.Camera camera,
            BlockGetter level,
            Entity entity,
            boolean thirdPerson,
            boolean thirdPersonReverse,
            float partialTick,
            CallbackInfo callbackInfo
    ) {
        Camera module = Camera.INSTANCE;
        if (!NiloreClient.isReady() || module == null) {
            return;
        }

        if (!module.shouldApplyMotionCamera(thirdPerson)) {
            module.resetMotionCamera();
            return;
        }

        Vec3 currentPos = camera.getPosition();
        if (!module.isMotionCameraInitialized()) {
            module.initializeMotionCamera(currentPos.x, currentPos.y, currentPos.z);
        }

        double factor = module.motionCameraFactor.getValue().doubleValue();
        double newX = Mth.lerp(factor, module.lastMotionCamX, currentPos.x);
        double newY = Mth.lerp(factor, module.lastMotionCamY, currentPos.y);
        double newZ = Mth.lerp(factor, module.lastMotionCamZ, currentPos.z);

        Vec3 smoothedPosition = new Vec3(newX, newY, newZ);
        ReflectionUtil.setInstanceField(
                camera,
                smoothedPosition,
                "position",
                "net/minecraft/client/Camera"
        );

        module.updateMotionCamera(newX, newY, newZ);
    }
}
