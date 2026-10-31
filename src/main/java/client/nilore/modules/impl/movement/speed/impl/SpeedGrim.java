package client.nilore.modules.impl.movement.speed.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.MotionEvent;
import client.nilore.modules.impl.movement.SpeedAccess;
import client.nilore.modules.impl.movement.SpeedGrimCollide;
import client.nilore.modules.impl.movement.SpeedModule;
import client.nilore.modules.impl.movement.TargetStrafe;
import client.nilore.modules.impl.movement.speed.SpeedMode;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.MovementUtil;

/**
 * Grim 模式：实体碰撞加速。
 */
public class SpeedGrim extends SpeedMode {
    private static final double COLLISION_EXPAND = 0.2;
    private static final double BASE_BOOST = 0.065;
    private static final double SPEED_LIMIT = 0.34;

    public final BooleanSetting onlyInAir = new BooleanSetting(
            "Only Air", true, () -> SpeedModule.is(this));
    public final BooleanSetting strafeBoost = new BooleanSetting(
            "Strafe Boost", true, () -> SpeedModule.is(this));

    // 【修改点 1】Boost 滑块最大上限由 0.08 调整为 0.3
    public final NumberSetting boost = new NumberSetting(
            "Boost", BASE_BOOST, 0.005, 0.3, 0.005, () -> SpeedModule.is(this));

    // 【修改点 2】Speed Limit 滑块最大上限由 1.0 调整为 3.0
    public final NumberSetting speedLimit = new NumberSetting(
            "Speed Limit", SPEED_LIMIT, 0.20, 3.0, 0.01, () -> SpeedModule.is(this));

    public SpeedGrim() {
        super("Grim");
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (motionEvent.isPost()
                || mc.player == null
                || mc.level == null
                || !MovementUtil.isInputActive()
                || (this.onlyInAir.getValue() && mc.player.onGround())) {
            return;
        }

        int contacts = this.countContacts();
        if (contacts <= 0) {
            return;
        }

        Vec3 motion = mc.player.getDeltaMovement();
        SpeedAccess.Collision collision = SpeedAccess.Collision.of(contacts, motion.x, motion.z);

        double yawRad = MovementUtil.getMovementYaw();
        if (this.strafeBoost.getValue() && TargetStrafe.isOrbiting()) {
            yawRad = TargetStrafe.orbitYawRad();
        }

        double limitValue = this.speedLimit.getValue().doubleValue();
        double increment = SpeedGrimCollide.increment(collision, (float) yawRad,
                this.boost.getValue().doubleValue(),
                limitValue);
        if (increment <= 0.0) {
            return;
        }

        double currentSpeed = Math.hypot(motion.x, motion.z);
        double finalSpeed = Math.min(currentSpeed + increment, limitValue);
        MovementUtil.strafeWithYaw(yawRad, finalSpeed);
    }

    private int countContacts() {
        AABB box = mc.player.getBoundingBox().inflate(COLLISION_EXPAND);
        int contacts = 0;
        for (Entity entity : mc.level.getEntities(mc.player, box)) {
            if (!isPusher(entity) || !this.collidesWith(entity, box)) {
                continue;
            }
            if (++contacts >= 4) {
                return contacts;
            }
        }
        return contacts;
    }

    private boolean isPusher(Entity entity) {
        if (entity == mc.player || !entity.isAlive() || entity instanceof ArmorStand) {
            return false;
        }
        if (entity.getId() == -8 || entity.getId() == -1337) {
            return false;
        }
        if (!(entity instanceof LivingEntity
                || entity instanceof Boat
                || entity instanceof Minecart
                || entity instanceof FishingHook)) {
            return false;
        }
        return this.canPush(entity);
    }

    private boolean collidesWith(Entity entity, AABB box) {
        return box.intersects(entity.getBoundingBox());
    }

    private boolean canPush(Entity entity) {
        if (mc.player.getTeam() == null || entity.getTeam() == null) {
            return true;
        }
        Team.CollisionRule rule = entity.getTeam().getCollisionRule();
        if (rule == Team.CollisionRule.ALWAYS) {
            return true;
        }
        boolean allied = mc.player.getTeam().isAlliedTo(entity.getTeam());
        return allied ? rule == Team.CollisionRule.PUSH_OWN_TEAM
                : rule == Team.CollisionRule.PUSH_OTHER_TEAMS;
    }
}