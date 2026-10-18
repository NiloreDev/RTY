package client.nilore.modules.impl.render;

import client.nilore.gui.MaterialClickGui;
import client.nilore.gui.NewClickGui;
import client.nilore.gui.OldClickGui;
import client.nilore.gui.PanelClickGui;
import client.nilore.gui.SuperSoftClickGui;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClickGui extends Module {
    public static ClickGui INSTANCE;
    public static boolean chinese = false;
    public static final Logger LOGGER = LogManager.getLogger(ClickGui.class);

    public final BooleanSetting chineseSetting = new BooleanSetting("Chinese", false);
    public final ModeSetting styleSetting = new ModeSetting("Mode", "SuperSoft", "Old", "Panel", "New", "Material3").withDefault("SuperSoft");
    public final ModeSetting materialTheme = new ModeSetting("Material Theme", "Dark", "Light").withVisibility(() -> this.styleSetting.is("Material3"));
    public final NumberSetting superSoftScale = new NumberSetting("SuperSoft Scale", 80, 50, 150, 5);
    public final NumberSetting superSoftPanelAlpha = new NumberSetting("SuperSoft Panel Alpha", 100, 0, 100, 5);

    public ClickGui() {
        super("ClickGui", Category.RENDER, 344);
        INSTANCE = this;
        this.registerSetting(chineseSetting, styleSetting, materialTheme, superSoftScale, superSoftPanelAlpha);
    }

    // ================================================================
    // === 终极完整中英文翻译词典 (涵盖所有可能的单词) =================
    // ================================================================
    public static String cn(String text) {
        if (!chinese || text == null) return text;
        return switch (text) {
            // ================= 界面分类 =================
            case "Combat" -> "战斗";
            case "Movement" -> "移动";
            case "Player" -> "玩家";
            case "Misc" -> "杂项";
            case "World" -> "世界";

            // ================= 所有模块名 =================
            case "KillAura" -> "杀戮光环";
            case "SuperAura" -> "超级光环";
            case "SuperReach" -> "超级距离";
            case "Velocity" -> "反击退";
            case "AntiBots" -> "反机器人";
            case "Fly" -> "飞行";
            case "NoSlow" -> "无减速";
            case "Scaffold" -> "自动搭路";
            case "Sprint" -> "疾跑";
            case "AutoClicker" -> "自动点击";
            case "AimAssist" -> "瞄准辅助";
            case "ClickGui" -> "点击界面";
            case "AntiFireball" -> "反火球";
            case "AutoTotem" -> "自动图腾";
            case "Auto Offhand" -> "自动副手";
            case "AutoSoup" -> "自动蘑菇煲";
            case "AutoThrow" -> "自动投掷";
            case "Backtrack" -> "回溯";
            case "Critical" -> "暴击";
            case "CrystalAura" -> "水晶光环";
            case "FakeLag" -> "假延迟";
            case "KeepSprint" -> "保持疾跑";
            case "TargetStrafe" -> "目标环绕";
            case "NoJumpDelay" -> "无跳跃延迟";
            case "NoPush" -> "无推动";
            case "FastWeb" -> "快速蜘蛛网";
            case "FireballBlink" -> "火球闪现";
            case "Grimfly" -> "Grim飞行";
            case "ChestStealer" -> "自动开箱";
            case "GhostHand" -> "幽灵手";
            case "InventoryManager" -> "背包管理";
            case "InvManager" -> "物品管理";
            case "NoFall" -> "无摔落伤害";
            case "Stuck" -> "卡位";
            case "Animations" -> "动画";
            case "ArcaneCircle" -> "奥术圆环";
            case "AttackEffect" -> "攻击特效";
            case "Camera" -> "视角";
            case "Cape" -> "披风";
            case "ChestESP" -> "箱子透视";
            case "DamageIndicator" -> "伤害显示";
            case "EntityEditor" -> "实体编辑";
            case "Effects" -> "药水效果";
            case "AutoTools" -> "自动工具";
            case "BedAura" -> "床光环";
            case "BlockIn" -> "方块插入";
            case "BlockIn2" -> "方块插入2";
            case "Debugger" -> "调试器";
            case "Protocol" -> "协议";
            case "Teams" -> "队伍检测";
            case "WebUI" -> "网页界面";
            case "Disabler" -> "禁用检测";
            case "FastPlace" -> "快速放置";
            case "AutoRod" -> "自动鱼竿";
            case "KillSay" -> "击杀喊话";
            case "MusicPlayer" -> "音乐播放器";
            case "AntiTNT" -> "反TNT";
            case "AntiVoid" -> "防虚空";
            case "Wings" -> "翅膀";
            case "FootRing" -> "脚底光环";
            case "AntiWeb" -> "反蜘蛛网";
            case "AutoArmor" -> "自动穿甲";
            case "AntiKick" -> "防踢";
            case "AutoCrossbow" -> "自动弩";
            case "Ambience" -> "环境氛围";
            case "AutoMLG" -> "自动落地水";
            case "AutoWebPlace" -> "自动放网";
            case "Eagle" -> "边缘潜行";
            case "Helper" -> "助手";
            case "MidPearl" -> "中键珍珠";
            case "AspectRatio" -> "宽高比";
            case "BedPlates" -> "床板提示";
            case "BetterChat" -> "聊天优化";
            case "Compass" -> "罗盘";
            case "DamageGlow" -> "伤害发光";
            case "Drep" -> "掉落提示";
            case "EatingHUD" -> "进食显示";
            case "ESP" -> "透视";
            case "FullBright" -> "夜视";
            case "IQBoost" -> "智商增强";
            case "ItemDisplay" -> "物品显示";
            case "ItemTags" -> "物品标签";
            case "JumpCircles" -> "跳跃圆圈";
            case "KillEffect" -> "击杀特效";
            case "LieDetector" -> "测谎仪";
            case "NameProtect" -> "名字保护";
            case "NameTags" -> "名字标签";
            case "NoFov" -> "固定视野";
            case "NoHurtCam" -> "无受击抖动";
            case "PlayerGlow" -> "玩家发光";
            case "Projectiles" -> "投掷物预测";
            case "TimeWeather" -> "锁定时间天气";
            case "Auto Totem" -> "自动图腾";
            case "AutoOffHand" -> "自动副手";
            case "XRay" -> "X射线";
            case "AntiStaff" -> "反管理";
            case "ModuleBedAura" -> "床光环";
            case "AutoPlay" -> "自动匹配";
            case "YsmModel" -> "YSM模型";
            case "AntiWebPlace" -> "反放网";
            case "Nuker" -> "范围破坏";
            case "ChestAura" -> "箱子光环";

            // ================= HUD 模块名 =================
            case "ModuleList" -> "模块列表";
            case "MusicPlayerHud" -> "音乐播放器";
            case "Notification" -> "通知提示";
            case "PlayerList" -> "玩家列表";
            case "Scoreboard" -> "计分板";
            case "TargetHUD" -> "目标信息";
            case "Hotkeys" -> "按键显示";
            case "KeyBinds" -> "按键绑定";
            case "Lyrics" -> "歌词显示";
            case "Watermark" -> "水印";
            case "Interface" -> "界面";
            case "InventoryHud" -> "背包HUD";
            case "Armor" -> "护甲显示";

            // ================= 截图专属词条 (通知 / 声音) =================
            case "Side Mode" -> "侧边模式";
            case "Break" -> "断开";
            case "Show Suffix" -> "显示后缀";
            case "Suffix Color" -> "后缀颜色";
            case "Suffix Brackets" -> "后缀括号";
            case "Margin" -> "边距";
            case "Duration" -> "持续时间";
            case "Max Notifications" -> "最大通知数";
            case "Notifications" -> "通知数";

            // 声音选项
            case "Lever" -> "拉杆";
            case "Sigma" -> "Sigma";
            case "Sweet" -> "甜美";
            case "Bell" -> "铃声";
            case "Pling" -> "叮当";
            case "Click" -> "点击声";
            case "Bass" -> "低音";
            case "Anime" -> "二次元";
            case "Chime" -> "风铃";
            case "Harp" -> "竖琴";
            case "Celesta" -> "钢片琴";
            case "Soft" -> "柔和";
            case "Crystal" -> "水晶";

            // 其它漏掉的风格
            case "Simple" -> "简单";
            case "New" -> "新颖";
            case "Old" -> "旧版";
            case "Panel" -> "面板";

            // ================= 颜色名称 =================
            case "Red" -> "红色";
            case "Gold" -> "金色";
            case "Yellow" -> "黄色";
            case "Green" -> "绿色";
            case "DarkGreen" -> "深绿色";
            case "Aqua" -> "青色";
            case "DarkAqua" -> "深青色";
            case "Blue" -> "蓝色";
            case "DarkBlue" -> "深蓝色";
            case "Pink" -> "粉色";
            case "Purple" -> "紫色";
            case "Gray" -> "灰色";
            case "DarkGray" -> "深灰色";
            case "White" -> "白色";
            case "DarkRed" -> "深红色";
            case "Black" -> "黑色";

            // ================= 其它特效词条 =================
            case "Electric Spark" -> "电火花";
            case "Glow Burst" -> "发光爆发";
            case "Firework Spark" -> "烟花火花";
            case "Totem Burst" -> "图腾爆发";
            case "Cloud Burst" -> "云雾爆发";
            case "Flame Ring" -> "火焰圆环";
            case "Poof Burst" -> "噗噗爆发";
            case "Lava Spark" -> "熔岩火花";
            case "Splash Burst" -> "水花爆发";
            case "Enchant Burst" -> "附魔爆发";
            case "Damage Burst" -> "伤害爆发";
            case "Ground Web" -> "地面蜘蛛网";
            case "Lava" -> "熔岩";
            case "Lava Wait Water Bucket" -> "熔岩等待水桶";
            case "Debug" -> "调试模式";
            case "AutoDisable" -> "自动禁用";
            case "PlaceOrder" -> "放置顺序";
            case "Filter" -> "过滤器";
            case "Blacklist" -> "黑名单";
            case "Whitelist" -> "白名单";
            case "Blocks" -> "方块";
            case "Scan Range" -> "扫描范围";
            case "Placer WallRange" -> "放置墙距";
            case "Auto Jump Roof" -> "自动跳跃屋顶";
            case "Repair Scan" -> "修复扫描";
            case "ESP Keep Seconds" -> "透视保留秒数";
            case "Placer Cooldown Min" -> "最小放置冷却";
            case "Placer Cooldown Max" -> "最大放置冷却";
            case "Placer SlotReset Min" -> "最小槽位重置";
            case "Placer SlotReset Max" -> "最大槽位重置";
            case "Placer ConstructFailResult" -> "放置失败结果";
            case "OnTickRot" -> "刻内旋转";
            case "Place Delay" -> "放置延迟";

            // ================= 其它通用设置项 =================
            case "Mode" -> "模式";
            case "Delay" -> "延迟";
            case "Range" -> "范围";
            case "Reach" -> "触及距离";
            case "Target" -> "目标";
            case "Targets" -> "目标";
            case "Players" -> "玩家";
            case "Mobs" -> "怪物";
            case "Animals" -> "动物";
            case "Invisible" -> "隐身";
            case "Through Walls" -> "穿墙";
            case "Multi Attack" -> "多目标攻击";
            case "Rotation" -> "视角旋转";
            case "Silent" -> "静默";
            case "KeyBind" -> "按键绑定";
            case "Key" -> "按键";
            case "Enabled" -> "启用";
            case "Disabled" -> "禁用";
            case "Speed" -> "速度";
            case "Size" -> "大小";
            case "Color" -> "颜色";
            case "Rainbow" -> "彩虹";
            case "Sound" -> "声音";
            case "Volume" -> "音量";
            case "Render" -> "渲染";
            case "Theme" -> "主题";
            case "Style" -> "风格";
            case "Amount" -> "数量";
            case "Time" -> "时间";
            case "Alpha" -> "透明度";
            case "Auto" -> "自动";
            case "Manual" -> "手动";
            case "Normal" -> "普通";
            case "Vanilla" -> "原版";
            case "None" -> "无";
            case "Max" -> "最大";
            case "Min" -> "最小";
            case "Horizontal" -> "水平";
            case "Vertical" -> "垂直";
            case "Both" -> "双端";

            // ================= 模块高级细节设置项 =================
            case "Attack Player" -> "攻击玩家";
            case "Attack Invisible" -> "攻击隐身";
            case "Attack Animals" -> "攻击动物";
            case "Attack Mobs" -> "攻击怪物";
            case "Infinity Switch" -> "无限切换";
            case "Prefer Baby" -> "优先婴儿";
            case "More Particles" -> "更多粒子";
            case "Override Raycast" -> "覆盖射线检测";
            case "Ignore skip ticks" -> "忽略跳过刻";
            case "Fake AutoBlock" -> "假自动格挡";
            case "Aim Range" -> "瞄准范围";
            case "Attack Range" -> "攻击范围";
            case "Max APS" -> "最大APS";
            case "Min APS" -> "最小APS";
            case "Switch Size" -> "切换数量";
            case "Switch Delay" -> "切换延迟";
            case "FoV" -> "视野";
            case "Hurt Time" -> "受击时间";
            case "Priority" -> "优先级";
            case "Target ESP" -> "目标透视";
            case "Prediction" -> "预测";
            case "Rotation Speed" -> "旋转速度";
            case "Drift" -> "漂移";
            case "Jitter" -> "抖动";
            case "Auto Shield Breaker" -> "自动破盾";
            case "Switch Back" -> "切换回原物品";
            case "Range Circle" -> "范围圆圈";
            case "Circle Color" -> "圆圈颜色";
            case "HurtCam" -> "受击抖动";
            case "Fixed FoV" -> "固定视野";
            case "Kill Say" -> "击杀喊话";
            case "Anti Bot" -> "反机器人";
            case "Teams Check" -> "队伍检测";
            case "Custom Color" -> "自定义颜色";
            case "Scan Speed" -> "扫描速度";
            case "X Offset" -> "X轴偏移";
            case "Y Offset" -> "Y轴偏移";
            case "Z Offset" -> "Z轴偏移";
            case "Scale" -> "缩放";
            case "Width" -> "宽度";
            case "Height" -> "高度";
            case "Rotate" -> "旋转";
            case "Pitch" -> "俯仰角";
            case "Yaw" -> "偏航角";
            case "Distance" -> "距离";
            case "Health" -> "生命值";
            case "Held Item" -> "手持物品";
            case "Ping" -> "延迟";
            case "Sneaking" -> "潜行状态";
            case "Swing" -> "挥动";
            case "Swing Mode" -> "挥动模式";
            case "Server" -> "服务端";
            case "Client" -> "客户端";

            // ================= 药水/Buff/实体 =================
            case "Speed Effect" -> "速度效果";
            case "Jump Boost" -> "跳跃提升";
            case "Regeneration" -> "生命恢复";
            case "Strength" -> "力量";
            case "Weakness" -> "虚弱";
            case "Poison" -> "中毒";
            case "Wither" -> "凋零";
            case "Fire Resistance" -> "抗火";
            case "Water Breathing" -> "水下呼吸";
            case "Invisibility" -> "隐身";
            case "Blindness" -> "失明";
            case "Night Vision" -> "夜视";
            case "Creeper" -> "苦力怕";
            case "Enderman" -> "末影人";
            case "Zombie" -> "僵尸";
            case "Skeleton" -> "骷髅";
            case "Spider" -> "蜘蛛";
            case "Ghast" -> "恶魂";
            case "Pig" -> "猪";
            case "Cow" -> "牛";
            case "Sheep" -> "羊";
            case "Chicken" -> "鸡";
            case "Villager" -> "村民";
            case "Iron Golem" -> "铁傀儡";
            case "Wolf" -> "狼";
            case "Horse" -> "马";

            // ================= 方块名 =================
            case "Obsidian" -> "黑曜石";
            case "Bedrock" -> "基岩";
            case "Cobblestone" -> "圆石";
            case "Dirt" -> "泥土";
            case "Grass" -> "草方块";
            case "Sand" -> "沙子";
            case "Gravel" -> "沙砾";
            case "Wood" -> "木头";
            case "Planks" -> "木板";
            case "Glass" -> "玻璃";
            case "Wool" -> "羊毛";
            case "Carpet" -> "地毯";
            case "Ender Chest" -> "末影箱";
            case "Chest" -> "箱子";
            case "Furnace" -> "熔炉";
            case "Crafting Table" -> "工作台";
            case "Anvil" -> "铁砧";
            case "Enchanting Table" -> "附魔台";

            // ================= 按键名 =================
            case "Left Shift" -> "左Shift";
            case "Right Shift" -> "右Shift";
            case "Left Control" -> "左Ctrl";
            case "Right Control" -> "右Ctrl";
            case "Left Alt" -> "左Alt";
            case "Right Alt" -> "右Alt";
            case "Space" -> "空格";
            case "Enter" -> "回车";
            case "Escape" -> "退出";
            case "Tab" -> "Tab";
            case "Backspace" -> "退格";
            case "Delete" -> "删除";
            case "Insert" -> "插入";
            case "Home" -> "Home";
            case "End" -> "End";
            case "Page Up" -> "上翻页";
            case "Page Down" -> "下翻页";
            case "Caps Lock" -> "大写锁定";
            case "Scroll Lock" -> "滚动锁定";
            case "Pause" -> "暂停";
            case "Print Screen" -> "截屏";
            case "Num Lock" -> "数字锁定";
            case "Left" -> "左";
            case "Right" -> "右";
            case "Up" -> "上";
            case "Down" -> "下";

            default -> text;
        };
    }

    @Override
    public String getDisplayName() { return ""; }

    @Override
    public String getModuleName() { return ""; }

    @Override
    protected void onEnable() {
        try {
            chinese = chineseSetting.getValue();
            if (this.styleSetting.is("SuperSoft")) {
                float scale = this.superSoftScale.getValue().floatValue() / 100.0f;
                float panelAlpha = this.superSoftPanelAlpha.getValue().floatValue() / 100.0f;
                mc.setScreen(new SuperSoftClickGui(scale, panelAlpha));
            } else if (this.styleSetting.is("Old")) {
                mc.setScreen(new OldClickGui());
            } else if (this.styleSetting.is("Panel")) {
                mc.setScreen(PanelClickGui.panelClickGui);
            } else if (this.styleSetting.is("Material3")) {
                mc.setScreen(MaterialClickGui.instance);
            } else {
                mc.setScreen(new NewClickGui());
            }
        } catch (Exception exception) {
            LOGGER.error("Error opening ClickGUI", exception);
        } finally {
            this.setEnabled(false);
        }
    }

    @Override
    public void onTick() {}
}