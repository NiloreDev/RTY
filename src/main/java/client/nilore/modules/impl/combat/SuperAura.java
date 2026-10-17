package client.nilore.modules.impl.combat;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HundredMeterAura
 *
 * 一个独立的超远距离 Aura 模块。
 *
 * 功能：
 * - 默认 100 格搜索/攻击范围
 * - 最大 256 格
 * - Players / Monsters / Animals
 * - Through Walls
 * - Auto Rotate
 * - APS
 * - Distance / Health / Angle 排序
 * - Single / Switch 模式
 *
 * 注意：
 * 客户端可以选中和尝试攻击超远目标，
 * 但正常服务器可能会拒绝超过服务端允许距离的攻击。
 */
public class SuperAura extends Module {

    public static SuperAura INSTANCE;

    // =========================================================
    // RANGE
    // =========================================================

    public final NumberSetting range = new NumberSetting(
            "Range",
            100.0,
            3.0,
            256.0,
            1.0
    );

    // =========================================================
    // TARGETS
    // =========================================================

    public final BooleanSetting players = new BooleanSetting(
            "Players",
            true
    );

    public final BooleanSetting monsters = new BooleanSetting(
            "Monsters",
            false
    );

    public final BooleanSetting animals = new BooleanSetting(
            "Animals",
            false
    );

    public final BooleanSetting invisibles = new BooleanSetting(
            "Invisibles",
            false
    );

    public final BooleanSetting throughWalls = new BooleanSetting(
            "Through Walls",
            true
    );

    // =========================================================
    // ATTACK
    // =========================================================

    public final NumberSetting aps = new NumberSetting(
            "APS",
            8.0,
            1.0,
            20.0,
            1.0
    );

    public final ModeSetting targetMode = new ModeSetting(
            "Target Mode",
            "Single",
            "Switch"
    ).withDefault("Single");

    public final ModeSetting sortMode = new ModeSetting(
            "Sort",
            "Distance",
            "Health",
            "Angle"
    ).withDefault("Distance");

    public final BooleanSetting swing = new BooleanSetting(
            "Swing",
            true
    );

    // =========================================================
    // ROTATION
    // =========================================================

    public final BooleanSetting autoRotate = new BooleanSetting(
            "Auto Rotate",
            true
    );

    public final NumberSetting rotationSpeed = new NumberSetting(
            "Rotation Speed",
            180.0,
            1.0,
            360.0,
            5.0,
            this.autoRotate::getValue
    );

    // =========================================================
    // STATE
    // =========================================================

    private LivingEntity currentTarget;

    private long lastAttackTime = 0L;

    private int switchIndex = 0;

    private long lastSwitchTime = 0L;

    public SuperAura() {
        super("SuperAura", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getDisplayName() {
        if (this.currentTarget == null) {
            return "§fHundredMeterAura";
        }

        double distance = mc.player != null
                ? mc.player.distanceTo(this.currentTarget)
                : 0.0;

        return "§fHundredMeterAura §7["
                + String.format("%.1f", distance)
                + "m]";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getSuffix() {
        return "[" + (int) this.range.getValue().doubleValue() + "m]";
    }

    @Override
    public void onEnable() {
        this.currentTarget = null;
        this.lastAttackTime = 0L;
        this.switchIndex = 0;
        this.lastSwitchTime = 0L;

        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.currentTarget = null;

        super.onDisable();
    }

    // =========================================================
    // TICK
    // =========================================================

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null
                || mc.level == null
                || mc.gameMode == null) {
            this.currentTarget = null;
            return;
        }

        List<LivingEntity> targets = this.collectTargets();

        if (targets.isEmpty()) {
            this.currentTarget = null;
            return;
        }

        this.sortTargets(targets);

        if (this.targetMode.is("Switch")) {
            this.currentTarget = this.selectSwitchTarget(targets);
        } else {
            this.currentTarget = targets.get(0);
        }

        if (this.currentTarget == null) {
            return;
        }

        if (this.autoRotate.getValue()) {
            this.rotateToTarget(this.currentTarget);
        }

        if (this.canAttack()) {
            this.attack(this.currentTarget);
        }
    }

    // =========================================================
    // TARGET COLLECTION
    // =========================================================

    private List<LivingEntity> collectTargets() {
        List<LivingEntity> result = new ArrayList<>();

        if (mc.player == null || mc.level == null) {
            return result;
        }

        double maxRange = this.range.getValue().doubleValue();
        double maxRangeSq = maxRange * maxRange;

        for (Entity entity : mc.level.entitiesForRendering()) {

            if (!(entity instanceof LivingEntity living)) {
                continue;
            }

            if (living == mc.player) {
                continue;
            }

            if (!living.isAlive()) {
                continue;
            }

            if (living.isRemoved()) {
                continue;
            }

            if (living.getHealth() <= 0.0f) {
                continue;
            }

            if (!this.invisibles.getValue()
                    && living.isInvisible()) {
                continue;
            }

            if (!this.isAllowedType(living)) {
                continue;
            }

            double distanceSq = mc.player.distanceToSqr(living);

            if (distanceSq > maxRangeSq) {
                continue;
            }

            if (!this.throughWalls.getValue()
                    && !mc.player.hasLineOfSight(living)) {
                continue;
            }

            result.add(living);
        }

        return result;
    }

    private boolean isAllowedType(LivingEntity entity) {

        if (entity instanceof Player) {
            return this.players.getValue();
        }

        if (entity instanceof Monster) {
            return this.monsters.getValue();
        }

        if (entity instanceof Animal) {
            return this.animals.getValue();
        }

        return false;
    }

    // =========================================================
    // SORT
    // =========================================================

    private void sortTargets(List<LivingEntity> targets) {

        if (this.sortMode.is("Health")) {

            targets.sort(
                    Comparator.comparingDouble(
                            LivingEntity::getHealth
                    )
            );

            return;
        }

        if (this.sortMode.is("Angle")) {

            targets.sort(
                    Comparator.comparingDouble(
                            this::getAngleDifference
                    )
            );

            return;
        }

        // Distance
        targets.sort(
                Comparator.comparingDouble(
                        entity -> mc.player.distanceToSqr(entity)
                )
        );
    }

    // =========================================================
    // SWITCH MODE
    // =========================================================

    private LivingEntity selectSwitchTarget(
            List<LivingEntity> targets
    ) {

        if (targets.isEmpty()) {
            return null;
        }

        long now = System.currentTimeMillis();

        /*
         * 每约 450ms 换一个目标。
         * 避免每 tick 都切换导致目标抖动。
         */
        if (now - this.lastSwitchTime >= 450L) {

            this.lastSwitchTime = now;

            this.switchIndex++;

            if (this.switchIndex >= targets.size()) {
                this.switchIndex = 0;
            }
        }

        if (this.switchIndex >= targets.size()) {
            this.switchIndex = 0;
        }

        return targets.get(this.switchIndex);
    }

    // =========================================================
    // ATTACK
    // =========================================================

    private boolean canAttack() {

        double attacksPerSecond =
                Math.max(1.0, this.aps.getValue().doubleValue());

        long delay =
                (long) (1000.0 / attacksPerSecond);

        return System.currentTimeMillis()
                - this.lastAttackTime
                >= delay;
    }

    private void attack(LivingEntity target) {

        if (target == null
                || mc.player == null
                || mc.gameMode == null) {
            return;
        }

        this.lastAttackTime =
                System.currentTimeMillis();

        /*
         * 发起客户端正常攻击调用。
         *
         * 服务器是否接受 100+ 格距离攻击，
         * 由服务器本身的攻击距离校验决定。
         */
        mc.gameMode.attack(
                mc.player,
                target
        );

        if (this.swing.getValue()) {
            mc.player.swing(
                    InteractionHand.MAIN_HAND
            );
        }
    }

    // =========================================================
    // ROTATION
    // =========================================================

    private void rotateToTarget(LivingEntity target) {

        if (target == null
                || mc.player == null) {
            return;
        }

        Vec3 eye = mc.player.getEyePosition();

        /*
         * 瞄准目标身体中上部，
         * 比固定脚底更稳定。
         */
        Vec3 targetPos = new Vec3(
                target.getX(),
                target.getY()
                        + target.getBbHeight() * 0.65,
                target.getZ()
        );

        double dx = targetPos.x - eye.x;
        double dy = targetPos.y - eye.y;
        double dz = targetPos.z - eye.z;

        double horizontal =
                Math.sqrt(dx * dx + dz * dz);

        float wantedYaw =
                (float) Math.toDegrees(
                        Math.atan2(dz, dx)
                ) - 90.0f;

        float wantedPitch =
                (float) -Math.toDegrees(
                        Math.atan2(dy, horizontal)
                );

        Rotation current =
                RotationHandler.targetRotation;

        if (current == null) {
            current = new Rotation(
                    mc.player.getYRot(),
                    mc.player.getXRot()
            );
        }

        float speed =
                this.rotationSpeed.getValue().floatValue();

        float yawDifference =
                wrapDegrees(
                        wantedYaw - current.getYaw()
                );

        float pitchDifference =
                wantedPitch - current.getPitch();

        float yawChange =
                clamp(
                        yawDifference,
                        -speed,
                        speed
                );

        float pitchChange =
                clamp(
                        pitchDifference,
                        -speed,
                        speed
                );

        Rotation rotation =
                new Rotation(
                        current.getYaw() + yawChange,
                        current.getPitch() + pitchChange
                );

        RotationHandler.setTargetRotation(
                rotation
        );
    }

    // =========================================================
    // ANGLE SORT
    // =========================================================

    private double getAngleDifference(
            LivingEntity entity
    ) {

        if (mc.player == null) {
            return Double.MAX_VALUE;
        }

        double dx =
                entity.getX()
                        - mc.player.getX();

        double dz =
                entity.getZ()
                        - mc.player.getZ();

        float targetYaw =
                (float) Math.toDegrees(
                        Math.atan2(dz, dx)
                ) - 90.0f;

        return Math.abs(
                wrapDegrees(
                        targetYaw
                                - mc.player.getYRot()
                )
        );
    }

    // =========================================================
    // MATH
    // =========================================================

    private static float wrapDegrees(
            float value
    ) {

        value %= 360.0f;

        if (value >= 180.0f) {
            value -= 360.0f;
        }

        if (value < -180.0f) {
            value += 360.0f;
        }

        return value;
    }

    private static float clamp(
            float value,
            float min,
            float max
    ) {

        if (value < min) {
            return min;
        }

        if (value > max) {
            return max;
        }

        return value;
    }

    // =========================================================
    // ACCESS
    // =========================================================

    public LivingEntity getCurrentTarget() {
        return this.currentTarget;
    }

    public double getCurrentTargetDistance() {

        if (this.currentTarget == null
                || mc.player == null) {
            return -1.0;
        }

        return mc.player.distanceTo(
                this.currentTarget
        );
    }
}
