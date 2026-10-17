package client.nilore.gui;

import java.awt.Color;
import client.nilore.ClientBase;
import client.nilore.NiloreClient;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.GlHelper;
import client.nilore.event.EventTarget;

public class IntroAnimation extends ClientBase {
    private static volatile boolean isActive = false;
    private long startTime = -1L;
    private boolean finished = false;

    public IntroAnimation() {
        isActive = true;
    }

    public static boolean isRunning() {
        return isActive;
    }

    @EventTarget(value = 4)
    public void onRender(GlRenderEvent glRenderEvent) {
        if (this.finished) return;

        if (this.startTime < 0L) {
            this.startTime = System.currentTimeMillis();
        }
        long elapsed = System.currentTimeMillis() - this.startTime;

        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;

        // ========== 动画时间节点 ==========
        long nStart = 200L;
        long slideStart = 800L;
        long fadeOutStart = 3500L;
        long finishTime = fadeOutStart + 800L;

        if (elapsed > finishTime) {
            this.finish();
            return;
        }

        // 注意：这里彻底删除了背景暗化代码，保证画面完全透明。

        // ========== 1. 文本与颜色计算 ==========
        String clientName = NiloreClient.CLIENT_NAME_UPPER;
        if (clientName == null || clientName.isEmpty()) clientName = "Nebula";
        String firstChar = clientName.substring(0, 1);
        String restChars = clientName.length() > 1 ? clientName.substring(1) : "";

        // 整体淡出因子
        float masterFade = 1.0f;
        if (elapsed > fadeOutStart) {
            masterFade = 1.0f - clamp01((float)(elapsed - fadeOutStart) / 800.0f);
        }

        // 彩虹流光颜色 (基于 HSB 色轮循环)
        float hue = (elapsed % 4000) / 4000.0f;
        Color baseColor = Color.getHSBColor(hue, 0.6f, 1.0f);
        Color glowColor = Color.getHSBColor(hue, 0.8f, 1.0f);

        // ========== 2. 首字母 N 的弹性登场 ==========
        float nProgress = 0.0f;
        if (elapsed >= nStart) {
            nProgress = clamp01((elapsed - nStart) / 800.0f);
        }
        float nScale = easeOutBack(nProgress);
        float nAlpha = clamp01(nProgress * 2.0f);

        // ========== 3. 剩余字母的波浪滑入 ==========
        float slideProgress = 0.0f;
        if (elapsed >= slideStart) {
            slideProgress = easeOutCubic(clamp01((elapsed - slideStart) / 600.0f));
        }

        // 计算文字排版尺寸 (85.0f 是字号)
        FontRenderer nFont = FontPresets.axiformaBold(85.0f * nScale);
        FontRenderer baseFont = FontPresets.axiformaBold(85.0f);

        float nWidth = GlHelper.getStringWidth(firstChar, nFont);
        float baseNWidth = GlHelper.getStringWidth(firstChar, baseFont);
        float restWidth = GlHelper.getStringWidth(restChars, baseFont);

        // N 的起止位置
        float targetN_X = centerX - (baseNWidth + restWidth) / 2.0f;
        float startN_X = centerX - baseNWidth / 2.0f;
        float nX = lerp(startN_X, targetN_X, slideProgress);

        // 垂直居中计算
        float nY = centerY - nFont.getMetrics().capHeight() / 2.0f;

        // 绘制首字母 N (带发光)
        if (nAlpha > 0) {
            int alphaInt = (int) (clamp01(nAlpha * masterFade) * 255);
            int mainColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alphaInt).getRGB();
            int glowAlphaInt = (int) (alphaInt * 0.35f);
            int nGlowColor = new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), glowAlphaInt).getRGB();

            // 发光层 (简单的十字扩散，防色块)
            GlHelper.drawText(firstChar, nX - 2, nY, nFont, nGlowColor);
            GlHelper.drawText(firstChar, nX + 2, nY, nFont, nGlowColor);
            GlHelper.drawText(firstChar, nX, nY - 2, nFont, nGlowColor);
            GlHelper.drawText(firstChar, nX, nY + 2, nFont, nGlowColor);

            // 主字
            GlHelper.drawText(firstChar, nX, nY, nFont, mainColor);
        }

        // 绘制剩余字母 (波浪逐个飞入)
        if (!restChars.isEmpty() && slideProgress > 0) {
            float currentX = nX + nWidth;

            for (int i = 0; i < restChars.length(); i++) {
                String c = String.valueOf(restChars.charAt(i));
                float charStartTime = slideStart + (i * 80.0f);

                if (elapsed > charStartTime) {
                    float cProgress = clamp01((elapsed - charStartTime) / 600.0f);
                    float cAlpha = easeOutCubic(cProgress);

                    // 入场从下方 40 像素处飘上
                    float cOffsetY = (1.0f - cAlpha) * 40.0f;

                    // 出现后的持续波浪起伏 (极轻微的上下浮动)
                    float wave = 0.0f;
                    if (cProgress >= 1.0f) {
                        wave = (float) Math.sin((elapsed - charStartTime) * 0.004 + i * 0.8) * 5.0f;
                    }

                    float cY = centerY - baseFont.getMetrics().capHeight() / 2.0f + cOffsetY + wave;

                    int cAlphaInt = (int) (clamp01(cAlpha * masterFade) * 255);
                    int cColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), cAlphaInt).getRGB();
                    int cGlowAlphaInt = (int) (cAlphaInt * 0.35f);
                    int cGlowColor = new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), cGlowAlphaInt).getRGB();

                    // 发光层
                    GlHelper.drawText(c, currentX - 2, cY, baseFont, cGlowColor);
                    GlHelper.drawText(c, currentX + 2, cY, baseFont, cGlowColor);
                    GlHelper.drawText(c, currentX, cY - 2, baseFont, cGlowColor);
                    GlHelper.drawText(c, currentX, cY + 2, baseFont, cGlowColor);

                    // 主字
                    GlHelper.drawText(c, currentX, cY, baseFont, cColor);
                }
                currentX += GlHelper.getStringWidth(c, baseFont);
            }
        }

        // ========== 4. 超级高光扫过特效 ==========
        if (elapsed > 1000 && elapsed < 1800) {
            float sweepProg = clamp01((elapsed - 1000) / 800.0f);
            // 用正弦曲线算亮度，中间最亮，两头暗
            float sAlpha = (float) Math.sin(sweepProg * Math.PI) * 80 * masterFade;

            if (sAlpha > 0) {
                String fullText = clientName;
                FontRenderer fullFont = FontPresets.axiformaBold(85.0f * slideProgress);
                float fullW = GlHelper.getStringWidth(fullText, fullFont);
                float fullX = centerX - fullW / 2.0f;

                int sweepColor = new Color(255, 255, 255, (int)sAlpha).getRGB();
                GlHelper.drawText(fullText, fullX, centerY - fullFont.getMetrics().capHeight() / 2.0f, fullFont, sweepColor);
            }
        }
    }

    private void finish() {
        if (!this.finished) {
            this.finished = true;
            try {
                NiloreClient.instance.getEventBus().unregister(this);
            } catch (Throwable ignored) {}
            isActive = false;
        }
    }

    // ========== 数学辅助函数 ==========
    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float easeOutCubic(float t) {
        float clamped = clamp01(t);
        return (float) (1.0 - Math.pow(1.0f - clamped, 3.0));
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float clamped = clamp01(t);
        return 1 + c3 * (float)Math.pow(clamped - 1, 3) + c1 * (float)Math.pow(clamped - 1, 2);
    }
}