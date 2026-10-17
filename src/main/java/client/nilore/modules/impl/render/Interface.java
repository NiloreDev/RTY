package client.nilore.modules.impl.render;

import client.nilore.modules.Category;
import client.nilore.modules.Module;

public class Interface
        extends Module {
    public Interface() {
        super("Hud", Category.RENDER);
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
}