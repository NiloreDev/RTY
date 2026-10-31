package client.nilore.modules.impl.combat;

import client.nilore.NiloreClient;
import client.nilore.modules.impl.combat.antikb.AntiKBMode;
import client.nilore.modules.impl.combat.antikb.JumpResetMode;
import client.nilore.modules.impl.combat.antikb.ReduceXZMode;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
import net.minecraftforge.client.ForgeHooksClient;
import client.nilore.ClientBase;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.event.impl.WorldChangeEvent;
import client.nilore.hud.ModuleListHud;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
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
import client.nilore.utils.game.MotionSimulator;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.math.MathUtil;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.utils.misc.Assets;
import client.nilore.utils.render.RenderUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;
import client.nilore.utils.rotation.RotationSmoother;
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

    private static long attackLockTick = -1L;

    // === 融合后的所有开关 ===
    public final BooleanSetting use1_9Cooldown = new BooleanSetting("1.9 Cooldown", false);
    public final BooleanSetting attackCooldown = new BooleanSetting("Attack Cooldown", true);

    public final BooleanSetting attackPlayer    = new BooleanSetting("Attack Player", true);
    public final BooleanSetting attackInvisible = new BooleanSetting("Attack Invisible", true);
    public final BooleanSetting attackAnimals   = new BooleanSetting("Attack Animals", false);
    public final BooleanSetting attackMobs      = new BooleanSetting("Attack Mobs", false);
    public final BooleanSetting multiAttack     = new BooleanSetting("Multi Attack", false);
    public final BooleanSetting infSwitch       = new BooleanSetting("Infinity Switch", false);

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

    // aimRange: 锁定/瞄准范围
    // attackRange: 挥刀范围（大致"看起来能打到"）
    // reach: 真实命中距离（真正控制攻击距离）
    public final NumberSetting aimRange    = new NumberSetting("Aim Range", 4.0, 1.0, 8.0, 0.1);
    public final NumberSetting attackRange = new NumberSetting("Attack Range", 3.5, 1.0, 8.0, 0.1);
    public final NumberSetting reach       = new NumberSetting("Reach", 3.0, 1.0, 10.0, 0.1);

    public final NumberSetting maxAps      = new NumberSetting("Max APS", 9, 1.0, 20.0, 1.0);
    public final NumberSetting minAps      = new NumberSetting("Min APS", 8, 1.0, 20.0, 1.0);
    public final NumberSetting switchSize  = new NumberSetting("Switch Size", 1.0, 1.0, 5.0, 1.0,
            () -> !(Boolean) this.infSwitch.getValue());
    public final NumberSetting switchDelay = new NumberSetting("Switch Delay (Attack Times)", 1.0, 1.0, 10.0, 1.0);

    public final BooleanSetting fovEnabled = new BooleanSetting("FoV", true);
    public final NumberSetting fovValue   = new NumberSetting("FoV Range", 360.0, 0.0, 360.0, 1.0, () -> this.fovEnabled.getValue());

    public final NumberSetting hurtTime    = new NumberSetting("Hurt Time", 10.0, 0.0, 10.0, 1.0);
    public final ModeSetting delayMode    = new ModeSetting("Delay Mode", "1.8", "1.9").withDefault("1.8");

    public final ModeSetting targetMode   = new ModeSetting("Target Mode", "Single", "Switch", "Multiple").withDefault("Single");
    public final ModeSetting priorityMode = new ModeSetting("Priority", "Distance", "FoV", "Health", "None").withDefault("FoV");

    public final BooleanSetting predictionEnabled  = new BooleanSetting("Prediction", true);
    public final NumberSetting enemyDelayThreshold = new NumberSetting("Enemy Delay Ticks", 4, 1, 5, 1,
            () -> (Boolean) this.predictionEnabled.getValue());
    public final NumberSetting selfDelayThreshold  = new NumberSetting("Self Delay Ticks", 2, 1, 5, 1,
            () -> (Boolean) this.predictionEnabled.getValue());

    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 110, 1, 180, 1);
    public final NumberSetting rotationDrift = new NumberSetting("Drift", 0.3, 0, 3, 0.1);
    public final NumberSetting rotationJitter = new NumberSetting("Jitter", 1.3, 0, 3, 0.1);

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

    public final ModeSetting targetEsp    = new ModeSetting("Target ESP", "无",
            "无", "螺旋", "方框", "边框板", "Nurik贴图", "发光", "角框", "追踪线", "光环",
            "涟漪", "羽翼", "能量球", "空间扭曲", "雷电", "方块阵列", "漩涡", "六边护盾",
            "龙卷风", "心跳脉冲",
            "符文环", "星轨", "能量栅栏", "双螺旋", "数据流", "光十字",
            "破碎空间", "冰晶", "磁场线", "时间轮回", "灵魂锁链", "星系漩涡"
    ).withDefault("发光");

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
    private final RotationSmoother rotSmoother = new RotationSmoother(new Random());
    private float organicYawOffset;
    private float organicPitchOffset;

    private int shieldBreakerTimer = 0;
    private int previousSlot = -1;
    private boolean isBreakingShield = false;
    private int shieldBreakAttempts = 0;
    private long lastShieldBreakTime = 0;
    private int shieldBreakerSwitchTick = 0;
    // 新增：破盾攻击后延迟切回的 tick 计数
    private int shieldBreakRestoreDelay = 0;

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
        String modeName = this.targetMode.getValue();
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
        String modeName = this.targetMode.getValue();
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
        this.rotSmoother.reset();
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
        this.shieldBreakRestoreDelay = 0;
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
        this.shieldBreakRestoreDelay = 0;
        attackLockTick = -1L;
        restoreSlot();
        super.onDisable();
    }

    private void reinitOrganicModel() {
        this.organicRandom = new Random(System.nanoTime());
        this.organicYawOffset = 0.0f;
        this.organicPitchOffset = 0.0f;
    }

    private Rotation computeRotation(Entity target, Rotation from, double speed, double range) {
        Vec3 aimPoint = RotationUtil.findAimPoint(target, range, 1.0f, this.throughWalls.getValue());
        if (aimPoint == null) {
            return null;
        }
        Rotation base = RotationUtil.rotationToPoint(mc.player.getEyePosition(), aimPoint);

        this.organicYawOffset += (float) ((this.organicRandom.nextDouble() - 0.5) * this.rotationDrift.getValue().doubleValue());
        this.organicPitchOffset += (float) ((this.organicRandom.nextDouble() - 0.5) * 2.0 * this.rotationJitter.getValue().doubleValue());

        Rotation jittered = new Rotation(base.getYaw() + this.organicYawOffset,
                Mth.clamp(base.getPitch() + this.organicPitchOffset, -90.0f, 90.0f));
        HitResult probe = RotationUtil.rayTraceForAim(jittered, range);
        if (!(probe instanceof EntityHitResult hit) || hit.getEntity() != target) {
            this.organicYawOffset = 0.0f;
            this.organicPitchOffset = 0.0f;
            jittered = base;
        }

        Rotation smoothed = this.rotSmoother.tick(from, jittered, speed);
        smoothed = RotationSmoother.patchConstantRotation(smoothed, from);
        if (smoothed == null
                || Float.isNaN(smoothed.getYaw()) || Float.isNaN(smoothed.getPitch())
                || Float.isInfinite(smoothed.getYaw()) || Float.isInfinite(smoothed.getPitch())) {
            return jittered;
        }
        return smoothed;
    }

    private boolean isAimReadyToAttack() {
        if (this.style.is("onTickRot")) {
            return true;
        }
        if (target == null || this.rotation == null) {
            return false;
        }

        if (mc.player != null) {
            // 射线检测距离取 reachLimit 与有效攻击范围最大值，保证能覆盖真实命中范围
            double currentReach = Math.max(
                    RotationUtil.getReachLimit(),
                    this.getEffectiveAttackRange()
            );
            HitResult probe = RotationUtil.rayTraceForAim(this.rotation, currentReach);
            if (probe instanceof EntityHitResult hit) {
                Entity hitEntity = hit.getEntity();
                if (hitEntity == target || targetList.contains(hitEntity)) {
                    return true;
                }
            }
        }

        Rotation idealRotation = this.aimRotation != null ? this.aimRotation : RotationUtil.entityRotation(target);
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
        if (!this.targetEsp.is("无")) {
            Entity entity = aimingTarget;
            if (entity != null && mc.gameRenderer != null) {
                PoseStack poseStack = event.poseStack();
                poseStack.pushPose();
                Camera camera = mc.gameRenderer.getMainCamera();
                Vec3 cameraPos = camera.getPosition();

                String renderMode = this.targetEsp.getValue();
                if (!"Nurik贴图".equals(renderMode) && !"追踪线".equals(renderMode)) {
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
                    switch (renderMode) {
                        case "螺旋" -> RenderUtil.drawSpiralEffect(poseStack, entity, event.partialTick());
                        case "方框" -> {
                            int hurtTime = entity instanceof LivingEntity le ? le.hurtTime : 0;
                            Color color = hurtTime == 0 ? new Color(0, 0, 0, 130)
                                    : (hurtTime >= 9 && hurtTime <= 10 ? new Color(0, 255, 255, 200) : new Color(255, 0, 0, 200));
                            AABB base = EntityUtil.getInterpolatedAABB(entity, event.partialTick()).move(offset);
                            AABB padded = new AABB(base.minX - 0.175, base.minY - 0.125, base.minZ - 0.175, base.maxX + 0.175, base.maxY + 0.225, base.maxZ + 0.175);
                            RenderUtil.drawFilledColoredBox(padded, poseStack, color, color);
                        }
                        case "边框板" -> {
                            int hurtTime = entity instanceof LivingEntity le ? le.hurtTime : 0;
                            Color color = hurtTime == 0 ? new Color(0, 0, 0, 130)
                                    : (hurtTime == 3 ? new Color(255, 255, 255, 200) : new Color(255, 0, 0, 200));
                            AABB base = EntityUtil.getInterpolatedAABB(entity, event.partialTick()).move(offset);
                            AABB band = new AABB(base.minX, base.minY + entity.getEyeHeight() + 0.11, base.minZ, base.maxX, base.maxY - 0.13, base.maxZ);
                            RenderUtil.drawFilledColoredBox(band, poseStack, color, color);
                        }
                        case "发光" -> this.renderEspGlow(poseStack, entity, event.partialTick(), offset);
                        case "角框" -> this.renderEspCorner(poseStack, entity, event.partialTick(), offset);
                        case "追踪线" -> this.renderEspTracer(poseStack, entity, event.partialTick());
                        case "光环" -> this.renderEspHalo(poseStack, entity, event.partialTick(), offset);
                        case "Nurik贴图" -> this.renderNurikZapen(poseStack, entity, event.partialTick());
                        case "涟漪" -> this.renderEspRipple(poseStack, entity, event.partialTick(), offset);
                        case "羽翼" -> this.renderEspWing(poseStack, entity, event.partialTick(), offset);
                        case "能量球" -> this.renderEspEnergyOrb(poseStack, entity, event.partialTick(), offset);
                        case "空间扭曲" -> this.renderEspWarp(poseStack, entity, event.partialTick(), offset);
                        case "雷电" -> this.renderEspLightning(poseStack, entity, event.partialTick(), offset);
                        case "方块阵列" -> this.renderEspCubeField(poseStack, entity, event.partialTick(), offset);
                        case "漩涡" -> this.renderEspVortex(poseStack, entity, event.partialTick(), offset);
                        case "六边护盾" -> this.renderEspHexShield(poseStack, entity, event.partialTick(), offset);
                        case "龙卷风" -> this.renderEspTornado(poseStack, entity, event.partialTick(), offset);
                        case "心跳脉冲" -> this.renderEspPulse(poseStack, entity, event.partialTick(), offset);
                        case "符文环" -> this.renderEspRuneRing(poseStack, entity, event.partialTick(), offset);
                        case "星轨" -> this.renderEspStarOrbit(poseStack, entity, event.partialTick(), offset);
                        case "能量栅栏" -> this.renderEspEnergyFence(poseStack, entity, event.partialTick(), offset);
                        case "双螺旋" -> this.renderEspDNA(poseStack, entity, event.partialTick(), offset);
                        case "数据流" -> this.renderEspDataStream(poseStack, entity, event.partialTick(), offset);
                        case "光十字" -> this.renderEspCrossHalo(poseStack, entity, event.partialTick(), offset);
                        case "破碎空间" -> this.renderEspShatter(poseStack, entity, event.partialTick(), offset);
                        case "冰晶" -> this.renderEspCrystal(poseStack, entity, event.partialTick(), offset);
                        case "磁场线" -> this.renderEspMagnetic(poseStack, entity, event.partialTick(), offset);
                        case "时间轮回" -> this.renderEspTimeLoop(poseStack, entity, event.partialTick(), offset);
                        case "灵魂锁链" -> this.renderEspSoulChain(poseStack, entity, event.partialTick(), offset);
                        case "星系漩涡" -> this.renderEspGalaxy(poseStack, entity, event.partialTick(), offset);
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

    private void renderEspGlow(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        long time = System.currentTimeMillis();
        float pulse = (float) (Math.sin(time / 300.0) * 0.5 + 0.5);
        int color = Color.HSBtoRGB((time % 4000) / 4000f, 0.8f, 1.0f);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        for (int i = 1; i <= 3; i++) {
            float expand = i * 0.02f + (pulse * 0.015f);
            AABB glowBox = base.inflate(expand);
            int alpha = (int)(150 / i - (pulse * 30));
            if (alpha < 0) alpha = 0;
            RenderSystem.setShaderColor(r, g, b, alpha / 255.0f);
            RenderUtil.drawOutlineBox(glowBox, poseStack);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (entity instanceof LivingEntity le && le.hurtTime > 0) {
            RenderUtil.drawFilledColoredBox(base, poseStack, new Color(255, 0, 0, 60), new Color(255, 0, 0, 60));
        }
    }

    private void renderEspCorner(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset).inflate(0.1);
        int color = 0xFF00FFFF;
        if (entity instanceof LivingEntity le && le.hurtTime > 0) color = 0xFFFF0000;
        Vec3[] v = {
                new Vec3(base.minX, base.minY, base.minZ), new Vec3(base.maxX, base.minY, base.minZ),
                new Vec3(base.minX, base.maxY, base.minZ), new Vec3(base.maxX, base.maxY, base.minZ),
                new Vec3(base.minX, base.minY, base.maxZ), new Vec3(base.maxX, base.minY, base.maxZ),
                new Vec3(base.minX, base.maxY, base.maxZ), new Vec3(base.maxX, base.maxY, base.maxZ)
        };
        float L = 0.2f;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 8; i++) {
            Vec3 p = v[i];
            float dx = (p.x == base.minX) ? L : -L;
            float dy = (p.y == base.minY) ? L : -L;
            float dz = (p.z == base.minZ) ? L : -L;
            buffer.vertex(matrix, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, 255).endVertex();
            buffer.vertex(matrix, (float)p.x + dx, (float)p.y, (float)p.z).color(r, g, b, 255).endVertex();
            buffer.vertex(matrix, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, 255).endVertex();
            buffer.vertex(matrix, (float)p.x, (float)p.y + dy, (float)p.z).color(r, g, b, 255).endVertex();
            buffer.vertex(matrix, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, 255).endVertex();
            buffer.vertex(matrix, (float)p.x, (float)p.y, (float)p.z + dz).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.lineWidth(1.0f);
    }

    private void renderEspTracer(PoseStack poseStack, Entity entity, float partialTick) {
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.5;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        float playerX = 0.3f, playerY = -0.4f, playerZ = 0.5f;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        float targetX = (float) (x - camPos.x), targetY = (float) (y - camPos.y), targetZ = (float) (z - camPos.z);
        long time = System.currentTimeMillis();
        int color = Color.HSBtoRGB((time % 3000) / 3000f, 0.9f, 1.0f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float width = 0.02f;
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, playerX - width, playerY, playerZ).color(r, g, b, 180).endVertex();
        buffer.vertex(matrix, playerX + width, playerY, playerZ).color(r, g, b, 180).endVertex();
        buffer.vertex(matrix, targetX + width, targetY, targetZ).color(r, g, b, 0).endVertex();
        buffer.vertex(matrix, targetX - width, targetY, targetZ).color(r, g, b, 0).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private void renderEspHalo(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.maxY + 0.3, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        float rotation = (time % 3000) / 3000f * 360f;
        float pulse = (float) (Math.sin(time / 200.0) * 0.2 + 0.8);
        int color = 0xFFFFD700;
        if (entity instanceof LivingEntity le && le.hurtTime > 0) color = 0xFFFF0000;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
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
            buffer.vertex(matrix, (float) (Math.cos(angle) * radius), 0, (float) (Math.sin(angle) * radius)).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.mulPose(Axis.YP.rotationDegrees(-rotation * 1.5f));
        matrix = poseStack.last().pose();
        radius = 0.3f * pulse;
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * Math.PI * 2 / segments);
            buffer.vertex(matrix, (float) (Math.cos(angle) * radius), 0, (float) (Math.sin(angle) * radius)).color(r, g, b, 150).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
    }

    private void renderEspRipple(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY + 0.05, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int segments = 50;
        for (int ring = 0; ring < 3; ring++) {
            float phase = ((time % 2000) / 2000.0f + ring * 0.33f) % 1.0f;
            float radius = phase * 1.5f;
            int alpha = (int) ((1.0f - phase) * 255);
            if (alpha <= 0) continue;
            int color = Color.HSBtoRGB((time % 5000) / 5000f + ring * 0.1f, 0.8f, 1.0f);
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= segments; i++) {
                float angle = (float) (i * Math.PI * 2 / segments);
                buffer.vertex(matrix, (float) (Math.cos(angle) * radius), 0, (float) (Math.sin(angle) * radius)).color(r, g, b, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspWing(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY + (base.maxY - base.minY) * 0.7, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        float flap = (float) Math.sin(time / 150.0) * 0.5f;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() + 90));
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int r = 100, g = 200, b = 255, a = 200;
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 5; i++) {
                float length = 0.6f + i * 0.15f;
                float width = 0.2f + i * 0.1f;
                float yOffset = i * 0.1f + flap * (i * 0.05f);
                buffer.vertex(matrix, 0, 0, 0).color(r, g, b, a).endVertex();
                buffer.vertex(matrix, side * width, yOffset, -length).color(r, g, b, 50).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
    }

    private void renderEspEnergyOrb(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        float rot = (time % 4000) / 4000f * 360f;
        float radius = (float) (Math.max(base.maxX - base.minX, base.maxY - base.minY) * 0.8 + 0.2);
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(rot));
        poseStack.mulPose(Axis.XP.rotationDegrees(rot * 0.5f));
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int color = Color.HSBtoRGB((time % 3000) / 3000f, 0.9f, 1.0f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        for (int lat = -1; lat <= 1; lat++) {
            float yOff = radius * 0.6f * lat;
            float ringRad = (float) Math.sqrt(radius * radius - yOff * yOff);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 40; i++) {
                float angle = (float) (i * Math.PI * 2 / 40);
                buffer.vertex(matrix, (float) (Math.cos(angle) * ringRad), yOff, (float) (Math.sin(angle) * ringRad)).color(r, g, b, 180).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        for (int lon = 0; lon < 4; lon++) {
            float lonAngle = (float) (lon * Math.PI / 2);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 40; i++) {
                float angle = (float) (i * Math.PI * 2 / 40);
                buffer.vertex(matrix, (float) (Math.cos(angle) * radius * Math.cos(lonAngle)), (float) (Math.sin(angle) * radius), (float) (Math.cos(angle) * radius * Math.sin(lonAngle))).color(r, g, b, 180).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspWarp(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY, z = (base.minZ + base.maxZ) / 2.0;
        double height = (base.maxY - base.minY) + 0.5;
        long time = System.currentTimeMillis();
        float rot = (time % 1500) / 1500f * 360f;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(rot));
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float radius = 0.6f;
        for (int arm = 0; arm < 2; arm++) {
            float armOffset = arm * (float) Math.PI;
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 60; i++) {
                float progress = (float) i / 60;
                float py = progress * (float) height;
                float angle = progress * (float) (Math.PI * 6) + armOffset;
                int color = Color.HSBtoRGB((progress * 0.5f + (time % 3000) / 3000f) % 1.0f, 0.9f, 1.0f);
                buffer.vertex(matrix, (float) (Math.cos(angle) * radius), py, (float) (Math.sin(angle) * radius)).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 200).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspLightning(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.maxY + 2.5, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        if (time % 1000 > 600) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int r = 200 + (int)(Math.random() * 55), g = 150 + (int)(Math.random() * 55), b = 255;
        for (int branch = 0; branch < 3; branch++) {
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 10; i++) {
                float progress = (float) i / 10;
                float py = progress * -2.5f;
                float jitterAmt = (1.0f - progress) * 0.4f;
                float px = (i == 10) ? 0 : (float)(Math.random() - 0.5) * jitterAmt * 2.0f;
                float pz = (i == 10) ? 0 : (float)(Math.random() - 0.5) * jitterAmt * 2.0f;
                buffer.vertex(matrix, px, py, pz).color(r, g, b, 255 - (int)(progress*100)).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspCubeField(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int r = 0, g = 255, b = 255;
        for (int i = 0; i < 8; i++) {
            float angle = (float) (i * Math.PI * 2 / 8) + (time % 3000) / 3000f * (float)Math.PI * 2;
            float radius = 0.8f + (float)Math.sin(time / 200.0 + i) * 0.2f;
            float px = (float) (Math.cos(angle) * radius);
            float pz = (float) (Math.sin(angle) * radius);
            float py = (float) Math.sin(time / 300.0 + i * 1.5f) * 0.8f;
            float size = 0.1f;
            buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            buffer.vertex(matrix, px-size, py-size, pz-size).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px+size, py-size, pz-size).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px+size, py-size, pz-size).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px+size, py-size, pz+size).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px+size, py-size, pz+size).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px-size, py-size, pz+size).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px-size, py-size, pz+size).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px-size, py-size, pz-size).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px-size, py+size, pz-size).color(r, g, b, 100).endVertex();
            buffer.vertex(matrix, px+size, py+size, pz-size).color(r, g, b, 100).endVertex();
            buffer.vertex(matrix, px+size, py+size, pz-size).color(r, g, b, 100).endVertex();
            buffer.vertex(matrix, px+size, py+size, pz+size).color(r, g, b, 100).endVertex();
            buffer.vertex(matrix, px+size, py+size, pz+size).color(r, g, b, 100).endVertex();
            buffer.vertex(matrix, px-size, py+size, pz+size).color(r, g, b, 100).endVertex();
            buffer.vertex(matrix, px-size, py+size, pz+size).color(r, g, b, 100).endVertex();
            buffer.vertex(matrix, px-size, py+size, pz-size).color(r, g, b, 100).endVertex();
            buffer.vertex(matrix, px-size, py-size, pz-size).color(r, g, b, 150).endVertex();
            buffer.vertex(matrix, px-size, py+size, pz-size).color(r, g, b, 150).endVertex();
            buffer.vertex(matrix, px+size, py-size, pz-size).color(r, g, b, 150).endVertex();
            buffer.vertex(matrix, px+size, py+size, pz-size).color(r, g, b, 150).endVertex();
            buffer.vertex(matrix, px+size, py-size, pz+size).color(r, g, b, 150).endVertex();
            buffer.vertex(matrix, px+size, py+size, pz+size).color(r, g, b, 150).endVertex();
            buffer.vertex(matrix, px-size, py-size, pz+size).color(r, g, b, 150).endVertex();
            buffer.vertex(matrix, px-size, py+size, pz+size).color(r, g, b, 150).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspVortex(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY + 0.02, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 100; i++) {
            float progress = i / 100f;
            float radius = progress * 1.5f;
            float angle = progress * (float)Math.PI * 8 + (time % 2000) / 2000f * (float)Math.PI * 2;
            int alpha = (int) ((1.0f - progress) * 255);
            buffer.vertex(matrix, (float) (Math.cos(angle) * radius), 0, (float) (Math.sin(angle) * radius)).color(80, 0, 120, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        for (int ring = 0; ring < 2; ring++) {
            float phase = ((time % 1500) / 1500f + ring * 0.5f) % 1.0f;
            float radius = 1.5f * (1.0f - phase);
            int alpha = (int) (phase * 200);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 40; i++) {
                float angle = (float) (i * Math.PI * 2 / 40) + (time % 1000)/1000f * (float)Math.PI;
                buffer.vertex(matrix, (float) (Math.cos(angle) * radius), 0, (float) (Math.sin(angle) * radius)).color(200, 0, 255, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspHexShield(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        float radius = (float) ((base.maxX - base.minX) * 0.8 + 0.3);
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int r = 255, g = 170, b = 0;
        for (int layer = 0; layer < 2; layer++) {
            float rot = (time % 3000) / 3000f * 360f * (layer == 0 ? 1 : -1);
            poseStack.mulPose(Axis.YP.rotationDegrees(rot));
            float rad = radius + layer * 0.15f;
            int alpha = layer == 0 ? 200 : 100;
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 6; i++) {
                float angle = (float) (i * Math.PI / 3);
                buffer.vertex(matrix, (float) (Math.cos(angle) * rad), rad*0.5f, (float) (Math.sin(angle) * rad)).color(r, g, b, alpha).endVertex();
            }
            for (int i = 6; i >= 0; i--) {
                float angle = (float) (i * Math.PI / 3);
                buffer.vertex(matrix, (float) (Math.cos(angle) * rad), -rad*0.5f, (float) (Math.sin(angle) * rad)).color(r, g, b, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspTornado(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY, z = (base.minZ + base.maxZ) / 2.0;
        double height = (base.maxY - base.minY) + 0.5;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int l = 0; l < 4; l++) {
            float lineOffset = (float) (l * Math.PI * 2 / 4);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 40; i++) {
                float progress = (float) i / 40;
                float py = progress * (float) height;
                float currentRadius = 0.1f + progress * 0.9f;
                float angle = lineOffset + progress * (float)Math.PI * 6 + (time % 500) / 500f * (float)Math.PI * 2;
                int color = Color.HSBtoRGB((progress + (time % 2000)/2000f) % 1.0f, 0.8f, 1.0f);
                buffer.vertex(matrix, (float) (Math.cos(angle) * currentRadius), py, (float) (Math.sin(angle) * currentRadius)).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 220).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspPulse(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float baseWidth = (float)(base.maxX - base.minX);
        float baseHeight = (float)(base.maxY - base.minY);
        int r = 255, g = 51, b = 102;
        for (int box = 0; box < 3; box++) {
            float phase = ((time % 1500) / 1500f + box * 0.33f) % 1.0f;
            float scale = 1.0f + phase * 1.5f;
            int alpha = (int) ((1.0f - phase) * 200);
            float w = baseWidth * scale, h = baseHeight * scale, d = w;
            buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            buffer.vertex(matrix, -w/2, -h/2, d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, w/2, -h/2, d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, w/2, h/2, d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, -w/2, h/2, d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, -w/2, -h/2, -d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, w/2, -h/2, -d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, w/2, h/2, -d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, -w/2, h/2, -d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, -w/2, -h/2, d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, -w/2, -h/2, -d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, w/2, -h/2, d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, w/2, -h/2, -d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, w/2, h/2, d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, w/2, h/2, -d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, -w/2, h/2, d/2).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, -w/2, h/2, -d/2).color(r, g, b, alpha).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspRuneRing(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY + 0.02, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int color1 = Color.HSBtoRGB((time % 8000) / 8000f, 0.9f, 1.0f);
        int color2 = Color.HSBtoRGB((time % 8000) / 8000f + 0.33f, 0.9f, 1.0f);
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees((time % 6000) / 6000f * 360f));
        Matrix4f m1 = poseStack.last().pose();
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 72; i++) {
            float angle = (float)(i * Math.PI * 2 / 72);
            buffer.vertex(m1, (float)Math.cos(angle), 0, (float)Math.sin(angle)).color(r1, g1, b1, 220).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 24; i++) {
            float angle = (float)(i * Math.PI * 2 / 24), cx = (float)Math.cos(angle), cz = (float)Math.sin(angle);
            float tickLen = (i % 6 == 0) ? 0.15f : 0.07f;
            buffer.vertex(m1, cx, 0, cz).color(r1, g1, b1, 255).endVertex();
            buffer.vertex(m1, cx * (1 + tickLen), 0, cz * (1 + tickLen)).color(r1, g1, b1, 100).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-(time % 4000) / 4000f * 360f));
        Matrix4f m2 = poseStack.last().pose();
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 48; i++) {
            float angle = (float)(i * Math.PI * 2 / 48);
            buffer.vertex(m2, (float)(Math.cos(angle) * 0.65f), 0, (float)(Math.sin(angle) * 0.65f)).color(r2, g2, b2, 200).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
        Matrix4f m3 = poseStack.last().pose();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 4; i++) {
            float angle = (float)(i * Math.PI / 2 + (time % 2000) / 2000f * Math.PI / 2);
            buffer.vertex(m3, 0, 0, 0).color(r2, g2, b2, 255).endVertex();
            buffer.vertex(m3, (float)Math.cos(angle) * 0.5f, 0, (float)Math.sin(angle) * 0.5f).color(r2, g2, b2, 100).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
    }

    private void renderEspStarOrbit(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int s = 0; s < 6; s++) {
            float baseAngle = (float)(s * Math.PI * 2 / 6);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int t = 0; t < 14; t++) {
                float progress = t / 14f;
                float angle = baseAngle + (time % 4000) / 4000f * (float)(Math.PI * 2) - progress * 0.6f;
                float radius = 1.0f + (float)Math.sin(time / 400.0 + s) * 0.1f;
                int alpha = (int)((1.0f - progress) * 220);
                int color = Color.HSBtoRGB((s / 6f + (time % 5000) / 5000f) % 1.0f, 0.85f, 1.0f);
                buffer.vertex(matrix, (float)(Math.cos(angle) * radius), (float)Math.sin(angle * 2 + s) * 0.15f, (float)(Math.sin(angle) * radius * 0.6f))
                        .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 64; i++) {
            float angle = (float)(i * Math.PI * 2 / 64);
            buffer.vertex(matrix, (float)Math.cos(angle), 0, (float)(Math.sin(angle) * 0.6)).color(120, 120, 255, 60).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
    }

    private void renderEspEnergyFence(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY, z = (base.minZ + base.maxZ) / 2.0;
        double height = (base.maxY - base.minY) + 0.4;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int color = Color.HSBtoRGB((time % 3000) / 3000f, 0.85f, 1.0f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        for (int p = 0; p < 12; p++) {
            float angle = (float)(p * Math.PI * 2 / 12 + (time % 8000) / 8000f * Math.PI * 2);
            float px = (float)(Math.cos(angle) * 0.9f), pz = (float)(Math.sin(angle) * 0.9f), pulseHeight = ((time % 1500) / 1500f) * (float)height;
            buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            buffer.vertex(matrix, px, 0, pz).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px, (float)height, pz).color(r, g, b, 200).endVertex();
            buffer.vertex(matrix, px, pulseHeight, pz).color(255, 255, 255, 255).endVertex();
            buffer.vertex(matrix, px, pulseHeight + 0.15f, pz).color(255, 255, 255, 0).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        }
        for (int cap = 0; cap < 2; cap++) {
            float capY = cap == 0 ? 0 : (float)height;
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 48; i++) {
                float angle = (float)(i * Math.PI * 2 / 48);
                buffer.vertex(matrix, (float)(Math.cos(angle) * 0.9f), capY, (float)(Math.sin(angle) * 0.9f)).color(r, g, b, 255).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspDNA(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY, z = (base.minZ + base.maxZ) / 2.0;
        double height = (base.maxY - base.minY) + 0.5;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float radius = 0.55f, rotationOffset = (time % 3000) / 3000f * (float)(Math.PI * 2);
        for (int strand = 0; strand < 2; strand++) {
            int color = Color.HSBtoRGB((time % 5000) / 5000f + strand * 0.5f, 0.9f, 1.0f);
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 60; i++) {
                float progress = (float)i / 60, py = progress * (float)height;
                float angle = progress * (float)(Math.PI * 6) + strand * (float)Math.PI + rotationOffset;
                buffer.vertex(matrix, (float)(Math.cos(angle) * radius), py, (float)(Math.sin(angle) * radius)).color(r, g, b, 220).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 20; i++) {
            float progress = i / 20f, py = progress * (float)height;
            float angle = progress * (float)(Math.PI * 6) + rotationOffset;
            int color = Color.HSBtoRGB((progress + (time % 4000) / 4000f) % 1.0f, 0.7f, 1.0f);
            buffer.vertex(matrix, (float)(Math.cos(angle) * radius), py, (float)(Math.sin(angle) * radius)).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 180).endVertex();
            buffer.vertex(matrix, (float)(Math.cos(angle + Math.PI) * radius), py, (float)(Math.sin(angle + Math.PI) * radius)).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 180).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
    }

    private void renderEspDataStream(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY, z = (base.minZ + base.maxZ) / 2.0;
        double height = (base.maxY - base.minY) + 1.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int c = 0; c < 8; c++) {
            float angle = (float)(c * Math.PI * 2 / 8);
            float px = (float)(Math.cos(angle)), pz = (float)(Math.sin(angle));
            for (int ch = 0; ch < 8; ch++) {
                float progress = ((time % 3000) / 3000f + ch * 0.1f + c * 0.05f) % 1.0f;
                float cy = (float)(progress * height);
                int alpha = (int)((1.0f - progress) * 220);
                if (((c * 7 + ch * 3) % 2) == 0) {
                    buffer.vertex(matrix, px, cy, pz).color(0, 255, 102, alpha).endVertex();
                    buffer.vertex(matrix, px, cy + 0.12f, pz).color(0, 255, 102, alpha).endVertex();
                } else {
                    buffer.vertex(matrix, px - 0.04f, cy, pz).color(0, 255, 102, alpha).endVertex();
                    buffer.vertex(matrix, px + 0.04f, cy, pz).color(0, 255, 102, alpha).endVertex();
                }
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
    }

    private void renderEspCrossHalo(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.maxY + 0.3, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int color = Color.HSBtoRGB((time % 5000) / 5000f, 0.6f, 1.0f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees((time % 4000) / 4000f * 360f));
        Matrix4f m = poseStack.last().pose();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(m, -0.7f, 0, 0).color(r, g, b, 255).endVertex(); buffer.vertex(m, 0.7f, 0, 0).color(r, g, b, 255).endVertex();
        buffer.vertex(m, 0, 0, -0.7f).color(r, g, b, 255).endVertex(); buffer.vertex(m, 0, 0, 0.7f).color(r, g, b, 255).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
        for (int ring = 0; ring < 2; ring++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees((time % 3000) / 3000f * 360f * (ring == 0 ? 1 : -1)));
            Matrix4f mr = poseStack.last().pose();
            float radius = ring == 0 ? 0.9f : 0.7f;
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 48; i++) {
                float angle = (float)(i * Math.PI * 2 / 48);
                buffer.vertex(mr, (float)(Math.cos(angle) * radius), 0, (float)(Math.sin(angle) * radius)).color(r, g, b, ring == 0 ? 180 : 100).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void renderEspShatter(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int s = 0; s < 10; s++) {
            float phase = ((time % 3000) / 3000f + s / 10f) % 1.0f;
            float angle = (float)(s * Math.PI * 2 / 10) + (time % 6000) / 6000f * (float)Math.PI * 2;
            float dist = 0.5f + phase * 1.2f;
            int alpha = (int)((1.0f - phase) * 255);
            if (alpha <= 0) continue;
            int color = Color.HSBtoRGB((time % 5000) / 5000f + s / 10f, 0.9f, 1.0f);
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            poseStack.pushPose();
            poseStack.translate((float)(Math.cos(angle) * dist), (float)Math.sin(phase * Math.PI * 2) * 0.5f, (float)(Math.sin(angle) * dist));
            poseStack.mulPose(Axis.YP.rotationDegrees(angle * 180f / (float)Math.PI + time * 0.5f));
            Matrix4f ms = poseStack.last().pose();
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 3; i++) {
                float a = (float)(i * Math.PI * 2 / 3);
                buffer.vertex(ms, (float)(Math.cos(a) * 0.1f), 0, (float)(Math.sin(a) * 0.1f)).color(r, g, b, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void renderEspCrystal(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees((time % 6000) / 6000f * 360f));
        poseStack.mulPose(Axis.XP.rotationDegrees((time % 4000) / 4000f * 360f * 0.5f));
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float radius = (float)(Math.max(base.maxX - base.minX, base.maxY - base.minY) * 0.9 + 0.3);
        int[][] edges = {{0, 2}, {0, 3}, {0, 4}, {0, 5}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 4}, {4, 3}, {3, 5}, {5, 2}};
        float[][] verts = {{0, radius, 0}, {0, -radius, 0}, {radius, 0, 0}, {-radius, 0, 0}, {0, 0, radius}, {0, 0, -radius}};
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int[] e : edges) {
            buffer.vertex(matrix, verts[e[0]][0], verts[e[0]][1], verts[e[0]][2]).color(170, 230, 255, 220).endVertex();
            buffer.vertex(matrix, verts[e[1]][0], verts[e[1]][1], verts[e[1]][2]).color(170, 230, 255, 220).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        float pulse = (float)(Math.sin(time / 200.0) * 0.1 + 1.0);
        float innerR = radius * 0.5f * pulse;
        float[][] innerVerts = {{0, innerR, 0}, {0, -innerR, 0}, {innerR, 0, 0}, {-innerR, 0, 0}, {0, 0, innerR}, {0, 0, -innerR}};
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int[] e : edges) {
            buffer.vertex(matrix, innerVerts[e[0]][0], innerVerts[e[0]][1], innerVerts[e[0]][2]).color(200, 245, 255, 180).endVertex();
            buffer.vertex(matrix, innerVerts[e[1]][0], innerVerts[e[1]][1], innerVerts[e[1]][2]).color(200, 245, 255, 180).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
    }

    private void renderEspMagnetic(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(25f));
        poseStack.mulPose(Axis.YP.rotationDegrees((time % 8000) / 8000f * 360f));
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int r = 51, g = 204, b = 255;
        for (int fl = 0; fl < 8; fl++) {
            float theta = (float)(fl * Math.PI * 2 / 8);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 40; i++) {
                float t = (float)i / 40;
                float angle = t * (float)Math.PI;
                float px = (float)(Math.sin(angle) * Math.cos(theta));
                float pz = (float)(Math.sin(angle) * Math.sin(theta));
                float py = (float)(Math.cos(angle));
                float bulge = (float)Math.sin(angle);
                px *= 1.0f + bulge * 0.3f;
                pz *= 1.0f + bulge * 0.3f;
                int alpha = (int)(150 + Math.sin(angle) * 50);
                buffer.vertex(matrix, px, py, pz).color(r, g, b, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

    private void renderEspTimeLoop(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = base.minY + 0.03, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int r = 255, g = 215, b = 0;
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 60; i++) {
            float angle = (float)(i * Math.PI * 2 / 60);
            float cx = (float)Math.cos(angle);
            float cz = (float)Math.sin(angle);
            boolean isHour = (i % 5 == 0);
            float inner = isHour ? 0.85f : 0.92f;
            buffer.vertex(matrix, cx, 0, cz).color(r, g, b, 220).endVertex();
            buffer.vertex(matrix, cx * inner, 0, cz * inner).color(r, g, b, isHour ? 255 : 120).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 64; i++) {
            float angle = (float)(i * Math.PI * 2 / 64);
            buffer.vertex(matrix, (float)Math.cos(angle), 0, (float)Math.sin(angle)).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        float secAngle = (time % 60000) / 60000f * (float)Math.PI * 2 - (float)Math.PI / 2;
        float minAngle = (time % 3600000) / 3600000f * (float)Math.PI * 2 - (float)Math.PI / 2;
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0, 0, 0).color(255, 80, 80, 255).endVertex();
        buffer.vertex(matrix, (float)Math.cos(secAngle) * 0.9f, 0, (float)Math.sin(secAngle) * 0.9f).color(255, 80, 80, 255).endVertex();
        buffer.vertex(matrix, 0, 0, 0).color(r, g, b, 255).endVertex();
        buffer.vertex(matrix, (float)Math.cos(minAngle) * 0.65f, 0, (float)Math.sin(minAngle) * 0.65f).color(r, g, b, 255).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        poseStack.popPose();
    }

    private void renderEspSoulChain(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int r = 170, g = 0, b = 255;
        float radius = 0.9f;
        int linkCount = 16;
        float rotateSpeed = (time % 4000) / 4000f * 360f;
        for (int tilt = -1; tilt <= 1; tilt += 2) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(tilt * 20f));
            poseStack.mulPose(Axis.YP.rotationDegrees(rotateSpeed * tilt));
            for (int l = 0; l < linkCount; l++) {
                float angle = (float)(l * Math.PI * 2 / linkCount);
                poseStack.pushPose();
                poseStack.translate((float)Math.cos(angle) * radius, 0, (float)Math.sin(angle) * radius);
                poseStack.mulPose(Axis.YP.rotationDegrees(angle * 180f / (float)Math.PI));
                poseStack.mulPose(Axis.ZP.rotationDegrees(90));
                Matrix4f ml = poseStack.last().pose();
                buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                for (int i = 0; i <= 12; i++) {
                    float la = (float)(i * Math.PI * 2 / 12);
                    buffer.vertex(ml, (float)Math.cos(la) * 0.08f, (float)Math.sin(la) * 0.12f, 0).color(r, g, b, 220).endVertex();
                }
                BufferUploader.drawWithShader(buffer.end());
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void renderEspGalaxy(PoseStack poseStack, Entity entity, float partialTick, Vec3 offset) {
        AABB base = EntityUtil.getInterpolatedAABB(entity, partialTick).move(offset);
        double x = (base.minX + base.maxX) / 2.0, y = (base.minY + base.maxY) / 2.0, z = (base.minZ + base.maxZ) / 2.0;
        long time = System.currentTimeMillis();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees((time % 10000) / 10000f * 360f));
        poseStack.mulPose(Axis.XP.rotationDegrees(15f));
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int arm = 0; arm < 4; arm++) {
            float armOffset = (float)(arm * Math.PI * 2 / 4);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 60; i++) {
                float t = (float)i / 60;
                float angle = armOffset + t * (float)Math.PI * 3;
                float radius = t * 1.2f;
                float px = (float)(Math.cos(angle) * radius);
                float pz = (float)(Math.sin(angle) * radius);
                float py = (float)Math.sin(angle * 2) * 0.1f * (1 - t);
                int color = Color.HSBtoRGB((t * 0.5f + (time % 6000) / 6000f) % 1.0f, 0.9f, 1.0f - t * 0.3f);
                int alpha = (int)((1.0f - t * 0.7f) * 230);
                buffer.vertex(matrix, px, py, pz).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        int color = Color.HSBtoRGB((time % 4000) / 4000f, 0.8f, 1.0f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        for (int ring = 0; ring < 3; ring++) {
            float ringR = (0.08f + ring * 0.04f) * (float)(Math.sin(time / 200.0 + ring) * 0.3 + 1.0);
            buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= 20; i++) {
                float angle = (float)(i * Math.PI * 2 / 20);
                buffer.vertex(matrix, (float)Math.cos(angle) * ringR, 0, (float)Math.sin(angle) * ringR).color(r, g, b, (int)(255 * (1 - ring * 0.3f))).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }
        poseStack.popPose();
    }

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
            r = (color >> 16) & 0xFF; g = (color >> 8) & 0xFF; b = color & 0xFF;
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
                default -> { r = 0; g = 255; b = 0; }
            }
        }

        poseStack.pushPose();
        poseStack.translate(mc.player.getX() - cameraPos.x(), mc.player.getY() + 0.05 - cameraPos.y(), mc.player.getZ() - cameraPos.z());
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
            buffer.vertex(matrix, (float) (Math.cos(angle) * radius), 0, (float) (Math.sin(angle) * radius)).color(r, g, b, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        if (this.rangeCircleFilled.getValue()) {
            int fillAlpha = Math.max(1, alpha / 3);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < segments; i++) {
                float a1 = i * angleStep, a2 = (i + 1) * angleStep;
                float x1 = (float) (Math.cos(a1) * radius), z1 = (float) (Math.sin(a1) * radius);
                float x2 = (float) (Math.cos(a2) * radius), z2 = (float) (Math.sin(a2) * radius);
                buffer.vertex(matrix, 0, 0, 0).color(r, g, b, fillAlpha).endVertex();
                buffer.vertex(matrix, 0, 0, 0).color(r, g, b, fillAlpha).endVertex();
                buffer.vertex(matrix, x2, 0, z2).color(r, g, b, fillAlpha).endVertex();
                buffer.vertex(matrix, x1, 0, z1).color(r, g, b, fillAlpha).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }

        if (this.rangeCircleMarkTargets.getValue() && !targetList.isEmpty()) {
            if (target != null && targetList.contains(target)) {
                renderTargetDot(matrix, buffer, target, radius, 0xFFFF0000);
            }
            for (Entity entity : targetList) {
                if (entity != target) {
                    renderTargetDot(matrix, buffer, entity, radius, 0xFFFF8800);
                }
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
    }

    private void renderTargetDot(Matrix4f matrix, BufferBuilder buffer, Entity entity, float radius, int color) {
        if (mc.player == null) return;
        Vec3 toTarget = entity.position().subtract(mc.player.position());
        double angle = Math.atan2(toTarget.z, toTarget.x);
        float xOff = (float) (Math.cos(angle) * radius);
        float zOff = (float) (Math.sin(angle) * radius);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;

        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, xOff, 0, zOff).color(r, g, b, 120).endVertex();
        for (int i = 0; i <= 16; i++) {
            float dotAngle = (float) (2.0 * Math.PI * i / 16);
            buffer.vertex(matrix, xOff + (float) (Math.cos(dotAngle) * 0.25f), 0, zOff + (float) (Math.sin(dotAngle) * 0.25f)).color(r, g, b, 0).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, xOff, 0, zOff).color(r, g, b, 255).endVertex();
        for (int i = 0; i <= 12; i++) {
            float dotAngle = (float) (2.0 * Math.PI * i / 12);
            buffer.vertex(matrix, xOff + (float) (Math.cos(dotAngle) * 0.06f), 0, zOff + (float) (Math.sin(dotAngle) * 0.06f)).color(r, g, b, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
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
                    moduleList.getThemeColor(0, 0.0f, 3), moduleList.getThemeColor(1, 0.33f, 3),
                    moduleList.getThemeColor(2, 0.67f, 3), moduleList.getThemeColor(3, 1.0f, 3)
            };
            float size = 0.75f;
            float[][] corners = {
                    {-size, size, 0.0f, 0.0f}, {size, size, 1.0f, 0.0f},
                    {size, -size, 1.0f, 1.0f}, {-size, -size, 0.0f, 1.0f}
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

    private static void ensureNurikCaptureTexture() {
        if (nurikTextureLoaded || nurikTextureLoadFailed) return;
        try (InputStream inputStream = Assets.open(NURIK_CAPTURE_ASSET)) {
            if (inputStream == null) {
                nurikTextureLoadFailed = true;
                return;
            }
            mc.getTextureManager().register(NURIK_CAPTURE_TEXTURE, new DynamicTexture(NativeImage.read(inputStream)));
            nurikTextureLoaded = true;
        } catch (IOException exception) {
            nurikTextureLoadFailed = true;
        }
    }

    // ==========================================
    // === 攻击核心逻辑 =========================
    // ==========================================

    @EventTarget
    public void onTick(TickEvent event) {
        if (!NiloreClient.isReady()) return;

        if (mc.screen != null && !this.attackInInventory.getValue()) {
            target = null; aimingTarget = null; this.currentBestHit = null; this.rotation = null;
            this.aimRotation = null; this.prevBestHit = null; targetList.clear();
            this.sprintTickCounter = 0; this.attacks = 0.0f; this.sprintCounter = 0;
            return;
        }

        if (ItemUtil.hasServerItem()
                || (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled() && !this.style.is("onTickRot"))
                || (Stuck.INSTANCE != null && Stuck.INSTANCE.isEnabled())
                || (Helper.INSTANCE != null && Helper.INSTANCE.isEnabled() && Helper.targetRotation != null)
                || AntiWeb.targetRotation != null || AntiTNT.targetRotation != null || MidPearl.targetRotation != null
                || this.isWebPlacing()) {
            target = null; aimingTarget = null; this.currentBestHit = null; this.rotation = null;
            this.aimRotation = null; this.prevBestHit = null; targetList.clear();
            this.sprintTickCounter = 0; this.attacks = 0.0f; this.sprintCounter = 0;
            return;
        }

        boolean isSwitch = this.targetMode.is("Switch");
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
                Rotation desiredAimRotation = this.currentBestHit != null && this.currentBestHit.rotation() != null
                        ? this.currentBestHit.rotation() : RotationUtil.entityRotation(aimingTarget);
                this.aimRotation = desiredAimRotation;

                if (desiredAimRotation != null) {
                    if (this.style.is("onTickRot")) {
                        this.rotation = null;
                    } else {
                        Rotation from = RotationHandler.prevRotation != null
                                ? RotationHandler.prevRotation
                                : new Rotation(mc.player.getYRot(), mc.player.getXRot());
                        double speed = Mth.clamp(this.rotationSpeed.getValue().doubleValue(),
                                this.rotationSpeed.getMin().doubleValue(), this.rotationSpeed.getMax().doubleValue());
                        double rayRange = Math.max(this.getEffectiveAttackRange(), this.reach.getValue().doubleValue());
                        this.rotation = this.computeRotation(aimingTarget, from, speed, Math.max(rayRange, 3.05));
                        if (this.rotation == null) this.rotation = desiredAimRotation;
                    }
                } else {
                    this.rotation = null; this.aimRotation = null;
                }
            } else {
                this.rotation = null; this.aimRotation = null; this.currentBestHit = null;
            }
        } else {
            this.rotation = null; this.aimRotation = null; this.currentBestHit = null;
        }

        this.prevBestHit = this.currentBestHit;

        if (targetList.isEmpty()) {
            target = null;
            return;
        }
        if (this.targetIndex > targetList.size() - 1) this.targetIndex = 0;

        float aimRangeValue = this.aimRange.getValue().floatValue();
        if (isSwitch && targetList.size() > 1 && (this.attackTimes >= this.switchDelay.getValue().intValue()
                || (this.currentBestHit != null && this.currentBestHit.distance() > aimRangeValue))) {
            this.attackTimes = 0;
            for (int i = 0; i < targetList.size(); ++i) {
                ++this.targetIndex;
                if (this.targetIndex > targetList.size() - 1) this.targetIndex = 0;
                Entity nextTarget = targetList.get(this.targetIndex);
                if (this.getRangeDistance(nextTarget) <= aimRangeValue) break;
            }
        }
        if (this.targetIndex > targetList.size() - 1 || !isSwitch) this.targetIndex = 0;
        target = targetList.get(this.targetIndex);

        // ===== 破盾状态机：只负责"等待、恢复"，真正的破盾逻辑在 onPreMotion =====
        if (this.autoShieldBreaker.getValue() && this.isBreakingShield) {
            if (this.shieldBreakerSwitchTick > 0) this.shieldBreakerSwitchTick--;
            if (this.shieldBreakRestoreDelay > 0) {
                if (--this.shieldBreakRestoreDelay == 0) {
                    restoreSlot();
                }
            } else if (this.shieldBreakerSwitchTick == 0 && (target == null || !isTargetBlocking(target))) {
                restoreSlot();
            }
        }

        if (this.delayMode.is("1.9") && mc.player.getAttackStrengthScale(0.0f) < 0.95f) {
            this.attacks = 0.0f;
            return;
        }

        if (this.style.is("Old")) {
            float apsValue = this.maxAps.getValue().floatValue();
            float minApsValue = this.minAps.getValue().floatValue();
            if (AntiKBMode.isAttacking) {
                int kbAttackAmount = Velocity.INSTANCE != null ? Velocity.INSTANCE.attackAmount.getValue().intValue() : 0;
                apsValue -= kbAttackAmount;
                minApsValue -= kbAttackAmount;
            }
            this.attacks += (float)(MathUtil.randomDouble(minApsValue, apsValue) / 20.0);
        } else {
            float apsValue = this.maxAps.getValue().floatValue();
            float minApsValue = this.minAps.getValue().floatValue();
            if (AntiKBMode.isAttacking) {
                int kbAttackAmount = Velocity.INSTANCE != null ? Velocity.INSTANCE.attackAmount.getValue().intValue() : 0;
                apsValue -= kbAttackAmount;
                minApsValue -= kbAttackAmount;
            }
            this.attacks += (float)(MathUtil.randomDouble(minApsValue, apsValue) / 20.0);
        }
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mc.player == null) return;
        if (this.isWebPlacing()) {
            this.attacks = 0.0f;
            return;
        }

        // ===== 破盾：进入 onPreMotion 阶段处理切换 → 等待 → 攻击 → 延迟切回 =====
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
            if (!offhand.isEmpty() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) return;
            float cooldown = mc.player.getAttackStrengthScale(0.5f);
            if (cooldown >= 0.9f && this.attacks >= 1.0f) {
                if (this.critHold()) return;
                if (this.isAimReadyToAttack() && this.doAttack()) this.attacks -= 1.0f;
            }
            this.attacks += 0.1f;
            if (this.attacks > 2.0f) this.attacks = 2.0f;
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
                    if (this.attacks > 1.0f) this.attacks = 1.0f;
                    return;
                }
                if (this.critHold()) {
                    if (this.attacks > 1.0f) this.attacks = 1.0f;
                    return;
                }
                while (this.attacks >= 1.0f) {
                    if (!this.doAttack()) break;
                    this.attacks -= 1.0f;
                }
            } else {
                this.attacks = 0.0f;
            }
        } else {
            this.attacks = 0.0f;
        }
    }

    private boolean canAttackNow() {
        if (mc.player == null || mc.screen != null || mc.player.isUsingItem()) return false;
        if (target == null || !targetList.contains(target)) return false;
        double reachNow = Math.max(this.reach.getValue().doubleValue(), RotationUtil.getReachLimit());
        if (!this.ignoreBlocksAt(target) && !RotationUtil.isLookingAt(target, reachNow, 0.1f)) return false;
        if (this.delayMode.is("1.9") && mc.player.getAttackStrengthScale(0.0f) < 0.95f) return false;
        if (!(this.ignoreSkipTicks.getValue() || ClientBase.delayPackets.isEmpty()
                || (Critical.INSTANCE != null && Critical.INSTANCE.isEnabled()))) return false;

        if (ReduceXZMode.isCountering()) return false;
        if (ReduceXZMode.isInDelayWindow() && shouldKeepSprintInDelayWindow()) return false;
        if (KeepSprint.INSTANCE != null && KeepSprint.INSTANCE.isEnabled() && mc.player.isSprinting() && !ReduceXZMode.handlingVelocity) {
            return false;
        }
        return this.isWithinReach(target);
    }

    private boolean ignoreBlocksAt(Entity entity) {
        if (!this.throughWalls.getValue() || entity == null || mc.player == null) return false;
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        double dist = RotationUtil.closestPoint(eyePos, entity.getBoundingBox()).distanceTo(eyePos);
        return dist <= this.throughWallsRange.getValue().doubleValue();
    }

    public static boolean shouldKeepSprintInDelayWindow() {
        if (mc.player == null || mc.level == null) return false;
        if (mc.player.onGround() || ReduceXZMode.isCountering()) return true;
        if (mc.player.getDeltaMovement().y > 0 || mc.player.fallDistance <= 0.0f) return false;
        return new MotionSimulator(mc.player).findLandingBlock(1) != null;
    }

    public boolean canCritUnderVelocity() {
        if (mc.player == null || mc.level == null || target == null) return false;
        if (Velocity.INSTANCE != null && Velocity.INSTANCE.isEnabled()) {
            if (ReduceXZMode.isInDelayWindow() && shouldKeepSprintInDelayWindow()) return false;
            if (ReduceXZMode.isCountering()) return false;
            if (Velocity.mode.is("JumpReset") && JumpResetMode.isJumping) return false;
        }
        if (target.distanceToSqr(mc.player) > 12.25) return false;
        if (mc.player.isDeadOrDying() || KeepSprint.INSTANCE == null || !KeepSprint.INSTANCE.isEnabled()) return false;
        return !mc.player.isUsingItem() || !mc.options.keyUse.isDown();
    }

    @EventTarget
    public void onSprint(SprintEvent sprintEvent) {
        if (!this.canCritUnderVelocity()) return;
        mc.options.keySprint.setDown(false);
        if (mc.player != null && mc.player.isSprinting()) mc.player.setSprinting(false);
    }

    public static boolean attackAndLock() {
        long tick = mc.level != null ? mc.level.getGameTime() : (mc.player != null ? mc.player.tickCount : 0);
        if (tick == attackLockTick) return false;
        attackLockTick = tick;
        return true;
    }

    public boolean doAttack() {
        if (this.isWebPlacing()) { this.attacks = 0.0f; return false; }
        if (targetList.isEmpty()) return false;
        if (this.rotation == null && !this.style.is("onTickRot")) return false;
        if (!this.style.is("onTickRot") && !this.isAimReadyToAttack()) return false;

        if (this.autoShieldBreaker.getValue() && target != null && isTargetBlocking(target)) {
            if (performShieldBreakAttack(target)) return true;
        }

        HitResult hitResult;
        if ((this.style.is("Old") || this.style.is("onTickRot")) && (Boolean) this.overrideRaycast.getValue() && this.aimRotation != null) {
            hitResult = RotationUtil.performRaycast(this.aimRotation, true);
        } else {
            hitResult = mc.hitResult;
        }
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (AntiBots.isBot(hitEntity)) return false;
        }

        if (this.targetMode.is("Multiple") || this.multiAttack.getValue()) {
            int limit = this.targetMode.is("Multiple") ? Integer.MAX_VALUE : 2;
            int attacked = 0;
            for (Entity entity : targetList) {
                if (mc.player == null) break;
                // 改为使用本地 isWithinReach（真正命中距离用 reach 判定）
                if (!this.isWithinReach(entity)) continue;
                if (!this.ignoreBlocksAt(entity) && !RotationUtil.canSeeAnyPoint(entity)) continue;
                if (this.attackEntity(entity)) attacked++;
                if (attacked >= limit) break;
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
            if (!list.isEmpty()) entity = list.get(0);
        }
        if (entity != null) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && AntiBots.isBot(entity)) return null;
        }
        return entity;
    }

    public void updateTargets() {
        List<Entity> next = this.getTargets();
        targetList = next != null ? next : new ArrayList<>();
        if (targetList.isEmpty()) {
            target = null; this.targetIndex = 0; return;
        }
        if (this.targetIndex < 0 || this.targetIndex >= targetList.size()) this.targetIndex = 0;
        if (target != null && !targetList.contains(target)) target = null;
    }

    public boolean isValidTarget(Entity entity) {
        if (!NiloreClient.isReady()) return false;
        if (entity == mc.player) return false;
        if (entity instanceof LivingEntity livingEntity) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) return false;
            if (livingEntity.isDeadOrDying() || livingEntity.getHealth() <= 0.0f) return false;
            if (entity instanceof ArmorStand) return false;
            if (entity.isInvisible() && !(Boolean) this.attackInvisible.getValue()) return false;
            if (entity instanceof Player player) {
                if (this.test.getValue() && player.getY() >= mc.player.getY() + 0.05f) return true;
            }
            if (Teams.isSameTeam(entity)) return false;
            if (entity instanceof Player && !(Boolean) this.attackPlayer.getValue()) return false;
            if (entity instanceof Player && (entity.getBbWidth() < 0.5 || livingEntity.isSleeping())) return false;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem)
                    && !(Boolean) this.attackMobs.getValue()) return false;
            if ((entity instanceof Animal || entity instanceof Squid) && !(Boolean) this.attackAnimals.getValue()) return false;
            if (entity instanceof Villager && !(Boolean) this.attackAnimals.getValue()) return false;
            return !(entity instanceof Player) || !entity.isSpectator();
        }
        return false;
    }

    public boolean isValidAttack(Entity entity) {
        if (mc.player == null) return false;
        if (!this.isValidTarget(entity)) return false;
        if (entity instanceof LivingEntity le && le.hurtTime > this.hurtTime.getValue().intValue()) return false;

        double dist = this.getRangeDistance(entity);
        float maxAimRange = this.aimRange.getValue().floatValue();
        if (dist > maxAimRange) {
            if (!(Boolean) this.predictionEnabled.getValue() || this.predictDistance(entity) >= maxAimRange) return false;
        }

        if ((this.style.is("New") || this.style.is("onTickRot")) && mc.level != null) {
            boolean skipWallCheck = this.throughWalls.getValue() && dist <= this.throughWallsRange.getValue().floatValue();
            if (!skipWallCheck && !this.hasVisibleHitPoint(entity)) return false;
        }

        return this.isInFov(entity);
    }

    private boolean isInFov(Entity entity) {
        if (!this.fovEnabled.getValue()) return true;
        float fov = this.fovValue.getValue().floatValue();
        if (fov >= 360.0f) return true;
        if (fov <= 0.0f) return false;
        if (mc.player == null || entity == null) return false;

        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 target = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        Vec3 dir = target.subtract(eye);
        if (dir.lengthSqr() < 1.0E-6) return true;

        Vec3 look = mc.player.getLookAngle();
        double dot = look.normalize().dot(dir.normalize());
        double angle = Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));
        return angle <= fov / 2.0;
    }

    private boolean hasVisibleHitPoint(Entity entity) {
        if (mc.player == null || mc.level == null || entity == null) return false;
        Vec3 eye = mc.player.getEyePosition(1.0f);
        AABB box = entity.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5, centerZ = (box.minZ + box.maxZ) * 0.5;
        double height = Math.max(0.01, box.maxY - box.minY);
        Vec3 center = new Vec3(centerX, box.minY + height * 0.50, centerZ);
        Vec3 chest = new Vec3(centerX, box.minY + height * 0.72, centerZ);
        Vec3 head = new Vec3(centerX, Math.min(box.maxY - 0.05, entity.getEyeY() - 0.03), centerZ);
        Vec3 closest = new Vec3(
                Mth.clamp(eye.x, box.minX + 0.01, box.maxX - 0.01),
                Mth.clamp(eye.y, box.minY + 0.05, box.maxY - 0.05),
                Mth.clamp(eye.z, box.minZ + 0.01, box.maxZ - 0.01)
        );
        return this.isPointVisible(eye, chest) || this.isPointVisible(eye, head)
                || this.isPointVisible(eye, center) || this.isPointVisible(eye, closest);
    }

    private boolean isPointVisible(Vec3 eye, Vec3 point) {
        if (mc.level == null) return false;
        if (eye.distanceToSqr(point) <= 1.0E-6) return true;
        BlockHitResult blockHit = mc.level.clip(new ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (blockHit.getType() == HitResult.Type.MISS) return true;
        return blockHit.getLocation().distanceToSqr(point) <= 0.04;
    }

    private double getRangeDistance(Entity entity) {
        if (mc.player == null || entity == null) return Double.MAX_VALUE;
        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double dx = eye.x - Mth.clamp(eye.x, box.minX, box.maxX);
        double dy = eye.y - Mth.clamp(eye.y, box.minY, box.maxY);
        double dz = eye.z - Mth.clamp(eye.z, box.minZ, box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 有效攻击范围（挥刀范围）。真正命中距离由 isWithinReach() 使用 reach 控制。
     * 1.9 模式强制 3.0。
     */
    private float getEffectiveAttackRange() {
        if (this.use1_9Cooldown.getValue()) return FORCED_1_9_RANGE;
        return Math.max(this.attackRange.getValue().floatValue(), this.reach.getValue().floatValue());
    }

    private boolean isWithinAttackRange(Entity entity) {
        return this.getRangeDistance(entity) <= this.getEffectiveAttackRange();
    }

    /**
     * 真正命中距离：由 Reach 设置独立控制。
     * 距离超过 Reach 时会挥刀但不会发包（不命中），符合"reach 才是真正的攻击距离"。
     */
    private boolean isWithinReach(Entity entity) {
        if (mc.player == null || entity == null) return false;
        return this.getRangeDistance(entity) <= this.reach.getValue().floatValue();
    }

    private double predictDistance(Entity entity) {
        double selfDelayMs = 0.0;
        if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) {
            selfDelayMs = mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
        }
        double selfDelayTicks = Math.min(selfDelayMs / 50.0, this.selfDelayThreshold.getValue().doubleValue());
        double enemyDelayMs = 0.0;
        if (entity instanceof Player player && mc.getConnection() != null && mc.getConnection().getPlayerInfo(player.getUUID()) != null) {
            enemyDelayMs = mc.getConnection().getPlayerInfo(player.getUUID()).getLatency();
        }
        double enemyDelayTicks = Math.min(enemyDelayMs / 50.0, this.enemyDelayThreshold.getValue().doubleValue());
        double totalTicks = 2.0 + selfDelayTicks + enemyDelayTicks;
        double predictedPlayerX = mc.player.getX() + (mc.player.getX() - mc.player.xOld) * totalTicks;
        double predictedPlayerZ = mc.player.getZ() + (mc.player.getZ() - mc.player.zOld) * totalTicks;
        double predictedEnemyX = entity.getX() + (entity.getX() - entity.xOld) * totalTicks;
        double predictedEnemyZ = entity.getZ() + (entity.getZ() - entity.zOld) * totalTicks;
        double dx = predictedEnemyX - predictedPlayerX, dz = predictedEnemyZ - predictedPlayerZ;
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
            if (!offhand.isEmpty() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) return false;
        }

        if (!this.use1_9Cooldown.getValue()) return this.attackEntity1_8(entity);
        if (this.style.is("onTickRot")) return this.attackEntityNewFix(entity);

        float currentYaw = mc.player.getYRot(), currentPitch = mc.player.getXRot();
        if (!this.silentMode.getValue() && RotationHandler.targetRotation != null) {
            mc.player.setYRot(RotationHandler.targetRotation.getYaw());
            mc.player.setXRot(RotationHandler.targetRotation.getPitch());
        }
        ++this.attackTimes;
        int attackKey = mc.options.keyAttack.getKey().getValue();
        if (canActuallyHit) attackWithKeepSprint(entity);
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
        if (!this.attackCooldown.getValue() && !this.isBreakingShield) mc.player.resetAttackStrengthTicker();
        if (this.delayMode.is("1.9")) this.sprintCounter = (int) mc.player.getCurrentItemAttackStrengthDelay();
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
            if (!offhand.isEmpty() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) return false;
        }
        if (this.style.is("onTickRot")) {
            if (mc.getConnection() == null) return false;
            float origYaw = mc.player.getYRot(), origPitch = mc.player.getXRot();
            Rotation targetRot = this.aimRotation;
            if (targetRot == null) return false;
            float jitter1 = rotationJitter(), jitter2 = rotationJitter(), jitter3 = rotationJitter();
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), targetRot.getYaw() + jitter1, targetRot.getPitch() + jitter1, mc.player.onGround()));
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), targetRot.getYaw() + jitter2, targetRot.getPitch() + jitter2, mc.player.onGround()));
            ++this.attackTimes;
            if (canActuallyHit) attackWithKeepSprint(entity);
            mc.player.swing(InteractionHand.MAIN_HAND);
            if (canActuallyHit && this.morePart.getValue()) { mc.player.magicCrit(entity); mc.player.crit(entity); }
            if (!this.attackCooldown.getValue()) mc.player.resetAttackStrengthTicker();
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), origYaw + jitter3, origPitch + jitter3, mc.player.onGround()));
            return true;
        }
        float currentYaw = mc.player.getYRot(), currentPitch = mc.player.getXRot();
        if (!this.silentMode.getValue() && RotationHandler.targetRotation != null) {
            mc.player.setYRot(RotationHandler.targetRotation.getYaw());
            mc.player.setXRot(RotationHandler.targetRotation.getPitch());
        }
        ++this.attackTimes;
        int attackKey = mc.options.keyAttack.getKey().getValue();
        if (canActuallyHit) attackWithKeepSprint(entity);
        ForgeHooksClient.onMouseButtonPre(attackKey, 1, 0);
        mc.player.swing(InteractionHand.MAIN_HAND);
        ForgeHooksClient.onMouseButtonPost(attackKey, 1, 0);
        if (canActuallyHit && this.morePart.getValue()) { mc.player.magicCrit(entity); mc.player.crit(entity); }
        if (this.silentMode.getValue()) {
            mc.player.setYRot(currentYaw);
            mc.player.setXRot(currentPitch);
        }
        if (!this.attackCooldown.getValue()) mc.player.resetAttackStrengthTicker();
        if (this.delayMode.is("1.9")) this.sprintCounter = (int) mc.player.getCurrentItemAttackStrengthDelay();
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
            if (!offhand.isEmpty() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) return false;
        }
        float origYaw = mc.player.getYRot(), origPitch = mc.player.getXRot();
        Rotation targetRot = this.aimRotation;
        if (targetRot == null) return false;
        float jitter1 = rotationJitter(), jitter2 = rotationJitter(), jitter3 = rotationJitter();
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), targetRot.getYaw() + jitter1, targetRot.getPitch() + jitter1, mc.player.onGround()));
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), targetRot.getYaw() + jitter2, targetRot.getPitch() + jitter2, mc.player.onGround()));
        ++this.attackTimes;
        if (canActuallyHit) attackWithKeepSprint(entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (canActuallyHit && this.morePart.getValue()) { mc.player.magicCrit(entity); mc.player.crit(entity); }
        if (!this.attackCooldown.getValue()) mc.player.resetAttackStrengthTicker();
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), origYaw + jitter3, origPitch + jitter3, mc.player.onGround()));
        return true;
    }

    private void attackWithKeepSprint(Entity entity) {
        if (entity == null || mc.player == null || mc.gameMode == null) return;
        if (!attackAndLock()) return;

        if (KeepSprint.INSTANCE != null && KeepSprint.INSTANCE.isEnabled()) {
            KeepSprint.INSTANCE.attack(entity);
        } else {
            mc.gameMode.attack(mc.player, entity);
        }
    }

    private float rotationJitter() {
        float offset = (float) (Math.random() * 0.04 + 0.03);
        return Math.random() > 0.5 ? offset : -offset;
    }

    private boolean critHold() { return this.critHold(target); }

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
        if (mc.player == null || mc.level == null) return new ArrayList<>();

        double searchRange = Math.max(
                Math.max(this.aimRange.getValue().doubleValue(), this.attackRange.getValue().doubleValue()),
                this.reach.getValue().doubleValue()
        ) + 0.5;
        AABB box = mc.player.getBoundingBox().inflate(searchRange);

        List<Entity> possibleTargets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!box.intersects(entity.getBoundingBox())) continue;
            if (!this.isValidAttack(entity)) continue;
            possibleTargets.add(entity);
        }

        if (this.priorityMode.is("Distance")) possibleTargets.sort(BY_DISTANCE);
        else if (this.priorityMode.is("FoV")) possibleTargets.sort(BY_ANGLE);
        else if (this.priorityMode.is("Health")) possibleTargets.sort(BY_HEALTH);

        if (this.preferBaby.getValue() && hasBaby(possibleTargets)) possibleTargets.removeIf(KillAura::isNotBaby);
        possibleTargets.sort(BY_CRYSTAL_PRIORITY);
        possibleTargets.sort(BY_OUT_OF_REACH);

        if (this.infSwitch.getValue() || this.targetMode.is("Multiple")) return possibleTargets;
        int limit = (int) Math.min(possibleTargets.size(), this.switchSize.getValue().intValue());
        return new ArrayList<>(possibleTargets.subList(0, limit));
    }

    private static Integer getCrystalPriority(Entity entity) { return entity instanceof EndCrystal ? 0 : 1; }
    private static boolean isNotBaby(Entity entity) { return !(entity instanceof LivingEntity) || !((LivingEntity) entity).isBaby(); }
    private static boolean isBaby(Entity entity) { return entity instanceof LivingEntity && ((LivingEntity) entity).isBaby(); }
    private static boolean hasBaby(List<Entity> entities) {
        for (Entity entity : entities) if (isBaby(entity)) return true;
        return false;
    }

    private static final Comparator<Entity> BY_DISTANCE = Comparator.comparingDouble(KillAura::getDistanceToPlayer);
    private static final Comparator<Entity> BY_ANGLE = Comparator.comparingDouble(KillAura::getAngleDiffToTarget);
    private static final Comparator<Entity> BY_HEALTH = Comparator.comparingDouble(KillAura::getEntityHealth);
    private static final Comparator<Entity> BY_CRYSTAL_PRIORITY = Comparator.comparing(KillAura::getCrystalPriority);
    private static final Comparator<Entity> BY_OUT_OF_REACH = Comparator.comparingInt(KillAura::getOutOfReachRank);

    private static int getOutOfReachRank(Entity entity) { return RotationUtil.isWithinReach(entity) ? 0 : 1; }
    private static double getEntityHealth(Entity entity) { return entity instanceof LivingEntity le ? le.getHealth() + le.getAbsorptionAmount() : 0.0; }
    private static double getAngleDiffToTarget(Entity entity) {
        float baseYaw = RotationHandler.targetRotation != null ? RotationHandler.targetRotation.getYaw() : mc.player.getYRot();
        return RotationUtil.angleDiff(baseYaw, RotationUtil.entityRotation(entity).getYaw());
    }
    private static double getDistanceToPlayer(Entity entity) { return entity.distanceTo(mc.player); }

    // ==========================================
    // === 破盾 (Auto Shield Breaker) ===========
    // ==========================================

    private boolean isTargetBlocking(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        if (!living.isUsingItem()) return false;
        return living.getUseItem().getItem() instanceof ShieldItem;
    }

    private int findAxeSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() instanceof AxeItem) return i;
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
        this.shieldBreakRestoreDelay = 0;
    }

    private void cancelAttackCooldownForShieldBreak() {
        if (mc.player != null) mc.player.resetAttackStrengthTicker();
    }

    /**
     * 破盾攻击。
     *
     * 流程（保证 C09 切换包先于 C02 攻击包发出）：
     *   1) 未拿斧头 → 切到斧头槽 → switchTick = 2 → 本 tick 结束
     *   2) 拿斧头但 switchTick > 0 → 等待（onTick 递减）
     *   3) switchTick == 0 → 发攻击（attackWithKeepSprint）
     *   4) 攻击后：switchBack=true → shieldBreakRestoreDelay = 2 → 2 tick 后切回
     *            switchBack=false → 保持斧头（下次直接攻击）
     * 破盾最小间隔由 Shield Breaker CPS 控制，真正生效。
     */
    private boolean performShieldBreakAttack(Entity entity) {
        if (mc.player == null || mc.gameMode == null || entity == null) return false;
        if (!isTargetBlocking(entity)) return false;
        if (!this.isWithinReach(entity)) return false;

        long currentTime = System.currentTimeMillis();
        float cps = Mth.clamp(this.shieldBreakerCPS.getValue().floatValue(), 1.0f, 20.0f);
        long cooldownMs = (long) (1000.0f / cps);
        if (currentTime - this.lastShieldBreakTime < cooldownMs) return false;

        // 第 1 步：没拿斧头，切换，下一 tick 才能攻击
        if (!(mc.player.getMainHandItem().getItem() instanceof AxeItem)) {
            int axeSlot = findAxeSlot();
            if (axeSlot == -1) return false;
            if (this.previousSlot == -1) this.previousSlot = mc.player.getInventory().selected;
            mc.player.getInventory().selected = axeSlot;
            this.isBreakingShield = true;
            this.shieldBreakerSwitchTick = 2;
            return false;
        }

        // 第 2 步：等待切换包发出
        if (this.shieldBreakerSwitchTick > 0) return false;
        if (this.shieldBreakRestoreDelay > 0) return false;

        // 第 3 步：攻击
        cancelAttackCooldownForShieldBreak();
        attackWithKeepSprint(entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        cancelAttackCooldownForShieldBreak();

        this.lastShieldBreakTime = currentTime;

        // 第 4 步：延迟切回
        if (this.switchBackAfterBreak.getValue()) {
            this.shieldBreakRestoreDelay = 2;
        } else {
            this.isBreakingShield = false;
        }
        return true;
    }
}