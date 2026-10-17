package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.KeyPressEvent;
import client.nilore.event.impl.MousePressEvent;
import client.nilore.manager.FriendManager;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.misc.ChatUtil;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Key Friend —— 按键/鼠标中键点击玩家，切换好友状态。
 * 适配自 Epsilon 版，改为 Nilore 的模块 / 事件体系。
 */
public class KeyFriend extends Module {

    public static KeyFriend INSTANCE;

    // ==================== 设置 ====================
    /**
     * 激活键。
     * 键盘：GLFW 键码（0~400）
     * 鼠标：1000 + 鼠标键号（例如中键 = 1002）
     */
    public final NumberSetting activateKey =
            new NumberSetting("Activate Key", 1002, -1, 1400, 1);

    // ==================== 构造 ====================
    public KeyFriend() {
        super("KeyFriend", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public String getDisplayName() {
        return "§fKey Friend";
    }

    @Override
    public String getModuleName() {
        return "KeyFriend";
    }

    @Override
    public void onTick() {
    }

    // ==================== 事件 ====================
    @EventTarget
    public void onKeyPress(KeyPressEvent event) {
        if (handleInput(event.getKey(), event.getAction())) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMousePress(MousePressEvent event) {
        if (handleInput(encodeMouse(event.getButton()), event.getAction())) {
            event.setCancelled(true);
        }
    }

    // ==================== 逻辑 ====================
    private boolean handleInput(int key, int action) {
        if (mc.player == null || mc.level == null) return false;
        if (mc.screen != null) return false;

        int bound = activateKey.getValue().intValue();
        if (bound == -1) return false;
        if (key != bound) return false;
        if (action != InputConstants.PRESS) return false;

        if (!(mc.hitResult instanceof EntityHitResult hitResult)) return false;
        if (!(hitResult.getEntity() instanceof Player player)) return false;

        String playerName = player.getGameProfile().getName();

        if (FriendManager.INSTANCE.isFriend(playerName)) {
            FriendManager.INSTANCE.removeFriend(playerName);
            ChatUtil.print(true, "§c[KeyFriend] Removed friend: §f" + playerName);
        } else {
            FriendManager.INSTANCE.addFriend(playerName);
            ChatUtil.print(true, "§a[KeyFriend] Added friend: §f" + playerName);
        }

        return true;
    }

    // ==================== 工具 ====================
    /** 把鼠标键号编码成和键盘键码不冲突的值。 */
    public static int encodeMouse(int button) {
        return 1000 + button;
    }
}