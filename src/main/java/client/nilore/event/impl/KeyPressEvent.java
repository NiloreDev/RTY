package client.nilore.event.impl;

import client.nilore.event.Event;

/**
 * KeyPressEvent —— 键盘按下/释放事件。
 * 直接存 key / modifiers，不依赖 Epsilon 的 KeyEvent 包装类。
 */
public class KeyPressEvent extends Event {

    private final int key;
    private final int action;
    private final int modifiers;

    public KeyPressEvent(int key, int action, int modifiers) {
        this.key = key;
        this.action = action;
        this.modifiers = modifiers;
    }

    public int getKey() {
        return key;
    }

    public int getAction() {
        return action;
    }

    public int getModifiers() {
        return modifiers;
    }
}