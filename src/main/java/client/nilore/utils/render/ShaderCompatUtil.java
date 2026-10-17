// client/nilore/utils/render/ShaderCompatUtil.java
package client.nilore.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import java.lang.reflect.Field;

public class ShaderCompatUtil {

    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * 检测是否开启了光影（通过反射兼容所有版本）
     */
    public static boolean isShaderActive() {
        try {
            // 尝试直接获取 shaderPack 字段
            Field field = mc.options.getClass().getDeclaredField("shaderPack");
            field.setAccessible(true);
            Object value = field.get(mc.options);
            return value != null && !value.toString().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 开始 UI 渲染
     */
    public static void beginUIRender() {
        if (isShaderActive()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
    }

    /**
     * 结束 UI 渲染
     */
    public static void endUIRender() {
        if (isShaderActive()) {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /**
     * 3D 渲染使用原版着色器
     */
    public static void beginNoShaderRender() {
        if (isShaderActive()) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
    }
}