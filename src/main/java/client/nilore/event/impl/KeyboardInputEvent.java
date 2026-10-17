package client.nilore.event.impl;

import client.nilore.event.Event;

/**
 * KeyboardInputEvent —— 键盘/移动输入事件。
 * 适配自 Epsilon 版，去掉 1.21+ 的 Input record 依赖。
 */
public class KeyboardInputEvent extends Event {

    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    public KeyboardInputEvent(float forward, float strafe,
                              boolean jump, boolean sneak, boolean sprint) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
    }

    public float getForward() { return forward; }
    public void setForward(float forward) { this.forward = forward; }

    public float getStrafe() { return strafe; }
    public void setStrafe(float strafe) { this.strafe = strafe; }

    public boolean isJump() { return jump; }
    public void setJump(boolean jump) { this.jump = jump; }

    public boolean isSneak() { return sneak; }
    public void setSneak(boolean sneak) { this.sneak = sneak; }

    public boolean isSprint() { return sprint; }
    public void setSprint(boolean sprint) { this.sprint = sprint; }
}