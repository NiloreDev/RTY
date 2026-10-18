package client.nilore.modules.impl.combat;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.EntityRemoveEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PlayerTickEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.ReduceXZMode;
import client.nilore.modules.impl.combat.antikb.NavenVelocityMode;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.FightManager;
import client.nilore.utils.game.RotationUtil;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;

/**
 * Critical —— 暴击辅助 (融合版)
 * GrimAC 等同于 Grim，使用 PostMotion 发包确保疾跑释放，并支持 Auto Jump。
 */
public class Critical extends Module {

    public static Critical INSTANCE;

    public static volatile boolean stopAttack = false;

    // ==================== 模式 ====================
    public final ModeSetting mode = new ModeSetting("Mode",
            "None", "Normal", "GrimAC", "GrimTimer", "Stuck", "1.9+", "SkipTicks", "Optimize")
            .withDefault("GrimAC");

    // ==================== GrimTimer 设置 ====================
    public final BooleanSetting grimTimerAutoJump =
            new BooleanSetting("GrimTimer Auto Jump", true,
                    () -> this.mode.is("GrimTimer"));
    public final NumberSetting grimTimerIdleTime =
            new NumberSetting("GrimTimer Idle Time", 250.0f, 10.0f, 500.0f, 10.0f,
                    () -> this.mode.is("GrimTimer"));
    public final NumberSetting grimTimerDownHeight =
            new NumberSetting("GrimTimer Down Height", 2.0f, 1.0f, 10.0f, 1.0f,
                    () -> this.mode.is("GrimTimer"));
    public final BooleanSetting grimTimerSendC03 =
            new BooleanSetting("GrimTimer Send C03", true,
                    () -> this.mode.is("GrimTimer"));

    private static final double GRIM_TIMER_MAX_FALL_DISTANCE = 3.0D;
    private static final float DEFAULT_MS_PER_TICK = 50.0F;

    // ==================== SkipTicks / Optimize 设置 ====================
    public final NumberSetting skipTicksRange =
            new NumberSetting("SkipTicks Range", 3.0, 1.0, 6.0, 0.1,
                    () -> this.mode.is("SkipTicks"));

    // 【修改】Auto Jump 改为全局可见，所有模式都能开，作用是在打人时保持疾跑
    public final BooleanSetting autoJump =
            new BooleanSetting("Auto Jump", false);

    // ==================== 通用 debug 开关 ====================
    public final BooleanSetting debug = new BooleanSetting("Debug", false);

    // ==================== 通用状态 ====================
    public int fallTicks;
    private boolean stopSprinting;

    // ==================== SkipTicks / Optimize / Grim 状态 ====================
    private LivingEntity enemy;
    private boolean isAttack;
    private boolean isSprint;
    private boolean again;
    private boolean balance;
    private int skipTicks;

    // ==================== 1.9+ ====================
    public final NumberSetting tolerance = new NumberSetting("Tolerance", 2.0,
            0.1, 3.0, 0.1, () -> this.mode.is("1.9+"));

    // ==================== Stuck ====================
    public final NumberSetting waitTicks = new NumberSetting("WaitTicks", 1.0,
            1.0, 3.0, 1.0, () -> this.mode.is("Stuck"));

    public final BooleanSetting fallDistance = new BooleanSetting("FallDistance", false,
            () -> this.mode.is("Stuck"));

    private float lastCritDamage;

    public Critical() {
        super("Critical", Category.COMBAT);
        INSTANCE = this;
    }

    // ==================== 生命周期 ====================
    @Override
    public void onEnable() {
        super.onEnable();
        this.fallTicks = 0;
        this.stopSprinting = false;
        this.clearState();
    }

    @Override
    public void onDisable() {
        this.fallTicks = 0;
        this.stopSprinting = false;
        this.clearState();

        restoreTimer();
        if (mc != null && mc.options != null) {
            mc.options.keyJump.setDown(false);
        }

        super.onDisable();
    }

    private void clearState() {
        this.enemy = null;
        this.isAttack = false;
        this.isSprint = false;
        this.again = false;
        this.balance = false;
        this.skipTicks = 0;
        stopAttack = false;
    }

    // ==================== 事件入口 ====================
    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (this.mode.is("Normal")) {
            this.tickNormal();
        } else if (this.mode.is("GrimTimer")) {
            this.tickGrimTimer();
        } else if (this.mode.is("Stuck") || this.mode.is("1.9+")) {
            this.tickAdvanced();
        }
        // GrimAC 和 Optimize 的状态机已被移至 onPostMotion
    }

    /**
     * 版本 2 的 Grim / Optimize 逻辑：在 Post 阶段释放疾跑。
     */
    @EventTarget
    public void onPostMotion(MotionEvent event) {
        if (mc.player == null || !event.isPost()) {
            return;
        }
        if (this.enemy == null) {
            return;
        }

        if (this.mode.is("GrimAC")) {
            if (this.enemy.hurtTime < 2 || this.enemy.distanceTo(mc.player) > 4.0) {
                this.enemy = null;
                this.isAttack = false;
                return;
            }
            if (this.isAttack && !mc.player.onGround() && mc.player.fallDistance > 0.0f) {
                this.releaseSprint();
                this.isSprint = true;
            }
        } else if (this.mode.is("Optimize")) {
            if (this.enemy.hurtTime < 2 || this.enemy.distanceTo(mc.player) > 4.0) {
                this.enemy = null;
                this.isAttack = false;
                stopAttack = false;
                return;
            }
            double velocityY = mc.player.getDeltaMovement().y;
            stopAttack = !mc.player.onGround() && velocityY > 0.0 && velocityY < 0.16;
            if (this.isAttack && this.isFalling()) {
                this.releaseSprint();
                this.debugLog("Balance.");
                this.balance = true;
            }
        }
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mc.player == null) return;
        if (this.mode.is("SkipTicks")) {
            this.updateSkipTicks();
        } else if (this.mode.is("Optimize")) {
            this.optimizeExtraAttack();
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null) return;

        if (this.mode.is("Normal") && this.stopSprinting) {
            this.stopSprinting = false;

            if (this.fallTicks > 0
                    && this.fallTicks < 3
                    && this.canCritNormal()
                    && mc.player.isSprinting()
                    && KillAura.target != null
                    && event.getForward() > 0.0f) {
                mc.player.setSprinting(false);
                mc.options.keySprint.setDown(false);
            }
            return;
        }

        // 【新增】版本 2 的 Auto Jump 逻辑：只要开了 Auto Jump，且在地面、疾跑、有攻击目标，就保持疾跑
        if (this.autoJump.getValue()) {
            if (mc.player.onGround() && mc.player.isSprinting() && this.isAttack) {
                event.setSprinting(true);
            }
        }
    }

    @EventTarget
    public void onAttack(EntityRemoveEvent event) {
        if (mc.player == null || event.dead()) {
            return;
        }

        if (this.mode.is("SkipTicks")) {
            if (mc.player.fallDistance > 0.0f && !mc.player.isSprinting()
                    && event.entity() instanceof LivingEntity living && living.hurtTime < 2) {
                this.debugLog("Crit.");
            }
            return;
        }

        // 版本 2 的 Grim 攻击记录逻辑
        if (!(event.entity() instanceof LivingEntity living)) {
            this.enemy = null;
            this.isAttack = false;
            this.isSprint = false;
            return;
        }

        if (mc.player.isSprinting()) {
            this.enemy = living;
            this.isAttack = true;
        }

        if (mc.player.fallDistance > 0.0f && !mc.player.isSprinting() && this.isAttack && this.isSprint) {
            this.isAttack = false;
            this.isSprint = false;
            if (this.mode.is("Optimize")) {
                stopAttack = false;
            }

            // 【保留第1个的Debug输出】
            if (this.mode.is("GrimAC") && this.debug.getValue()) {
                this.grimDebugLog(living);
            } else {
                this.debugLog("Crit.");
            }
        }
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent event) {
        if (!this.consumeSkipTick()) return;
        event.setCancelled(true);
    }

    public boolean consumeSkipTick() {
        if (!this.isEnabled() || !this.mode.is("SkipTicks") || this.skipTicks <= 0) {
            return false;
        }
        this.skipTicks--;
        return true;
    }

    // ==================== 模式专属逻辑 ====================
    private void tickNormal() {
        if (mc.player == null || !canCritNormal() || mc.player.fallDistance >= 1.0f) {
            fallTicks = 0;
        } else {
            fallTicks++;
        }

        stopSprinting = mc.player != null
                && fallTicks > 0
                && fallTicks < 3
                && mc.player.isSprinting()
                && KillAura.target != null;
    }

    private void tickGrimTimer() {
        if (mc.player == null) return;

        if (KillAura.target != null && mc.player.onGround()) {
            if (this.grimTimerAutoJump.getValue()) {
                mc.options.keyJump.setDown(true);
            }
            setMsPerTick(getTimer(), DEFAULT_MS_PER_TICK);
        }

        if (KillAura.target != null && !mc.player.onGround()) {
            mc.options.keySprint.setDown(false);

            double fallDistance = mc.player.fallDistance;
            double maxFallDistance = GRIM_TIMER_MAX_FALL_DISTANCE;

            if (fallDistance > 0 && fallDistance < maxFallDistance / 2.0D) {
                setMsPerTick(getTimer(), this.grimTimerIdleTime.getValue().floatValue());
                if (this.grimTimerSendC03.getValue()) {
                    sendC03Packet(true);
                }
            } else if (fallDistance >= maxFallDistance / this.grimTimerDownHeight.getValue().floatValue()) {
                setMsPerTick(getTimer(), DEFAULT_MS_PER_TICK);
            }
            if (this.grimTimerSendC03.getValue()) {
                sendC03Packet(false);
            }
        }

        if (mc.player.onGround() || mc.player.hurtTime > 0) {
            setMsPerTick(getTimer(), DEFAULT_MS_PER_TICK);
        }
    }

    private void tickAdvanced() {
        if (mc.player == null) return;
        if (this.isReleaseWindow()) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }
    }

    private void updateSkipTicks() {
        if (mc.player == null) return;

        Entity target = KillAura.target;
        if (target == null) return;
        if (this.cantCrit(target)) {
            this.skipTicks = 0;
            return;
        }
        boolean auraActive = KillAura.INSTANCE != null && KillAura.INSTANCE.isEnabled();
        if (mc.player.getDeltaMovement().y < 0.0
                && !mc.player.onGround()
                && auraActive
                && mc.player.distanceTo(target) <= this.skipTicksRange.getValue().floatValue()) {
            if (this.skipTicks <= 0) {
                this.skipTicks++;
            }
        } else if (!auraActive) {
            this.skipTicks = 0;
        }
    }

    private void optimizeExtraAttack() {
        if (mc.player == null) return;
        if (!this.isAttack || !this.balance || !this.isFalling() || mc.player.isSprinting()) return;

        this.again = false;
        stopAttack = false;
        LivingEntity current = this.enemy;
        if (current != null && this.isLookingAt(current, 4.0)) {
            if (FightManager.attackAndLock()) {
                mc.gameMode.attack(mc.player, current);
                mc.player.resetAttackStrengthTicker();
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                this.again = true;
            }
        }
        if (!this.again) {
            this.isSprint = true;
            this.balance = false;
            this.debugLog("Crit.");
        }
    }

    // ==================== 通用工具 ====================
    private void releaseSprint() {
        if (mc.player == null) return;
        mc.player.setSprinting(false);
        mc.options.keyShift.setDown(false);
    }

    private boolean canPrepareCritical() {
        return mc.player != null
                && !mc.player.onGround()
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.isPassenger()
                && !mc.player.getAbilities().flying;
    }

    private boolean isFalling() {
        return this.canPrepareCritical() && mc.player.fallDistance > 0.0f;
    }

    private boolean cantCrit(Entity target) {
        if (mc.player == null) return true;
        if (!(target instanceof LivingEntity living)) return true;
        return mc.player.onClimbable()
                || mc.player.isInWater()
                || mc.player.isInLava()
                || mc.player.isPassenger()
                || living.hurtTime > 10
                || living.getHealth() <= 0.0f;
    }

    private boolean isLookingAt(Entity entity, double range) {
        if (mc.player == null) return false;
        Rotation rotation = RotationHandler.targetRotation != null
                ? RotationHandler.targetRotation
                : new Rotation(mc.player.getYRot(), mc.player.getXRot());
        return RotationUtil.rayTraceForAim(rotation, range) instanceof EntityHitResult hit
                && hit.getEntity() == entity;
    }

    private boolean canCritNormal() {
        if (mc.player == null) return false;
        return mc.player.fallDistance > 0.0
                && !mc.player.onGround()
                && !mc.player.isPassenger()
                && !mc.player.isSleeping()
                && !mc.player.isFallFlying()
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.isInLava();
    }

    private void debugLog(String message) {
        if (this.debug.getValue() && mc.player != null && mc.level != null) {
            ChatUtil.print("[Critical] " + message);
        }
    }

    // ==================== 【核心】GrimAC 只输出伤害 ====================
    private void grimDebugLog(LivingEntity target) {
        if (mc.player == null || mc.level == null) return;
        if (!this.debug.getValue()) return;

        float damage = this.computeCurrentDamage();

        ChatUtil.print(String.format(
                "§c[Velocity] §4[Critical] §fDamage: %.2f",
                damage));
    }

    private float computeCurrentDamage() {
        if (mc.player == null) return 0f;
        float base = (float) mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float charge = mc.player.getAttackStrengthScale(0.5f);
        float damage = base * (0.2f + charge * charge * 0.8f);
        damage *= 1.5f;
        return damage;
    }

    // ==================== GrimTimer 辅助 ====================
    private void sendC03Packet(boolean onGround) {
        if (mc.getConnection() == null || mc.player == null) return;
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYRot(),
                mc.player.getXRot(),
                onGround
        ));
    }

    public static Timer getTimer() {
        if (mc == null) return null;
        try {
            Field timerField = Minecraft.class.getDeclaredField("timer");
            timerField.setAccessible(true);
            return (Timer) timerField.get(mc);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setMsPerTick(Timer timer, float msPerTick) {
        if (timer == null) return;
        try {
            Field msPerTickField = Timer.class.getDeclaredField("msPerTick");
            msPerTickField.setAccessible(true);
            msPerTickField.setFloat(timer, msPerTick);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void restoreTimer() {
        Timer timer = getTimer();
        if (timer != null) {
            setMsPerTick(timer, DEFAULT_MS_PER_TICK);
        }
    }

    // ==================== 1.9+ 逻辑 ====================
    public boolean isReleaseWindow() {
        if (ReduceXZMode.handlingVelocity) return false;
        if (NavenVelocityMode.handlingVelocity) return false;
        if (mc.player == null) return false;

        Entity target = KillAura.target;
        if (!(target instanceof LivingEntity living)) return false;

        if (mc.player.onGround()) return false;
        if (mc.player.isInWater() || mc.player.isInLava()) return false;
        if (mc.player.isUsingItem()) return false;
        if (mc.player.isShiftKeyDown()) return false;
        if (mc.player.isFallFlying()) return false;
        if (mc.player.isPassenger()) return false;
        if (mc.player.onClimbable()) return false;
        if (mc.player.hasEffect(MobEffects.BLINDNESS)) return false;
        if (mc.player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) return false;
        if (mc.player.hasEffect(MobEffects.LEVITATION)) return false;

        int hurtTime = living.hurtTime;
        return hurtTime >= 7 || hurtTime <= 3;
    }

    public boolean shouldCritEntity(Entity entity) {
        if (!this.isEnabled() || !this.mode.is("1.9+") || mc.player == null) return false;
        if (!(entity instanceof LivingEntity living)) return false;

        float damage = critDamage();
        if (living.hurtTime > 0 && damage <= lastCritDamage) return false;
        if (isCritBlocked(false)) return false;

        double velocityY = mc.player.getDeltaMovement().y;
        if (velocityY < -0.08) {
            lastCritDamage = damage;
            return false;
        }

        float chargeGap = Math.max(0.0f,
                (0.95f - mc.player.getAttackStrengthScale(0.5f))
                        * mc.player.getCurrentItemAttackStrengthDelay());

        float window = Math.max(chargeGap, (float) (velocityY / 0.08));
        return window <= tolerance.getValue().floatValue()
                && !blockedAhead((int) (window * 1.3f));
    }

    public boolean holdAttack(Entity entity) {
        if (!this.isEnabled() || !this.mode.is("1.9+")) return false;
        return mc.player != null
                && mc.player.getAttackStrengthScale(0.0f) < 0.95f
                || shouldCritEntity(entity);
    }

    private float critDamage() {
        if (mc.player == null) return -1.0f;
        float base = (float) mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float charge = mc.player.getAttackStrengthScale(0.5f);
        float damage = base * (0.2f + charge * charge * 0.8f);
        if (!isCritBlocked(false) && mc.player.getDeltaMovement().y < -0.08) damage *= 1.5f;
        return damage;
    }

    private boolean isCritBlocked(boolean allowAir) {
        if (mc.player == null) return true;
        if (mc.player.hasEffect(MobEffects.BLINDNESS)
                || mc.player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                || mc.player.hasEffect(MobEffects.LEVITATION)) return true;
        if (mc.player.isUsingItem() || mc.player.isInWater()
                || mc.player.isShiftKeyDown() || mc.player.isFallFlying()
                || mc.player.isPassenger()) return true;
        return !allowAir && mc.player.onGround() || mc.player.onClimbable();
    }

    private boolean blockedAhead(int ticks) {
        if (mc.player == null || mc.level == null) return false;
        Vec3 pos = mc.player.position();
        Vec3 velocity = mc.player.getDeltaMovement();
        AABB box = mc.player.getBoundingBox();

        for (int i = 0; i < ticks; i++) {
            pos = pos.add(velocity);
            velocity = new Vec3(velocity.x * 0.91,
                    (velocity.y - 0.08) * 0.98,
                    velocity.z * 0.91);
            if (!mc.level.noCollision(mc.player, box.move(
                    pos.x - mc.player.getX(),
                    pos.y - mc.player.getY(),
                    pos.z - mc.player.getZ())))
                return true;
        }
        return false;
    }

    // ==================== 显示 ====================
    @Override
    public String getSuffix() {
        return this.mode.getValue();
    }

    @Override
    public String getDisplayName() {
        String m = this.mode.getValue();
        if (m == null || m.isEmpty()) return "§fCritical";
        return "§fCritical §7[" + m + "]";
    }

    @Override
    public String getModuleName() {
        return "Critical";
    }

    @Override
    public void onTick() {
    }
}