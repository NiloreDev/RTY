package client.nilore.event.impl;

import client.nilore.event.Event;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * 3D 渲染事件 - 在游戏渲染 3D 世界时触发
 * 用于 ESP、Camera、动画等模块
 */
public class Render3DEvent extends Event {

    private final PoseStack poseStack;
    private final float partialTick;
    private final Matrix4f projectionMatrix;

    public Render3DEvent(PoseStack poseStack, float partialTick, Matrix4f projectionMatrix) {
        this.poseStack = poseStack;
        this.partialTick = partialTick;
        this.projectionMatrix = projectionMatrix;
    }

    /**
     * 获取 PoseStack（用于 3D 变换）
     */
    public PoseStack getPoseStack() {
        return this.poseStack;
    }

    /**
     * 获取部分 tick 时间（用于插值）
     */
    public float getPartialTick() {
        return this.partialTick;
    }

    /**
     * 获取投影矩阵
     */
    public Matrix4f getProjectionMatrix() {
        return this.projectionMatrix;
    }
}