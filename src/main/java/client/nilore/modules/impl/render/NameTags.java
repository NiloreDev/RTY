package client.nilore.modules.impl.render;

import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.render.nametag.NameTagStyle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.event.EventTarget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

public class NameTags
        extends Module {

    public static NameTags INSTANCE;

    // ==================== 基础设置 ====================
    public final ModeSetting styleSetting = new ModeSetting("Style", "Opal", "Simple", "Naven").withDefault("Opal");
    public final NumberSetting scaleSetting = new NumberSetting("Scale", 0.7, 0.5, 10.0, 0.01);
    public final NumberSetting distanceSetting = new NumberSetting("Max Distance", 128.0, 8.0, 256.0, 1.0);

    // ==================== 透明度 ====================
    public final NumberSetting opacitySetting = new NumberSetting("Opacity", 1.0, 0.0, 10.0, 0.05);

    // ==================== 功能开关 ====================
    // 这三个 Simple 也用，所以 Opal / Simple 都显示
    public final BooleanSetting showHealthSetting = new BooleanSetting(
            "Invisibles", true,
            () -> isStyle("Opal", "Simple"));
    public final BooleanSetting showArmorSetting = new BooleanSetting(
            "Show Artifacts", true,
            () -> isStyle("Opal", "Simple"));
    public final BooleanSetting showPingSetting = new BooleanSetting(
            "Hide Teammates", false,
            () -> isStyle("Opal", "Simple"));

    // 以下都仅 Opal
    public final BooleanSetting showDistanceSetting = new BooleanSetting(
            "Show Distance", false,
            () -> isStyle("Opal"));

    // Nametags+ equipment display
    public final BooleanSetting showEquipmentSetting = new BooleanSetting(
            "Nametags+ Equipment", true,
            () -> isStyle("Opal"));
    public final BooleanSetting showHeldItemSetting = new BooleanSetting(
            "Show Held Item", true,
            () -> isStyle("Opal"));
    public final BooleanSetting showArmorItemsSetting = new BooleanSetting(
            "Show Armor Items", true,
            () -> isStyle("Opal"));
    public final BooleanSetting showEnchantmentsSetting = new BooleanSetting(
            "Show Enchantments", false,
            () -> isStyle("Opal"));

    public final BooleanSetting showGearSetting = new BooleanSetting(
            "Show Gear", true,
            () -> isStyle("Opal"));

    // ==================== 掉落物设置 ====================
    public final BooleanSetting showItemCountSetting = new BooleanSetting(
            "Show Item Count", true,
            () -> isStyle("Opal"));
    public final BooleanSetting showItemBackgroundSetting = new BooleanSetting(
            "Item Background", true,
            () -> isStyle("Opal"));

    // ==================== Killer 开关 ====================
    public final BooleanSetting killerSetting = new BooleanSetting(
            "Killer", false,
            () -> isStyle("Opal"));

    // ==================== 显示目标开关 ====================
    public final BooleanSetting showPlayersSetting = new BooleanSetting(
            "Show Players", true,
            () -> isStyle("Opal"));
    public final BooleanSetting showAnimalsSetting = new BooleanSetting(
            "Show Animals", false,
            () -> isStyle("Opal"));
    public final BooleanSetting showMonstersSetting = new BooleanSetting(
            "Show Monsters", false,
            () -> isStyle("Opal"));
    public final BooleanSetting showItemsSetting = new BooleanSetting(
            "Show Items", false,
            () -> isStyle("Opal"));
    public final BooleanSetting showOtherEntitiesSetting = new BooleanSetting(
            "Show Other Entities", false,
            () -> isStyle("Opal"));
    public final BooleanSetting showInvisiblePlayersSetting = new BooleanSetting(
            "Show Invisible", false,
            () -> isStyle("Opal"));

    // ==================== Opal 专属设置 ====================
    public final BooleanSetting opalBackgroundSetting = new BooleanSetting(
            "Opal Background", true,
            () -> isStyle("Opal"));

    public final NumberSetting opalBackgroundAlphaSetting = new NumberSetting(
            "Opal Background Alpha", 0.47, 0.0, 1.0, 0.01,
            () -> isStyle("Opal"));

    /**
     * 判断当前 Style 是否在给定的候选名单里。
     * 传入的条件字符串会与 styleSetting 的当前值精确匹配（大小写敏感）。
     */
    private boolean isStyle(String... allowed) {
        String current = this.styleSetting == null ? null : this.styleSetting.getValue();
        if (current == null) return false;
        for (String s : allowed) {
            if (s.equals(current)) return true;
        }
        return false;
    }

    public static boolean shouldPreserveVanillaNameTag(Entity entity) {
        return entity instanceof ArmorStand;
    }

    public NameTags() {
        super("NameTags", Category.RENDER);
        INSTANCE = this;
        NameTagStyle.registerStyles();
    }

    @Override
    public void onEnable() {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onEnable();
        }
    }

    @Override
    public void onDisable() {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onDisable();
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
    public void onTick() {
    }

    @EventTarget
    public void onRender(RenderEvent renderEvent) {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onRender(renderEvent);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onRender2D(render2DEvent);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        NameTagStyle nameTagStyle = NameTagStyle.getByName(this.styleSetting.getValue());
        if (nameTagStyle != null) {
            nameTagStyle.onPacket(packetEvent);
        }
    }
}