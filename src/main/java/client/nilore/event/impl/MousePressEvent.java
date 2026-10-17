package client.nilore.event.impl;

import client.nilore.event.Event;

/**
 * MousePressEvent —— 鼠标按下/释放事件。
 * 适配自 Epsilon 版，继承 Nilore 的 Event 基类。
 */
public class MousePressEvent extends Event {

    private final int button;
    private final int action;
    private final int modifiers;

    public MousePressEvent(int button, int action, int modifiers) {
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
    }

    public int getButton() {
        return button;
    }

    public int getAction() {
        return action;
    }

    public int getModifiers() {
        return modifiers;
    }
}