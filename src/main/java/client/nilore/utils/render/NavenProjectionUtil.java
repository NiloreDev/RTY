package client.nilore.utils.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * NavenXD-style world -> GUI projection for Nilore / Forge 1.20.1.
 *
 * 淇閲嶇偣锛�
 * View Bobbing 鐨� Y translation 蹇呴』鍙栧弽锛屼笉鑳界洿鎺ユ竻闆躲€�
 * 鍚﹀垯璧拌矾鏃舵爣绛句細闅忕潃瑙嗚 pitch 鏂瑰悜涓婁笅婕傜Щ銆�
 */
public final class NavenProjectionUtil {

    private static final Minecraft mc = Minecraft.getInstance();

    private NavenProjectionUtil() {
    }

    /**
     * 涓栫晫鍧愭爣 -> GUI 鍧愭爣銆�
     *
     * @return 鐐瑰湪闀滃ご鍚庢柟鏃惰繑鍥� null銆�
     */
    public static Vector2f project(
            double x,
            double y,
            double z,
            float partialTick
    ) {
        if (mc.gameRenderer == null
                || mc.getEntityRenderDispatcher() == null) {
            return null;
        }

        Camera camera =
                mc.gameRenderer.getMainCamera();

        if (camera == null) {
            return null;
        }

        Vec3 cameraPos =
                camera.getPosition();

        /*
         * 涓� Naven 鐨勫潗鏍囩鍙蜂繚鎸佷竴鑷达細
         *
         * camera - target
         *
         * 杩欐牱缁忚繃 inverse camera rotation 鍚庯紝
         * 闀滃ご鍓嶆柟鐨勭偣 Z 涓鸿礋鏁般€�
         */
        Vector3f cameraSpace =
                new Vector3f(
                        (float) (cameraPos.x - x),
                        (float) (cameraPos.y - y),
                        (float) (cameraPos.z - z)
                );

        /*
         * 涓栫晫 -> Camera Space銆�
         */
        Quaternionf inverseCamera =
                new Quaternionf(
                        mc.getEntityRenderDispatcher()
                                .cameraOrientation()
                ).conjugate();

        cameraSpace.rotate(inverseCamera);

        /*
         * Vanilla 鐨� View Bobbing 鏄湪 Camera 鍙樻崲浠ュ悗
         * 鍙堥澶栦綔鐢ㄥ埌涓栫晫鐭╅樀涓婄殑銆�
         *
         * 鎵€浠ヨ繖閲屽彧鐢� cameraOrientation() 杩樹笉澶燂紝
         * 闇€瑕佽ˉ涓� bobbing 鐨勯€嗗彉鎹€€�
         */

        double fov =
                getEffectiveFov();

        return toScreen(
                cameraSpace,
                fov
        );
    }

    /**
     * 瀵瑰簲 Vanilla/GameRenderer View Bobbing 鐨勯€嗚ˉ鍋裤€�
     *
     * 鍏抽敭淇锛�
     *
     * 閿欒锛�
     *     translation.y = 0.0F;
     *
     * 姝ｇ‘锛�
     *     translation.y = -translation.y;
     *
     * 鍘熼€昏緫鐨� vertical bob 蹇呴』鍙嶅悜搴旂敤銆�
     */
    private static void applyViewBobbing(
            Player player,
            Vector3f point,
            float partialTick
    ) {
        /*
         * 1.20.1锛�
         * walkDist / walkDistO
         * bob / oBob
         */
        float walked =
                player.walkDist;

        float walkDelta =
                walked - player.walkDistO;

        float phase =
                -(walked
                        + walkDelta * partialTick);

        float bob =
                Mth.lerp(
                        partialTick,
                        player.oBob,
                        player.bob
                );

        /*
         * Pitch bob inverse.
         */
        float pitchDegrees =
                Math.abs(
                        Mth.cos(
                                phase
                                        * (float) Math.PI
                                        - 0.2F
                        ) * bob
                ) * 5.0F;

        Quaternionf pitchBob =
                new Quaternionf()
                        .rotationX(
                                pitchDegrees
                                        * ((float) Math.PI / 180.0F)
                        )
                        .conjugate();

        point.rotate(pitchBob);

        /*
         * Roll bob inverse.
         */
        float rollDegrees =
                Mth.sin(
                        phase * (float) Math.PI
                ) * bob * 3.0F;

        Quaternionf rollBob =
                new Quaternionf()
                        .rotationZ(
                                rollDegrees
                                        * ((float) Math.PI / 180.0F)
                        )
                        .conjugate();

        point.rotate(rollBob);

        /*
         * Bob translation.
         *
         * Vanilla锛�
         * X = sin(phase * PI) * bob * 0.5
         * Y = -abs(cos(phase * PI) * bob)
         *
         * 鎶曞奖鍦ㄨˉ鍋垮畠鏃堕渶瑕佸弽杞� Y銆�
         */
        Vector3f translation =
                new Vector3f(
                        Mth.sin(
                                phase * (float) Math.PI
                        ) * bob * 0.5F,

                        -Math.abs(
                                Mth.cos(
                                        phase * (float) Math.PI
                                ) * bob
                        ),

                        0.0F
                );

        /*
         * !!! 杩欓噷鏄師鏂囦欢鐪熸鐨� Bug !!!
         *
         * 涓嶈兘锛�
         *
         * translation.y = 0.0F;
         *
         * 蹇呴』鎸夌収 Naven/鍘熻ˉ鍋块€昏緫鍙嶈浆 Y銆�
         */
        translation.y =
                -translation.y;

        point.add(translation);
    }

    /**
     * 褰撳墠鏈夋晥 FOV銆�
     *
     * 鍖呭惈锛�
     * - Options FOV
     * - Sprint
     * - Speed effect
     * - Bow / Use item
     * 绛夊鎴风鍔ㄦ€� FOV modifier銆�
     */
    private static double getEffectiveFov() {
        double fov =
                70.0D;

        try {
            Integer configured =
                    mc.options.fov().get();

            if (configured != null) {
                fov =
                        configured.doubleValue();
            }
        } catch (Throwable ignored) {
        }

        try {
            if (mc.getCameraEntity()
                    instanceof AbstractClientPlayer player) {

                float modifier =
                        player.getFieldOfViewModifier();

                if (Float.isFinite(modifier)
                        && modifier > 0.01F) {

                    fov *= modifier;
                }
            }
        } catch (Throwable ignored) {
        }

        return Mth.clamp(
                fov,
                1.0D,
                179.0D
        );
    }

    /**
     * Camera Space -> GUI scaled coordinates.
     */
    private static Vector2f toScreen(
            Vector3f point,
            double fov
    ) {
        /*
         * 褰撳墠鍧愭爣绾﹀畾锛�
         * 闀滃ご鍓嶆柟 = Z < 0銆�
         */
        if (!(point.z() < -1.0E-4F)) {
            return null;
        }

        float guiWidth =
                mc.getWindow()
                        .getGuiScaledWidth();

        float guiHeight =
                mc.getWindow()
                        .getGuiScaledHeight();

        float halfHeight =
                guiHeight / 2.0F;

        /*
         * Perspective focal length銆�
         */
        float factor =
                halfHeight
                        / (
                        point.z()
                                * (float) Math.tan(
                                Math.toRadians(
                                        fov / 2.0D
                                )
                        )
                );

        float screenX =
                -point.x()
                        * factor
                        + guiWidth / 2.0F;

        float screenY =
                guiHeight / 2.0F
                        - point.y()
                        * factor;

        if (!Float.isFinite(screenX)
                || !Float.isFinite(screenY)) {
            return null;
        }

        return new Vector2f(
                screenX,
                screenY
        );
    }
}