package client.nilore.modules.impl.combat;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * AntiKick 模块
 * 1. 拦截断开连接数据包（防踢，可设置延迟踢出时间）
 * 2. 自动回应心跳包（防超时）
 * 3. 防挂机自动交替移动（即使在聊天/背包界面也能强行物理移动）
 */
public class AntiKick extends Module {

    // ==================== 配置选项 ====================
    public final BooleanSetting ignoreDisconnect = new BooleanSetting("Ignore Disconnect", true);

    // 延迟踢出时间
    public final NumberSetting delayTime = new NumberSetting("Delay Time", 10, 1, 100, 1, () -> ignoreDisconnect.getValue());

    public final BooleanSetting autoKeepAlive = new BooleanSetting("Auto KeepAlive", true);
    public final BooleanSetting showAlert = new BooleanSetting("Show Alert", true);

    // ==================== 防挂机开关 ====================
    public final BooleanSetting antiAFK = new BooleanSetting("Anti AFK", false);

    // 防挂机移动方向开关
    public final BooleanSetting afkForwardBack = new BooleanSetting("Forward/Backward", false, () -> antiAFK.getValue());
    public final BooleanSetting afkLeftRight = new BooleanSetting("Left/Right", true, () -> antiAFK.getValue());

    // 防挂机间隔时间：1 ~ 500 秒
    public final NumberSetting afkInterval = new NumberSetting("AFK Interval", 60, 1, 500, 1, () -> antiAFK.getValue());

    // 内部状态
    private long lastAfkActionTime = 0;

    // AFK 动作状态机
    private int afkPhase = 0; // 0:空闲 1:去程按键 2:回程按键
    private int afkTickTimer = 0;

    // 动作类型与方向
    private int afkType = 0; // 0: 前后移动, 1: 左右移动

    // 踢出状态
    private long kickReceivedTime = -1;
    private Component kickReason = null;

    public AntiKick() {
        super("AntiKick", Category.COMBAT);
    }

    @Override
    public String getName() {
        return "AntiKick";
    }

    @Override
    public void onEnable() {
        resetAfkTimer();
        resetKickState();
    }

    @Override
    public void onDisable() {
        afkPhase = 0;
        afkTickTimer = 0;
        resetKickState();
    }

    @Override
    public String getDisplayName() {
        return "AntiKick";
    }

    @Override
    public String getModuleName() {
        return "AntiKick";
    }

    @Override
    public void onTick() {
    }

    @EventTarget
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }

        Packet<?> packet = event.getPacket();

        // 1. 拦截服务端的踢人包 (Disconnect)
        if (packet instanceof ClientboundDisconnectPacket disconnect) {
            if (ignoreDisconnect.getValue()) {
                event.setCancelled(true);

                if (kickReceivedTime == -1) {
                    kickReceivedTime = System.currentTimeMillis();
                    kickReason = disconnect.getReason();

                    if (showAlert.getValue() && mc.player != null) {
                        long delay = delayTime.getValue().longValue();
                        mc.player.displayClientMessage(
                                Component.literal("§c[AntiKick] §f已拦截服务端踢出！将在 §e" + delay + "秒§f后自动断开。")
                                        .append(Component.literal("\n§7原因: ").append(kickReason)),
                                false
                        );
                    }
                }
            }
        }

        // 2. 自动处理 KeepAlive 心跳包
        else if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
            if (autoKeepAlive.getValue()) {
                event.setCancelled(true);
                mc.getConnection().send(new ServerboundKeepAlivePacket(keepAlive.getId()));
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        // ==================== 处理延迟踢出 ====================
        if (kickReceivedTime != -1) {
            long elapsed = System.currentTimeMillis() - kickReceivedTime;
            long maxDelayMs = delayTime.getValue().longValue() * 1000L;

            if (elapsed >= maxDelayMs) {
                if (mc.getConnection() != null) {
                    try {
                        mc.getConnection().getConnection().disconnect(
                                kickReason != null ? kickReason : Component.literal("Disconnected (AntiKick Delay End)")
                        );
                    } catch (Exception ignored) {}
                }
                resetKickState();
                return;
            }
        }

        // ==================== 处理 Anti-AFK 防挂机 ====================
        if (antiAFK.getValue()) {

            // 如果正在执行移动动作
            if (afkPhase > 0) {
                handleAfkMovement();
                return;
            }

            // 检查时间是否到达
            long intervalMs = afkInterval.getValue().longValue() * 1000L;
            if (System.currentTimeMillis() - lastAfkActionTime >= intervalMs) {

                // 确保玩家站在地上且没有骑马/坐船 (取消了对界面的判断，任何界面下都能触发)
                if (mc.player.onGround() && !mc.player.isPassenger()) {

                    // 决定当前执行哪种动作
                    boolean doFB = afkForwardBack.getValue();
                    boolean doLR = afkLeftRight.getValue();

                    if (!doFB && !doLR) doLR = true; // 兜底，如果全关，默认左右

                    if (doFB && doLR) {
                        afkType = (afkType + 1) % 2; // 两个都开，交替执行
                    } else {
                        afkType = doFB ? 0 : 1; // 只开了一个，就用那个
                    }

                    afkPhase = 1; // 开始去程
                    afkTickTimer = 4; // 去程持续 4 tick
                } else {
                    resetAfkTimer();
                }
            }
        } else {
            afkPhase = 0;
            afkTickTimer = 0;
        }
    }

    /**
     * 执行移动动作（直接使用物理引擎移动，无视界面限制）
     */
    private void handleAfkMovement() {
        float yaw = mc.player.getYRot();
        double speed = 0.15; // 模拟单次行走的位移距离

        // 如果玩家在潜行，模拟原版减速
        if (mc.player.isShiftKeyDown()) {
            speed *= 0.3;
        }

        double dx = 0;
        double dz = 0;

        if (afkType == 0) { // 前后移动
            if (afkPhase == 1) { // 去程：前进 (W)
                dx = -Math.sin(Math.toRadians(yaw)) * speed;
                dz = Math.cos(Math.toRadians(yaw)) * speed;
            } else { // 回程：后退 (S)
                dx = Math.sin(Math.toRadians(yaw)) * speed;
                dz = -Math.cos(Math.toRadians(yaw)) * speed;
            }
        } else { // 左右移动
            if (afkPhase == 1) { // 去程：左移 (A)
                dx = Math.cos(Math.toRadians(yaw)) * speed;
                dz = Math.sin(Math.toRadians(yaw)) * speed;
            } else { // 回程：右移 (D)
                dx = -Math.cos(Math.toRadians(yaw)) * speed;
                dz = -Math.sin(Math.toRadians(yaw)) * speed;
            }
        }

        // 核心：直接调用原版引擎移动。自带碰撞检测，不会穿墙掉坑，哪怕开着聊天框也能走
        mc.player.move(MoverType.SELF, new Vec3(dx, 0, dz));

        afkTickTimer--;
        if (afkTickTimer <= 0) {
            if (afkPhase == 1) {
                afkPhase = 2; // 去程结束，进入回程
                afkTickTimer = 4;
            } else {
                afkPhase = 0; // 回程结束
                resetAfkTimer();
            }
        }
    }

    private void resetAfkTimer() {
        lastAfkActionTime = System.currentTimeMillis();
    }

    private void resetKickState() {
        kickReceivedTime = -1;
        kickReason = null;
    }
}