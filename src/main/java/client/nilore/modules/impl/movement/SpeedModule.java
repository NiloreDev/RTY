package client.nilore.modules.impl.movement;

import client.nilore.NiloreClient;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.movement.speed.SpeedMode;
import client.nilore.modules.impl.movement.speed.impl.SpeedLegit;
import client.nilore.modules.impl.movement.speed.impl.SpeedMotion;
import client.nilore.modules.impl.movement.speed.impl.SpeedOnGround;
import client.nilore.settings.impl.ModeSetting;

import java.util.List;

public class SpeedModule extends Module {
    /** 供 SpeedLegit 的可见性判断引用 */
    public static SpeedModule INSTANCE;

    private final List<SpeedMode> modes = List.of(
            new SpeedOnGround(),
            new SpeedMotion(),
            new SpeedLegit()
    );
    public final ModeSetting mode;
    private static SpeedMode activeMode;

    public SpeedModule() {
        super("Speed", Category.MOVEMENT);
        INSTANCE = this;
        mode = new ModeSetting("Mode", "OnGround", "Motion", "Legit").withDefault("OnGround");
        activeMode = modes.get(0);
        activeMode.setActive(true);
        modes.forEach(speedMode -> getSettings().addAll(speedMode.getValues()));
    }

    @Override
    public String getModuleName() {
        return "Speed";
    }

    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return null;
        }
        return "[" + modeName + "]";
    }

    @Override
    public String getDisplayName() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fSpeed";
        }
        return "§fSpeed[" + modeName + "]";
    }

    @Override
    public void onEnable() {
        activeMode = getActiveMode();
        activeMode.setActive(true);
        NiloreClient.getInstance().getEventBus().register(activeMode);
        activeMode.onEnable();
    }

    @Override
    public void onDisable() {
        activeMode.onDisable();
        NiloreClient.getInstance().getEventBus().unregister(activeMode);
        activeMode.setActive(false);
    }

    @Override
    public void onTick() {

    }

    @EventTarget
    public void onTick(TickEvent event) {
        SpeedMode selected = getActiveMode();
        if (activeMode != selected) {
            activeMode.onDisable();
            NiloreClient.getInstance().getEventBus().unregister(activeMode);
            activeMode.setActive(false);
            activeMode = selected;
            activeMode.setActive(true);
            NiloreClient.getInstance().getEventBus().register(activeMode);
            activeMode.onEnable();
        }
    }

    private SpeedMode getActiveMode() {
        for (SpeedMode speedMode : modes) {
            if (mode.is(speedMode.getName())) {
                return speedMode;
            }
        }
        return modes.get(0);
    }

    public static boolean is(SpeedMode mode) {
        return activeMode == mode;
    }
}