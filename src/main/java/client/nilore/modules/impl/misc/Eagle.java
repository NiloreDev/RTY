package client.nilore.modules.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import client.nilore.event.impl.MotionEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.event.EventTarget;

public
class Eagle
        extends Module {
    public Eagle() {
        super("Eagle", Category.MISC);
    }

    public static boolean isOnBlockEdge(float inset) {
        return !mc.level.getCollisions(mc.player, mc.player.getBoundingBox().move(0.0, -0.5, 0.0).inflate(-inset, 0.0, -inset)).iterator().hasNext();
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (motionEvent.isPost() && mc.player != null && mc.level != null) {
            mc.options.keyShift.setDown(mc.player.onGround() && Eagle.isOnBlockEdge(0.3f));
        }
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
    public void onDisable() {
        boolean keyDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyShift.getKey().getValue());
        mc.options.keyShift.setDown(keyDown);
    }

    @Override
    public void onTick() {

    }
}