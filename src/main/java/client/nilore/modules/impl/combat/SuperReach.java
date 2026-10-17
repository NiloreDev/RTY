package client.nilore.modules.impl.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import client.nilore.NiloreClient;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.hud.ModuleListHud;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.utils.render.RenderUtil;

public class SuperReach extends Module {
    public static SuperReach INSTANCE;

    private boolean isBlinking;
    private long blinkStartTime;
    private boolean wasRepositioned;

    // Debug 输出开关
    public final BooleanSetting debug = new BooleanSetting("Debug", false);
    // 原 Ghost ESP 开关（控制轨迹点 + 方块）
    public final BooleanSetting showGhostEsp = new BooleanSetting("Ghost ESP", true);
    // Esp 开关（控制填充 + 描边）
    public final BooleanSetting esp = new BooleanSetting("Esp", false);
    // 颜色选择（同时影响 Ghost ESP 和 Esp）
    public final ModeSetting espColor = new ModeSetting(
            "Esp Color",
            "cyan",
            "cyan",
            "red",
            "white",
            "rainbow",
            "green",
            "yellow"
    ).withDefault("cyan");
    // 轨迹记录数量限制
    public final NumberSetting maxTrailPoints = new NumberSetting("Max Trail Points", 100.0, 10.0, 500.0, 10.0);

    private final List<Integer> impulsePacketBoundaries;
    private final LinkedBlockingQueue<Packet<?>> packetQueue;

    // 存储被定住实体的真实移动轨迹
    private final Map<Integer, List<Vec3>> realEntityTrails;

    public SuperReach() {
        // 【修改】分类从 MOVEMENT 改成 COMBAT
        super("SuperReach", Category.COMBAT);
        this.isBlinking = false;
        this.wasRepositioned = false;
        this.impulsePacketBoundaries = new ArrayList<>();
        this.packetQueue = new LinkedBlockingQueue<>();
        this.realEntityTrails = new ConcurrentHashMap<>();
        INSTANCE = this;
    }

    @Override
    public String getDisplayName() {
        return "§fBlinkReach";
    }

    @Override
    public String getModuleName() {
        return "BlinkReach";
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        this.isBlinking = true;
        this.wasRepositioned = false;
        this.blinkStartTime = System.currentTimeMillis();
        this.impulsePacketBoundaries.clear();
        this.packetQueue.clear();
        this.realEntityTrails.clear();

        if (this.debug.getValue()) {
            ChatUtil.print("Blink started");
        }
    }

    @Override
    protected void onDisable() {
        this.isBlinking = false;
        this.flushPackets();
        this.impulsePacketBoundaries.clear();
        this.packetQueue.clear();
        this.realEntityTrails.clear();

        if (this.debug.getValue()) {
            ChatUtil.print("Blink released and disabled");
        }
        super.onDisable();
    }

    @Override
    public void onTick() {}

    private void flushPackets() {
        while (!this.packetQueue.isEmpty()) {
            try {
                Packet<?> packet = this.packetQueue.poll();
                if (packet == null || mc.getConnection() == null) continue;
                handlePacket(packet, mc.getConnection());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void handlePacket(Packet packet, PacketListener listener) {
        packet.handle(listener);
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        if (!this.isEnabled() || mc.level == null || mc.player == null) {
            if (this.isBlinking) {
                mc.execute(this::flushPackets);
                this.isBlinking = false;
            }
            return;
        }

        if (this.isBlinking && packetEvent.isIncoming()) {
            Packet<?> packet = packetEvent.getPacket();

            if (packet instanceof ClientboundPlayerPositionPacket) {
                this.wasRepositioned = true;
                packetEvent.setCancelled(true);
                this.isBlinking = false;
                this.flushPackets();
                this.impulsePacketBoundaries.clear();
                this.packetQueue.clear();
                this.realEntityTrails.clear();

                if (this.debug.getValue()) {
                    ChatUtil.print("Auto released on position sync");
                }
                mc.execute(() -> this.setEnabled(false));
                return;
            }

            if (packet instanceof ClientboundMoveEntityPacket move) {
                Entity entity = move.getEntity(mc.level);
                if (entity != null && move.hasPosition()) {
                    List<Vec3> trail = this.realEntityTrails.computeIfAbsent(entity.getId(), k -> new ArrayList<>());
                    Vec3 base = trail.isEmpty() ? entity.position() : trail.get(trail.size() - 1);
                    Vec3 newPos = base.add(move.getXa() / 4096.0, move.getYa() / 4096.0, move.getZa() / 4096.0);
                    trail.add(newPos);

                    int max = this.maxTrailPoints.getValue().intValue();
                    if (trail.size() > max) {
                        trail.remove(0);
                    }
                }
            } else if (packet instanceof ClientboundTeleportEntityPacket teleport) {
                List<Vec3> trail = this.realEntityTrails.computeIfAbsent(teleport.getId(), k -> new ArrayList<>());
                trail.add(new Vec3(teleport.getX(), teleport.getY(), teleport.getZ()));
                int max = this.maxTrailPoints.getValue().intValue();
                if (trail.size() > max) {
                    trail.remove(0);
                }
            }

            packetEvent.setCancelled(true);
            this.packetQueue.add(packet);
        }
    }

    /**
     * 3D 渲染：仅在 Ghost ESP 或 Esp 打开时才绘制。
     */
    @EventTarget
    public void onRender(RenderEvent event) {
        if (!this.isEnabled() || !this.isBlinking) return;
        if (mc.level == null || mc.player == null || this.realEntityTrails.isEmpty()) return;
        if (!this.showGhostEsp.getValue() && !this.esp.getValue()) return;

        PoseStack poseStack = event.poseStack();
        if (poseStack == null) return;

        poseStack.pushPose();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        float[] rgb = resolveEspColor();

        for (Map.Entry<Integer, List<Vec3>> entry : this.realEntityTrails.entrySet()) {
            List<Vec3> trail = entry.getValue();
            if (trail.isEmpty()) continue;

            Vec3 currentPos = trail.get(trail.size() - 1);

            if (this.showGhostEsp.getValue()) {
                RenderSystem.setShader(GameRenderer::getPositionShader);

                int trailSize = trail.size();
                for (int i = 0; i < trailSize; i++) {
                    Vec3 p = trail.get(i);
                    AABB pointBox = new AABB(
                            p.x - 0.1, p.y + 0.9, p.z - 0.1,
                            p.x + 0.1, p.y + 1.1, p.z + 0.1
                    );

                    float progress = trailSize <= 1 ? 1.0F : (float) i / (trailSize - 1);
                    float brightness = 0.35F + 0.65F * progress;

                    RenderSystem.setShaderColor(
                            rgb[0] * brightness,
                            rgb[1] * brightness,
                            rgb[2] * brightness,
                            0.9F
                    );
                    RenderUtil.drawBoxVerts(bufferBuilder, poseStack.last().pose(), pointBox);
                }

                AABB currentBox = new AABB(
                        currentPos.x - 0.3, currentPos.y, currentPos.z - 0.3,
                        currentPos.x + 0.3, currentPos.y + 1.8, currentPos.z + 0.3
                );
                RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], 1.0F);
                RenderUtil.drawBoxVerts(bufferBuilder, poseStack.last().pose(), currentBox);
            }

            if (this.esp.getValue()) {
                AABB espBox = new AABB(
                        currentPos.x - 0.3, currentPos.y, currentPos.z - 0.3,
                        currentPos.x + 0.3, currentPos.y + 1.8, currentPos.z + 0.3
                );

                RenderSystem.setShader(GameRenderer::getPositionShader);
                RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], 0.30F);
                RenderUtil.drawBoxVerts(bufferBuilder, poseStack.last().pose(), espBox);

                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.lineWidth(2.0F);
                bufferBuilder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                LevelRenderer.renderLineBox(
                        poseStack, bufferBuilder,
                        espBox.inflate(0.01),
                        rgb[0], rgb[1], rgb[2], 1.0F
                );
                tesselator.end();
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private float[] resolveEspColor() {
        String mode = this.espColor.getValue();
        if (mode == null) mode = "cyan";

        switch (mode) {
            case "red":
                return new float[]{1.0F, 0.15F, 0.15F};
            case "white":
                return new float[]{1.0F, 1.0F, 1.0F};
            case "green":
                return new float[]{0.15F, 1.0F, 0.35F};
            case "yellow":
                return new float[]{1.0F, 0.9F, 0.1F};
            case "rainbow": {
                float hue = ((System.currentTimeMillis() % 4000L) / 4000.0F);
                int rgb = Color.HSBtoRGB(hue, 0.85F, 1.0F);
                return new float[]{
                        ((rgb >> 16) & 0xFF) / 255.0F,
                        ((rgb >> 8) & 0xFF) / 255.0F,
                        (rgb & 0xFF) / 255.0F
                };
            }
            case "cyan":
            default:
                return new float[]{0.15F, 1.0F, 1.0F};
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        String status;
        if (this.isBlinking) {
            long elapsed = System.currentTimeMillis() - this.blinkStartTime;
            int trackedEntities = this.realEntityTrails.size();
            int totalPoints = this.realEntityTrails.values().stream().mapToInt(List::size).sum();

            status = String.format("packets, %.1fs | Ghosts: %d (Trail: %d pts)",
                    elapsed / 1000.0f, trackedEntities, totalPoints);
        } else {
            status = "BlinkReach idle";
        }

        int textWidth = mc.font.width(status);
        float textX = width / 2.0f - textWidth / 2.0f;
        float textY = height / 2.0f + 20.0f;

        this.drawGradientString(event, status, (int) textX, (int) textY, true);

        // 进度条
        if (this.isBlinking) {
            float elapsedSec = (System.currentTimeMillis() - this.blinkStartTime) / 1000.0f;
            float progress = Math.min(1.0f, elapsedSec / 15.0f);

            float barHeight = 2.0f;
            float barWidth = textWidth + 20.0f;
            float barX = textX - 10.0f;
            float barY = textY + 11.0f;

            event.guiGraphics().fill(
                    (int) barX,
                    (int) barY,
                    (int) (barX + barWidth),
                    (int) (barY + barHeight),
                    0x40FFFFFF
            );

            if (progress > 0.0f) {
                int fillColor = this.getThemeFillColor();
                event.guiGraphics().fill(
                        (int) barX,
                        (int) barY,
                        (int) (barX + barWidth * progress),
                        (int) (barY + barHeight),
                        fillColor
                );
            }
        }
    }

    private int getThemeFillColor() {
        ModuleListHud moduleList = NiloreClient.getInstance().getHudManager() != null
                ? NiloreClient.getInstance().getHudManager().getHudElement(ModuleListHud.class)
                : null;

        if (moduleList != null) {
            int c = moduleList.getThemeColor(0, 0.5f, 2);
            return (c & 0x00FFFFFF) | 0xFF000000;
        }
        return 0xFF00FFFF;
    }

    private void drawGradientString(Render2DEvent event, String text, int x, int y, boolean shadow) {
        ModuleListHud moduleList = NiloreClient.getInstance().getHudManager() != null
                ? NiloreClient.getInstance().getHudManager().getHudElement(ModuleListHud.class)
                : null;

        int length = text.length();
        int currentX = x;
        long time = System.currentTimeMillis();

        for (int i = 0; i < length; i++) {
            String character = String.valueOf(text.charAt(i));
            int color;

            if (moduleList != null) {
                float progress = length <= 1 ? 0.0f : i / (float) (length - 1);
                color = moduleList.getThemeColor(i, progress, Math.max(1, length - 1));
                // 强制 alpha = 255，保证首字母可见
                color = (color & 0x00FFFFFF) | 0xFF000000;
            } else {
                float hue = ((time % 3000) / 3000.0f + (i / (float) length)) % 1.0f;
                color = Color.HSBtoRGB(hue, 0.8f, 1.0f) | 0xFF000000;
            }

            event.guiGraphics().drawString(mc.font, character, currentX, y, color, shadow);
            currentX += mc.font.width(character);
        }
    }
}