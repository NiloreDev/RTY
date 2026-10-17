package client.nilore.modules.impl.combat;

import client.nilore.NiloreClient;
import client.nilore.modules.impl.combat.antikb.AntiKBMode;
import client.nilore.modules.impl.movement.Stuck;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import client.nilore.ClientBase;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.event.impl.WorldChangeEvent;
import client.nilore.hud.ModuleListHud;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.ReduceXZMode;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.modules.impl.player.AntiTNT;
import client.nilore.modules.impl.player.AntiWeb;
import client.nilore.modules.impl.player.AutoWebPlace;
import client.nilore.modules.impl.player.Helper;
import client.nilore.modules.impl.player.MidPearl;
import client.nilore.modules.impl.world.Teams;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.EntityUtil;
import client.nilore.utils.game.ItemUtil;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.math.MathUtil;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.utils.misc.Assets;
import client.nilore.utils.render.RenderUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;
import client.nilore.event.EventTarget;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ItemStack;

public class KillAura extends Module {
    public static KillAura INSTANCE;
    public static Entity target;
    public static Entity aimingTarget;
    public static List<Entity> targetList = new ArrayList<>();

    private static final ResourceLocation NURIK_CAPTURE_TEXTURE = ResourceLocation.tryParse("nilore:nurik/capture");
    private static final String NURIK_CAPTURE_ASSET = "/assets/nilore/nurik/capture.png";
    private static boolean nurikTextureLoaded;
    private static boolean nurikTextureLoadFailed;

    private static final float AIM_READY_TOLERANCE_DEG = 2.0f;
    private static final float FORCED_1_9_RANGE = 3.0f;

    /** 每 tick 攻击锁：aura / ReduceXZ 反击 / Critical 额外一击共用。 */
    private static long attackLockTick = -1L;

    public final BooleanSetting use1_9Cooldown = new BooleanSetting("1.9 Cooldown", false);
    public final BooleanSetting attackCooldown = new BooleanSetting("Attack Cooldown", true);

    public final BooleanSetting attackPlayer    = new BooleanSetting("Attack Player", true);
    public final BooleanSetting attackInvisible = new BooleanSetting("Attack Invisible", true);
    public final BooleanSetting attackAnimals   = new BooleanSetting("Attack Animals", false);
    public final BooleanSetting attackMobs      = new BooleanSetting("Attack Mobs", false);
    public final BooleanSetting multiAttack     = new BooleanSetting("Multi Attack", false);
    public final BooleanSetting infSwitch       = new BooleanSetting("Infinity Switch", false);

    public final ModeSetting attackMode = new ModeSetting(
            "Attack Mode",
            "Switch",
            "Single"
    ).withDefault("Single");

    public final BooleanSetting preferBaby      = new BooleanSetting("Prefer Baby", false);
    public final BooleanSetting morePart        = new BooleanSetting("More Particles", false);
    public final ModeSetting style = new ModeSetting("Style", "New", "Old", "onTickRot").withDefault("New");

    public final BooleanSetting fix             = new BooleanSetting("Fix", false,
            () -> this.style.is("Old"));
    public final BooleanSetting overrideRaycast = new BooleanSetting("Override Raycast", true,
            () -> this.style.is("Old") || this.style.is("onTickRot"));
    public final BooleanSetting throughWalls    = new BooleanSetting("Through Walls", false);
    public final NumberSetting throughWallsRange = new NumberSetting("Through Walls Range", 3.0, 1.0, 6.0, 0.1,
            () -> (Boolean) this.throughWalls.getValue());
    public final BooleanSetting ignoreSkipTicks = new BooleanSetting("Ignore skip ticks", false);
    public final BooleanSetting fakeAutoBlock   = new BooleanSetting("Fake AutoBlock", true);
    public final BooleanSetting test            = new BooleanSetting("Test", false);

    public final BooleanSetting silentMode = new BooleanSetting("Silent", false);

    public final NumberSetting aimRange    = new NumberSetting("Aim Range", 4.0, 1.0, 8.0, 0.1);
    public final NumberSetting attackRange = new NumberSetting("Attack Range", 3.5, 1.0, 8.0, 0.1);
    public final NumberSetting reach       = new NumberSetting("Reach", 3.0, 1.0, 3.0, 0.1);

    public final NumberSetting maxAps      = new NumberSetting("Max APS", 12.0, 1.0, 20.0, 1.0);
    public final NumberSetting minAps      = new NumberSetting("Min APS", 9.0, 1.0, 20.0, 1.0);
    public final NumberSetting switchSize  = new NumberSetting("Switch Size", 1.0, 1.0, 5.0, 1.0,
            () -> this.attackMode.is("Switch"));
    public final NumberSetting switchDelay = new NumberSetting("Switch Delay (Attack Times)", 1.0, 1.0, 10.0, 1.0,
            () -> this.attackMode.is("Switch"));
    public final NumberSetting fov         = new NumberSetting("FoV", 360.0, 10.0, 360.0, 1.0);
    public final NumberSetting hurtTime    = new NumberSetting("Hurt Time", 10.0, 0.0, 10.0, 1.0);
    public final ModeSetting delayMode    = new ModeSetting("Delay Mode", "1.8", "1.9").withDefault("1.8");
    public final ModeSetting priorityMode = new ModeSetting("Priority", "Distance", "FoV", "Health", "None").withDefault("FoV");

    // 新增 Glow, Corner, Tracer, Halo 模式
    public final ModeSetting targetEsp    = new ModeSetting("Target ESP", "None",
            "None", "Spiral", "Box", "Tab", "NurikZapen", "Glow", "Corner", "Tracer", "Halo").withDefault("Glow");

    public final BooleanSetting predictionEnabled  = new BooleanSetting("Prediction", true);
    public final NumberSetting enemyDelayThreshold = new NumberSetting("Enemy Delay Ticks", 4, 1, 5, 1,
            () -> (Boolean) this.predictionEnabled.getValue());
    public final NumberSetting selfDelayThreshold  = new NumberSetting("Self Delay Ticks", 2, 1, 5, 1,
            () -> (Boolean) this.predictionEnabled.getValue());

    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180, 0, 720, 5);
    public final NumberSetting rotationDrift = new NumberSetting("Drift", 0.1, 0, 5, 0.1);
    public final NumberSetting rotationJitter = new NumberSetting("Jitter", 0.02, 0, 1, 0.01);

    public final BooleanSetting autoShieldBreaker = new BooleanSetting("Auto Shield Breaker", false);
    public final NumberSetting shieldBreakerCooldown = new NumberSetting("Shield Breaker Cooldown", 5, 1, 20, 1,
            () -> this.autoShieldBreaker.getValue());
    public final BooleanSetting switchBackAfterBreak = new BooleanSetting("Switch Back", true,
            () -> this.autoShieldBreaker.getValue());
    public final NumberSetting shieldBreakerSwitchDelay = new NumberSetting("Shield Breaker Switch Delay", 1, 1, 10, 1,
            () -> this.autoShieldBreaker.getValue());
    public final NumberSetting shieldBreakerCPS = new NumberSetting("Shield Breaker CPS", 8.0, 1.0, 20.0, 0.5,
            () -> this.autoShieldBreaker.getValue());

    public final BooleanSetting rangeCircle = new BooleanSetting("Range Circle", false);
    public final NumberSetting rangeCircleRadius = new NumberSetting("Range Circle Radius", 3.0, 1.0, 6.0, 0.1,
            () -> this.rangeCircle.getValue());
    public final NumberSetting rangeCircleAlpha = new NumberSetting("Range Circle Alpha", 80, 0, 255, 5,
            () -> this.rangeCircle.getValue());
    public final BooleanSetting rangeCircleFilled = new BooleanSetting("Range Circle Filled", false,
            () -> this.rangeCircle.getValue());
    public final BooleanSetting rangeCircleRainbow = new BooleanSetting("Rainbow", true,
            () -> this.rangeCircle.getValue());
    public final BooleanSetting rangeCircleMarkTargets = new BooleanSetting("Mark Targets", true,
            () -> this.rangeCircle.getValue());
    public final ModeSetting circleColor = new ModeSetting("Circle Color", "Green",
            "Rainbow", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "White")
            .withDefault("Green");

    public final BooleanSetting attackInInventory = new BooleanSetting("Attack In Inventory", false);

    private RotationUtil.BestHitInfo currentBestHit;
    private RotationUtil.BestHitInfo prevBestHit;
    private int attackTimes;
    private float attacks;
    private int targetIndex;
    public int sprintTickCounter;
    private int sprintCounter;
    public Rotation rotation;
    private Rotation aimRotation;

    private Random organicRandom;
    private double organicTimeAccumulator;
    private double orgFreqYaw1, orgFreqYaw2, orgFreqPitch1, orgFreqPitch2;
    private double orgPhaseYaw1, orgPhaseYaw2, orgPhasePitch1, orgPhasePitch2;

    private int shieldBreakerTimer = 0;
    private int previousSlot = -1;
    private boolean isBreakingShield = false;
    private int shieldBreakAttempts = 0;
    private long lastShieldBreakTime = 0;
    private int shieldBreakerSwitchTick = 0;
    private static final long SHIELD_BREAK_COOLDOWN_MS = 50;

    public KillAura() {
        super("KillAura", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getModuleName() {
        return "KillAura";
    }

    @Override
    public String getSuffix() {
        String modeName = this.attackMode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return null;
        }
        return "[" + modeName + "]";
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getDisplayName() {
        String modeName = this.attackMode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fKillAura";
        }
        return "§fKillAura[" + modeName + "]";
    }

    @Override
    public void onEnable() {
        this.rotation = null;
        this.aimRotation = null;
        this.reinitOrganicModel();
        this.targetIndex = 0;
        this.attacks = 0.0f;
        target = null;
        aimingTarget = null;
        targetList.clear();
        this.shieldBreakerTimer = 0;
        this.previousSlot = -1;
        this.isBreakingShield = false;
        this.shieldBreakAttempts = 0;
        this.lastShieldBreakTime = 0;
        this.shieldBreakerSwitchTick = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.attacks = 0.0f;
        target = null;
        aimingTarget = null;
        targetList.clear();
        this.currentBestHit = null;
        this.prevBestHit = null;
        this.sprintTickCounter = 0;
        this.sprintCounter = 0;
        this.attackTimes = 0;
        this.rotation = null;
        this.aimRotation = null;
        this.shieldBreakerTimer = 0;
        this.isBreakingShield = false;
        this.shieldBreakAttempts = 0;
        this.shieldBreakerSwitchTick = 0;
        attackLockTick = -1L;
        restoreSlot();
        super.onDisable();
    }

    private void reinitOrganicModel() {
        this.organicRandom = new Random(System.nanoTime());
        this.organicTimeAccumulator = 0.0;
        this.orgFreqYaw1 = this.organicRandom.nextDouble() * 0.3 + 0.1;
        this.orgFreqYaw2 = this.organicRandom.nextDouble() * 0.5 + 0.5;
        this.orgFreqPitch1 = this.organicRandom.nextDouble() * 0.3 + 0.1;
        this.orgFreqPitch2 = this.organicRandom.nextDouble() * 0.5 + 0.5;
        this.orgPhaseYaw1 = this.organicRandom.nextDouble() * Math.PI * 2;
        this.orgPhaseYaw2 = this.organicRandom.nextDouble() * Math.PI * 2;
        this.orgPhasePitch1 = this.organicRandom.nextDouble() * Math.PI * 2;
        this.orgPhasePitch2 = this.organicRandom.nextDouble() * Math.PI * 2;
    }

    private Rotation applyOrganicRotation(Rotation from, Rotation to, float timeDelta) {
        float rawYawDelta = Mth.wrapDegrees(to.getYaw() - from.getYaw());
        float rawPitchDelta = to.getPitch() - from.getPitch();

        double speed = this.rotationSpeed.getValue().doubleValue();
        double driftIntensity = this.rotationDrift.getValue().doubleValue();
        double jitterIntensity = this.rotationJitter.getValue().doubleValue();

        if (speed <= 0) {
            return to;
        }

        float deltaYaw = Mth.clamp(rawYawDelta * timeDelta, -speed > 0 ? -(float)speed : -180F,
                speed > 0 ? (float)speed : 180F);
        float deltaPitch = Mth.clamp(rawPitchDelta * timeDelta, -((float)speed),
                (float)speed);

        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance < driftIntensity) {
            return new Rotation(from.getYaw() + deltaYaw, from.getPitch() + deltaPitch);
        }

        if (distance > 0) {
            double ratioYaw = Math.abs(deltaYaw) / distance;
            double ratioPitch = Math.abs(deltaPitch) / distance;
            double maxYaw = speed * ratioYaw * timeDelta;
            double maxPitch = speed * ratioPitch * timeDelta;
            deltaYaw = Mth.clamp(deltaYaw, (float)-maxYaw, (float)maxYaw);
            deltaPitch = Mth.clamp(deltaPitch, (float)-maxPitch, (float)maxPitch);
        }

        this.organicTimeAccumulator += timeDelta;

        double sinYaw = Math.sin(this.organicTimeAccumulator * this.orgFreqYaw1 + this.orgPhaseYaw1)
                + (this.organicRandom.nextDouble() * 0.1 + 0.45) * Math.sin(this.organicTimeAccumulator * this.orgFreqYaw2 + this.orgPhaseYaw2);
        double sinPitch = Math.sin(this.organicTimeAccumulator * this.orgFreqPitch1 + this.orgPhasePitch1)
                + (this.organicRandom.nextDouble() * 0.1 + 0.45) * Math.sin(this.organicTimeAccumulator * this.orgFreqPitch2 + this.orgPhasePitch2);
        double driftYaw = sinYaw * driftIntensity * timeDelta;
        double driftPitch = sinPitch * driftIntensity * timeDelta;

        double jitterYaw = (this.organicRandom.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;
        double jitterPitch = (this.organicRandom.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;

        float moveYaw = deltaYaw + (float)driftYaw + (float)jitterYaw;
        float movePitch = deltaPitch + (float)driftPitch + (float)jitterPitch;

        float finalYaw = from.getYaw() + moveYaw;
        float finalPitch = Mth.clamp(from.getPitch() + movePitch, -90.0f, 90.0f);
        return patchConstantRotation(new Rotation(finalYaw, finalPitch), from);
    }

    private static Rotation patchConstantRotation(Rotation rotation, Rotation prevRotation) {
        double sensitivity = mc.options.sensitivity().get().floatValue() * 0.6 + 0.2;
        double multiplier = (sensitivity * sensitivity * sensitivity) * 8.0;
        double divisor = multiplier * 0.15;

        float yawDelta = rotation.getYaw() - prevRotation.getYaw();
        float pitchDelta = rotation.getPitch() - prevRotation.getPitch();
        float yaw = prevRotation.getYaw() + (float)(Math.round(yawDelta / divisor) * divisor);
        float pitch = Mth.clamp(prevRotation.getPitch() + (float)(Math.round(pitchDelta / divisor) * divisor), -90.0f, 90.0f);
        return new Rotation(yaw, pitch);
    }

    private boolean isAimReadyToAttack() {
        if (this.style.is("onTickRot")) {
            return true;
        }
        if (target == null || this.rotation == null) {
            return false;
        }

        if (mc.player != null) {
            double currentReach = RotationUtil.getReachLimit();
            if (currentReach <= 0) {
                currentReach = Math.min(
                        this.reach.getValue().doubleValue(),
                        this.getEffectiveAttackRange());
            }
            HitResult probe = RotationUtil.rayTraceForAim(this.rotation, currentReach);
            if (probe instanceof EntityHitResult hit) {
                Entity hitEntity = hit.getEntity();
                if (hitEntity == target || targetList.contains(hitEntity)) {
                    return true;
                }
            }
        }

        Rotation idealRotation = this.aimRotation != null
                ? this.aimRotation
                : RotationUtil.entityRotation(target);
        if (idealRotation != null) {
            float yawDiff = Math.abs(Mth.wrapDegrees(this.rotation.getYaw() - idealRotation.getYaw()));
            float pitchDiff = Math.abs(this.rotation.getPitch() - idealRotation.getPitch());
            if (yawDiff <= AIM_READY_TOLERANCE_DEG && pitchDiff <= AIM_READY_TOLERANCE_DEG) {
                return true;
            }
        }

        return false;
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        target = null;
        aimingTarget = null;
        this.attacks = 0.0f;
        this.setEnabled(false);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (!this.targetEsp.is("None")) {
            Entity entity = aimingTarget;
            if (entity != null && mc.gameRenderer != null) {
                PoseStack poseStack = event.poseStack();
                poseStack.pushPose();
                Camera camera = mc.gameRenderer.getMainCamera();
                Vec3 cameraPos = camera.getPosition();

                String renderMode = this.targetEsp.getValue();
                if (!"NurikZapen".equals(renderMode) && !"Tracer".equals(renderMode)) {
                    poseStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());
                }

                double dx = entity.getX() - entity.xOld;
                double dy = entity.getY() - entity.yOld;
                double dz = entity.getZ() - entity.zOld;
                Vec3 playerDelta = mc.player.getDeltaMovement();
                Vec3 offset = new Vec3(
                        dx + playerDelta.x + 0.005,
                        dy + playerDelta.y - 0.002,
                        dz + playerDelta.z + 0.005);

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);

                try {
                    String mode = renderMode;
                    switch (mode) {
                        case "Spiral" -> RenderUtil.drawSpiralEffect(poseStack, entity, event.partialTick());
                        case "Box" -> {
                            int hurtTime = entity instanceof LivingEntity le ? le.hurtTime : 0;
                            Color color = hurtTime == 0
                                    ? new Color(0, 0, 0, 130)
                                    : (hurtTime >= 9 && hurtTime <= 10
                                    ? new Color(0, 255, 255, 200)
                                    : new Color(255, 0, 0, 200));

                            AABB base = EntityUtil.getInterpolatedAABB(entity, event.partialTick()).move(offset);
                            AABB padded = new AABB(
                                    base.minX - 0.175, base.minY - 0.125, base.minZ - 0.175,
                                    base.maxX + 0.175, base.maxY + 0.225, base.maxZ + 0.175);
                            RenderUtil.drawFilledColoredBox(padded, poseStack, color, color);
                        }
                        case "Tab" -> {
                            int hurtTime = entity instanceof LivingEntity le ? le.hurtTime : 0;
                            Color color = hurtTime == 0
                                    ? new Color(0, 0, 0, 130)
                                    : (hurtTime == 3
                                    ? new Color(255, 255, 255, 200)
                                    : new Color(255, 0, 0, 200));

                            AABB base = EntityUtil.getInterpolatedAABB(entity, event.partialTick()).move(offset);
                            AABB band = new AABB(
                                    base.minX, base.minY + entity.getEyeHeight() + 0.11, base.minZ,
                                    base.maxX, base.maxY - 0.13, base.maxZ);
                            RenderUtil.drawFilledColoredBox(band, poseStack, color, color);
                        }
                        case "Glow" -> this.renderEspGlow(poseStack, entity, event.partialTick(), offset);
                        case "Corner" -> this.renderEspCorner(poseStack, entity, event.partialTick(), offset);
                        case "Tracer" -> this.renderEspTracer(poseStack, entity, event.partialTick());
                        case "Halo" -> this.renderEspHalo(poseStack, entity, event.partialTick(), offset);
                        case "NurikZapen" -> this.renderNurikZapen(poseStack, entity, event.partialTick());
                        default -> {}
                    }
                } finally {
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableBlend();
                    poseStack.popPose();
                }
            }
        }

        if (this.rangeCircle.getValue()) {
            renderRangeCircle(event.poseStack(), event.partialTick());
        }
    }

    // ==========================================
    // === 新增超级炫酷的 TargetESP 渲染 =========
    // ==========================================

    private void renderEspGlow(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        long time = System.currentTimeMillis();
        float pulse = (float) (Math.sin(time / 300.0) * 0.5 + 0.5);

        // 动态变色 (青/紫)
        int color = Color.HSBtoRGB((time % 4000) / 4000f, 0.8f, 1.0f);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // 绘制多层发光边框 (修复参数问题，改用 setShaderColor)
        for (int i = 1; i <= 3; i++) {
            float expand = i * 0.02f + (pulse * 0.015f);
            AABB glowBox = base.inflate(expand);
            int alpha = (int)(150 / i - (pulse * 30));
            if (alpha < 0) alpha = 0;

            RenderSystem.setShaderColor(r, g, b, alpha / 255.0f);
            RenderUtil.drawOutlineBox(glowBox, poseStack);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 受击变红
        if (entity instanceof LivingEntity le && le.hurtTime > 0) {
            RenderUtil.drawFilledColoredBox(base, poseStack, new Color(255, 0, 0, 60), new Color(255, 0, 0, 60));
        }
    }

    private void renderEspCorner(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset).inflate(0.1);
        int color = 0xFF00FFFF; // 青色
        if (entity instanceof LivingEntity le && le.hurtTime > 0) color = 0xFFFF0000;

        // 获取 3D 盒子的 8 个顶点
        Vec3[] v = {
                new Vec3(base.minX, base.minY, base.minZ), new Vec3(base.maxX, base.minY, base.minZ),
                new Vec3(base.minX, base.maxY, base.minZ), new Vec3(base.maxX, base.maxY, base.minZ),
                new Vec3(base.minX, base.minY, base.maxZ), new Vec3(base.maxX, base.minY, base.maxZ),
                new Vec3(base.minX, base.maxY, base.maxZ), new Vec3(base.maxX, base.maxY, base.maxZ)
        };

        // 定义每个角的延伸方向 (L型角标)
        float L = 0.2f; // 角标长度
        Matrix4f matrix = poseStack.last().pose();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 8; i++) {
            Vec3 p = v[i];
            float dx = (p.x == base.minX) ? L : -L;
            float dy = (p.y == base.minY) ? L : -L;
            float dz = (p.z == base.minZ) ? L : -L;

            // X 轴延伸
            buffer.vertex(matrix, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, 255).endVertex();
            buffer.vertex(matrix, (float)p.x + dx, (float)p.y, (float)p.z).color(r, g, b, 255).endVertex();
            // Y 轴延伸
            buffer.vertex(matrix, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, 255).endVertex();
            buffer.vertex(matrix, (float)p.x, (float)p.y + dy, (float)p.z).color(r, g, b, 255).endVertex();
            // Z 轴延伸
            buffer.vertex(matrix, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, 255).endVertex();
            buffer.vertex(matrix, (float)p.x, (float)p.y, (float)p.z + dz).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.lineWidth(1.0f);
    }

    private void renderEspTracer(PoseStack poseStack, Entity entity, float partialTick) {
        // 使用实体插值坐标
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.5;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        // 玩家视图坐标 (在摄像机空间)
        // 玩家自身视角的底部中间偏右一点(模拟枪口)
        float playerX = 0.3f;
        float playerY = -0.4f;
        float playerZ = 0.5f;

        // 计算目标相对相机的坐标
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        float targetX = (float) (x - camPos.x);
        float targetY = (float) (y - camPos.y);
        float targetZ = (float) (z - camPos.z);

        long time = System.currentTimeMillis();
        int color = Color.HSBtoRGB((time % 3000) / 3000f, 0.9f, 1.0f);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // 绘制 3D 追踪线 (QUAD 模拟厚度)
        float width = 0.02f;
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 线段的起始和结束
        buffer.vertex(matrix, playerX - width, playerY, playerZ).color(r, g, b, 180).endVertex();
        buffer.vertex(matrix, playerX + width, playerY, playerZ).color(r, g, b, 180).endVertex();
        buffer.vertex(matrix, targetX + width, targetY, targetZ).color(r, g, b, 0).endVertex(); // 渐隐到目标
        buffer.vertex(matrix, targetX - width, targetY, targetZ).color(r, g, b, 0).endVertex();

        BufferUploader.drawWithShader(buffer.end());
    }

    private void renderEspHalo(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0;
        double y = base.maxY + 0.3;
        double z = (base.minZ + base.maxZ) / 2.0;

        long time = System.currentTimeMillis();
        float rotation = (time % 3000) / 3000f * 360f;
        float pulse = (float) (Math.sin(time / 200.0) * 0.2 + 0.8);

        int color = 0xFFFFD700; // 金色光环
        if (entity instanceof LivingEntity le && le.hurtTime > 0) color = 0xFFFF0000;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        int segments = 40;
        float radius = 0.5f * pulse;

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * Math.PI * 2 / segments);
            float px = (float) (Math.cos(angle) * radius);
            float pz = (float) (Math.sin(angle) * radius);
            buffer.vertex(matrix, px, 0, pz).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        // 内圈反向旋转
        poseStack.mulPose(Axis.YP.rotationDegrees(-rotation * 1.5f));
        matrix = poseStack.last().pose();
        radius = 0.3f * pulse;
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * Math.PI * 2 / segments);
            float px = (float) (Math.cos(angle) * radius);
            float pz = (float) (Math.sin(angle) * radius);
            buffer.vertex(matrix, px, 0, pz).color(r, g, b, 150).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        poseStack.popPose();
    }

    // ==========================================
    // === 原本的 TargetESP 结束 =================
    // ==========================================

    private void renderRangeCircle(PoseStack poseStack, float partialTick) {
        if (mc.player == null || mc.gameRenderer == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        float radius = this.rangeCircleRadius.getValue().floatValue();
        int alpha = this.rangeCircleAlpha.getValue().intValue();

        int r = 0, g = 255, b = 0;

        if (this.rangeCircleRainbow.getValue()) {
            float hue = (System.currentTimeMillis() % 6000) / 6000.0f;
            int color = Color.HSBtoRGB(hue, 0.8f, 1.0f);
            r = (color >> 16) & 0xFF;
            g = (color >> 8) & 0xFF;
            b = color & 0xFF;
        } else {
            String colorMode = this.circleColor.getValue();
            switch (colorMode) {
                case "Red" -> { r = 255; g = 0; b = 0; }
                case "Orange" -> { r = 255; g = 165; b = 0; }
                case "Yellow" -> { r = 255; g = 255; b = 0; }
                case "Green" -> { r = 0; g = 255; b = 0; }
                case "Cyan" -> { r = 0; g = 255; b = 255; }
                case "Blue" -> { r = 0; g = 0; b = 255; }
                case "Purple" -> { r = 128; g = 0; b = 255; }
                case "Pink" -> { r = 255; g = 105; b = 180; }
                case "White" -> { r = 255; g = 255; b = 255; }
                default -> {
                    float hue = (System.currentTimeMillis() % 6000) / 6000.0f;
                    int color = Color.HSBtoRGB(hue, 0.8f, 1.0f);
                    r = (color >> 16) & 0xFF;
                    g = (color >> 8) & 0xFF;
                    b = color & 0xFF;
                }
            }
        }

        poseStack.pushPose();
        poseStack.translate(
                mc.player.getX() - cameraPos.x(),
                mc.player.getY() + 0.05 - cameraPos.y(),
                mc.player.getZ() - cameraPos.z()
        );

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        int segments = 64;
        float angleStep = (float) (2.0 * Math.PI / segments);

        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = i * angleStep;
            float xOff = (float) (Math.cos(angle) * radius);
            float zOff = (float) (Math.sin(angle) * radius);
            buffer.vertex(matrix, xOff, 0, zOff).color(r, g, b, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        if (this.rangeCircleFilled.getValue()) {
            int fillAlpha = Math.max(1, alpha / 3);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < segments; i++) {
                float a1 = i * angleStep;
                float a2 = (i + 1) * angleStep;
                float x1 = (float) (Math.cos(a1) * radius);
                float z1 = (float) (Math.sin(a1) * radius);
                float x2 = (float) (Math.cos(a2) * radius);
                float z2 = (float) (Math.sin(a2) * radius);

                buffer.vertex(matrix, 0, 0, 0).color(r, g, b, fillAlpha).endVertex();
                buffer.vertex(matrix, 0, 0, 0).color(r, g, b, fillAlpha).endVertex();
                buffer.vertex(matrix, x2, 0, z2).color(r, g, b, fillAlpha).endVertex();
                buffer.vertex(matrix, x1, 0, z1).color(r, g, b, fillAlpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }

        if (this.rangeCircleMarkTargets.getValue() && !targetList.isEmpty()) {
            renderTargetsOnCircle(matrix, radius);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        poseStack.popPose();
    }

    private void renderTargetsOnCircle(Matrix4f matrix, float radius) {
        if (mc.player == null) return;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        if (target != null && targetList.contains(target)) {
            renderTargetDot(matrix, buffer, target, radius, 0xFFFF0000);
        }
        for (Entity entity : targetList) {
            if (entity != target) {
                renderTargetDot(matrix, buffer, entity, radius, 0xFFFF8800);
            }
        }
    }

    private void renderTargetDot(Matrix4f matrix, BufferBuilder buffer, Entity entity, float radius, int color) {
        if (mc.player == null) return;

        Vec3 toTarget = entity.position().subtract(mc.player.position());
        double angle = Math.atan2(toTarget.z, toTarget.x);

        float xOff = (float) (Math.cos(angle) * radius);
        float zOff = (float) (Math.sin(angle) * radius);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, xOff, 0, zOff).color(r, g, b, 120).endVertex();
        float glowSize = 0.25f;
        for (int i = 0; i <= 16; i++) {
            float dotAngle = (float) (2.0 * Math.PI * i / 16);
            float dx = (float) (Math.cos(dotAngle) * glowSize);
            float dz = (float) (Math.sin(dotAngle) * glowSize);
            buffer.vertex(matrix, xOff + dx, 0, zOff + dz).color(r, g, b, 0).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, xOff, 0, zOff).color(r, g, b, 255).endVertex();
        float coreSize = 0.06f;
        for (int i = 0; i <= 12; i++) {
            float dotAngle = (float) (2.0 * Math.PI * i / 12);
            float dx = (float) (Math.cos(dotAngle) * coreSize);
            float dz = (float) (Math.sin(dotAngle) * coreSize);
            buffer.vertex(matrix, xOff + dx, 0, zOff + dz).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void ensureNurikCaptureTexture() {
        if (nurikTextureLoaded || nurikTextureLoadFailed) {
            return;
        }
        try (InputStream inputStream = Assets.open(NURIK_CAPTURE_ASSET)) {
            if (inputStream == null) {
                nurikTextureLoadFailed = true;
                System.out.println("KillAura: NurikZapen texture not found - " + NURIK_CAPTURE_ASSET);
                return;
            }
            mc.getTextureManager().register(NURIK_CAPTURE_TEXTURE, new DynamicTexture(NativeImage.read(inputStream)));
            nurikTextureLoaded = true;
        } catch (IOException exception) {
            nurikTextureLoadFailed = true;
            System.out.println("KillAura: failed to load NurikZapen texture - " + exception.getMessage());
        }
    }

    private void renderNurikZapen(PoseStack poseStack, Entity entity, float partialTick) {
        ensureNurikCaptureTexture();

        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getEyeHeight() * 0.5f;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        poseStack.pushPose();
        try {
            poseStack.translate(x - cameraPos.x(), y - cameraPos.y(), z - cameraPos.z());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - camera.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(-camera.getXRot()));
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) ((System.currentTimeMillis() / 5.0) % 360.0)));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            if (nurikTextureLoaded) {
                RenderSystem.setShaderTexture(0, NURIK_CAPTURE_TEXTURE);
            }

            ModuleListHud moduleList = NiloreClient.getInstance().getHudManager().getHudElement(ModuleListHud.class);
            int[] colors = moduleList == null
                    ? new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}
                    : new int[]{
                    moduleList.getThemeColor(0, 0.0f, 3),
                    moduleList.getThemeColor(1, 0.33f, 3),
                    moduleList.getThemeColor(2, 0.67f, 3),
                    moduleList.getThemeColor(3, 1.0f, 3)
            };
            float size = 0.75f;
            float[][] corners = {
                    {-size, size, 0.0f, 0.0f},
                    {size, size, 1.0f, 0.0f},
                    {size, -size, 1.0f, 1.0f},
                    {-size, -size, 0.0f, 1.0f}
            };
            Matrix4f matrix = poseStack.last().pose();
            BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < corners.length; i++) {
                int color = colors[i];
                bufferBuilder.vertex(matrix, corners[i][0], corners[i][1], 0.0f)
                        .uv(corners[i][2], corners[i][3])
                        .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 200)
                        .endVertex();
            }
            BufferUploader.drawWithShader(bufferBuilder.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            poseStack.popPose();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!NiloreClient.isReady()) {
            return;
        }

        if (mc.screen != null) {
            if (!this.attackInInventory.getValue()) {
                target = null;
                aimingTarget = null;
                this.currentBestHit = null;
                this.rotation = null;
                this.aimRotation = null;
                this.prevBestHit = null;
                targetList.clear();
                this.sprintTickCounter = 0;
                this.attacks = 0.0f;
                this.sprintCounter = 0;
                return;
            }
        }

        if (ItemUtil.hasServerItem()
                || (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled() && !this.style.is("onTickRot"))
                || (Stuck.INSTANCE != null && Stuck.INSTANCE.isEnabled())
                || (Helper.INSTANCE != null && Helper.INSTANCE.isEnabled() && Helper.targetRotation != null)
                || AntiWeb.targetRotation != null
                || AntiTNT.targetRotation != null
                || MidPearl.targetRotation != null
                || this.isWebPlacing()) {
            target = null;
            aimingTarget = null;
            this.currentBestHit = null;
            this.rotation = null;
            this.aimRotation = null;
            this.prevBestHit = null;
            targetList.clear();
            this.sprintTickCounter = 0;
            this.attacks = 0.0f;
            this.sprintCounter = 0;
            return;
        }

        boolean isSwitch = this.attackMode.is("Switch");
        this.updateTargets();

        aimingTarget = null;
        this.currentBestHit = null;

        Entity candidate = this.getTarget();
        if (candidate != null) {
            double distance = this.getRangeDistance(candidate);
            float aimRangeValue = this.aimRange.getValue().floatValue();

            if (distance <= aimRangeValue) {
                aimingTarget = candidate;
                this.currentBestHit = RotationUtil.getBestHit(aimingTarget);

                Rotation desiredAimRotation = this.currentBestHit != null
                        && this.currentBestHit.rotation() != null
                        ? this.currentBestHit.rotation()
                        : RotationUtil.entityRotation(aimingTarget);
                this.aimRotation = desiredAimRotation;

                if (desiredAimRotation != null) {
                    if (this.style.is("onTickRot")) {
                        this.rotation = null;
                    } else {
                        Rotation from = RotationHandler.prevRotation != null
                                ? RotationHandler.prevRotation
                                : new Rotation(mc.player.getYRot(), mc.player.getXRot());

                        Rotation organic = this.applyOrganicRotation(from, desiredAimRotation, 1.0f);
                        this.rotation = (organic != null
                                && !Float.isNaN(organic.getYaw())
                                && !Float.isNaN(organic.getPitch())
                                && !Float.isInfinite(organic.getYaw())
                                && !Float.isInfinite(organic.getPitch()))
                                ? organic
                                : desiredAimRotation;
                    }
                } else {
                    this.rotation = null;
                    this.aimRotation = null;
                }
            } else {
                this.rotation = null;
                this.aimRotation = null;
                this.currentBestHit = null;
            }
        } else {
            this.rotation = null;
            this.aimRotation = null;
            this.currentBestHit = null;
        }

        this.prevBestHit = this.currentBestHit;

        if (targetList.isEmpty()) {
            target = null;
            return;
        }
        if (this.targetIndex > targetList.size() - 1) {
            this.targetIndex = 0;
        }

        float aimRangeValue = this.aimRange.getValue().floatValue();
        if (isSwitch && targetList.size() > 1
                && (this.attackTimes >= this.switchDelay.getValue().intValue()
                || (this.currentBestHit != null && this.currentBestHit.distance() > aimRangeValue))) {
            this.attackTimes = 0;
            for (int i = 0; i < targetList.size(); ++i) {
                ++this.targetIndex;
                if (this.targetIndex > targetList.size() - 1) {
                    this.targetIndex = 0;
                }
                Entity nextTarget = targetList.get(this.targetIndex);
                if (this.getRangeDistance(nextTarget) <= aimRangeValue) {
                    break;
                }
            }
        }
        if (this.targetIndex > targetList.size() - 1 || !isSwitch) {
            this.targetIndex = 0;
        }
        target = targetList.get(this.targetIndex);

        if (this.autoShieldBreaker.getValue()) {
            if (this.shieldBreakerTimer > 0) {
                this.shieldBreakerTimer--;
            }

            boolean targetBlocking = target != null && isTargetBlocking(target);

            if (targetBlocking) {
                int axeSlot = findAxeSlot();
                if (axeSlot != -1) {
                    if (this.previousSlot == -1 && !this.isBreakingShield) {
                        this.previousSlot = mc.player.getInventory().selected;
                        this.shieldBreakerSwitchTick = 0;
                    }

                    int switchDelay = this.shieldBreakerSwitchDelay.getValue().intValue();

                    if (this.shieldBreakerSwitchTick < switchDelay) {
                        this.shieldBreakerSwitchTick++;
                        return;
                    }

                    if (mc.player.getInventory().selected != axeSlot) {
                        mc.player.getInventory().selected = axeSlot;
                        this.isBreakingShield = true;
                        this.shieldBreakerSwitchTick = 0;
                    }

                    if (this.isBreakingShield && mc.player.getInventory().selected == axeSlot) {
                        float breakerCPS = this.shieldBreakerCPS.getValue().floatValue();
                        breakerCPS = Mth.clamp(breakerCPS, 1.0f, 20.0f);
                        float cpsPerTick = breakerCPS / 20.0f;

                        this.attacks += cpsPerTick;
                        this.shieldBreakAttempts++;
                    }
                } else {
                    if (this.isBreakingShield) {
                        restoreSlot();
                    }
                }
            } else {
                if (this.isBreakingShield) {
                    restoreSlot();
                    this.shieldBreakerTimer = this.shieldBreakerCooldown.getValue().intValue();
                }
                if (this.previousSlot != -1 && !this.isBreakingShield && this.switchBackAfterBreak.getValue()) {
                    restoreSlot();
                }
            }
        }

        if (this.delayMode.is("1.9") && mc.player.getAttackStrengthScale(0.0f) < 0.95f) {
            this.attacks = 0.0f;
            return;
        }

        if (this.style.is("Old")) {
            float apsValue;
            float minApsValue;
            if (AntiKBMode.isAttacking) {
                int kbAttackAmount = Velocity.INSTANCE != null
                        ? Velocity.INSTANCE.isNoXZAltAttackAmount.getValue().intValue()
                        : 0;
                apsValue = this.maxAps.getValue().floatValue() - kbAttackAmount;
                minApsValue = this.minAps.getValue().floatValue() - kbAttackAmount;
            } else {
                apsValue = this.maxAps.getValue().floatValue();
                minApsValue = this.minAps.getValue().floatValue();
            }
            this.attacks += (float)(MathUtil.randomDouble(minApsValue, apsValue) / 20.0);
        } else {
            float apsValue = this.maxAps.getValue().floatValue();
            float minApsValue = this.minAps.getValue().floatValue();
            if (AntiKBMode.isAttacking) {
                int kbAttackAmount = Velocity.INSTANCE != null
                        ? Velocity.INSTANCE.isNoXZAltAttackAmount.getValue().intValue()
                        : 0;
                apsValue -= kbAttackAmount;
                minApsValue -= kbAttackAmount;
            }
            this.attacks += (float)(MathUtil.randomDouble(minApsValue, apsValue) / 20.0);
        }
    }

    private boolean isTargetBlocking(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        if (!living.isUsingItem()) return false;
        ItemStack activeItem = living.getUseItem();
        return activeItem.getItem() instanceof ShieldItem;
    }

    private int findAxeSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    private void restoreSlot() {
        if (mc.player == null) return;
        if (this.previousSlot != -1 && this.switchBackAfterBreak.getValue()) {
            mc.player.getInventory().selected = this.previousSlot;
        }
        this.previousSlot = -1;
        this.isBreakingShield = false;
        this.shieldBreakAttempts = 0;
        this.shieldBreakerSwitchTick = 0;
    }

    private void cancelAttackCooldownForShieldBreak() {
        if (mc.player == null) return;
        mc.player.resetAttackStrengthTicker();
    }

    private boolean performShieldBreakAttack(Entity entity) {
        if (mc.player == null || mc.gameMode == null) return false;
        if (entity == null) return false;

        if (!isTargetBlocking(entity)) return false;

        if (!this.isWithinReach(entity)) return false;

        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastShieldBreakTime < SHIELD_BREAK_COOLDOWN_MS) return false;

        if (!isHoldingAxe()) {
            int axeSlot = findAxeSlot();
            if (axeSlot == -1) return false;
            if (this.previousSlot == -1) {
                this.previousSlot = mc.player.getInventory().selected;
            }
            mc.player.getInventory().selected = axeSlot;
            this.isBreakingShield = true;
        }

        cancelAttackCooldownForShieldBreak();
        attackWithKeepSprint(entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        cancelAttackCooldownForShieldBreak();

        this.lastShieldBreakTime = currentTime;
        if (this.switchBackAfterBreak.getValue()) {
            restoreSlot();
        } else {
            this.isBreakingShield = false;
        }
        return true;
    }

    private boolean isHoldingAxe() {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().getItem() instanceof AxeItem;
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mc.player == null) return;
        if (this.isWebPlacing()) {
            this.attacks = 0.0f;
            return;
        }

        if (this.autoShieldBreaker.getValue() && target != null) {
            if (isTargetBlocking(target)) {
                if (this.isWithinReach(target)) {
                    performShieldBreakAttack(target);
                    return;
                }
            }
        }

        if (this.use1_9Cooldown.getValue()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.isEmpty() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                return;
            }
        }

        if (this.use1_9Cooldown.getValue()) {
            float cooldown = mc.player.getAttackStrengthScale(0.5f);
            if (cooldown >= 0.9f && this.attacks >= 1.0f) {
                if (this.critHold()) {
                    return;
                }
                if (this.isAimReadyToAttack() && this.doAttack()) {
                    this.attacks -= 1.0f;
                }
            }
            this.attacks += 0.1f;
            if (this.attacks > 2.0f) {
                this.attacks = 2.0f;
            }
            return;
        }

        if ((this.attackInInventory.getValue() || mc.player.getUseItem().isEmpty())
                && (this.ignoreSkipTicks.getValue() || ClientBase.delayPackets.isEmpty()
                || (Critical.INSTANCE != null && Critical.INSTANCE.isEnabled()))) {
            if (this.attackInInventory.getValue() || mc.screen == null) {
                if (KeepSprint.shouldSkipAttack()) {
                    this.attacks = 0.0f;
                    return;
                }

                if (!this.isAimReadyToAttack()) {
                    if (this.attacks > 1.0f) {
                        this.attacks = 1.0f;
                    }
                    return;
                }

                if (this.critHold()) {
                    if (this.attacks > 1.0f) {
                        this.attacks = 1.0f;
                    }
                    return;
                }

                while (this.attacks >= 1.0f) {
                    if (!this.doAttack()) {
                        break;
                    }
                    this.attacks -= 1.0f;
                }
            } else {
                this.attacks = 0.0f;
            }
        } else {
            this.attacks = 0.0f;
        }
    }

    public boolean doAttack() {
        if (this.isWebPlacing()) {
            this.attacks = 0.0f;
            return false;
        }
        if (targetList.isEmpty()) return false;
        if (this.rotation == null && !this.style.is("onTickRot")) return false;

        if (!this.style.is("onTickRot") && !this.isAimReadyToAttack()) {
            return false;
        }

        if (this.autoShieldBreaker.getValue() && target != null && isTargetBlocking(target)) {
            if (performShieldBreakAttack(target)) {
                return true;
            }
        }

        HitResult hitResult;
        if ((this.style.is("Old") || this.style.is("onTickRot"))
                && (Boolean) this.overrideRaycast.getValue()
                && this.aimRotation != null) {
            hitResult = RotationUtil.performRaycast(this.aimRotation, true);
        } else {
            hitResult = mc.hitResult;
        }
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (AntiBots.isBot(hitEntity)) {
                ChatUtil.print("Skipped attack on suspected bot");
                return false;
            }
        }
        if (this.multiAttack.getValue()) {
            int attacked = 0;
            Rotation aimRot = this.aimRotation != null
                    ? this.aimRotation
                    : RotationHandler.targetRotation;
            if (aimRot == null) {
                if (mc.player != null) {
                    aimRot = new Rotation(mc.player.getYRot(), mc.player.getXRot());
                }
            }
            for (Entity entity : targetList) {
                if (mc.player == null) break;
                if (RotationUtil.getHitDistance(entity, mc.player.getEyePosition(), aimRot)
                        > this.getEffectiveAttackRange()) continue;
                if (this.attackEntity(entity)) {
                    attacked++;
                }
                if (attacked >= 2) break;
            }
            return attacked > 0;
        } else if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            return this.attackEntity(hitEntity);
        } else if (target != null && targetList.contains(target) && this.isValidAttack(target)) {
            return this.attackEntity(target);
        }
        return false;
    }

    public Entity getTarget() {
        Entity entity = target;
        if (entity == null) {
            List<Entity> list = this.getTargets();
            if (!list.isEmpty()) {
                entity = list.get(0);
            }
        }
        if (entity != null) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && AntiBots.isBot(entity)) {
                return null;
            }
        }
        return entity;
    }

    public void updateTargets() {
        List<Entity> next = this.getTargets();
        targetList = next != null ? next : new ArrayList<>();

        if (targetList.isEmpty()) {
            target = null;
            this.targetIndex = 0;
            return;
        }

        if (this.targetIndex < 0 || this.targetIndex >= targetList.size()) {
            this.targetIndex = 0;
        }
        if (target != null && !targetList.contains(target)) {
            target = null;
        }
    }

    public boolean isValidTarget(Entity entity) {
        if (!NiloreClient.isReady()) return false;
        if (entity == mc.player) return false;
        if (entity instanceof LivingEntity livingEntity) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) {
                return false;
            }
            if (livingEntity.isDeadOrDying() || livingEntity.getHealth() <= 0.0f) return false;
            if (entity instanceof ArmorStand) return false;
            if (entity.isInvisible() && !(Boolean) this.attackInvisible.getValue()) return false;
            if (entity instanceof Player player) {
                if (this.test.getValue() && player.getY() >= mc.player.getY() + 0.05f) {
                    return true;
                }
            }
            if (Teams.isSameTeam(entity)) return false;
            if (entity instanceof Player && !(Boolean) this.attackPlayer.getValue()) return false;
            if (entity instanceof Player && (entity.getBbWidth() < 0.5 || livingEntity.isSleeping())) return false;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem)
                    && !(Boolean) this.attackMobs.getValue()) {
                return false;
            }
            if ((entity instanceof Animal || entity instanceof Squid) && !(Boolean) this.attackAnimals.getValue()) {
                return false;
            }
            if (entity instanceof Villager && !(Boolean) this.attackAnimals.getValue()) return false;
            return !(entity instanceof Player) || !entity.isSpectator();
        }
        return false;
    }

    public boolean isValidAttack(Entity entity) {
        if (mc.player == null) return false;
        if (!this.isValidTarget(entity)) return false;
        if (entity instanceof LivingEntity le && le.hurtTime > this.hurtTime.getValue().intValue()) {
            return false;
        }

        double dist = this.getRangeDistance(entity);

        float maxAimRange = this.aimRange.getValue().floatValue();

        if (dist > maxAimRange) {
            if (!(Boolean) this.predictionEnabled.getValue()
                    || this.predictDistance(entity) >= maxAimRange) {
                return false;
            }
        }

        if ((this.style.is("New") || this.style.is("onTickRot")) && mc.level != null) {
            boolean skipWallCheck = this.throughWalls.getValue()
                    && dist <= this.throughWallsRange.getValue().floatValue();

            if (!skipWallCheck && !this.hasVisibleHitPoint(entity)) {
                return false;
            }
        }

        return RotationUtil.isEntityInFov(
                entity,
                this.fov.getValue().floatValue() / 2.0f
        );
    }

    private boolean hasVisibleHitPoint(Entity entity) {
        if (mc.player == null || mc.level == null || entity == null) {
            return false;
        }

        Vec3 eye = mc.player.getEyePosition(1.0f);
        AABB box = entity.getBoundingBox();

        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;

        double height = Math.max(0.01, box.maxY - box.minY);

        Vec3 center = new Vec3(
                centerX,
                box.minY + height * 0.50,
                centerZ
        );

        Vec3 chest = new Vec3(
                centerX,
                box.minY + height * 0.72,
                centerZ
        );

        Vec3 head = new Vec3(
                centerX,
                Math.min(box.maxY - 0.05, entity.getEyeY() - 0.03),
                centerZ
        );

        Vec3 closest = new Vec3(
                Mth.clamp(eye.x, box.minX + 0.01, box.maxX - 0.01),
                Mth.clamp(eye.y, box.minY + 0.05, box.maxY - 0.05),
                Mth.clamp(eye.z, box.minZ + 0.01, box.maxZ - 0.01)
        );

        return this.isPointVisible(eye, chest)
                || this.isPointVisible(eye, head)
                || this.isPointVisible(eye, center)
                || this.isPointVisible(eye, closest);
    }

    private boolean isPointVisible(Vec3 eye, Vec3 point) {
        if (mc.level == null) {
            return false;
        }

        if (eye.distanceToSqr(point) <= 1.0E-6) {
            return true;
        }

        BlockHitResult blockHit = mc.level.clip(
                new ClipContext(
                        eye,
                        point,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        mc.player
                )
        );

        if (blockHit.getType() == HitResult.Type.MISS) {
            return true;
        }

        double hitToTargetSq = blockHit.getLocation().distanceToSqr(point);
        return hitToTargetSq <= 0.04;
    }

    private double getRangeDistance(Entity entity) {
        if (mc.player == null || entity == null) {
            return Double.MAX_VALUE;
        }

        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();

        double closestX = Mth.clamp(eye.x, box.minX, box.maxX);
        double closestY = Mth.clamp(eye.y, box.minY, box.maxY);
        double closestZ = Mth.clamp(eye.z, box.minZ, box.maxZ);

        double dx = eye.x - closestX;
        double dy = eye.y - closestY;
        double dz = eye.z - closestZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private float getEffectiveAttackRange() {
        if (this.use1_9Cooldown.getValue()) {
            return FORCED_1_9_RANGE;
        }
        return Math.min(
                this.attackRange.getValue().floatValue(),
                this.aimRange.getValue().floatValue()
        );
    }

    private boolean isWithinAttackRange(Entity entity) {
        return this.getRangeDistance(entity) <= this.getEffectiveAttackRange();
    }

    private boolean isWithinReach(Entity entity) {
        float effectiveReach = Math.min(
                this.reach.getValue().floatValue(),
                this.getEffectiveAttackRange()
        );
        return this.getRangeDistance(entity) <= effectiveReach;
    }

    private double predictDistance(Entity entity) {
        double selfDelayMs = 0.0;
        if (mc.getConnection() != null
                && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) {
            selfDelayMs = mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
        }
        double selfDelayTicks = Math.min(selfDelayMs / 50.0, this.selfDelayThreshold.getValue().doubleValue());

        double enemyDelayMs = 0.0;
        if (entity instanceof Player player) {
            if (mc.getConnection() != null
                    && mc.getConnection().getPlayerInfo(player.getUUID()) != null) {
                enemyDelayMs = mc.getConnection().getPlayerInfo(player.getUUID()).getLatency();
            }
        }
        double enemyDelayTicks = Math.min(enemyDelayMs / 50.0, this.enemyDelayThreshold.getValue().doubleValue());

        double totalTicks = 2.0 + selfDelayTicks + enemyDelayTicks;

        double playerVelX = mc.player.getX() - mc.player.xOld;
        double playerVelZ = mc.player.getZ() - mc.player.zOld;
        double enemyVelX = entity.getX() - entity.xOld;
        double enemyVelZ = entity.getZ() - entity.zOld;

        double predictedPlayerX = mc.player.getX() + playerVelX * totalTicks;
        double predictedPlayerZ = mc.player.getZ() + playerVelZ * totalTicks;
        double predictedEnemyX = entity.getX() + enemyVelX * totalTicks;
        double predictedEnemyZ = entity.getZ() + enemyVelZ * totalTicks;

        double dx = predictedEnemyX - predictedPlayerX;
        double dz = predictedEnemyZ - predictedPlayerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public boolean attackEntity(Entity entity) {
        if (mc.player == null || mc.gameMode == null) return false;
        if (this.isWebPlacing()) return false;
        if (entity == null) return false;

        if (this.critHold(entity)) return false;

        if (!this.isWithinAttackRange(entity)) return false;

        final boolean canActuallyHit = this.isWithinReach(entity);

        if (this.use1_9Cooldown.getValue()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.isEmpty() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                return false;
            }
        }

        if (!this.use1_9Cooldown.getValue()) {
            return this.attackEntity1_8(entity);
        }

        if (this.style.is("onTickRot")) {
            return this.attackEntityNewFix(entity);
        }

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        if (!this.silentMode.getValue() && RotationHandler.targetRotation != null) {
            mc.player.setYRot(RotationHandler.targetRotation.getYaw());
            mc.player.setXRot(RotationHandler.targetRotation.getPitch());
        }

        ++this.attackTimes;
        int attackKey = mc.options.keyAttack.getKey().getValue();

        if (canActuallyHit) {
            attackWithKeepSprint(entity);
        }

        ForgeHooksClient.onMouseButtonPre(attackKey, 1, 0);
        mc.player.swing(InteractionHand.MAIN_HAND);
        ForgeHooksClient.onMouseButtonPost(attackKey, 1, 0);

        if (canActuallyHit && this.morePart.getValue()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }

        if (this.silentMode.getValue()) {
            mc.player.setYRot(currentYaw);
            mc.player.setXRot(currentPitch);
        }

        if (!this.attackCooldown.getValue()) {
            if (!this.isBreakingShield) {
                mc.player.resetAttackStrengthTicker();
            }
        }

        if (this.delayMode.is("1.9")) {
            this.sprintCounter = (int) mc.player.getCurrentItemAttackStrengthDelay();
        }
        return true;
    }

    public boolean attackEntity1_8(Entity entity) {
        if (mc.player == null || mc.gameMode == null) return false;
        if (this.isWebPlacing()) return false;
        if (entity == null) return false;

        if (this.critHold(entity)) return false;

        if (!this.isWithinAttackRange(entity)) return false;
        final boolean canActuallyHit = this.isWithinReach(entity);

        if (this.use1_9Cooldown.getValue()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.isEmpty() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                return false;
            }
        }

        if (this.style.is("onTickRot")) {
            if (mc.getConnection() == null) return false;
            float origYaw = mc.player.getYRot();
            float origPitch = mc.player.getXRot();
            Rotation targetRot = this.aimRotation;
            if (targetRot == null) return false;

            float jitter1 = rotationJitter();
            float jitter2 = rotationJitter();
            float jitter3 = rotationJitter();

            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    targetRot.getYaw() + jitter1,
                    targetRot.getPitch() + jitter1,
                    mc.player.onGround()
            ));

            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    targetRot.getYaw() + jitter2,
                    targetRot.getPitch() + jitter2,
                    mc.player.onGround()
            ));

            ++this.attackTimes;
            if (canActuallyHit) {
                attackWithKeepSprint(entity);
            }
            mc.player.swing(InteractionHand.MAIN_HAND);
            if (canActuallyHit && this.morePart.getValue()) {
                mc.player.magicCrit(entity);
                mc.player.crit(entity);
            }

            if (!this.attackCooldown.getValue()) {
                mc.player.resetAttackStrengthTicker();
            }

            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    origYaw + jitter3,
                    origPitch + jitter3,
                    mc.player.onGround()
            ));
            return true;
        }

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        if (!this.silentMode.getValue() && RotationHandler.targetRotation != null) {
            mc.player.setYRot(RotationHandler.targetRotation.getYaw());
            mc.player.setXRot(RotationHandler.targetRotation.getPitch());
        }

        ++this.attackTimes;
        int attackKey = mc.options.keyAttack.getKey().getValue();
        if (canActuallyHit) {
            attackWithKeepSprint(entity);
        }
        ForgeHooksClient.onMouseButtonPre(attackKey, 1, 0);
        mc.player.swing(InteractionHand.MAIN_HAND);
        ForgeHooksClient.onMouseButtonPost(attackKey, 1, 0);

        if (canActuallyHit && this.morePart.getValue()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }

        if (this.silentMode.getValue()) {
            mc.player.setYRot(currentYaw);
            mc.player.setXRot(currentPitch);
        }

        if (!this.attackCooldown.getValue()) {
            mc.player.resetAttackStrengthTicker();
        }

        if (this.delayMode.is("1.9")) {
            this.sprintCounter = (int) mc.player.getCurrentItemAttackStrengthDelay();
        }
        return true;
    }

    private boolean attackEntityNewFix(Entity entity) {
        if (mc.getConnection() == null || mc.player == null || mc.gameMode == null) return false;
        if (entity == null) return false;

        if (this.critHold(entity)) return false;

        if (!this.isWithinAttackRange(entity)) return false;
        final boolean canActuallyHit = this.isWithinReach(entity);

        if (this.use1_9Cooldown.getValue()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.isEmpty() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                return false;
            }
        }

        float origYaw = mc.player.getYRot();
        float origPitch = mc.player.getXRot();

        Rotation targetRot = this.aimRotation;
        if (targetRot == null) return false;

        float jitter1 = rotationJitter();
        float jitter2 = rotationJitter();
        float jitter3 = rotationJitter();

        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                targetRot.getYaw() + jitter1,
                targetRot.getPitch() + jitter1,
                mc.player.onGround()
        ));

        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                targetRot.getYaw() + jitter2,
                targetRot.getPitch() + jitter2,
                mc.player.onGround()
        ));

        ++this.attackTimes;
        if (canActuallyHit) {
            attackWithKeepSprint(entity);
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (canActuallyHit && this.morePart.getValue()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }

        if (!this.attackCooldown.getValue()) {
            mc.player.resetAttackStrengthTicker();
        }

        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                origYaw + jitter3,
                origPitch + jitter3,
                mc.player.onGround()
        ));

        return true;
    }

    private void attackWithKeepSprint(Entity entity) {
        if (entity == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        if (!attackAndLock()) {
            return;
        }

        if (KeepSprint.INSTANCE != null && KeepSprint.INSTANCE.isEnabled()) {
            KeepSprint.INSTANCE.attack(entity);
        } else {
            mc.gameMode.attack(mc.player, entity);
        }
    }

    /**
     * One attack per client tick, shared by the three paths that can swing: the aura itself,
     * ReduceXZMode's counter and Critical's extra hit. Whoever calls first in a tick wins, the rest
     * are dropped, so the same target can never eat two hits in one tick.
     */
    public static boolean attackAndLock() {
        long tick = mc.level != null ? mc.level.getGameTime()
                : (mc.player != null ? mc.player.tickCount : 0);
        if (tick == attackLockTick) {
            return false;
        }
        attackLockTick = tick;
        return true;
    }

    /**
     * 【新增】判断当前 Velocity / ReduceXZ 是否处于反击或延迟窗口，
     * 若是则不应进行暴击（避免额外击退破坏 counter 节奏）。
     */
    public static boolean canCritUnderVelocity() {
        if (Velocity.INSTANCE != null && Velocity.INSTANCE.isEnabled()) {
            if (ReduceXZMode.isCountering() || ReduceXZMode.isInDelayWindow()) {
                return false;
            }
            if (client.nilore.modules.impl.combat.antikb.ReduceXZMode.isCountering()
                    || client.nilore.modules.impl.combat.antikb.ReduceXZMode.isInDelayWindow()) {
                return false;
            }
        }
        if (Velocity.INSTANCE != null && Velocity.INSTANCE.isEnabled()) {
            if (client.nilore.modules.impl.combat.antikb.ReduceXZMode.isCountering()
                    || client.nilore.modules.impl.combat.antikb.ReduceXZMode.isInDelayWindow()) {
                return false;
            }
        }
        return true;
    }

    private float rotationJitter() {
        float offset = (float) (Math.random() * 0.04 + 0.03);
        return Math.random() > 0.5 ? offset : -offset;
    }

    private boolean critHold() {
        return this.critHold(target);
    }

    private boolean critHold(Entity entity) {
        if (entity == null) return false;
        if (Critical.INSTANCE == null || !Critical.INSTANCE.isEnabled()) return false;
        if (!Critical.INSTANCE.mode.is("1.9+")) return false;
        return Critical.INSTANCE.holdAttack(entity);
    }

    private boolean isWebPlacing() {
        return AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled() && AutoWebPlace.targetRotation != null;
    }

    private List<Entity> getTargets() {
        if (mc.player == null || mc.level == null) {
            return new ArrayList<>();
        }
        Stream<Entity> stream = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), true)
                .filter(this::isValidAttack);
        List<Entity> possibleTargets = stream.collect(Collectors.toList());
        if (this.priorityMode.is("Distance")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getDistanceToPlayer));
        } else if (this.priorityMode.is("FoV")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getAngleDiffToTarget));
        } else if (this.priorityMode.is("Health")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getEntityHealth));
        }
        if (this.preferBaby.getValue()
                && possibleTargets.stream().anyMatch(KillAura::isBaby)) {
            possibleTargets.removeIf(KillAura::isNotBaby);
        }
        possibleTargets.sort(Comparator.comparing(KillAura::getCrystalPriority));
        if (this.attackMode.is("Single")) {
            return possibleTargets.isEmpty()
                    ? possibleTargets
                    : new ArrayList<>(possibleTargets.subList(0, 1));
        }

        if (this.infSwitch.getValue()) {
            return possibleTargets;
        }

        int limit = (int) Math.min(possibleTargets.size(), this.switchSize.getValue().intValue());
        return new ArrayList<>(possibleTargets.subList(0, limit));
    }

    private static Integer getCrystalPriority(Entity entity) {
        return entity instanceof EndCrystal ? 0 : 1;
    }

    private static boolean isNotBaby(Entity entity) {
        return !(entity instanceof LivingEntity) || !((LivingEntity) entity).isBaby();
    }

    private static boolean isBaby(Entity entity) {
        return entity instanceof LivingEntity && ((LivingEntity) entity).isBaby();
    }

    private static double getEntityHealth(Entity entity) {
        if (entity instanceof LivingEntity le) {
            return le.getHealth();
        }
        return 0.0;
    }

    private static double getAngleDiffToTarget(Entity entity) {
        float baseYaw = RotationHandler.targetRotation != null
                ? RotationHandler.targetRotation.getYaw()
                : mc.player.getYRot();
        return RotationUtil.angleDiff(baseYaw, RotationUtil.entityRotation(entity).getYaw());
    }

    private static double getDistanceToPlayer(Entity entity) {
        return entity.distanceTo(mc.player);
    }

    private EntityHitResult entityRaycast(float yaw, float pitch, double reach) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = eye.add(direction.scale(reach));

        Vec3 center = eye.add(direction.scale(reach * 0.5));
        AABB query = new AABB(
                center.x - reach, center.y - reach, center.z - reach,
                center.x + reach, center.y + reach, center.z + reach
        );

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.player,
                eye,
                end,
                query,
                e -> e != mc.player
                        && e.isPickable()
                        && !e.isSpectator(),
                reach * reach
        );

        if (entityHit == null) {
            return null;
        }

        if (!this.throughWalls.getValue()
                && !this.hasVisibleHitPoint(entityHit.getEntity())) {
            return null;
        }

        return entityHit;
    }

    private static boolean isLivingEntity(Entity entity) {
        return entity instanceof LivingEntity;
    }
}