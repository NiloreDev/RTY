package client.nilore.manager;

import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;

/**
 * FriendManager —— 好友名单管理。
 * 适配自 Epsilon 版，放到 Nilore 的 managers 包下。
 */
public class FriendManager {

    public static final FriendManager INSTANCE = new FriendManager();

    private final HashSet<String> friends = new HashSet<>();

    private FriendManager() {
    }

    public boolean isFriend(Player player) {
        return friends.contains(player.getGameProfile().getName());
    }

    public boolean isFriend(String name) {
        return friends.contains(name);
    }

    public void addFriend(String name) {
        friends.add(name);
    }

    public void removeFriend(String name) {
        friends.remove(name);
    }

    public void clearFriends() {
        friends.clear();
    }

    public List<String> getFriends() {
        return friends.stream().toList();
    }
}