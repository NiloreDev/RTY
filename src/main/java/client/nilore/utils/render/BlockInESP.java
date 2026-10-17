package client.nilore.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import client.nilore.modules.impl.world.BlockIn;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * BlockIn placement ESP for Forge 1.20.1.
 *
 * v9 self-registers on Forge's world render event, so no external render hook
 * is required. Successful BlockIn placements are rendered as slightly
 * expanded wireframe cubes for the configured retention time.
 */
public final class BlockInESP {
    private static boolean registered;

    private BlockInESP() {
    }

    /** Call once from BlockIn's constructor. Safe to call repeatedly. */
    public static synchronized void bootstrap() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(BlockInESP::onRenderLevel);
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        // Draw late in the world pass so block edges are not immediately
        // overwritten by normal level geometry.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        render(event.getPoseStack());
    }

    /** Public entry remains available if Nilore wants to invoke it manually. */
    public static void render(PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        BlockIn module = BlockIn.INSTANCE;

        if (poseStack == null
                || mc.level == null
                || mc.player == null
                || module == null
                || !module.isEspEnabled()) {
            return;
        }

        var positions = module.getEspBlocks();
        if (positions.isEmpty()) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0F);

        for (BlockPos pos : positions) {
            // Render immediately after useItemOn even if the client has not yet
            // received the server's block update. This makes ESP responsive and
            // avoids the old "recorded but invisible" race.
            AABB box = new AABB(pos).inflate(0.0035D);
            LevelRenderer.renderLineBox(
                    poseStack,
                    lines,
                    box,
                    0.18F,
                    0.92F,
                    1.00F,
                    0.98F
            );
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
    }
}