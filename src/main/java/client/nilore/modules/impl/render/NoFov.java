package client.nilore.modules.impl.render;

import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;

public class NoFov extends Module {
    public static NoFov INSTANCE;

    /**
     * 缩放（Zoom）进行中置为 true，NoFov 在此期间完全失效，
     * 让缩放模块自己决定 FOV。
     *
     * 使用 volatile 保证跨线程可见性（Mixin / 渲染线程 / 事件线程可能不同）。
     */
    private static volatile boolean paused = false;

    public final ModeSetting modeSetting = new ModeSetting("Mode", "Constant", "Custom").withDefault("Constant");
    public final NumberSetting fovSetting = new NumberSetting("FOV", 90, 1, 179, 1);
    public final NumberSetting multiplierSetting = new NumberSetting("Multiplier", 1.0, 0.1, 1.5, 0.05);

    /** 自动检测缩放：开启后如果检测到外部放大模组导致 FOV 骤降，NoFov自动失效 */
    public final BooleanSetting autoZoomCheck = new BooleanSetting("Auto Zoom Check", true);

    public NoFov() {
        super("NoFov", Category.RENDER);
        INSTANCE = this;
        // 注册设置项，防止在 GUI 中不显示
        this.registerSetting(modeSetting, fovSetting, multiplierSetting, autoZoomCheck);
    }

    // ============================================================
    // 暂停控制接口
    // ============================================================

    /** 外部（例如 Zoom 模块）在开始缩放时调用 setPaused(true)，结束时 setPaused(false)。 */
    public static void setPaused(boolean value) {
        paused = value;
    }

    /** 当前 NoFov 是否被暂停（缩放中）。 */
    public static boolean isPaused() {
        return paused;
    }

    // ============================================================
    // 生效判定 / FOV 计算
    // ============================================================

    /**
     * NoFov 是否应当生效。
     * 只有在：模块已启用、且未被暂停（缩放中）时才返回 true。
     */
    public boolean isEffective() {
        return this.isEnabled() && !paused;
    }

    /**
     * 兼容旧写法：如果外部还在调用 isConstant()，
     * 暂停时返回 false，让上层判定 NoFov "不应生效"。
     */
    public boolean isConstant() {
        if (paused) return false;
        return this.modeSetting.is("Constant");
    }

    /**
     * 统一的 FOV 计算入口。
     */
    public float applyFov(float originalFov) {
        // ============================================================
        // 【新增逻辑】：自动检测第三方放大模组
        // 原理：如果原版设定的 FOV (比如 70) 跟当前传入的 originalFov 差距过大
        // 说明有第三方模组（如放大模组）介入并强制拉低了 FOV，此时直接跳过 NoFov。
        // ============================================================
        if (this.autoZoomCheck.getValue() && mc != null && mc.options != null) {
            float baseFov = mc.options.fov().get();
            // 只要传入的 FOV 小于原版 FOV 的 85%（或者小于40），就判定为正在触发放大效果
            if (originalFov < baseFov * 0.85f || originalFov < 40.0f) {
                return originalFov;
            }
        }

        // 模块没启用 / 被内部暂停（缩放中）→ 原样返回
        if (!this.isEffective()) {
            return originalFov;
        }

        if (this.modeSetting.is("Constant")) {
            return this.fovSetting.getValue().floatValue();
        }
        // Custom 模式：在原版基础上乘倍率
        return originalFov * this.multiplierSetting.getValue().floatValue();
    }

    // ============================================================
    // Module 基类要求
    // ============================================================

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
}