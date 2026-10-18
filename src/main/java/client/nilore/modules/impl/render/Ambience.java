package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import net.minecraft.core.particles.ParticleTypes;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambience 环境氛围模块
 * 完全使用原版粒子系统，确保能完美落到方块上，且兼容任何光影。
 */
public class Ambience extends Module {

    // 5种模式（已删除 Leaves）
    public final ModeSetting mode = new ModeSetting("Mode", "Snow", "Snow", "Cherry", "Gold", "Star", "Heart")
            .withDefault("Snow");

    // 粒子总数，数值越大越密集
    public final NumberSetting amount = new NumberSetting("Amount", 400, 100, 2000, 50);
    // 生成范围半径
    public final NumberSetting range = new NumberSetting("Range", 15.0, 5.0, 30.0, 1.0);

    public Ambience() {
        super("Ambience", Category.RENDER);
    }

    @Override
    public String getName() {
        return "Ambience";
    }

    @Override
    public String getDisplayName() { return "Ambience"; }

    @Override
    public String getModuleName() { return "Ambience"; }

    @Override
    public void onTick() {}

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        // 每 tick 动态计算生成量。总数量 / 20（大概每秒补足一波）
        int totalAmount = amount.getValue().intValue();
        int perTickSpawn = Math.max(1, totalAmount / 20);

        double r = range.getValue().doubleValue();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < perTickSpawn; i++) {
            // 在玩家周围的范围生成
            double px = mc.player.getX() + (random.nextDouble() - 0.5) * r * 2;
            // 高度设定：在玩家头顶上方 3 ~ 15 格之间生成
            double py = mc.player.getY() + 3 + random.nextDouble() * 12;
            double pz = mc.player.getZ() + (random.nextDouble() - 0.5) * r * 2;

            // 赋予向下的初始速度（模拟下落），加一点轻微的横向速度模拟风吹
            double vx = (random.nextDouble() - 0.5) * 0.05;
            double vy = -0.1 - random.nextDouble() * 0.2;
            double vz = (random.nextDouble() - 0.5) * 0.05;

            // 根据当前选中的模式，生成对应的原版粒子
            switch (mode.getValue()) {
                case "Snow" ->
                        mc.level.addParticle(ParticleTypes.SNOWFLAKE, px, py, pz, vx, vy, vz);
                case "Cherry" ->
                        mc.level.addParticle(ParticleTypes.CHERRY_LEAVES, px, py, pz, vx, vy, vz);
                case "Gold" ->
                    // 用发光的魔法粒子代替金币
                        mc.level.addParticle(ParticleTypes.WAX_ON, px, py, pz, vx, vy, vz);
                case "Star" ->
                        mc.level.addParticle(ParticleTypes.END_ROD, px, py, pz, vx, vy, vz);
                case "Heart" ->
                        mc.level.addParticle(ParticleTypes.HEART, px, py, pz, vx, vy, vz);
            }
        }
    }
}