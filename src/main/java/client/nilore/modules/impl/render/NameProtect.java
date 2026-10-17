package client.nilore.modules.impl.render;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import org.apache.commons.lang3.StringUtils;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.ChatReceiveEvent;
import client.nilore.event.impl.DisconnectEvent;

import client.nilore.modules.Category;
import client.nilore.modules.Module;

import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.TextSetting;

public class NameProtect extends Module {

    public static NameProtect INSTANCE;

    /*
     * Fixed  = 固定名字
     * Random = 随机取服务器里的一个玩家名字
     * Hidden = Hidden
     */
    private final ModeSetting modeSetting =
            new ModeSetting(
                    "Mode",
                    "Fixed",
                    "Random",
                    "Hidden"
            ).withDefault("Hidden");

    /*
     * 当 Mode = Hidden 时，
     * 使用这里输入的名字。
     */
    private final TextSetting customName =
            new TextSetting(
                    "Hidden Name",
                    "Player"
            );

    private String cachedRandomName;

    private final Random random =
            new Random();

    public NameProtect() {

        super(
                "NameProtect",
                Category.RENDER
        );

        INSTANCE = this;

        /*
         * 保留你原来默认开启的行为。
         */
        this.setEnabled(true);
    }

    /*
     * ============================================================
     * Disconnect
     * ============================================================
     */

    @EventTarget
    public void onDisconnect(
            DisconnectEvent event
    ) {

        /*
         * 离开服务器以后重新生成随机名称。
         */
        cachedRandomName = null;
    }

    /*
     * ============================================================
     * Replace
     * ============================================================
     */

    public static String replacePlayerName(
            String text
    ) {

        if (text == null) {
            return null;
        }

        if (INSTANCE == null) {
            return text;
        }

        /*
         * NameProtect 关闭时不要替换。
         */
        if (!INSTANCE.isEnabled()) {
            return text;
        }

        if (mc.player == null) {
            return text;
        }

        String realName =
                mc.player
                        .getName()
                        .getString();

        String protectedName =
                INSTANCE.getProtectedDisplayName();

        if (protectedName == null
                || protectedName.isEmpty()) {

            return text;
        }

        if (protectedName.equals(realName)) {
            return text;
        }

        if (!text.contains(realName)) {
            return text;
        }

        return StringUtils.replace(
                text,
                realName,
                protectedName
        );
    }

    /*
     * 其他模块需要获取保护后的用户名时
     * 可以直接调用：
     *
     * NameProtect.getProtectedName()
     */
    public static String getProtectedName() {

        if (mc.player == null) {
            return "Player";
        }

        if (INSTANCE == null
                || !INSTANCE.isEnabled()) {

            return mc.player
                    .getName()
                    .getString();
        }

        return INSTANCE
                .getProtectedDisplayName();
    }

    /*
     * ============================================================
     * Name Logic
     * ============================================================
     */

    private String getProtectedDisplayName() {

        if (mc.player == null) {
            return "Player";
        }

        /*
         * =========================
         * Hidden
         * =========================
         * 使用输入框里的名字
         */
        if (modeSetting.is("Hidden")) {

            String name =
                    customName
                            .getValue()
                            .trim();

            if (name.isEmpty()) {
                return "Player";
            }

            return name;
        }

        /*
         * =========================
         * Fixed
         * =========================
         */
        if (modeSetting.is("Fixed")) {

            return "Player";
        }

        /*
         * =========================
         * Random
         * =========================
         */
        if (modeSetting.is("Random")) {

            String randomName =
                    generateRandomName();

            if (randomName != null
                    && !randomName.isEmpty()) {

                return randomName;
            }

            /*
             * 当前服务器没有其他玩家时的备用名字。
             */
            return "Player";
        }

        return mc.player
                .getName()
                .getString();
    }

    /*
     * Module GUI/ArrayList 显示。
     */
    @Override
    public String getDisplayName() {

        return getProtectedDisplayName();
    }

    /*
     * ============================================================
     * Random
     * ============================================================
     */

    private String generateRandomName() {

        if (mc.player == null) {
            return null;
        }

        if (mc.getConnection() == null) {
            return null;
        }

        /*
         * 如果已经缓存，
         * 不要每帧乱跳名字。
         */
        if (cachedRandomName != null
                && !cachedRandomName.isEmpty()) {

            return cachedRandomName;
        }

        ArrayList<PlayerInfo> players =
                new ArrayList<>(
                        mc.getConnection()
                                .getOnlinePlayers()
                );

        ArrayList<String> names =
                new ArrayList<>();

        String realName =
                mc.player
                        .getName()
                        .getString();

        for (PlayerInfo info : players) {

            if (info == null
                    || info.getProfile() == null) {
                continue;
            }

            String name =
                    info.getProfile()
                            .getName();

            if (name == null
                    || name.isEmpty()) {
                continue;
            }

            /*
             * 不选择自己。
             */
            if (name.equalsIgnoreCase(realName)) {
                continue;
            }

            names.add(name);
        }

        if (names.isEmpty()) {
            return null;
        }

        cachedRandomName =
                names.get(
                        random.nextInt(
                                names.size()
                        )
                );

        return cachedRandomName;
    }

    /*
     * ============================================================
     * Chat
     * ============================================================
     */

    @EventTarget
    public void onChatReceive(
            ChatReceiveEvent event
    ) {

        if (!isEnabled()) {
            return;
        }

        if (event.getComponent() == null) {
            return;
        }

        String original =
                event.getComponent()
                        .getString();

        String replaced =
                replacePlayerName(
                        original
                );

        event.setComponent(
                Component.literal(
                        replaced
                )
        );
    }

    /*
     * ============================================================
     * Module Info
     * ============================================================
     */

    @Override
    public String getModuleName() {
        return "NameProtect";
    }

    @Override
    public String getSuffix() {

        if (modeSetting.is("Random")) {

            String name =
                    generateRandomName();

            return name == null
                    ? "Random"
                    : name;
        }

        return modeSetting
                .getValue();
    }

    @Override
    public void onTick() {

        /*
         * 如果切换到 Random，
         * 保证至少生成一次名字。
         */
        if (modeSetting.is("Random")
                && cachedRandomName == null) {

            generateRandomName();
        }
    }
}