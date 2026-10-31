package client.nilore.modules.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.AntiBots;
import client.nilore.modules.impl.world.Teams;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.Fonts;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.game.ItemAlertTracker;
import client.nilore.utils.math.MathUtil;
import client.nilore.utils.math.Vector2f;
import client.nilore.utils.render.ProjectionUtil;

public class OpalNameTag extends Module {

    private static final int COLOR_LIGHT_GRAY = Color.LIGHT_GRAY.getRGB();
    private static final int COLOR_WHITE = Color.WHITE.getRGB();
    private static final int COLOR_RED = new Color(255, 85, 85).getRGB();
    private static final int COLOR_GREEN = new Color(85, 255, 85).getRGB();
    private static final int COLOR_GOLD = new Color(255, 215, 0).getRGB();
    private static final int COLOR_AQUA = new Color(85, 255, 255).getRGB();

    private static final int COLOR_ITEM_MACE = new Color(255, 85, 85).getRGB();
    private static final int COLOR_ITEM_NETHERITE = new Color(190, 60, 60).getRGB();
    private static final int COLOR_ITEM_RED_SPECIAL = new Color(255, 85, 85).getRGB();
    private static final int COLOR_ITEM_GOLD = new Color(255, 215, 0).getRGB();

    private static final int ENCHANT_CARD_BG = 0x00000000;

    private static final Map<String, Integer> ENCHANT_COLORS = new HashMap<>();

    static {
        ENCHANT_COLORS.put("protection",             0xFF55FFFF);
        ENCHANT_COLORS.put("fire_protection",        0xFFFFAA00);
        ENCHANT_COLORS.put("feather_falling",        0xFF55FFFF);
        ENCHANT_COLORS.put("blast_protection",       0xFFFF5555);
        ENCHANT_COLORS.put("projectile_protection",  0xFF55FFFF);
        ENCHANT_COLORS.put("respiration",            0xFF5555FF);
        ENCHANT_COLORS.put("aqua_affinity",          0xFF5555FF);
        ENCHANT_COLORS.put("thorns",                 0xFFFF5555);
        ENCHANT_COLORS.put("depth_strider",          0xFF00AAFF);
        ENCHANT_COLORS.put("frost_walker",           0xFF55FFFF);
        ENCHANT_COLORS.put("binding_curse",          0xFFFF5555);
        ENCHANT_COLORS.put("soul_speed",             0xFF55FFFF);
        ENCHANT_COLORS.put("swift_sneak",            0xFF55FFFF);

        ENCHANT_COLORS.put("sharpness",              0xFF55FF55);
        ENCHANT_COLORS.put("smite",                  0xFFFFFF55);
        ENCHANT_COLORS.put("bane_of_arthropods",     0xFFFFFF55);
        ENCHANT_COLORS.put("knockback",              0xFFFFFF55);
        ENCHANT_COLORS.put("fire_aspect",            0xFFFFAA00);
        ENCHANT_COLORS.put("looting",                0xFFFFFF55);
        ENCHANT_COLORS.put("sweeping",               0xFFFFFF55);
        ENCHANT_COLORS.put("sweeping_edge",          0xFFFFFF55);

        ENCHANT_COLORS.put("efficiency",             0xFF55FFFF);
        ENCHANT_COLORS.put("silk_touch",             0xFFFFFF55);
        ENCHANT_COLORS.put("unbreaking",             0xFFFFAA00);
        ENCHANT_COLORS.put("fortune",                0xFF55FFFF);

        ENCHANT_COLORS.put("power",                  0xFFFF5555);
        ENCHANT_COLORS.put("punch",                  0xFFFF5555);
        ENCHANT_COLORS.put("flame",                  0xFFFFAA00);
        ENCHANT_COLORS.put("infinity",               0xFF55FFFF);

        ENCHANT_COLORS.put("luck_of_the_sea",        0xFF55FFFF);
        ENCHANT_COLORS.put("lure",                   0xFF55FFFF);

        ENCHANT_COLORS.put("loyalty",                0xFF55FFFF);
        ENCHANT_COLORS.put("impaling",               0xFFFF5555);
        ENCHANT_COLORS.put("riptide",                0xFF55FFFF);
        ENCHANT_COLORS.put("channeling",             0xFFFF55FF);

        ENCHANT_COLORS.put("multishot",              0xFF55FFFF);
        ENCHANT_COLORS.put("quick_charge",           0xFF55FFFF);
        ENCHANT_COLORS.put("piercing",               0xFF55FFFF);

        ENCHANT_COLORS.put("mending",                0xFFFF55FF);
        ENCHANT_COLORS.put("vanishing_curse",        0xFFFF5555);

        ENCHANT_COLORS.put("density",                0xFF55FFFF);
        ENCHANT_COLORS.put("breach",                 0xFFFF5555);
        ENCHANT_COLORS.put("wind_burst",             0xFF55FFFF);
    }

    private static final int DEFAULT_ENCHANT_COLOR = 0xFFFFFFFF;

    public static final Map<String, AtomicInteger> scoreboardHealthMap = new ConcurrentHashMap<>();

    // ==================== 模块设置 ====================
    public final NumberSetting scaleSetting = new NumberSetting("Scale", 0.7, 0.5, 10.0, 0.01);
    public final NumberSetting distanceSetting = new NumberSetting("Max Distance", 128.0, 8.0, 256.0, 1.0);

    public final BooleanSetting showHealthSetting = new BooleanSetting("Invisibles", true);
    public final BooleanSetting showPingSetting   = new BooleanSetting("Hide Teammates", false);
    public final BooleanSetting showArmorSetting  = new BooleanSetting("Show Artifacts", true);

    public final BooleanSetting showDistanceSetting = new BooleanSetting("Show Distance", false);
    public final BooleanSetting showEquipmentSetting = new BooleanSetting("Nametags+ Equipment", true);
    public final BooleanSetting showHeldItemSetting  = new BooleanSetting("Show Held Item", true);
    public final BooleanSetting showArmorItemsSetting = new BooleanSetting("Show Armor Items", true);
    public final BooleanSetting showEnchantmentsSetting = new BooleanSetting("Show Enchantments", false);
    public final BooleanSetting showGearSetting = new BooleanSetting("Show Gear", true);

    public final BooleanSetting showItemCountSetting = new BooleanSetting("Show Item Count", true);
    public final BooleanSetting showItemBackgroundSetting = new BooleanSetting("Item Background", true);

    public final BooleanSetting killerSetting = new BooleanSetting("Killer", false);

    public final BooleanSetting showPlayersSetting = new BooleanSetting("Show Players", true);
    public final BooleanSetting showAnimalsSetting = new BooleanSetting("Show Animals", false);
    public final BooleanSetting showMonstersSetting = new BooleanSetting("Show Monsters", false);
    public final BooleanSetting showItemsSetting = new BooleanSetting("Show Items", false);
    public final BooleanSetting showOtherEntitiesSetting = new BooleanSetting("Show Other Entities", false);
    public final BooleanSetting showInvisiblePlayersSetting = new BooleanSetting("Show Invisible", false);

    public final BooleanSetting opalBackgroundSetting = new BooleanSetting("Opal Background", true);
    public final NumberSetting opalBackgroundAlphaSetting = new NumberSetting("Opal Background Alpha", 0.47, 0.0, 1.0, 0.01);

    // ==================== 内部数据 ====================
    private final FontRenderer mainFont;
    private final FontRenderer nameFont;
    private final FontRenderer iconFont;
    private final Paint paint;
    private final Map<Entity, Vector2f> entityPositions;
    private final Map<UUID, Long> itemCheckTimestamps;
    private final Map<String, String> decodedNameCache;
    private final Map<String, Long> nameDecodeTimestamps;
    private final DecimalFormat df = new DecimalFormat("#.#");

    private final Map<UUID, ItemStack> killerMemory = new ConcurrentHashMap<>();
    private final Set<UUID> killerNotified = ConcurrentHashMap.newKeySet();

    public OpalNameTag() {
        super("OpalNameTag", Category.RENDER);

        this.mainFont = FontPresets.pingfang(28.0f);
        this.nameFont = Fonts.getRenderer("AstaSans-Medium.ttf", 28.0f);
        this.iconFont = Fonts.getRenderer("MaterialIcons-Regular.ttf", 28.0f);
        this.paint = new Paint();
        this.entityPositions = new ConcurrentHashMap<>();
        this.itemCheckTimestamps = new HashMap<>();
        this.decodedNameCache = new HashMap<>();
        this.nameDecodeTimestamps = new HashMap<>();
    }

    @Override
    public String getModuleName() {
        return "OpalNameTag";
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getDisplayName() {
        return "Opal NameTags";
    }

    @Override
    public void onEnable() {
        this.entityPositions.clear();
        this.killerMemory.clear();
        this.killerNotified.clear();
        ItemAlertTracker.clear();
    }

    @Override
    public void onDisable() {
        this.onEnable();
    }

    private int getPaddingColor() {
        if (!this.opalBackgroundSetting.getValue()) {
            return 0x00000000;
        }
        float alpha = this.opalBackgroundAlphaSetting.getValue().floatValue();
        if (alpha <= 0.0f) {
            return 0x00000000;
        }
        int a = Math.round(alpha * 255.0f);
        if (a > 255) a = 255;
        return (a << 24);
    }

    private boolean shouldShowEntity(Entity entity) {
        if (entity instanceof ArmorStand) {
            return false;
        }

        if (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity)) {
            return false;
        }

        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;

        double rangeSq = Math.pow(this.distanceSetting.getValue().doubleValue(), 2.0);
        if (entity.distanceToSqr(mc.player) > rangeSq) return false;
        if (entity.getName().getString().startsWith("CIT-")) return false;

        boolean isInvisible = entity.isInvisible();
        if (isInvisible && !this.showInvisiblePlayersSetting.getValue()) return false;

        if (entity instanceof Player) {
            if (!this.showPlayersSetting.getValue()) return false;
            if (this.showPingSetting.getValue() && Teams.isSameTeam(entity)) return false;
            return true;
        }

        if (entity instanceof Monster) {
            return this.showMonstersSetting.getValue();
        }

        if (entity instanceof Animal) {
            return this.showAnimalsSetting.getValue();
        }

        if (entity instanceof ItemEntity) {
            return this.showItemsSetting.getValue();
        }

        return this.showOtherEntitiesSetting.getValue();
    }

    private void updatePositions(RenderEvent event) {
        if (mc.level == null || mc.player == null) {
            this.entityPositions.clear();
            this.killerMemory.clear();
            this.killerNotified.clear();
            ItemAlertTracker.clear();
            return;
        }
        ProjectionUtil.updateMatrices();
        float partial = event.partialTick();
        HashSet<Entity> seen = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!shouldShowEntity(entity)) continue;

            double x = MathUtil.lerp(partial, entity.xo, entity.getX());
            double y = MathUtil.lerp(partial, entity.yo, entity.getY()) + entity.getBbHeight() + 0.5;
            double z = MathUtil.lerp(partial, entity.zo, entity.getZ());
            Vector2f screen = ProjectionUtil.project(x, y, z);
            if (screen == null) continue;
            screen.setY(screen.getY() - 2.0f);
            this.entityPositions.put(entity, screen);
            seen.add(entity);
        }
        this.entityPositions.keySet().removeIf(e -> !seen.contains(e));
        ItemAlertTracker.updateItems(seen);

        if (this.killerSetting.getValue()) {
            Set<UUID> aliveUuids = new HashSet<>();
            for (Player p : mc.level.players()) {
                if (p != null && p.isAlive()) {
                    aliveUuids.add(p.getUUID());
                }
            }
            this.killerMemory.keySet().removeIf(uuid -> !aliveUuids.contains(uuid));
            this.killerNotified.removeIf(uuid -> !aliveUuids.contains(uuid));
        }
    }

    @EventTarget
    public void onRender(RenderEvent renderEvent) {
        try {
            this.updatePositions(renderEvent);
        } catch (Exception ignored) {
        }
    }

    private String getEntityDisplayName(Entity entity) {
        if (entity instanceof Player) {
            return entity.getDisplayName().getString();
        } else if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            String name = resolveItemDisplayName(stack, entity);

            if (this.showItemCountSetting.getValue()) {
                int count = Math.max(1, stack.getCount());
                name = name + " x" + count;
            }
            return name;
        } else {
            return entity.getName().getString();
        }
    }

    private String resolveItemDisplayName(ItemStack stack, Entity fallbackEntity) {
        if (stack == null || stack.isEmpty()) {
            return fallbackEntity.getName().getString();
        }
        String rawName = fallbackEntity.getName().getString();
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return rawName;
        }
        String path = key.getPath();

        if (path.equals("mace")) {
            String localized = safeGetLocalizedName("item.minecraft.mace");
            if (localized != null && !localized.isEmpty()) {
                return localized;
            }
            return "Mace";
        }

        if (path.equals("netherite_spear")) {
            String localized = safeGetLocalizedName("item.minecraft.netherite_spear");
            if (localized != null && !localized.isEmpty()) {
                return localized;
            }
            return "Netherite Spear";
        }

        return rawName;
    }

    private String safeGetLocalizedName(String translationKey) {
        try {
            String s = Component.translatable(translationKey).getString();
            if (s == null || s.isEmpty() || s.equals(translationKey)) {
                return null;
            }
            return s;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String getEntityTypeIcon(Entity entity) {
        if (entity instanceof Player) return "";
        if (entity instanceof Monster) return "";
        if (entity instanceof Animal) return "";
        if (entity instanceof ItemEntity) return "";
        return "";
    }

    private int getItemEntityNameColor(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (stack == null || stack.isEmpty()) {
            return COLOR_WHITE;
        }

        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return COLOR_WHITE;
        String path = key.getPath();

        if (path.equals("mace")) {
            return COLOR_ITEM_MACE;
        }

        if (path.startsWith("netherite_")) {
            return COLOR_ITEM_NETHERITE;
        }

        if (path.equals("enchanted_golden_apple")) return COLOR_ITEM_RED_SPECIAL;
        if (path.equals("ender_pearl"))            return COLOR_ITEM_RED_SPECIAL;
        if (path.equals("end_crystal"))            return COLOR_ITEM_RED_SPECIAL;
        if (path.equals("trident"))                return COLOR_ITEM_RED_SPECIAL;
        if (path.equals("conduit"))                return COLOR_ITEM_RED_SPECIAL;
        if (path.equals("heart_of_the_sea"))       return COLOR_ITEM_RED_SPECIAL;

        if (path.equals("golden_apple"))           return COLOR_ITEM_GOLD;
        if (path.startsWith("diamond_"))           return COLOR_ITEM_GOLD;

        return COLOR_WHITE;
    }

    private int getEntityNameColor(Entity entity) {
        if (entity instanceof Player) {
            return !Teams.isSameTeam(entity) ? COLOR_RED : COLOR_GREEN;
        }
        if (entity instanceof Monster) {
            return COLOR_RED;
        }
        if (entity instanceof Animal) {
            return COLOR_GREEN;
        }
        if (entity instanceof ItemEntity itemEntity) {
            return getItemEntityNameColor(itemEntity);
        }
        return COLOR_WHITE;
    }

    private float getEntityHealth(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.getHealth();
        }
        return -1;
    }

    private static String getEnchantKey(Enchantment enchantment) {
        String path = enchantment.getDescriptionId();
        if (path == null) return "";
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private List<EnchantLine> getEnchantDisplayLines(ItemStack itemStack) {
        List<EnchantLine> lines = new ArrayList<>();
        if (itemStack == null || itemStack.isEmpty()) {
            return lines;
        }

        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(itemStack);
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            String enchantName = Component
                    .translatable(enchantment.getDescriptionId())
                    .getString();
            String text = enchantName + " " + level;
            int color = ENCHANT_COLORS.getOrDefault(getEnchantKey(enchantment), DEFAULT_ENCHANT_COLOR);
            lines.add(new EnchantLine(text, color));
        }
        return lines;
    }

    private boolean isKillerWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof SwordItem) return true;
        if (stack.getItem() instanceof BowItem) return true;
        if (stack.getItem() instanceof CrossbowItem) return true;
        return false;
    }

    private boolean isNotifyWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return false;
        String path = key.getPath();
        if (path.equals("diamond_sword")) return true;
        if (path.equals("bow")) return true;
        return false;
    }

    private List<ItemStack> collectGearItems(AbstractClientPlayer player) {
        if (this.killerSetting.getValue()) {
            UUID uuid = player.getUUID();

            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            ItemStack notifyItem = null;

            if (isKillerWeapon(main)) {
                this.killerMemory.put(uuid, main.copy());
                if (isNotifyWeapon(main)) {
                    notifyItem = main;
                }
            } else if (isKillerWeapon(off)) {
                this.killerMemory.put(uuid, off.copy());
                if (isNotifyWeapon(off)) {
                    notifyItem = off;
                }
            }

            if (notifyItem != null && !this.killerNotified.contains(uuid)) {
                this.killerNotified.add(uuid);
                final String playerName = player.getName().getString();

                ResourceLocation notifyKey = BuiltInRegistries.ITEM.getKey(notifyItem.getItem());
                String notifyPath = notifyKey == null ? "" : notifyKey.getPath();

                String roleText = null;
                String roleColor = null;

                if (notifyPath.equals("diamond_sword")) {
                    roleText = "杀手";
                    roleColor = "§c";
                } else if (notifyPath.equals("bow")) {
                    roleText = "侦探";
                    roleColor = "§b";
                }

                if (roleText != null && mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("§7[NameTags] §f" + playerName
                                    + roleColor + " 是" + roleText),
                            false
                    );
                }
            }

            ItemStack remembered = this.killerMemory.get(uuid);
            if (remembered != null && !remembered.isEmpty()) {
                return Collections.singletonList(remembered);
            }
            return Collections.emptyList();
        }

        List<ItemStack> gear = new ArrayList<>();

        if (this.showHeldItemSetting.getValue()) {
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            if (!mainHand.isEmpty()) gear.add(mainHand);
            if (!offHand.isEmpty()) gear.add(offHand);
        }

        if (this.showArmorItemsSetting.getValue()) {
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
            ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
            if (!helmet.isEmpty()) gear.add(helmet);
            if (!chest.isEmpty()) gear.add(chest);
            if (!legs.isEmpty()) gear.add(legs);
            if (!feet.isEmpty()) gear.add(feet);
        }

        return gear;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.entityPositions.isEmpty() || mc.level == null) {
            return;
        }
        float scale = this.scaleSetting.getValue().floatValue();
        int padding = 4;
        int gap = 4;
        float corner = 6.0f;
        boolean showHealth = this.showHealthSetting.getValue();
        boolean showArmor = this.showArmorSetting.getValue();
        boolean showGear = this.showGearSetting.getValue();
        boolean showEnchants = this.showEnchantmentsSetting.getValue();

        final int paddingColor = getPaddingColor();

        List<ItemRenderData> deferredItems = new ArrayList<>();
        Renderer.renderConsumer(ctx -> {
            float ascent = this.mainFont.getMetrics().ascent();
            float mainLine = this.mainFont.getMetrics().getLineHeight();
            float nameLine = this.nameFont.getMetrics().getLineHeight();

            for (Map.Entry<Entity, Vector2f> entry : this.entityPositions.entrySet()) {
                Entity entity = entry.getKey();
                Vector2f screenPos = entry.getValue();
                screenPos.set(Math.round(screenPos.x), Math.round(screenPos.y));

                String displayName = getEntityDisplayName(entity);
                String typeIcon = getEntityTypeIcon(entity);
                int nameColor = getEntityNameColor(entity);

                boolean isPlayer = entity instanceof AbstractClientPlayer;
                AbstractClientPlayer player = isPlayer ? (AbstractClientPlayer) entity : null;

                boolean isItemEntity = entity instanceof ItemEntity;
                boolean drawNameBg = !isItemEntity || this.showItemBackgroundSetting.getValue();

                String healthText = "";
                float health = getEntityHealth(entity);
                if (showHealth && health >= 0) {
                    healthText = df.format(health);
                }

                int distance = (int) mc.player.distanceTo(entity);
                String distanceIcon = "";
                String distanceText = distance + "m";

                String absorbText = "";
                if (isPlayer && player != null) {
                    int absorb = Math.round(player.getAbsorptionAmount());
                    absorbText = absorb > 0 ? String.valueOf(absorb) : "";
                }

                boolean hasAlerts = false;
                int alertCount = 0;
                Set<ItemStack> alertItems = new HashSet<>();
                if (isPlayer && player != null && showArmor) {
                    long now = System.currentTimeMillis();
                    UUID uuid = player.getUUID();
                    Long last = this.itemCheckTimestamps.get(uuid);
                    if (last == null || now - last >= 250L) {
                        ItemAlertTracker.trackPlayerItem(player, player.getMainHandItem());
                        ItemStack main = player.getMainHandItem();
                        if (ItemAlertTracker.isNewItem(main)) {
                            ItemAlertTracker.trackEntityItem(player, main);
                        }
                        ItemStack off = player.getOffhandItem();
                        if (ItemAlertTracker.isNewItem(off)) {
                            ItemAlertTracker.trackEntityItem(player, off);
                        }
                        this.itemCheckTimestamps.put(uuid, now);
                    }
                    alertItems = ItemAlertTracker.getEntityItems(player);
                    alertCount = showArmor && !alertItems.isEmpty() ? alertItems.size() : 0;
                    hasAlerts = alertCount > 0;
                }

                float distIconW = this.iconFont.getBounds(distanceIcon).getWidth();
                float distTextW = this.nameFont.getBounds(distanceText).getWidth();
                float distBoxW = distIconW + 2.0f + distTextW + padding * 2;

                float typeIconW = this.iconFont.getBounds(typeIcon).getWidth();
                float displayW = this.mainFont.getBounds(displayName).getWidth();
                float nameBoxW = typeIconW + 6.0f + displayW + padding * 2;

                float healthIconW = this.iconFont.getBounds("\uE87D").getWidth();
                float healthTextW = healthText.isEmpty() ? 0 : this.nameFont.getBounds(healthText).getWidth();
                float healthBoxW = healthText.isEmpty() ? 0 : healthIconW + 2.0f + healthTextW + padding * 2;

                float absorbIconW = this.iconFont.getBounds("\uE87D").getWidth();
                float absorbTextW = absorbText.isEmpty() ? 0 : this.nameFont.getBounds(absorbText).getWidth();
                float absorbBoxW = absorbText.isEmpty() ? 0 : absorbIconW + 2.0f + absorbTextW + padding * 2;

                float rowHeight = Math.max(mainLine, nameLine) + padding * 2 - 4.0f;
                float itemBoxH = rowHeight;
                float itemTotalW = itemBoxH * alertCount + (alertCount > 0 ? gap * (alertCount - 1) : 0);

                int boxCount = 2;
                if (!healthText.isEmpty()) boxCount++;
                if (!absorbText.isEmpty()) boxCount++;
                boxCount += alertCount;

                float fullWidth = distBoxW + nameBoxW + healthBoxW + absorbBoxW + itemTotalW + gap * (boxCount > 1 ? boxCount - 1 : 0);
                float originX = -fullWidth / 2.0f;
                float originY = -rowHeight;

                ctx.save();
                ctx.translate(screenPos.x, screenPos.y);
                ctx.scale(scale, scale);
                float cursorX = originX;
                float textBaseline = originY + padding - 1.0f + ascent + 28.0f;

                // 距离框
                this.paint.setColor(paddingColor);
                ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, distBoxW, rowHeight, corner), this.paint);
                this.paint.setColor(COLOR_LIGHT_GRAY);
                ctx.drawString(distanceIcon, cursorX + padding, textBaseline + 1.0f, this.iconFont, this.paint);
                ctx.drawString(distanceText, cursorX + padding + distIconW + 2.0f, textBaseline - 1.0f, this.nameFont, this.paint);
                cursorX += distBoxW + gap;

                // 名称框
                if (drawNameBg) {
                    this.paint.setColor(paddingColor);
                    ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, nameBoxW + 2.0f, rowHeight, corner), this.paint);
                }
                this.paint.setColor(COLOR_WHITE);
                ctx.drawString(typeIcon, cursorX + padding, textBaseline, this.iconFont, this.paint);
                this.paint.setColor(nameColor);
                ctx.drawString(displayName, cursorX + padding + typeIconW + 6.0f, textBaseline - 3.0f, this.mainFont, this.paint);
                cursorX += nameBoxW + gap;

                // 血量框
                if (!healthText.isEmpty()) {
                    this.paint.setColor(paddingColor);
                    ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, healthBoxW, rowHeight, corner), this.paint);
                    this.paint.setColor(COLOR_RED);
                    ctx.drawString("\uE87D", cursorX + padding, textBaseline + 1.0f, this.iconFont, this.paint);

                    float healthPercent = health / 20.0f;
                    int healthColor;
                    if (healthPercent > 0.5f) {
                        healthColor = COLOR_GREEN;
                    } else if (healthPercent > 0.25f) {
                        healthColor = COLOR_GOLD;
                    } else {
                        healthColor = COLOR_RED;
                    }
                    this.paint.setColor(healthColor);
                    ctx.drawString(healthText, cursorX + padding + healthIconW + 2.5f, textBaseline - 1.0f, this.nameFont, this.paint);
                    cursorX += healthBoxW;
                }

                // 吸收血量
                if (!absorbText.isEmpty()) {
                    cursorX += gap;
                    this.paint.setColor(paddingColor);
                    ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, absorbBoxW, rowHeight, corner), this.paint);
                    this.paint.setColor(COLOR_GOLD);
                    ctx.drawString("\uE87D", cursorX + padding, textBaseline + 1.0f, this.iconFont, this.paint);
                    this.paint.setColor(COLOR_WHITE);
                    ctx.drawString(absorbText, cursorX + padding + absorbIconW + 2.5f, textBaseline, this.nameFont, this.paint);
                    cursorX += absorbBoxW;
                }

                // 物品警报
                if (hasAlerts) {
                    for (ItemStack item : alertItems) {
                        if (ItemAlertTracker.hasItem(player.getUUID(), item.getItem())) continue;
                        cursorX += gap;
                        this.paint.setColor(paddingColor);
                        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(cursorX, originY, itemBoxH, rowHeight, corner), this.paint);
                        float itemX = screenPos.x + cursorX * scale;
                        float itemY = screenPos.y + originY * scale;
                        float itemSize = rowHeight * scale;
                        float centerX = itemX + itemSize / 2.0f;
                        float centerY = itemY + itemSize / 2.0f;
                        deferredItems.add(new ItemRenderData(
                                item,
                                new Vector2f(centerX - 5.0f, centerY - 5.0f),
                                Collections.emptyList(),
                                20,
                                scale
                        ));
                        cursorX += itemBoxH;
                    }
                }

                ctx.restore();

                // ========== 装备 + 手持物品 ==========
                if (isPlayer && player != null && showGear) {
                    List<ItemStack> gearItems = collectGearItems(player);
                    if (!gearItems.isEmpty()) {
                        final float itemSize = 16.0f;
                        final int cardGapPx = 3;
                        final int cardPaddingPx = 4;
                        final int minCardWidthPx = 22;

                        int itemScreenSize = Math.max(1, Math.round(itemSize * scale));

                        List<List<EnchantLine>> enchantLinesList = new ArrayList<>();
                        int[] cardWidths = new int[gearItems.size()];
                        int totalGearWidth = 0;

                        for (int i = 0; i < gearItems.size(); i++) {
                            ItemStack item = gearItems.get(i);

                            List<EnchantLine> lines = showEnchants
                                    ? getEnchantDisplayLines(item)
                                    : Collections.emptyList();
                            enchantLinesList.add(lines);

                            int maxTextWidth = 0;
                            for (EnchantLine line : lines) {
                                maxTextWidth = Math.max(maxTextWidth, mc.font.width(line.text()));
                            }

                            int cardWidth = Math.max(
                                    Math.max(itemScreenSize + 4, minCardWidthPx),
                                    maxTextWidth + cardPaddingPx * 2
                            );
                            cardWidths[i] = cardWidth;
                            totalGearWidth += cardWidth;
                        }
                        totalGearWidth += cardGapPx * (gearItems.size() - 1);

                        float gearTopLogicalY = originY - 2.0f - itemSize;
                        int itemTopPx = Math.round(screenPos.y + gearTopLogicalY * scale);
                        int cursorPx = Math.round(screenPos.x) - totalGearWidth / 2;

                        for (int i = 0; i < gearItems.size(); i++) {
                            ItemStack item = gearItems.get(i);
                            int cardWidth = cardWidths[i];
                            int itemXPx = cursorPx + (cardWidth - itemScreenSize) / 2;

                            deferredItems.add(new ItemRenderData(
                                    item,
                                    new Vector2f(itemXPx, itemTopPx),
                                    enchantLinesList.get(i),
                                    cardWidth,
                                    scale
                            ));

                            cursorPx += cardWidth + cardGapPx;
                        }
                    }
                }
            }
        });

        for (ItemRenderData data : deferredItems) {
            if (!data.enchantLines.isEmpty()) {
                drawEnchantCard(event, data);
            }
        }
        for (ItemRenderData data : deferredItems) {
            PoseStack stack = event.guiGraphics().pose();
            stack.pushPose();
            stack.translate(data.position.x, data.position.y, 0.0f);
            stack.scale(data.scale, data.scale, 1.0f);
            event.guiGraphics().renderItem(data.itemStack, 0, 0);
            event.guiGraphics().renderItemDecorations(mc.font, data.itemStack, 0, 0);
            stack.popPose();
        }
    }

    private void drawEnchantCard(Render2DEvent event, ItemRenderData data) {
        List<EnchantLine> lines = data.enchantLines;
        int lineHeight = mc.font.lineHeight + 1;
        int cardWidth = data.cardWidth;
        int cardHeight = lines.size() * lineHeight + 4;

        int itemScreenSize = Math.max(1, Math.round(16 * data.scale));
        int itemCenterX = Math.round(data.position.x) + itemScreenSize / 2;

        int cardLeft = itemCenterX - cardWidth / 2;
        int cardBottom = Math.round(data.position.y) - 2;
        int cardTop = cardBottom - cardHeight;

        for (int i = 0; i < lines.size(); i++) {
            EnchantLine line = lines.get(i);
            String text = line.text();
            int textWidth = mc.font.width(text);
            int textX = cardLeft + (cardWidth - textWidth) / 2;
            int textY = cardTop + 2 + i * lineHeight;
            event.guiGraphics().drawString(
                    mc.font,
                    text,
                    textX,
                    textY,
                    line.color(),
                    true
            );
        }
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        if (!(packetEvent.getPacket() instanceof ClientboundSetScorePacket packet)) return;
        if (mc.level == null || mc.player == null) return;
        String objective = packet.getObjectiveName();
        if (!"belowHealth".equals(objective) && !"health".equals(objective)) return;
        if (packet.getOwner().equals(mc.player.getGameProfile().getName())) return;
        scoreboardHealthMap.computeIfAbsent(packet.getOwner(), k -> new AtomicInteger()).set(packet.getScore());
    }

    private String getDecodedName(String name) {
        long now = System.currentTimeMillis();
        Long last = this.nameDecodeTimestamps.get(name);
        if (last != null && now - last < 1000L) {
            return this.decodedNameCache.get(name);
        }
        this.decodedNameCache.put(name, name);
        this.nameDecodeTimestamps.put(name, now);
        return name;
    }

    private record EnchantLine(String text, int color) {
    }

    private record ItemRenderData(
            ItemStack itemStack,
            Vector2f position,
            List<EnchantLine> enchantLines,
            int cardWidth,
            float scale
    ) {
    }
}