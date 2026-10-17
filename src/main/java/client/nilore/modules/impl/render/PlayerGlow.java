package client.nilore.modules.impl.render;

import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.world.Teams;
import client.nilore.settings.impl.BooleanSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerGlow extends Module {

    public static PlayerGlow INSTANCE;

    private static final String ENEMY_TEAM =
            "nilore_enemy";

    private static final String FRIEND_TEAM =
            "nilore_friend";

    private static final String HOSTILE_TEAM =
            "nilore_hostile";

    private static final String FRIENDLY_MOB_TEAM =
            "nilore_friendly_mob";

    private static final String ANIMAL_TEAM =
            "nilore_animal";

    private static final String ITEM_TEAM =
            "nilore_item";

    /*
     * =========================
     * 玩家
     * =========================
     */

    private final BooleanSetting enemies =
            new BooleanSetting(
                    "Enemies",
                    true
            );

    private final BooleanSetting teammates =
            new BooleanSetting(
                    "Teammates",
                    true
            );

    /*
     * =========================
     * 生物
     * =========================
     */

    /*
     * 敌对生物：
     * 僵尸、骷髅、苦力怕、蜘蛛等
     * → 红色
     */
    private final BooleanSetting hostileMobs =
            new BooleanSetting(
                    "Hostile Mobs",
                    true
            );

    /*
     * 普通/中立 Mob：
     * 例如村民、铁傀儡等不属于 Enemy
     * 且不属于 Animal 的 Mob
     * → 绿色
     */
    private final BooleanSetting friendlyMobs =
            new BooleanSetting(
                    "Friendly Mobs",
                    true
            );

    /*
     * 动物：
     * 牛、羊、猪、鸡等
     * → 绿色
     */
    private final BooleanSetting animals =
            new BooleanSetting(
                    "Animals",
                    true
            );

    /*
     * =========================
     * 掉落物
     * =========================
     */

    private final BooleanSetting items =
            new BooleanSetting(
                    "Items",
                    true
            );

    /*
     * 玩家原来的服务器 Team。
     */
    private final Map<UUID, String> oldTeams =
            new HashMap<>();

    public PlayerGlow() {

        super(
                "PlayerGlow",
                Category.RENDER
        );

        INSTANCE = this;
    }

    @Override
    public String getDisplayName() {
        return "PlayerGlow";
    }

    @Override
    public String getModuleName() {
        return "PlayerGlow";
    }

    /*
     * 你项目的 ModuleManager
     * 会正常调用这个。
     */
    @Override
    public void onTick() {

        if (mc.level == null
                || mc.player == null) {
            return;
        }

        /*
         * 确保客户端 Team 存在。
         */
        setupTeams();

        for (Entity entity :
                mc.level.entitiesForRendering()) {

            /*
             * =====================================
             * 玩家
             * =====================================
             */
            if (entity instanceof Player player) {

                /*
                 * 不处理自己。
                 */
                if (player == mc.player) {
                    continue;
                }

                if (!shouldGlowPlayer(player)) {

                    restorePlayerTeam(
                            player
                    );

                    continue;
                }

                boolean friend =
                        Teams.instance != null
                                && Teams.instance.isEnabled()
                                && Teams.isSameTeam(player);

                setPlayerTeam(
                        player,

                        friend
                                ? FRIEND_TEAM
                                : ENEMY_TEAM
                );

                continue;
            }

            /*
             * =====================================
             * 掉落物
             * =====================================
             */
            if (entity instanceof ItemEntity item) {

                if (items.getValue()) {

                    setEntityTeam(
                            item,
                            ITEM_TEAM
                    );

                } else {

                    removeEntityFromOurTeam(
                            item
                    );
                }

                continue;
            }

            /*
             * =====================================
             * 动物
             * =====================================
             *
             * Animal 必须在 Mob 之前判断。
             */
            if (entity instanceof Animal animal) {

                if (animals.getValue()) {

                    setEntityTeam(
                            animal,
                            ANIMAL_TEAM
                    );

                } else {

                    removeEntityFromOurTeam(
                            animal
                    );
                }

                continue;
            }

            /*
             * =====================================
             * 敌对生物
             * =====================================
             *
             * Minecraft 原版 Enemy 接口：
             * Zombie / Skeleton / Creeper 等
             * 都会进入这里。
             */
            if (entity instanceof Enemy
                    && entity instanceof Mob mob) {

                if (hostileMobs.getValue()) {

                    setEntityTeam(
                            mob,
                            HOSTILE_TEAM
                    );

                } else {

                    removeEntityFromOurTeam(
                            mob
                    );
                }

                continue;
            }

            /*
             * =====================================
             * 普通 / 友好 Mob
             * =====================================
             *
             * 不属于 Enemy
             * 不属于 Animal
             * 但仍然是 Mob。
             */
            if (entity instanceof Mob mob) {

                if (friendlyMobs.getValue()) {

                    setEntityTeam(
                            mob,
                            FRIENDLY_MOB_TEAM
                    );

                } else {

                    removeEntityFromOurTeam(
                            mob
                    );
                }
            }
        }
    }

    /*
     * ============================================================
     * MinecraftPatch 调用
     * ============================================================
     *
     * 这里只负责：
     *
     * 这个 Entity 应不应该进入 Minecraft
     * 原版 Glowing Render Pipeline。
     *
     * 颜色则由 Team 决定。
     */
    public boolean isGlowing(
            Entity entity
    ) {

        if (!isEnabled()) {
            return false;
        }

        if (mc.player == null) {
            return false;
        }

        /*
         * 自己不 Glow。
         */
        if (entity == mc.player) {
            return false;
        }

        /*
         * 掉落物。
         */
        if (entity instanceof ItemEntity) {

            return items.getValue();
        }

        /*
         * 玩家。
         */
        if (entity instanceof Player player) {

            boolean friend =
                    Teams.instance != null
                            && Teams.instance.isEnabled()
                            && Teams.isSameTeam(player);

            if (friend) {

                return teammates.getValue();
            }

            return enemies.getValue();
        }

        /*
         * 动物。
         */
        if (entity instanceof Animal) {

            return animals.getValue();
        }

        /*
         * 敌对生物。
         */
        if (entity instanceof Enemy) {

            return hostileMobs.getValue();
        }

        /*
         * 其他 Mob。
         */
        if (entity instanceof Mob) {

            return friendlyMobs.getValue();
        }

        return false;
    }

    /*
     * ============================================================
     * Player 判断
     * ============================================================
     */

    private boolean shouldGlowPlayer(
            Player player
    ) {

        boolean friend =
                Teams.instance != null
                        && Teams.instance.isEnabled()
                        && Teams.isSameTeam(player);

        if (friend) {

            return teammates.getValue();
        }

        return enemies.getValue();
    }

    /*
     * ============================================================
     * Team 初始化
     * ============================================================
     */

    private void setupTeams() {

        if (mc.level == null) {
            return;
        }

        Scoreboard scoreboard =
                mc.level.getScoreboard();

        /*
         * 玩家敌人
         * RED
         */
        PlayerTeam enemy =
                getOrCreateTeam(
                        scoreboard,
                        ENEMY_TEAM
                );

        enemy.setColor(
                ChatFormatting.RED
        );

        /*
         * 玩家队友
         * GREEN
         */
        PlayerTeam friend =
                getOrCreateTeam(
                        scoreboard,
                        FRIEND_TEAM
                );

        friend.setColor(
                ChatFormatting.GREEN
        );

        /*
         * 敌对生物
         * RED
         */
        PlayerTeam hostile =
                getOrCreateTeam(
                        scoreboard,
                        HOSTILE_TEAM
                );

        hostile.setColor(
                ChatFormatting.RED
        );

        /*
         * 友好 Mob
         * GREEN
         */
        PlayerTeam friendly =
                getOrCreateTeam(
                        scoreboard,
                        FRIENDLY_MOB_TEAM
                );

        friendly.setColor(
                ChatFormatting.GREEN
        );

        /*
         * 动物
         * GREEN
         */
        PlayerTeam animal =
                getOrCreateTeam(
                        scoreboard,
                        ANIMAL_TEAM
                );

        animal.setColor(
                ChatFormatting.GREEN
        );

        /*
         * 掉落物
         * WHITE
         */
        PlayerTeam item =
                getOrCreateTeam(
                        scoreboard,
                        ITEM_TEAM
                );

        item.setColor(
                ChatFormatting.WHITE
        );
    }

    private PlayerTeam getOrCreateTeam(
            Scoreboard scoreboard,
            String name
    ) {

        PlayerTeam team =
                scoreboard.getPlayerTeam(
                        name
                );

        if (team == null) {

            team =
                    scoreboard.addPlayerTeam(
                            name
                    );
        }

        return team;
    }

    /*
     * ============================================================
     * Player Team
     * ============================================================
     */

    private void setPlayerTeam(
            Player player,
            String teamName
    ) {

        if (mc.level == null) {
            return;
        }

        Scoreboard scoreboard =
                mc.level.getScoreboard();

        String name =
                player.getScoreboardName();

        /*
         * 第一次修改前，
         * 保存服务器原 Team。
         */
        if (!oldTeams.containsKey(
                player.getUUID()
        )) {

            PlayerTeam original =
                    scoreboard.getPlayersTeam(
                            name
                    );

            oldTeams.put(
                    player.getUUID(),

                    original == null
                            ? ""
                            : original.getName()
            );
        }

        PlayerTeam target =
                scoreboard.getPlayerTeam(
                        teamName
                );

        if (target == null) {
            return;
        }

        PlayerTeam current =
                scoreboard.getPlayersTeam(
                        name
                );

        if (current == target) {
            return;
        }

        if (current != null) {

            scoreboard.removePlayerFromTeam(
                    name,
                    current
            );
        }

        scoreboard.addPlayerToTeam(
                name,
                target
        );
    }

    /*
     * ============================================================
     * Mob / Item Team
     * ============================================================
     */

    private void setEntityTeam(
            Entity entity,
            String teamName
    ) {

        if (mc.level == null) {
            return;
        }

        Scoreboard scoreboard =
                mc.level.getScoreboard();

        PlayerTeam target =
                scoreboard.getPlayerTeam(
                        teamName
                );

        if (target == null) {
            return;
        }

        String name =
                entity.getScoreboardName();

        PlayerTeam current =
                scoreboard.getPlayersTeam(
                        name
                );

        if (current == target) {
            return;
        }

        /*
         * 对 Mob / Item，
         * 如果当前已经属于我们自己的 Glow Team，
         * 先移除。
         *
         * 如果它属于服务器真正的 Team，
         * 也暂时覆盖以保证 Glow 颜色正确。
         */
        if (current != null) {

            scoreboard.removePlayerFromTeam(
                    name,
                    current
            );
        }

        scoreboard.addPlayerToTeam(
                name,
                target
        );
    }

    /*
     * 关闭某一类 Glow 时，
     * 把实体从我们自己的 Team 移出去。
     */
    private void removeEntityFromOurTeam(
            Entity entity
    ) {

        if (mc.level == null) {
            return;
        }

        Scoreboard scoreboard =
                mc.level.getScoreboard();

        String name =
                entity.getScoreboardName();

        PlayerTeam current =
                scoreboard.getPlayersTeam(
                        name
                );

        if (current == null) {
            return;
        }

        if (!isOurTeam(
                current.getName()
        )) {
            return;
        }

        scoreboard.removePlayerFromTeam(
                name,
                current
        );
    }

    /*
     * ============================================================
     * Player Team 恢复
     * ============================================================
     */

    private void restorePlayerTeam(
            Player player
    ) {

        if (mc.level == null) {
            return;
        }

        UUID uuid =
                player.getUUID();

        if (!oldTeams.containsKey(uuid)) {
            return;
        }

        Scoreboard scoreboard =
                mc.level.getScoreboard();

        String playerName =
                player.getScoreboardName();

        PlayerTeam current =
                scoreboard.getPlayersTeam(
                        playerName
                );

        /*
         * 只把 Nilore Glow Team 移除。
         */
        if (current != null
                && isOurTeam(current.getName())) {

            scoreboard.removePlayerFromTeam(
                    playerName,
                    current
            );
        }

        String oldName =
                oldTeams.remove(
                        uuid
                );

        if (oldName == null
                || oldName.isEmpty()) {
            return;
        }

        PlayerTeam old =
                scoreboard.getPlayerTeam(
                        oldName
                );

        if (old != null) {

            scoreboard.addPlayerToTeam(
                    playerName,
                    old
            );
        }
    }

    /*
     * ============================================================
     * Disable
     * ============================================================
     */

    @Override
    protected void onDisable() {

        if (mc.level != null) {

            /*
             * 恢复所有玩家原队伍。
             */
            for (Entity entity :
                    mc.level.entitiesForRendering()) {

                if (entity instanceof Player player) {

                    restorePlayerTeam(
                            player
                    );

                } else {

                    /*
                     * Mob / Item 从 Nilore Team 移除。
                     */
                    removeEntityFromOurTeam(
                            entity
                    );
                }
            }

            /*
             * 最后删除客户端 Team。
             */
            removeTeam(
                    ENEMY_TEAM
            );

            removeTeam(
                    FRIEND_TEAM
            );

            removeTeam(
                    HOSTILE_TEAM
            );

            removeTeam(
                    FRIENDLY_MOB_TEAM
            );

            removeTeam(
                    ANIMAL_TEAM
            );

            removeTeam(
                    ITEM_TEAM
            );
        }

        oldTeams.clear();

        super.onDisable();
    }

    /*
     * ============================================================
     * Helpers
     * ============================================================
     */

    private boolean isOurTeam(
            String name
    ) {

        return ENEMY_TEAM.equals(name)
                || FRIEND_TEAM.equals(name)
                || HOSTILE_TEAM.equals(name)
                || FRIENDLY_MOB_TEAM.equals(name)
                || ANIMAL_TEAM.equals(name)
                || ITEM_TEAM.equals(name);
    }

    private void removeTeam(
            String name
    ) {

        if (mc.level == null) {
            return;
        }

        Scoreboard scoreboard =
                mc.level.getScoreboard();

        PlayerTeam team =
                scoreboard.getPlayerTeam(
                        name
                );

        if (team != null) {

            scoreboard.removePlayerTeam(
                    team
            );
        }
    }
}