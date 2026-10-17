package client.nilore.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ScaffoldEspRenderer {

    private static final Minecraft mc = Minecraft.getInstance();

    private ScaffoldEspRenderer() {
    }

    public static void render(PoseStack poseStack, BlockPos pos) {
        if (mc.level == null || mc.player == null || pos == null) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer
                .getMainCamera()
                .getPosition();

        AABB box = new AABB(pos).move(
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z
        );

        poseStack.pushPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        RenderSystem.setShaderColor(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        RenderSystem.lineWidth(2.0F);

        RenderUtil.drawOutlineBox(
                box.inflate(0.002D),
                poseStack
        );

        RenderSystem.lineWidth(1.0F);

        RenderSystem.setShaderColor(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }
}