package client.nilore.manager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import client.nilore.NiloreClient;
import client.nilore.modules.impl.combat.*;
import client.nilore.modules.impl.movement.*;
import client.nilore.modules.impl.movement.Stuck;
import client.nilore.modules.impl.combat.SuperReach;
import client.nilore.modules.impl.player.*;
import client.nilore.modules.impl.world.*;
import org.apache.commons.lang3.StringUtils;
import client.nilore.ClientBase;
import client.nilore.event.impl.KeyEvent;
import client.nilore.exception.ModuleNotFoundException;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.exploit.Disabler;
import client.nilore.modules.impl.exploit.FastPlace;
import client.nilore.modules.impl.misc.AimAssist;
import client.nilore.modules.impl.misc.AutoClicker;
import client.nilore.modules.impl.misc.AutoRod;
import client.nilore.modules.impl.misc.KillSay;
import client.nilore.modules.impl.misc.MusicPlayer;
import client.nilore.modules.impl.misc.Eagle;
import client.nilore.modules.impl.render.*; // <-- 这里包含了正确的 OpalNameTag
import client.nilore.event.EventTarget;

public class ModuleManager extends ClientBase {
    private final Map<String, Module> moduleMap = new ConcurrentHashMap<>();

    public ModuleManager() {
        NiloreClient.getInstance().getEventBus().register(this);
    }

    public void initModules() {
        this.register(new AntiBots());
        this.register(new AntiFireball());
        this.register(new Velocity());        // 【修复】原为 new Velocity() { 漏了闭合
        this.register(new SuperAura());
        this.register(new SuperReach());
        this.register(new AutoOffHand());
        this.register(new AntiKick());
        this.register(new AutoSoup());
        this.register(new KeepSprint());
        this.register(new Critical());
        this.register(new AutoThrow());
        this.register(new Backtrack());
        this.register(new CrystalAura());
        this.register(new KillAura());
        this.register(new FakeLag());

        this.register(new Disabler());
        this.register(new FastPlace());

        this.register(new AimAssist());
        this.register(new AutoClicker());
        this.register(new AutoRod());
        this.register(new Eagle());
        this.register(new MusicPlayer());
        this.register(new KillSay());

        this.register(new SpeedModule());
        this.register(new NoSlow());
        this.register(new FastWeb());
        this.register(new FireballBlink());
        this.register(new Grimfly());
        this.register(new Fly());
        this.register(new NoJumpDelay());
        this.register(new NoPush());
        this.register(new Scaffold());
        this.register(new Stuck());
        this.register(new Sprint());
        this.register(new TargetStrafe());

        this.register(new AntiTNT());
        this.register(new AntiVoid());
        this.register(new AutoArmor());
        this.register(new AntiWeb());
        this.register(new AutoCrossbow());
        this.register(new AutoTotem());
        this.register(new AutoMLG());
        this.register(new InvManager());
        this.register(new AutoWebPlace());
        this.register(new ChestStealer());
        this.register(new GhostHand());
        this.register(new Helper());
        this.register(new InventoryManager());
        this.register(new MidPearl());
        this.register(new NoFall());

        this.register(new AspectRatio());
        this.register(new BetterChat());
        this.register(new Wings());
        this.register(new Animations());
        this.register(new InventoryHud());
        this.register(new FootRing());
        this.register(new Ambience());
        this.register(new YsmModel());
        this.register(new ChestESP());
        this.register(new EatingHUD());
        this.register(new ItemDisplay());
        this.register(new IQBoost());
        this.register(new DamageIndicator());
        this.register(new Camera());
        this.register(new ClickGui());
        this.register(new AttackEffect());
        this.register(new JumpCircles());
        this.register(new BedPlates());
        this.register(new KillEffect());
        this.register(new Compass());
        this.register(new Cape());
        this.register(new PlayerGlow());
        this.register(new DamageGlow());
        this.register(new ESP());
        this.register(new EntityEditor());
        this.register(new Drep());
        this.register(new FullBright());
        this.register(new Interface());

        // 注册独立出来的 Opal 模块
        this.register(new OpalNameTag());

        this.register(new LyricsModule());
        this.register(new NameProtect());
        this.register(new NameTags());
        this.register(new NoFov());
        this.register(new NoHurtCam());
        this.register(new Projectiles());
        this.register(new TimeWeather());
        this.register(new Watermark());
        this.register(new XRay());

        this.register(new AntiStaff());
        this.register(new ModuleBedAura());
        this.register(new AutoPlay());
        this.register(new AutoTools());
        this.register(new BlockIn());
        this.register(new BlockIn2());
        this.register(new Debugger());
        this.register(new Teams());
        this.register(new WebUI());
        this.register(new Protocol());
    }

    public void register(Module module) {
        this.moduleMap.put(module.getClass().getSimpleName(), module);
        module.registerSettings();
    }

    public Module getModule(String string) {
        Module module = null;
        for (Module module2 : this.moduleMap.values()) {
            if (!StringUtils.replace(module2.getName(), " ", "").equalsIgnoreCase(string)) continue;
            module = module2;
        }
        if (module == null) {
            throw new ModuleNotFoundException();
        }
        return module;
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        Module module = clazz.cast(this.moduleMap.get(clazz.getSimpleName()));
        if (module == null) {
            throw new ModuleNotFoundException();
        }
        return (T) module;
    }

    public List<Module> getModules() {
        return this.moduleMap.values().stream().toList();
    }

    public List<Module> getModulesByCategory(Category category) {
        return this.moduleMap.values().stream()
                .filter(module -> module.getCategory().equals(category))
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .collect(Collectors.toList());
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (mc.screen == null) {
            for (Module module : this.moduleMap.values()) {
                if (module.getKey() != 0 && module.getKey() == event.getKeyCode() && event.isPressed()) {
                    module.toggle();
                }
            }
        }
    }
}