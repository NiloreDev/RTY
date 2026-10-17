package client.nilore.modules.impl.movement;

import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.utils.animation.SpringAnimation;

public

class NoJumpDelay extends Module {
    public static NoJumpDelay INSTANCE;
    public SpringAnimation fastDig;

    public NoJumpDelay() {
        super("NoJumpDelay", Category.MOVEMENT);
        INSTANCE = this;
    }

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

    public static NoJumpDelay getInstance() {
        return INSTANCE;
    }
}