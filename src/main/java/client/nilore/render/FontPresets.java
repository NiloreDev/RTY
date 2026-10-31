package client.nilore.render;

import client.nilore.render.FontRenderer;
import client.nilore.render.Fonts;

public final class FontPresets {

    public static FontRenderer pingfang(float size) {
        return pingfang(size, 1.0f, true);
    }
    public static FontRenderer pingfang(float size, float rasterScale, boolean antiAlias) {
        // 注：目前 Fonts.getRenderer 只支持 size。
        // 若底层 FontRenderer 已升级支持高分辨率渲染，可在此处将 rasterScale 和 antiAlias 传下去。
        return Fonts.getRenderer("pingfang_sc_regular.ttf", size);
    }

    public static FontRenderer productSans(float size) {
        return productSans(size, 1.0f, true);
    }
    public static FontRenderer productSans(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("product_sans_regular.ttf", size);
    }

    public static FontRenderer astaSans(float size) {
        return astaSans(size, 1.0f, true);
    }
    public static FontRenderer astaSans(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("AstaSans-Medium.ttf", size);
    }

    public static FontRenderer poppinsRegular(float size) {
        return poppinsRegular(size, 1.0f, true);
    }
    public static FontRenderer poppinsRegular(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("Poppins-Regular.ttf", size);
    }

    public static FontRenderer poppinsMedium(float size) {
        return poppinsMedium(size, 1.0f, true);
    }
    public static FontRenderer poppinsMedium(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("Poppins-Medium.ttf", size);
    }

    public static FontRenderer poppinsBold(float size) {
        return poppinsBold(size, 1.0f, true);
    }
    public static FontRenderer poppinsBold(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("Poppins-Bold.ttf", size);
    }

    public static FontRenderer niloreIcon(float size) {
        return niloreIcon(size, 1.0f, true);
    }
    public static FontRenderer niloreIcon(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("niloreicon-Regular.ttf", size);
    }

    public static FontRenderer museoSans(float size) {
        return museoSans(size, 1.0f, true);
    }
    public static FontRenderer museoSans(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("MuseoSansCyrl-900.ttf", size);
    }

    public static FontRenderer openSans(float size) {
        return openSans(size, 1.0f, true);
    }
    public static FontRenderer openSans(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("opensans.ttf", size);
    }

    public static FontRenderer materialIcons(float size) {
        return materialIcons(size, 1.0f, true);
    }
    public static FontRenderer materialIcons(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("MaterialIcons-Regular.ttf", size);
    }

    public static FontRenderer axiformaBold(float size) {
        return axiformaBold(size, 1.0f, true);
    }
    public static FontRenderer axiformaBold(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("axiforma_bold.ttf", size);
    }

    public static FontRenderer axiformaRegular(float size) {
        return axiformaRegular(size, 1.0f, true);
    }
    public static FontRenderer axiformaRegular(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("axiforma_regular.ttf", size);
    }

    public static FontRenderer axiformaExtraBold(float size) {
        return axiformaExtraBold(size, 1.0f, true);
    }
    public static FontRenderer axiformaExtraBold(float size, float rasterScale, boolean antiAlias) {
        return Fonts.getRenderer("axiforma_extrabold.ttf", size);
    }
}