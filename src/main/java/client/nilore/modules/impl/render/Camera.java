package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import net.minecraft.client.CameraType;

/**
 * Naven Camera adapted for Nilore.
 *
 * Features:
 * - ViewClip: overrides third-person collision zoom and supports distance scaling.
 * - Animation: smoothly animates the third-person camera distance when switching views.
 * - Motion Camera: smooths the third-person camera position.
 */
public class Camera extends Module {
    public static Camera INSTANCE;

    public final BooleanSetting viewClipEnabled = new BooleanSetting("ViewClip", true);
    public final NumberSetting scale = new NumberSetting(
            "Scale", 1.0F, 0.5F, 2.0F, 0.01F,
            () -> viewClipEnabled.getValue()
    );
    public final BooleanSetting animation = new BooleanSetting(
            "Animation", true,
            () -> viewClipEnabled.getValue()
    );
    public final NumberSetting animationSpeed = new NumberSetting(
            "Animation Speed", 0.3F, 0.01F, 0.5F, 0.01F,
            () -> viewClipEnabled.getValue() && animation.getValue()
    );
    public final BooleanSetting motionCameraEnabled = new BooleanSetting("Motion Camera", false);
    public final NumberSetting motionCameraFactor = new NumberSetting(
            "Smoothness", 0.1F, 0.01F, 0.5F, 0.01F,
            () -> motionCameraEnabled.getValue()
    );

    /** Naven's personViewAnimation.value, kept in the original 0..100 range. */
    private float personViewAnimationValue;
    private long lastAnimationUpdateMs;

    public double lastMotionCamX;
    public double lastMotionCamY;
    public double lastMotionCamZ;
    private boolean motionCameraInitialized;

    public Camera() {
        super("Camera", Category.RENDER);
        INSTANCE = this;
        this.personViewAnimationValue = isThirdPerson() ? 100.0F : 0.0F;
        this.lastAnimationUpdateMs = System.currentTimeMillis();
    }

    @Override
    protected void onEnable() {
        this.personViewAnimationValue = isThirdPerson() ? 100.0F : 0.0F;
        this.lastAnimationUpdateMs = System.currentTimeMillis();
        resetMotionCamera();
    }

    @Override
    protected void onDisable() {
        resetMotionCamera();
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
    public void onTick() {

    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!viewClipEnabled.getValue()) {
            return;
        }

        final boolean thirdPerson = isThirdPerson();
        final float target = thirdPerson ? 100.0F : 0.0F;

        if (!animation.getValue()) {
            personViewAnimationValue = target;
            lastAnimationUpdateMs = System.currentTimeMillis();
            return;
        }

        // Reproduce Naven's SmoothAnimationTimer using real frame delta instead of
        // Naven's global AnimationUtils.delta.
        long now = System.currentTimeMillis();
        long deltaMs = Math.max(0L, Math.min(100L, now - lastAnimationUpdateMs));
        lastAnimationUpdateMs = now;

        float speed = animationSpeed.getValue().floatValue();
        float animationRate = Math.max(10.0F, Math.abs(personViewAnimationValue - target) * 40.0F) * speed;
        float step = deltaMs * (animationRate / 1000.0F);

        if (personViewAnimationValue < target) {
            personViewAnimationValue = Math.min(target, personViewAnimationValue + step);
        } else if (personViewAnimationValue > target) {
            personViewAnimationValue = Math.max(target, personViewAnimationValue - step);
        }
    }

    public double getViewClipDistance(double startingDistance) {
        float progress = animation.getValue()
                ? personViewAnimationValue / 100.0F
                : (isThirdPerson() ? 1.0F : 0.0F);
        return startingDistance * scale.getValue().doubleValue() * progress;
    }

    public boolean shouldApplyViewClip() {
        return isEnabled() && viewClipEnabled.getValue();
    }

    public boolean shouldApplyMotionCamera(boolean thirdPerson) {
        return isEnabled() && motionCameraEnabled.getValue() && thirdPerson;
    }

    public boolean isMotionCameraInitialized() {
        return motionCameraInitialized;
    }

    public void initializeMotionCamera(double x, double y, double z) {
        this.lastMotionCamX = x;
        this.lastMotionCamY = y;
        this.lastMotionCamZ = z;
        this.motionCameraInitialized = true;
    }

    public void updateMotionCamera(double x, double y, double z) {
        this.lastMotionCamX = x;
        this.lastMotionCamY = y;
        this.lastMotionCamZ = z;
        this.motionCameraInitialized = true;
    }

    public void resetMotionCamera() {
        this.lastMotionCamX = 0.0D;
        this.lastMotionCamY = 0.0D;
        this.lastMotionCamZ = 0.0D;
        this.motionCameraInitialized = false;
    }

    private boolean isThirdPerson() {
        if (mc == null || mc.options == null) {
            return false;
        }
        CameraType cameraType = mc.options.getCameraType();
        return cameraType == CameraType.THIRD_PERSON_BACK || cameraType == CameraType.THIRD_PERSON_FRONT;
    }
}
