package client.nilore.modules.impl.combat;

import java.util.Arrays;
import java.util.Optional;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.Render3DEvent;
import client.nilore.event.impl.RotationEvent;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.AntiKBMode;
import client.nilore.modules.impl.combat.antikb.EpsilonCancelVelocityMode;
import client.nilore.modules.impl.combat.antikb.EpsilonReduceVelocityMode;
import client.nilore.modules.impl.combat.antikb.EpsilonDelayVelocityMode;
import client.nilore.modules.impl.combat.antikb.NavenVelocityMode;
import client.nilore.modules.impl.combat.antikb.OpalGrimMode;
import client.nilore.modules.impl.combat.antikb.OpalJumpMode;
import client.nilore.modules.impl.combat.antikb.OpalReduceMode;
import client.nilore.modules.impl.combat.antikb.OpalMixMode;
import client.nilore.modules.impl.movement.FireballBlink;
import client.nilore.modules.impl.movement.Grimfly;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.SpringAnimation;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.rotation.Rotation;
import net.minecraft.network.chat.Component;

public class Velocity extends Module {
    public static Velocity INSTANCE;
    public static Rotation rotation;
    public static ModeSetting mode;

    // =================================================================
    // === Opal 模式 ==================================================
    // =================================================================

    public final ModeSetting opalMode =
            new ModeSetting("Opal Mode", "OpalGrim", "OpalJump", "OpalReduce", "OpalMix")
                    .withDefault("OpalJump")
                    .withVisibility(() -> mode.is("Opal"));

    private boolean isOpalSub(String sub) {
        return mode.is("Opal") && opalMode.is(sub);
    }

    private boolean isReduceXZ() {
        return mode.is("Velocity") && velocityMode.is("ReduceXZ");
    }

    /** Legit 子模式判定（原 NoXZ） */
    private boolean isLegit() {
        return mode.is("Velocity") && velocityMode.is("Legit");
    }

    // =================================================================
    // --- Opal / Jump ---
    // =================================================================
    public final NumberSetting v2JumpChance =
            new NumberSetting("Jump Chance", 100, 0, 100, 1, () -> isOpalSub("OpalJump"));
    public final BooleanSetting v2JumpByHits =
            new BooleanSetting("Jump By Hits", false, () -> isOpalSub("OpalJump"));
    public final NumberSetting v2HitsMin =
            new NumberSetting("Hits Min", 2, 0, 10, 1, () -> isOpalSub("OpalJump"));
    public final NumberSetting v2HitsMax =
            new NumberSetting("Hits Max", 2, 0, 10, 1, () -> isOpalSub("OpalJump"));
    public final BooleanSetting v2JumpByDelay =
            new BooleanSetting("Jump By Delay", true, () -> isOpalSub("OpalJump"));
    public final NumberSetting v2DelayMin =
            new NumberSetting("Delay Min", 2, 0, 10, 1, () -> isOpalSub("OpalJump"));
    public final NumberSetting v2DelayMax =
            new NumberSetting("Delay Max", 2, 0, 10, 1, () -> isOpalSub("OpalJump"));
    public final BooleanSetting v2OnlyGround =
            new BooleanSetting("Only Ground", true, () -> isOpalSub("OpalJump"));
    public final BooleanSetting v2TargetCheck =
            new BooleanSetting("Target Check", false, () -> isOpalSub("OpalJump"));
    public final NumberSetting v2TargetRange =
            new NumberSetting("Target Range", 6.0, 1.0, 10.0, 0.5, () -> isOpalSub("OpalJump"));

    // =================================================================
    // --- Opal / Reduce ---
    // =================================================================
    public final NumberSetting v2ReduceTick =
            new NumberSetting("Reduce Tick", 2, 1, 100, 1, () -> isOpalSub("OpalReduce"));
    public final BooleanSetting v2AutoAttack =
            new BooleanSetting("Auto Attack", true, () -> isOpalSub("OpalReduce"));
    public final BooleanSetting v2FollowDirection =
            new BooleanSetting("Follow Direction", false, () -> isOpalSub("OpalReduce"));

    // =================================================================
    // --- Opal / Grim ---
    // =================================================================
    public final NumberSetting v2GrimAttackCount =
            new NumberSetting("Grim Attack Count", 1, 1, 5, 1, () -> isOpalSub("OpalGrim"));
    public final NumberSetting v2AttackRange =
            new NumberSetting("Attack Range", 3.0, 1.0, 6.0, 0.5, () -> isOpalSub("OpalGrim"));
    public final BooleanSetting v2IgnoreSprint =
            new BooleanSetting("Ignore Sprint", true, () -> isOpalSub("OpalGrim"));
    public final BooleanSetting v2GrimFlagCheck =
            new BooleanSetting("Grim Flag Check", false, () -> isOpalSub("OpalGrim"));
    public final NumberSetting v2GrimFlagTicks =
            new NumberSetting("Grim Flag Ticks", 10, 0, 40, 1, () -> isOpalSub("OpalGrim"));
    public final BooleanSetting v2GrimDebug =
            new BooleanSetting("Grim Debug", false, () -> isOpalSub("OpalGrim"));

    // =================================================================
    // --- Opal / Mix ---
    // =================================================================
    public final BooleanSetting v2Rotate =
            new BooleanSetting("Mix Rotate", false, () -> isOpalSub("OpalMix"));
    public final BooleanSetting v2TryAttack =
            new BooleanSetting("Mix Try Attack", false, () -> isOpalSub("OpalMix"));
    public final BooleanSetting v2MovementOverride =
            new BooleanSetting("Mix Movement Override", false, () -> isOpalSub("OpalMix"));
    public final BooleanSetting v2MixDebug =
            new BooleanSetting("Mix Debug", false, () -> isOpalSub("OpalMix"));

    // =================================================================
    // --- Opal 通用 ---
    // =================================================================
    public final BooleanSetting v2OnlyMove =
            new BooleanSetting("Only Move", false, () -> mode.is("Opal"));
    public final BooleanSetting v2GlobalOnlyGround =
            new BooleanSetting("Global Only Ground", false, () -> mode.is("Opal"));
    public final BooleanSetting v2FlagCheck =
            new BooleanSetting("Flag Check", false, () -> mode.is("Opal"));
    public final BooleanSetting v2FlagDisable =
            new BooleanSetting("Flag Disable", false, () -> mode.is("Opal"));
    public final NumberSetting v2FlagTicks =
            new NumberSetting("Flag Ticks", 40, 0, 100, 1, () -> mode.is("Opal"));
    public final BooleanSetting v2FlagDebug =
            new BooleanSetting("Flag Debug", false, () -> mode.is("Opal"));
    public final BooleanSetting v2Debug =
            new BooleanSetting("Debug", false, () -> mode.is("Opal"));

    private final OpalGrimMode   opalGrimHandler   = new OpalGrimMode(this);
    private final OpalJumpMode   opalJumpHandler   = new OpalJumpMode(this);
    private final OpalReduceMode opalReduceHandler = new OpalReduceMode(this);
    private final OpalMixMode    opalMixHandler    = new OpalMixMode(this);

    // =================================================================
    // === 原有 Velocity 设置 =========================================
    // =================================================================

    public final ModeSetting velocityMode =
            new ModeSetting("Velocity Mode", "JumpRest", "ReduceXZ", "Mix", "Legit")
                    .withDefault("JumpRest")
                    .withVisibility(() -> mode.is("Velocity"));

    public final BooleanSetting rotate =
            new BooleanSetting("Rotate", false,
                    () -> isEffectiveMode("JumpReset") || isEffectiveMode("Mix"));
    public final BooleanSetting tryAttack =
            new BooleanSetting("Try Attack", false,
                    () -> isEffectiveMode("Mix"));
    public final BooleanSetting movementOverride =
            new BooleanSetting("Movement Override", false,
                    () -> isEffectiveMode("Mix"));

    public final BooleanSetting followDirection =
            new BooleanSetting("Follow Direction", false,
                    () -> isEffectiveMode("JumpReset"));

    /** Jump Reset 是否过滤摔落/水落伤害的速度包 */
    public final BooleanSetting jumpResetNoFallCheck =
            new BooleanSetting("JumpReset NoFall Check", true,
                    () -> isEffectiveMode("JumpReset"));

    public final NumberSetting rotateTicks =
            new NumberSetting("Rotate Ticks", 12, 3, 20, 1,
                    () -> isEffectiveMode("JumpReset")
                            && (this.rotate.getValue() || this.followDirection.getValue()));

    // =================================================================
    // === ReduceXZ 共用设置（Velocity > ReduceXZ 与 Legit 共享） ======
    // =================================================================

    public final BooleanSetting autoAttackCount =
            new BooleanSetting("Auto Count", true, this::isReduceXZ);

    /** ReduceXZ 与 Legit 共用，语义是"计数阶段攻击次数" */
    public final NumberSetting attackAmount =
            new NumberSetting("Attack Amount", 5.0, 1.0, 20.0, 1.0,
                    () -> (isReduceXZ() && !this.autoAttackCount.getValue()) || isLegit());

    public final BooleanSetting instantAttack =
            new BooleanSetting("Instant Hits", false, this::isReduceXZ);

    public final BooleanSetting sprintStateCheck =
            new BooleanSetting("Require Sprint", false, this::isReduceXZ);

    /** Need KillAura —— ReduceXZ 与 Legit 共用 */
    public final BooleanSetting isReduceModeNeedKillAura =
            new BooleanSetting("Need KillAura", true,
                    () -> isReduceXZ() || isLegit());

    /** Auto Forward —— ReduceXZ 与 Legit 共用 */
    public final BooleanSetting autoForwards =
            new BooleanSetting("Auto Forward", true,
                    () -> isReduceXZ() || isLegit());

    // =================================================================
    // === ReduceXZ 专用设置 ==========================================
    // =================================================================

    public final NumberSetting isNoXZAltAttackAmount =
            new NumberSetting("Attack Counter", 4.0, 0.0, 8.0, 1, this::isReduceXZ);

    public final NumberSetting isNoXZAltAttackRange =
            new NumberSetting("Attack Range", 3.0, 3.0, 4.0, 0.1, this::isReduceXZ);

    public final BooleanSetting isNoXZAltDelayEnable =
            new BooleanSetting("Delay", true, this::isReduceXZ);

    public final NumberSetting isNoXZAltMaxDelayTicks =
            new NumberSetting("Max Delay Ticks", 10.0, 1.0, 120.0, 1.0,
                    () -> isReduceXZ() && isNoXZAltDelayEnable.getValue());

    public final NumberSetting isNoXZAltBufferRange =
            new NumberSetting("Buffer Range", 2.8, 2.0, 3.0, 0.1,
                    () -> isReduceXZ() && isNoXZAltDelayEnable.getValue());

    public final ModeSetting isNoXZAltReduceMode =
            new ModeSetting("Release Mode", "Sprint", "Sprint", "Ground")
                    .withDefault("Sprint")
                    .withVisibility(() -> isReduceXZ() && isNoXZAltDelayEnable.getValue());

    public final BooleanSetting isNoXZAltSideStrafe =
            new BooleanSetting("SideStrafe", true,
                    () -> isReduceXZ() && isNoXZAltDelayEnable.getValue());

    public final NumberSetting isNoXZAltReach =
            new NumberSetting("Reach", 3.0, 1.0, 3.0, 0.05, this::isReduceXZ);

    public final BooleanSetting isNoXZAltDebugLog =
            new BooleanSetting("Debug Log", false, this::isReduceXZ);

    // =================================================================
    // === Legit 设置（Velocity > Legit 子模式） =======================
    // =================================================================

    /** Legit 延迟开关：关闭时纯计数，开启时延迟窗口 */
    public final BooleanSetting legitDelayEnable =
            new BooleanSetting("Delay", true, this::isLegit);

    /** Legit 最大延迟 tick 数（仅在 Delay 开启时可见） */
    public final NumberSetting maxDelayTicks =
            new NumberSetting("Max Delay Ticks", 10.0, 1.0, 120.0, 1.0,
                    () -> isLegit() && legitDelayEnable.getValue());

    /** Jump Reset 同时服务 Legit 与 ReduceXZ */
    public final BooleanSetting jumpReset =
            new BooleanSetting("Jump Reset", false,
                    () -> isLegit() || isReduceXZ());

    /** Legit 进度条渲染开关 */
    public final BooleanSetting renderBar =
            new BooleanSetting("Render Bar", false, this::isLegit);

    /** Legit Target ESP 开关 */
    public final BooleanSetting targetEsp =
            new BooleanSetting("Target ESP", false, this::isLegit);

    /** Debug Log：Legit 与 Velocity 各模式共用 */
    public final BooleanSetting debugLog =
            new BooleanSetting("Debug Log", false);

    // =================================================================
    // === Naven 设置 =================================================
    // =================================================================

    public final ModeSetting navenMode =
            new ModeSetting("Naven Mode", "Reduce", "JumpReset", "Both")
                    .withDefault("Reduce")
                    .withVisibility(() -> mode.is("Naven"));

    public final BooleanSetting navenAutoForwards =
            new BooleanSetting("Auto Forwards", true, () -> mode.is("Naven"));

    public final BooleanSetting navenAutoRotation =
            new BooleanSetting("Auto Rotation", false,
                    () -> mode.is("Naven") && navenMode.is("JumpReset"));

    public final BooleanSetting navenRequiresKillAura =
            new BooleanSetting("Requires KillAura", true,
                    () -> mode.is("Naven") && navenMode.is("JumpReset"));

    public final BooleanSetting navenTargetESP =
            new BooleanSetting("Target ESP", true,
                    () -> mode.is("Naven") && !navenMode.is("JumpReset"));

    public final ModeSetting navenTargetESPColor =
            new ModeSetting("Target ESP Color", "White", "Cyan", "Red", "Green", "Purple")
                    .withDefault("White")
                    .withVisibility(() -> mode.is("Naven")
                            && !navenMode.is("JumpReset")
                            && navenTargetESP.getValue());

    public final NumberSetting navenAlinkY =
            new NumberSetting("ALINK Y", 18.0, 0.0, 500.0, 1.0,
                    () -> mode.is("Naven") && !navenMode.is("JumpReset"));

    /** Pace Check 只服务 ReduceXZ / Naven，Legit 下隐藏 */
    public final BooleanSetting paceCheck =
            new BooleanSetting("Pace Check", true,
                    () -> isReduceXZ() || mode.is("Naven"));

    public final BooleanSetting navenDebug =
            new BooleanSetting("Debug", false,
                    () -> mode.is("Naven") && !navenMode.is("JumpReset"));

    public final NumberSetting navenMaxCounter =
            new NumberSetting("Max Counter", 4.0, 0.0, 8.0, 1.0,
                    () -> mode.is("Naven") && !navenMode.is("JumpReset"));

    public final NumberSetting navenMaxDelayTicks =
            new NumberSetting("Max Delay Ticks", 10.0, 5.0, 120.0, 1.0,
                    () -> mode.is("Naven") && !navenMode.is("JumpReset"));

    public final ModeSetting navenReduceMode =
            new ModeSetting("Reduce Mode", "Normal", "Delay")
                    .withDefault("Normal")
                    .withVisibility(() -> mode.is("Naven") && !navenMode.is("JumpReset"));

    public final BooleanSetting navenAllDelay =
            new BooleanSetting("All Delay", false,
                    () -> mode.is("Naven")
                            && navenReduceMode.is("Delay")
                            && !navenMode.is("JumpReset"));

    // =================================================================
    // === Epsilon 设置 ===============================================
    // =================================================================

    public final BooleanSetting epsilonCancelMotion =
            new BooleanSetting("Epsilon Cancel Motion", true,
                    () -> mode.is("EpsilonCancel"));
    public final BooleanSetting epsilonCancelExplosion =
            new BooleanSetting("Epsilon Cancel Explosion", false,
                    () -> mode.is("EpsilonCancel"));
    public final NumberSetting epsilonAttackCounts =
            new NumberSetting("Epsilon Attack Counts", 1.0, 1.0, 5.0, 1.0,
                    () -> mode.is("EpsilonReduce"));
    public final NumberSetting epsilonSprintTicks =
            new NumberSetting("Epsilon Sprint Ticks", 3.0, 1.0, 5.0, 1.0,
                    () -> mode.is("EpsilonReduce"));
    public final NumberSetting epsilonMaxDelay =
            new NumberSetting("Epsilon Max Delay", 1000.0, 0.0, 1000.0, 50.0,
                    () -> mode.is("EpsilonReduce"));
    public final BooleanSetting epsilonSwingHand =
            new BooleanSetting("Epsilon Swing Hand", false,
                    () -> mode.is("EpsilonReduce"));
    public final NumberSetting epsilonDelayTicks =
            new NumberSetting("Epsilon Delay Ticks", 3.0, 1.0, 5.0, 1.0,
                    () -> mode.is("EpsilonDelay"));
    public final BooleanSetting epsilonJumpReset =
            new BooleanSetting("Epsilon Jump Reset", false,
                    () -> mode.is("EpsilonDelay"));

    // =================================================================
    // === Chat 通知 ==================================================
    // =================================================================

    public final BooleanSetting chatJumpReset =
            new BooleanSetting("Chat JumpReset", false,
                    () -> isEffectiveMode("JumpReset"));
    public final BooleanSetting chatMix =
            new BooleanSetting("Chat Mix", false,
                    () -> isEffectiveMode("Mix"));

    public final BooleanSetting chatReduceXZ =
            new BooleanSetting("Chat Alert", false, this::isReduceXZ);

    public final BooleanSetting chatLegit =
            new BooleanSetting("Chat Legit", false, this::isLegit);

    public final BooleanSetting chatNaven =
            new BooleanSetting("Chat Naven", false, () -> mode.is("Naven"));

    public final BooleanSetting chatEpsilonCancel =
            new BooleanSetting("Chat EpsilonCancel", false,
                    () -> mode.is("EpsilonCancel"));
    public final BooleanSetting chatEpsilonReduce =
            new BooleanSetting("Chat EpsilonReduce", false,
                    () -> mode.is("EpsilonReduce"));
    public final BooleanSetting chatEpsilonDelay =
            new BooleanSetting("Chat EpsilonDelay", false,
                    () -> mode.is("EpsilonDelay"));

    private final Timer grimSyncTimer = new Timer();
    public SpringAnimation navenAirDelayToGround;

    public Velocity() {
        super("Velocity", Category.COMBAT);
        INSTANCE = this;
        AntiKBMode.initModes();
    }

    @Override
    public String getModuleName() {
        return "Velocity";
    }

    // =================================================================
    // === 模式名 / 后缀 / 显示名 ====================================
    // =================================================================

    private String getEffectiveModeName() {
        if (mode.is("Opal")) {
            String sub = opalMode.getValue();
            if (sub == null) return "OpalJump";
            return sub;
        }

        if (!mode.is("Velocity")) {
            return mode.getValue();
        }

        String sub = velocityMode.getValue();
        if (sub == null) return "JumpReset";

        return switch (sub) {
            case "Jump", "JumpRest"     -> "JumpReset";
            case "Reduce", "ReduceXZ"   -> "ReduceXZ";
            case "Mix"                  -> "Mix";
            case "Legit"                -> "Legit";
            case "Grim"                 -> "Grim";
            default                     -> "JumpReset";
        };
    }

    private boolean isEffectiveMode(String name) {
        String eff = getEffectiveModeName();
        if (eff == null || name == null) return false;

        if (name.equalsIgnoreCase("JumpReset")) {
            return eff.equalsIgnoreCase("JumpReset") || eff.equalsIgnoreCase("JumpRest");
        }
        if (name.equalsIgnoreCase("ReduceXZ")) {
            return eff.equalsIgnoreCase("ReduceXZ") || eff.equalsIgnoreCase("Reduce");
        }
        return eff.equalsIgnoreCase(name);
    }

    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) return null;

        if (mode.is("Opal")) {
            String sub = opalMode.getValue();
            if (sub != null && !sub.isEmpty()) {
                return "[" + sub.replace("Opal", "") + "]";
            }
        }
        if (mode.is("Velocity")) {
            String sub = velocityMode.getValue();
            if (sub != null && !sub.isEmpty()) return "[" + sub + "]";
        }
        if (mode.is("Naven")) {
            String sub = navenMode.getValue();
            if (sub != null && !sub.isEmpty()) {
                String displaySub = sub.equalsIgnoreCase("Both") ? "NoXZ" : sub;
                return "[" + displaySub + "]";
            }
        }
        return "[" + modeName + "]";
    }

    @Override
    public void onTick() {
    }

    @Override
    public String getDisplayName() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fVelocity";
        }

        if (mode.is("Opal")) {
            String sub = opalMode.getValue();
            if (sub != null && !sub.isEmpty()) {
                return "§fVelocity[" + sub.replace("Opal", "") + "]";
            }
        }
        if (mode.is("Velocity")) {
            String sub = velocityMode.getValue();
            if (sub != null && !sub.isEmpty()) {
                return "§fVelocity[" + sub + "]";
            }
        }
        if (mode.is("Naven")) {
            String sub = navenMode.getValue();
            if (sub != null && !sub.isEmpty()) {
                String displaySub = sub.equalsIgnoreCase("Both") ? "NoXZ" : sub;
                return "§fVelocity[" + displaySub + "]";
            }
        }
        return "§fVelocity[" + modeName + "]";
    }

    // =================================================================
    // === 生命周期 ==================================================
    // =================================================================

    @Override
    public void onEnable() {
        grimSyncTimer.reset();
        rotation = null;

        if (!Arrays.stream(mode.getModes()).toList().contains(mode.getValue())) {
            mode.withDefault("Velocity");
        }

        if (mode.is("Opal")) {
            opalGrimHandler.onEnable();
            opalJumpHandler.onEnable();
            opalReduceHandler.onEnable();
            opalMixHandler.onEnable();
            return;
        }

        Optional<AntiKBMode> optional = currentMode();
        if (optional.isPresent()) {
            optional.get().onEnable();
        } else {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("§c[Velocity] §fHandler not found for §e"
                                + getEffectiveModeName()), false);
            }
        }
    }

    @Override
    public void onDisable() {
        rotation = null;

        opalGrimHandler.onDisable();
        opalJumpHandler.onDisable();
        opalReduceHandler.onDisable();
        opalMixHandler.onDisable();

        Optional<AntiKBMode> optional = currentMode();
        optional.ifPresent(AntiKBMode::onDisable);
    }

    private Optional<AntiKBMode> currentMode() {
        String eff = getEffectiveModeName();
        Optional<AntiKBMode> opt = AntiKBMode.findMode(eff);
        if (opt.isPresent()) return opt;

        if (mode.is("Velocity")) {
            String raw = velocityMode.getValue();
            if (raw != null && !raw.equals(eff)) {
                opt = AntiKBMode.findMode(raw);
                if (opt.isPresent()) return opt;
            }
        }

        return Optional.empty();
    }

    private AntiKBMode opalCurrentHandler() {
        String sub = opalMode.getValue();
        if (sub == null) return opalJumpHandler;
        return switch (sub) {
            case "OpalGrim"   -> opalGrimHandler;
            case "OpalReduce" -> opalReduceHandler;
            case "OpalMix"    -> opalMixHandler;
            default           -> opalJumpHandler;
        };
    }

    private boolean conflictingMovementModuleEnabled() {
        boolean scaffoldConflict = (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled());
        if (mode.is("Naven") || isReduceXZ() || isLegit() || isEffectiveMode("JumpReset")) {
            scaffoldConflict = false;
        }
        return (FireballBlink.INSTANCE != null && FireballBlink.INSTANCE.isEnabled())
                || (Grimfly.INSTANCE != null && Grimfly.INSTANCE.isEnabled())
                || scaffoldConflict;
    }

    // =================================================================
    // === 聊天栏通知 ================================================
    // =================================================================

    public void notifyChat(String message) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.literal(message), false);
    }

    public boolean isChatEnabledForCurrentMode() {
        if (mode.is("Opal")) {
            return v2Debug.getValue();
        }

        String eff = getEffectiveModeName();

        if ("Legit".equals(eff)) {
            return chatLegit.getValue();
        }
        if ("ReduceXZ".equals(eff)) {
            return chatReduceXZ.getValue();
        }

        return switch (eff) {
            case "JumpReset"     -> chatJumpReset.getValue();
            case "Mix"           -> chatMix.getValue();
            case "Naven"         -> chatNaven.getValue();
            case "EpsilonCancel" -> chatEpsilonCancel.getValue();
            case "EpsilonReduce" -> chatEpsilonReduce.getValue();
            case "EpsilonDelay"  -> chatEpsilonDelay.getValue();
            default              -> false;
        };
    }

    public void notifyChatIfEnabled(String message) {
        if (!isChatEnabledForCurrentMode()) return;
        notifyChat(message);
    }

    public void debugChat(String message) {
        if (!isChatEnabledForCurrentMode()) return;
        notifyChat(message);
    }

    // =================================================================
    // === 事件分发 ==================================================
    // =================================================================

    @EventTarget
    public void onGameTick(GameTickEvent event) {
        if (mode.is("Opal")) {
            opalCurrentHandler().onGameTick(event);
            return;
        }
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onGameTick(event);
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mode.is("Opal")) {
            opalCurrentHandler().onPreMotion(event);
            return;
        }
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onPreMotion(event);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mode.is("Opal")) {
            opalCurrentHandler().onTick(event);
            return;
        }
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onTick(event);
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (mode.is("Opal")) return;
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onSprint(event);
    }

    @EventTarget
    public void onRotation(RotationEvent event) {
        if (mode.is("Opal")) return;
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onRotation(event);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mode.is("Opal")) {
            opalCurrentHandler().onMotion(event);
            return;
        }
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onMotion(event);
    }

    @EventTarget(value = 1)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mode.is("Opal")) {
            opalCurrentHandler().onReceivePacket(event);
            return;
        }
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onReceivePacket(event);
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent event) {
        if (mode.is("Opal")) {
            opalGrimHandler.onDisconnect(event);
            opalJumpHandler.onDisconnect(event);
            opalReduceHandler.onDisconnect(event);
            opalMixHandler.onDisconnect(event);
            return;
        }
        Optional<AntiKBMode> optional = currentMode();
        if (optional.isEmpty()) return;
        optional.get().onDisconnect(event);
    }

    @EventTarget(value = 3)
    public void onStrafe(StrafeEvent event) {
        if (mode.is("Opal")) {
            opalCurrentHandler().onStrafe(event);
            return;
        }
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onStrafe(event);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (mode.is("Opal")) return;
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onRender(event);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mode.is("Opal")) return;
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;

        AntiKBMode m = optional.get();

        if (m instanceof NavenVelocityMode naven) naven.onRender3D(event);
        if (m instanceof EpsilonCancelVelocityMode cancel) cancel.onRender3D(event);
        if (m instanceof EpsilonReduceVelocityMode reduce) reduce.onRender3D(event);
        if (m instanceof EpsilonDelayVelocityMode delay) delay.onRender3D(event);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mode.is("Opal")) return;
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onRender2D(event);
    }

    static {
        mode = new ModeSetting("Mode",
                "Velocity",
                "Naven",
                "EpsilonCancel",
                "EpsilonReduce",
                "EpsilonDelay",
                "Opal")
                .withDefault("Velocity");
    }
}