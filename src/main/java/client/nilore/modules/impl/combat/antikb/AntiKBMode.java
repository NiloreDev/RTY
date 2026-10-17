package client.nilore.modules.impl.combat.antikb;

import java.util.HashMap;
import java.util.Optional;

import client.nilore.ClientBase;
import client.nilore.event.impl.*;
import client.nilore.modules.impl.combat.Velocity;

/**
 * AntiKBMode 注册表
 *
 * 统一模式：
 *   JumpReset / Mix / ReduceXZ / Legit / NoXZAlt / Naven / EpsilonCancel / EpsilonReduce / EpsilonDelay /
 *   OpalGrim / OpalJump / OpalReduce / OpalMix
 */
public abstract class AntiKBMode extends ClientBase {

    /** 全局攻击标记：由 ReduceXZMode 等在攻击窗口期打开 */
    public static boolean isAttacking;

    protected final String name;

    private static final HashMap<Class<? extends AntiKBMode>, AntiKBMode> modes =
            new HashMap<>();

    public AntiKBMode(String name) {
        this.name = name;
    }

    // ================================================================
    // 注册
    // ================================================================
    public static void initModes() {
        modes.clear();

        // Nilore 原版
        modes.put(JumpResetMode.class, new JumpResetMode());
        modes.put(MixMode.class, new MixMode());
        modes.put(ReduceXZMode.class, new ReduceXZMode());

        // Legit（原 NoXZ）
        modes.put(LegitMode.class, new LegitMode());

        // Naven
        modes.put(NavenVelocityMode.class, new NavenVelocityMode());

        // Epsilon
        modes.put(EpsilonCancelVelocityMode.class, new EpsilonCancelVelocityMode());
        modes.put(EpsilonReduceVelocityMode.class, new EpsilonReduceVelocityMode());
        modes.put(EpsilonDelayVelocityMode.class, new EpsilonDelayVelocityMode());

        // Opal（需要 Velocity 实例）
        if (Velocity.INSTANCE != null) {
            modes.put(OpalGrimMode.class,   new OpalGrimMode(Velocity.INSTANCE));
            modes.put(OpalJumpMode.class,   new OpalJumpMode(Velocity.INSTANCE));
            modes.put(OpalReduceMode.class, new OpalReduceMode(Velocity.INSTANCE));
            modes.put(OpalMixMode.class,    new OpalMixMode(Velocity.INSTANCE));
        }
    }

    // ================================================================
    // 查询
    // ================================================================
    public static Optional<AntiKBMode> findMode(String name) {
        if (name == null) return Optional.empty();

        // 别名：只能指向 ReduceXZ 或 JumpReset
        String normalized = switch (name) {
            case "Jump", "JumpRest" -> "JumpReset";
            case "Reduce"           -> "ReduceXZ";
            default                 -> name;
        };

        final String finalName = normalized;
        return modes.values().stream()
                .filter(mode -> mode.name.equals(finalName))
                .findFirst();
    }

    /** 通过类查询 */
    public static Optional<AntiKBMode> findMode(Class<? extends AntiKBMode> clazz) {
        return Optional.ofNullable(modes.get(clazz));
    }

    // ================================================================
    // 生命周期 + 事件
    // ================================================================
    public abstract void onEnable();
    public abstract void onDisable();
    public abstract String getName();

    public abstract void onRotation(RotationEvent event);
    public abstract void onReceivePacket(ReceivePacketEvent event);
    public abstract void onDisconnect(DisconnectEvent event);

    public abstract void reset();

    public abstract void onPreMotion(PreMotionEvent event);

    public abstract void onEntityHurt(EntityHurtEvent event);

    public abstract void onGameTick(GameTickEvent event);

    public abstract void onAttack(EntityRemoveEvent event);

    public abstract void onSprint(SprintEvent event);
    public abstract void onTick(TickEvent event);
    public abstract void onStrafe(StrafeEvent event);
    public abstract void onMotion(MotionEvent event);

    public void onRender(RenderEvent event) {
    }

    public void onRender2D(Render2DEvent event) {
    }

    public boolean isActive() {
        return false;
    }

    public abstract void onStrafe(StrafeEvent event, int unused);
}