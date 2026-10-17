package client.nilore.modules.impl.world;

import java.nio.file.Path;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import client.nilore.NiloreClient;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.protocol.heypixel.HeyPixelProtocolRuntime;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;

public final class Protocol extends Module {
    private final HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(
        mc, Path.of(NiloreClient.configDir));
    public final ModeSetting enabledHosts = new ModeSetting("Hosts", "pc.bjdmc.net,*.bjdmc.net");
    public final BooleanSetting traceLogger = new BooleanSetting("Trace Logger", false);
    public final BooleanSetting observeOnly = new BooleanSetting("Observe Only", true);
    public final BooleanSetting allowLiveSend = new BooleanSetting("Allow Live Send", false);
    public final BooleanSetting strictProviderGate = new BooleanSetting("Strict Provider Gate", true);

    public Protocol() {
        super("Protocol", Category.WORLD);
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
    public void onEnable() {
        updateRuntimeSettings();
        runtime.start();
    }

    @Override
    public void onDisable() {
        runtime.stop();
    }

    @Override
    public String getSuffix() {
        return runtime.isActiveForCurrentServer() ? "HeyPixel" : "Idle";
    }

    @Override
    public void onTick() {

    }

    @EventTarget
    public void onTick(TickEvent event) {
        updateRuntimeSettings();
        runtime.tick();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isIncoming()) return;
        if (event.getPacket() instanceof ClientboundCustomPayloadPacket payload) {
            runtime.handle(payload);
        }
    }

    public HeyPixelProtocolRuntime getRuntime() {
        return runtime;
    }

    private void updateRuntimeSettings() {
        runtime.configure(
            enabledHosts.getValue(),
            traceLogger.getValue(),
            observeOnly.getValue(),
            allowLiveSend.getValue(),
            strictProviderGate.getValue()
        );
    }
}
