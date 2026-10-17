package client.nilore.modules.impl.player;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Auto Crossbow Release —— 弩蓄力完成后自动松开使用键。
 * 适配自 Epsilon 版，改为 Nilore 的模块 / 事件体系。
 */
public class AutoCrossbow extends Module {

    public static AutoCrossbow INSTANCE;

    public AutoCrossbow() {
        super("AutoCrossbow", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public String getDisplayName() {
        return "AutoCrossbow";
    }

    @Override
    public String getModuleName() {
        return "AutoCrossbow";
    }

    /**
     * HUD 后缀：显示为 Reset
     */
    @Override
    public String getSuffix() {
        return "Reset";
    }

    @Override
    public void onTick() {

    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (!mc.player.isUsingItem()) return;

        ItemStack crossbow = mc.player.getUseItem();
        if (!(crossbow.getItem() instanceof CrossbowItem)) return;

        int chargeDuration = CrossbowItem.getChargeDuration(crossbow);
        if (mc.player.getTicksUsingItem() < chargeDuration + 1) return;

        mc.options.keyUse.setDown(false);
    }
}