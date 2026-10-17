package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.EntityRemoveEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

public final class AttackEffect extends Module {

    private final BooleanSetting lightning =
            new BooleanSetting("Lightning", true);

    private final ModeSetting lightningMode =
            new ModeSetting(
                    "Lightning Mode",
                    "Thin",
                    "Vanilla"
            ).withDefault("Thin");

    private final BooleanSetting bloodExplosion =
            new BooleanSetting(
                    "Blood Explosion",
                    true
            );

    private final BooleanSetting explosion =
            new BooleanSetting(
                    "Explosion",
                    true
            );

    // ================= 冷却时间拖动设置 =================
    // 名称: Cooldown, 默认值: 1.0, 最小值: 0.0, 最大值: 10.0, 步长: 0.5
    private final NumberSetting cooldown =
            new NumberSetting("Cooldown", 1.0, 0.0, 10.0, 0.1);
    // ====================================================

    private final BooleanSetting critBurst =
            new BooleanSetting("Crit Burst", false);

    private final BooleanSetting magicCrit =
            new BooleanSetting("Magic Crit", false);

    private final BooleanSetting smokeBurst =
            new BooleanSetting("Smoke Burst", false);

    private final BooleanSetting largeSmoke =
            new BooleanSetting("Large Smoke", false);

    private final BooleanSetting portalBurst =
            new BooleanSetting("Portal Burst", false);

    private final BooleanSetting reversePortal =
            new BooleanSetting("Reverse Portal", false);

    private final BooleanSetting endRod =
            new BooleanSetting("End Rod", false);

    private final BooleanSetting hearts =
            new BooleanSetting("Hearts", false);

    private final BooleanSetting happyVillager =
            new BooleanSetting("Happy Villager", false);

    private final BooleanSetting witchMagic =
            new BooleanSetting("Witch Magic", false);

    private final BooleanSetting soulBurst =
            new BooleanSetting("Soul Burst", false);

    private final BooleanSetting soulFlame =
            new BooleanSetting("Soul Flame", false);

    private final BooleanSetting dragonBreath =
            new BooleanSetting("Dragon Breath", false);

    private final BooleanSetting electricSpark =
            new BooleanSetting("Electric Spark", false);

    private final BooleanSetting glowBurst =
            new BooleanSetting("Glow Burst", false);

    private final BooleanSetting fireworkSpark =
            new BooleanSetting("Firework Spark", false);

    private final BooleanSetting totemBurst =
            new BooleanSetting("Totem Burst", false);

    private final BooleanSetting cloudBurst =
            new BooleanSetting("Cloud Burst", false);

    private final BooleanSetting flameRing =
            new BooleanSetting("Flame Ring", false);

    private final BooleanSetting poofBurst =
            new BooleanSetting("Poof Burst", false);

    private final BooleanSetting lavaSpark =
            new BooleanSetting("Lava Spark", false);

    private final BooleanSetting splashBurst =
            new BooleanSetting("Splash Burst", false);

    private final BooleanSetting enchantBurst =
            new BooleanSetting("Enchant Burst", false);

    private final BooleanSetting damageBurst =
            new BooleanSetting("Damage Burst", false);

    private final Random random = new Random();

    private Vec3 lastTargetPos;

    private float lastTargetHeight;

    /*
     * Thin lightning end time.
     */
    private long thinLightningUntil;

    /*
     * Vanilla LightningBolt.
     *
     * Not added to the world,
     * rendered directly through the vanilla EntityRenderDispatcher.
     */
    private LightningBolt vanillaBolt;

    private long vanillaLightningUntil;

    // 冷却时间记录变量
    private long lastEffectTime = 0L;

    public AttackEffect() {
        super(
                "AttackEffect",
                Category.RENDER
        );

        this.registerSetting(lightning);
        this.registerSetting(lightningMode);
        this.registerSetting(bloodExplosion);
        this.registerSetting(explosion);
        this.registerSetting(cooldown); // 注册冷却设置
        this.registerSetting(critBurst);
        this.registerSetting(magicCrit);
        this.registerSetting(smokeBurst);
        this.registerSetting(largeSmoke);
        this.registerSetting(portalBurst);
        this.registerSetting(reversePortal);
        this.registerSetting(endRod);
        this.registerSetting(hearts);
        this.registerSetting(happyVillager);
        this.registerSetting(witchMagic);
        this.registerSetting(soulBurst);
        this.registerSetting(soulFlame);
        this.registerSetting(dragonBreath);
        this.registerSetting(electricSpark);
        this.registerSetting(glowBurst);
        this.registerSetting(fireworkSpark);
        this.registerSetting(totemBurst);
        this.registerSetting(cloudBurst);
        this.registerSetting(flameRing);
        this.registerSetting(poofBurst);
        this.registerSetting(lavaSpark);
        this.registerSetting(splashBurst);
        this.registerSetting(enchantBurst);
        this.registerSetting(damageBurst);
    }

    @Override
    public String getDisplayName() {
        return "AttackEffect";
    }

    @Override
    public String getModuleName() {
        return "AttackEffect";
    }

    @Override
    public void onTick() {
    }

    @Override
    protected void onEnable() {
        lastTargetPos = null;
        lastTargetHeight = 0.0F;

        thinLightningUntil = 0L;

        vanillaBolt = null;
        vanillaLightningUntil = 0L;

        // 重置冷却时间
        lastEffectTime = 0L;
    }

    @Override
    protected void onDisable() {
        lastTargetPos = null;
        lastTargetHeight = 0.0F;

        thinLightningUntil = 0L;

        vanillaBolt = null;
        vanillaLightningUntil = 0L;

        // 重置冷却时间
        lastEffectTime = 0L;
    }

    /*
     * In this Nilore project, EntityRemoveEvent
     * is used by the attack patch to send the attack target.
     *
     * false = attack HEAD
     * true  = attack TAIL
     *
     * So we only record when dead == false.
     */
    @EventTarget
    public void onAttack(
            EntityRemoveEvent event
    ) {

        if (mc.player == null || mc.level == null) {
            return;
        }

        if (event.dead()) {
            return;
        }

        Entity entity = event.entity();

        if (entity instanceof LivingEntity living && living != mc.player) {

            // ================= 过滤逻辑 =================
            // 1. 距离过滤：确保目标在玩家的攻击范围内（4格内，平方为16）
            // 排除远处生物互殴触发特效，保证“不攻击就不生效”
            if (mc.player.distanceToSqr(living) > 16.0) {
                return;
            }

            // 2. 状态过滤：确保目标处于受击或死亡状态，排除自然消失或掉虚空
            if (living.hurtTime <= 0 && living.deathTime <= 0) {
                return;
            }
            // =============================================

            // ================= 动态冷却逻辑 =================
            // 获取滑块的值（秒），转换为毫秒（支持0.5秒即500毫秒）
            long cooldownMs = (long) (cooldown.getValue().doubleValue() * 1000L);

            // 如果冷却时间大于0，且距离上次触发不足冷却时间，则拦截
            if (cooldownMs > 0 && System.currentTimeMillis() - lastEffectTime < cooldownMs) {
                return;
            }

            // 记录本次触发时间
            lastEffectTime = System.currentTimeMillis();
            // ================================================

            lastTargetPos = living.position();
            lastTargetHeight = living.getBbHeight();

            playAttackEffect(lastTargetPos, lastTargetHeight);
        }
    }

    private void playAttackEffect(
            Vec3 pos,
            float height
    ) {

        if (pos == null || mc.level == null) {
            return;
        }

        // Lightning: single toggle, mode only switches between "Thin" and "Vanilla".
        if (lightning.getValue()) {
            lastTargetPos = pos;
            lastTargetHeight = height;

            if (lightningMode.is("Vanilla")) {
                createVanillaLightning(pos);
            } else {
                thinLightningUntil = System.currentTimeMillis() + 650L;
            }

            playLightningSound(pos);
        }

        if (bloodExplosion.getValue()) {
            spawnBloodExplosion(pos, height);
            playEffectSound(pos, SoundEvents.GENERIC_EXPLODE, 0.75F, 0.75F);
        }

        // spawnExplosion plays its own sound.
        if (explosion.getValue()) {
            spawnExplosion(pos, height);
        }
        if (critBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.CRIT, pos, height, 42, 0.65, 0.42, 0.18);
            playEffectSound(pos, SoundEvents.PLAYER_ATTACK_CRIT, 0.9F, 1.05F);
        }

        if (magicCrit.getValue()) {
            spawnParticleBurst(ParticleTypes.ENCHANTED_HIT, pos, height, 42, 0.65, 0.38, 0.16);
            playEffectSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.35F);
        }

        if (smokeBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.SMOKE, pos, height, 48, 0.75, 0.24, 0.13);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.55F, 0.65F);
        }

        if (largeSmoke.getValue()) {
            spawnParticleBurst(ParticleTypes.LARGE_SMOKE, pos, height, 26, 0.85, 0.18, 0.11);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.7F, 0.45F);
        }

        if (portalBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.PORTAL, pos, height, 64, 0.95, 0.55, 0.15);
            playEffectSound(pos, SoundEvents.ENDERMAN_TELEPORT, 0.9F, 1.0F);
        }

        if (reversePortal.getValue()) {
            spawnParticleBurst(ParticleTypes.REVERSE_PORTAL, pos, height, 64, 0.95, 0.45, 0.12);
            playEffectSound(pos, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.72F);
        }

        if (endRod.getValue()) {
            spawnParticleBurst(ParticleTypes.END_ROD, pos, height, 40, 0.70, 0.28, 0.14);
            playEffectSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65F, 1.65F);
        }

        if (hearts.getValue()) {
            spawnParticleBurst(ParticleTypes.HEART, pos, height, 24, 0.72, 0.18, 0.16);
            playEffectSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.75F);
        }

        if (happyVillager.getValue()) {
            spawnParticleBurst(ParticleTypes.HAPPY_VILLAGER, pos, height, 42, 0.78, 0.22, 0.13);
            playEffectSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65F, 1.5F);
        }

        if (witchMagic.getValue()) {
            spawnParticleBurst(ParticleTypes.WITCH, pos, height, 50, 0.85, 0.30, 0.13);
            playEffectSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.75F, 0.72F);
        }

        if (soulBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.SOUL, pos, height, 46, 0.78, 0.30, 0.15);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.65F, 0.72F);
        }

        if (soulFlame.getValue()) {
            spawnParticleBurst(ParticleTypes.SOUL_FIRE_FLAME, pos, height, 52, 0.78, 0.34, 0.16);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.75F, 0.9F);
        }

        if (dragonBreath.getValue()) {
            spawnParticleBurst(ParticleTypes.DRAGON_BREATH, pos, height, 58, 0.92, 0.38, 0.12);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.85F, 0.55F);
        }

        if (electricSpark.getValue()) {
            spawnParticleBurst(ParticleTypes.ELECTRIC_SPARK, pos, height, 58, 0.82, 0.50, 0.15);
            playEffectSound(pos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.75F, 1.45F);
        }

        if (glowBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.GLOW, pos, height, 50, 0.82, 0.32, 0.13);
            playEffectSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65F, 1.25F);
        }

        if (fireworkSpark.getValue()) {
            spawnParticleBurst(ParticleTypes.FIREWORK, pos, height, 62, 0.90, 0.62, 0.12);
            playEffectSound(pos, SoundEvents.FIREWORK_ROCKET_BLAST, 0.85F, 1.1F);
        }

        if (totemBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.TOTEM_OF_UNDYING, pos, height, 72, 0.90, 0.52, 0.16);
            playEffectSound(pos, SoundEvents.TOTEM_USE, 0.95F, 1.0F);
        }

        if (cloudBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.CLOUD, pos, height, 38, 0.88, 0.22, 0.10);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.5F, 1.55F);
        }

        if (flameRing.getValue()) {
            spawnParticleRing(ParticleTypes.FLAME, pos, height, 44, 0.85, 0.17);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.75F, 1.15F);
        }

        if (poofBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.POOF, pos, height, 48, 0.86, 0.38, 0.13);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.55F, 1.45F);
        }

        if (lavaSpark.getValue()) {
            spawnParticleBurst(ParticleTypes.LAVA, pos, height, 34, 0.72, 0.22, 0.18);
            playEffectSound(pos, SoundEvents.FIRECHARGE_USE, 0.75F, 0.72F);
        }

        if (splashBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.SPLASH, pos, height, 54, 0.82, 0.52, 0.10);
            playEffectSound(pos, SoundEvents.SPLASH_POTION_BREAK, 0.8F, 1.15F);
        }

        if (enchantBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.ENCHANT, pos, height, 58, 0.90, 0.26, 0.10);
            playEffectSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.75F, 0.85F);
        }

        if (damageBurst.getValue()) {
            spawnParticleBurst(ParticleTypes.DAMAGE_INDICATOR, pos, height, 38, 0.72, 0.35, 0.18);
            playEffectSound(pos, SoundEvents.PLAYER_HURT, 0.85F, 0.85F);
        }

    }

    private void playLightningSound(
            Vec3 pos
    ) {

        if (mc.level == null) {
            return;
        }

        mc.level.playLocalSound(
                pos.x,
                pos.y,
                pos.z,

                SoundEvents
                        .LIGHTNING_BOLT_THUNDER,

                SoundSource.WEATHER,

                4.0F,
                1.0F,

                false
        );

        mc.level.playLocalSound(
                pos.x,
                pos.y,
                pos.z,

                SoundEvents
                        .LIGHTNING_BOLT_IMPACT,

                SoundSource.WEATHER,

                2.0F,
                1.0F,

                false
        );
    }

    private void playEffectSound(
            Vec3 pos,
            net.minecraft.sounds.SoundEvent sound,
            float volume,
            float pitch
    ) {
        if (mc.level == null) {
            return;
        }

        mc.level.playLocalSound(
                pos.x, pos.y, pos.z,
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch,
                false
        );
    }

    /*
     * =========================================================
     * Vanilla Lightning
     * =========================================================
     */
    private void createVanillaLightning(
            Vec3 pos
    ) {

        if (mc.level == null) {
            return;
        }

        LightningBolt bolt =
                EntityType
                        .LIGHTNING_BOLT
                        .create(
                                mc.level
                        );

        if (bolt == null) {
            return;
        }

        bolt.moveTo(
                pos.x,
                pos.y,
                pos.z
        );

        /*
         * Visual only.
         */
        bolt.setVisualOnly(
                true
        );

        /*
         * Not added to the world.
         * Stored and rendered manually.
         */
        vanillaBolt = bolt;

        vanillaLightningUntil =
                System.currentTimeMillis()
                        + 750L;
    }

    /*
     * =========================================================
     * Render
     * =========================================================
     */
    @EventTarget
    public void onRender(
            RenderEvent event
    ) {

        if (
                mc.player == null
                        || mc.level == null
        ) {
            return;
        }

        if (!lightning.getValue()) {
            return;
        }

        if (
                lightningMode.is(
                        "Vanilla"
                )
        ) {

            renderVanillaLightning(
                    event
            );

            return;
        }

        if (!lightningMode.is("Vanilla")) {
            renderCustomLightning(event);
        }
    }

    /*
     * =========================================================
     * Vanilla Lightning (thick)
     * =========================================================
     */
    private void renderVanillaLightning(
            RenderEvent event
    ) {

        if (vanillaBolt == null) {
            return;
        }

        if (
                System.currentTimeMillis()
                        >= vanillaLightningUntil
        ) {

            vanillaBolt = null;
            return;
        }

        Vec3 camera =
                mc.gameRenderer
                        .getMainCamera()
                        .getPosition();

        double renderX =
                vanillaBolt.getX()
                        - camera.x;

        double renderY =
                vanillaBolt.getY()
                        - camera.y;

        double renderZ =
                vanillaBolt.getZ()
                        - camera.z;

        PoseStack poseStack =
                event.poseStack();

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                mc.renderBuffers()
                        .bufferSource();

        try {

            vanillaBolt.tickCount++;

            mc.getEntityRenderDispatcher()
                    .render(
                            vanillaBolt,

                            renderX,
                            renderY,
                            renderZ,

                            vanillaBolt.getYRot(),

                            event.partialTick(),

                            poseStack,

                            bufferSource,

                            0xF000F0
                    );

            bufferSource.endBatch();

        } catch (Throwable throwable) {

            throwable.printStackTrace();

            vanillaBolt = null;
        }

        poseStack.popPose();
    }

    /*
     * =========================================================
     * Thin Lightning
     * =========================================================
     */
    private void renderCustomLightning(
            RenderEvent event
    ) {

        if (lastTargetPos == null) {
            return;
        }

        if (
                System.currentTimeMillis()
                        >= thinLightningUntil
        ) {
            return;
        }

        Vec3 camera =
                mc.gameRenderer
                        .getMainCamera()
                        .getPosition();

        PoseStack poseStack =
                event.poseStack();

        poseStack.pushPose();

        poseStack.translate(

                lastTargetPos.x
                        - camera.x,

                lastTargetPos.y
                        - camera.y,

                lastTargetPos.z
                        - camera.z
        );

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        RenderSystem.lineWidth(getLightningLineWidth());

        Matrix4f matrix = poseStack.last().pose();
        renderLightningPattern(matrix);

        RenderSystem.lineWidth(1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    /*
     * Shared safe self-drawn logic used by the thin lightning modes.
     * Vanilla is drawn by Minecraft's LightningBoltRenderer.
     */
    private void renderLightningPattern(Matrix4f matrix) {
        drawBoltGroup(matrix, 3, 0.75F, 1.00F);
    }

    private void drawBoltGroup(
            Matrix4f matrix,
            int count,
            float spread,
            float jitter
    ) {
        for (int i = 0; i < count; i++) {
            drawBolt(matrix, i, count, spread, jitter);
        }
    }

    private float getLightningLineWidth() {
        return 2.0F;
    }

    private long getLightningDuration() {
        return 650L;
    }

    private void drawBolt(
            Matrix4f matrix,
            int boltIndex,
            int boltCount,
            float spread,
            float jitterScale
    ) {

        BufferBuilder buffer =
                Tesselator
                        .getInstance()
                        .getBuilder();

        buffer.begin(
                VertexFormat.Mode
                        .DEBUG_LINE_STRIP,

                DefaultVertexFormat
                        .POSITION_COLOR
        );

        float center = (boltCount - 1) * 0.5F;
        float offset = boltIndex - center;
        float startX = offset * 0.055F * spread;
        float startZ = -offset * 0.04F * spread;

        float height =
                Math.max(
                        7.0F,
                        lastTargetHeight
                                + 5.5F
                );

        buffer.vertex(
                        matrix,

                        startX,
                        height,
                        startZ
                )
                .color(
                        235,
                        245,
                        255,
                        245
                )
                .endVertex();

        final int segments = 12;

        long timeSeed =
                System.currentTimeMillis()
                        / 45L;

        Random renderRandom =
                new Random(
                        timeSeed
                                * 31L
                                + boltIndex
                                * 9176L
                );

        float x = startX;
        float z = startZ;

        for (
                int i = 1;
                i <= segments;
                i++
        ) {

            float progress =
                    i / (float) segments;

            float y =
                    height
                            * (
                            1.0F
                                    - progress
                    );

            float strength =
                    1.0F
                            - progress;

            x += (
                    renderRandom
                            .nextFloat()
                            - 0.5F
            )
                    * 0.75F
                    * strength
                    * jitterScale;

            z += (
                    renderRandom
                            .nextFloat()
                            - 0.5F
            )
                    * 0.75F
                    * strength
                    * jitterScale;

            if (i == segments) {

                x =
                        startX
                                * 0.2F;

                z =
                        startZ
                                * 0.2F;

                y = 0.05F;
            }

            int alpha =
                    Math.max(
                            130,

                            250
                                    - i
                                    * 6
                    );

            buffer.vertex(
                            matrix,
                            x,
                            y,
                            z
                    )
                    .color(
                            185,
                            220,
                            255,
                            alpha
                    )
                    .endVertex();
        }

        BufferUploader.drawWithShader(
                buffer.end()
        );
    }

    /*
     * =========================================================
     * Explosion
     * =========================================================
     */
    private void spawnExplosion(
            Vec3 pos,
            float height
    ) {

        if (mc.level == null) {
            return;
        }

        for (
                int i = 0;
                i < 45;
                i++
        ) {

            double velocityX =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.7;

            double velocityY =
                    random.nextDouble()
                            * 0.55;

            double velocityZ =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.7;

            mc.level.addParticle(
                    ParticleTypes.FLAME,

                    pos.x
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.5,

                    pos.y
                            + random.nextDouble()
                            * Math.max(
                            0.8,
                            height
                    ),

                    pos.z
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.5,

                    velocityX,
                    velocityY,
                    velocityZ
            );
        }

        for (
                int i = 0;
                i < 12;
                i++
        ) {

            mc.level.addParticle(
                    ParticleTypes.POOF,

                    pos.x
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.7,

                    pos.y
                            + random.nextDouble()
                            * Math.max(
                            1.0,
                            height
                    ),

                    pos.z
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.7,

                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.2,

                    random.nextDouble()
                            * 0.2,

                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.2
            );
        }

        mc.level.playLocalSound(
                pos.x,
                pos.y,
                pos.z,

                SoundEvents
                        .FIRECHARGE_USE,

                SoundSource.PLAYERS,

                1.0F,
                0.8F,

                false
        );
    }

    /*
     * =========================================================
     * Blood
     * =========================================================
     */
    private void spawnBloodExplosion(
            Vec3 pos,
            float height
    ) {

        if (mc.level == null) {
            return;
        }

        BlockParticleOption blood =
                new BlockParticleOption(
                        ParticleTypes.BLOCK,

                        Blocks.REDSTONE_BLOCK
                                .defaultBlockState()
                );

        for (
                int i = 0;
                i < 110;
                i++
        ) {

            double particleY =
                    pos.y
                            + random.nextDouble()
                            * Math.max(
                            1.0,
                            height + 0.4
                    );

            double velocityX =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.65;

            double velocityY =
                    (
                            random.nextDouble()
                                    - 0.1
                    )
                            * 0.5;

            double velocityZ =
                    (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.65;

            mc.level.addParticle(
                    blood,

                    pos.x
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.45,

                    particleY,

                    pos.z
                            + (
                            random.nextDouble()
                                    - 0.5
                    )
                            * 0.45,

                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }


    private void spawnParticleBurst(
            net.minecraft.core.particles.ParticleOptions particle,
            Vec3 pos,
            float height,
            int count,
            double spread,
            double speed,
            double upward
    ) {

        if (mc.level == null) {
            return;
        }

        double safeHeight = Math.max(0.8, height);

        for (int i = 0; i < count; i++) {

            double angle = random.nextDouble() * Math.PI * 2.0;
            double horizontal = random.nextDouble() * spread;

            double offsetX = Math.cos(angle) * horizontal;
            double offsetZ = Math.sin(angle) * horizontal;
            double offsetY = random.nextDouble() * safeHeight;

            double velocityX = (random.nextDouble() - 0.5) * speed;
            double velocityY = (random.nextDouble() - 0.22) * speed + upward;
            double velocityZ = (random.nextDouble() - 0.5) * speed;

            mc.level.addParticle(
                    particle,
                    pos.x + offsetX,
                    pos.y + offsetY,
                    pos.z + offsetZ,
                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }

    private void spawnParticleRing(
            net.minecraft.core.particles.ParticleOptions particle,
            Vec3 pos,
            float height,
            int count,
            double radius,
            double speed
    ) {

        if (mc.level == null) {
            return;
        }

        double centerY =
                pos.y + Math.max(0.45, height * 0.45);

        for (int i = 0; i < count; i++) {

            double angle =
                    (Math.PI * 2.0 * i) / count;

            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            mc.level.addParticle(
                    particle,
                    pos.x + cos * radius,
                    centerY,
                    pos.z + sin * radius,
                    cos * speed,
                    0.035,
                    sin * speed
            );
        }
    }


}