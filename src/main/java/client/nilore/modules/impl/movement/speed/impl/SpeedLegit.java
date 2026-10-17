package client.nilore.modules.impl.movement.speed.impl;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.impl.movement.SpeedModule;
import client.nilore.modules.impl.movement.speed.SpeedMode;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.utils.game.MovementUtil;
import net.minecraft.world.effect.MobEffects;

/**
 * Legit Speed
 *
 * <p>不修改移动速度，只提供 Auto Jump：在地面、正在移动、且前方可跳跃时自动触发跳跃。
 * 适合配合 GrimAC 等反作弊，行为接近原版玩家。</p>
 *
 * <p>Auto Jump 仅在 Speed 模块 Mode = Legit 时显示。</p>
 */
public class SpeedLegit extends SpeedMode {

    /** Auto Jump 开关：仅在 Legit 模式被选中时显示 */
    public final BooleanSetting autoJump = new BooleanSetting("Auto Jump", false,
            () -> SpeedModule.INSTANCE != null
                    && SpeedModule.INSTANCE.mode.is("Legit"));

    /** 跳跃后释放计时，避免按住跳跃键持续跳 */
    private int jumpReleaseTicks = 0;

    public SpeedLegit() {
        super("Legit");
    }

    @Override
    public void onEnable() {
        this.jumpReleaseTicks = 0;
    }

    @Override
    public void onDisable() {
        this.jumpReleaseTicks = 0;
        if (mc.options != null) {
            mc.options.keyJump.setDown(false);
        }
        if (mc.player != null && mc.player.input != null) {
            mc.player.input.jumping = false;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.options == null) return;

        // 跳跃后释放 2 tick，形成脉冲
        if (this.jumpReleaseTicks > 0) {
            this.jumpReleaseTicks--;
            mc.options.keyJump.setDown(false);
            return;
        }

        if (!this.autoJump.getValue()) return;
        if (!mc.player.onGround()) return;
        if (!MovementUtil.isMoving()) return;
        if (mc.player.isInWater() || mc.player.isInLava()) return;
        if (mc.player.onClimbable()) return;
        if (mc.player.isPassenger()) return;
        if (mc.player.hasEffect(MobEffects.JUMP)) return;
        if (mc.player.isUsingItem()) return;

        // 用 keyJump 触发，绕过 input.jumping 被 KeyboardInput.tick() 覆盖的问题
        mc.options.keyJump.setDown(true);
        this.jumpReleaseTicks = 2;
    }
}