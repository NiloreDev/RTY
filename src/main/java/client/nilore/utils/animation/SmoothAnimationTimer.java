package client.nilore.utils.animation;

import client.nilore.utils.math.Easing;
import client.nilore.utils.math.Easings;

public class SmoothAnimationTimer extends AnimationTimer {

    public void animate(double target, double duration) {
        this.animate(target, duration, Easings.EASE_OUT_QUAD);
    }

    public void animate(double target, double duration, Easing easing) {
        this.animate(target, duration, easing, false);
    }

    public void animate(double target, double duration, Easing easing, boolean debug) {
        this.setDebug(debug);

        // 修复：去掉了 target == this.getFromValue() 的阻断，防止动画中途被打断导致卡住
        if (this.isAnimating() && target == this.getToValue()) {
            if (this.isDebug()) {
                System.out.println("Animate cancelled: target equals toValue");
            }
            return;
        }

        this.setEasing(easing);
        this.setDuration(duration * 1000.0);
        this.setStartTime(System.currentTimeMillis());
        this.setFromValue(this.getValueF());
        this.setToValue(target);

        if (this.isDebug()) {
            double durationMs = this.getDuration();
            float current = this.getValueF();
            double to = this.getToValue();
            System.out.println("#animate {\n    to value: " + to + "\n    from value: " + current + "\n    duration: " + durationMs + "\n}");
        }
    }

    // =================================================================
    // 修正代理方法，解决外部调用报错
    // =================================================================

    /**
     * 获取当前动画值（返回 double，兼容 TargetHud 中的强转）
     */
    public double getValue() {
        return this.getValueF();
    }

    /**
     * 获取当前动画的目标值
     */
    public double getTarget() {
        return this.getToValue();
    }

    /**
     * 强制设置目标值
     */
    public void setTarget(double target) {
        this.setToValue(target);
    }
}