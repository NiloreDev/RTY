package client.nilore.utils.render;

import java.awt.Color;

/**
 * Gradient theme presets for ModuleList and other HUD elements.
 * Uses HSB color space for smoother interpolation.
 */
public enum GradientTheme {
    // ========== 原有颜色 (10种) ==========
    RAINBOW("Rainbow"),
    AURORA("Aurora"),
    SUNSET("Sunset"),
    OCEAN("Ocean"),
    COTTON("Cotton Candy"),
    LAVENDER("Lavender"),
    PEACH("Peach"),
    MINT("Mint"),
    CYBER("Cyberpunk"),
    DRIFT("Drift"),

    // ========== 新增颜色 (30+种) ==========
    // 红色系
    CRIMSON("Crimson"),
    RUBY("Ruby"),
    ROSE("Rose"),
    CANDY("Candy"),
    HOT_PINK("Hot Pink"),
    BURGUNDY("Burgundy"),
    // 橙色系
    TANGERINE("Tangerine"),
    PUMPKIN("Pumpkin"),
    CORAL("Coral"),
    AMBER("Amber"),
    MANGO("Mango"),
    // 黄色系
    LEMON("Lemon"),
    BANANA("Banana"),
    HONEY("Honey"),
    MUSTARD("Mustard"),
    BUTTER("Butter"),
    // 绿色系
    FOREST("Forest"),
    LIME("Lime"),
    OLIVE("Olive"),
    TEAL("Teal"),
    EMERALD("Emerald"),
    SAGE("Sage"),
    // 蓝色系
    SKY("Sky"),
    NAVY("Navy"),
    AZURE("Azure"),
    TURQUOISE("Turquoise"),
    INDIGO("Indigo"),
    SAPPHIRE("Sapphire"),
    CERULEAN("Cerulean"),
    // 紫色系
    PLUM("Plum"),
    LILAC("Lilac"),
    AMETHYST("Amethyst"),
    MAGENTA("Magenta"),
    ORCHID("Orchid"),
    // 粉色系
    BLUSH("Blush"),
    BUBBLEGUM("Bubblegum"),
    SALMON("Salmon"),
    FLAMINGO("Flamingo"),
    ROSE_GOLD("Rose Gold"),
    // 中性色
    SILVER("Silver"),
    CHAMPAGNE("Champagne"),
    PEARL("Pearl"),
    ONYX("Onyx"),
    PLATINUM("Platinum"),
    // 特殊色
    NEON("Neon"),
    PASTEL("Pastel"),
    VINTAGE("Vintage"),
    RETRO("Retro"),
    GALAXY("Galaxy");

    private final String displayName;

    GradientTheme(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get HSB color arrays [hue, saturation, brightness] for smooth interpolation.
     */
    public float[][] getHSBColors() {
        return switch (this) {
            // ========== 原有颜色 (10种) ==========
            case RAINBOW -> new float[][] {
                    {0.00f, 0.50f, 1.0f},
                    {0.17f, 0.50f, 1.0f},
                    {0.33f, 0.50f, 1.0f},
                    {0.50f, 0.50f, 1.0f},
                    {0.67f, 0.50f, 1.0f},
                    {0.83f, 0.50f, 1.0f},
                    {1.00f, 0.50f, 1.0f},
            };
            case AURORA -> new float[][] {
                    {0.45f, 0.70f, 1.0f},
                    {0.52f, 0.60f, 1.0f},
                    {0.72f, 0.55f, 0.95f},
                    {0.82f, 0.50f, 0.90f},
                    {0.90f, 0.55f, 0.95f},
                    {0.45f, 0.70f, 1.0f},
            };
            case SUNSET -> new float[][] {
                    {0.98f, 0.70f, 1.0f},
                    {0.05f, 0.75f, 1.0f},
                    {0.12f, 0.65f, 1.0f},
                    {0.15f, 0.50f, 1.0f},
                    {0.95f, 0.55f, 0.95f},
                    {0.98f, 0.70f, 1.0f},
            };
            case OCEAN -> new float[][] {
                    {0.57f, 0.80f, 0.90f},
                    {0.53f, 0.70f, 0.95f},
                    {0.50f, 0.60f, 1.0f},
                    {0.47f, 0.55f, 0.95f},
                    {0.43f, 0.50f, 0.90f},
                    {0.57f, 0.80f, 0.90f},
            };
            case COTTON -> new float[][] {
                    {0.92f, 0.40f, 1.0f},
                    {0.88f, 0.35f, 1.0f},
                    {0.80f, 0.30f, 1.0f},
                    {0.70f, 0.35f, 1.0f},
                    {0.60f, 0.40f, 1.0f},
                    {0.92f, 0.40f, 1.0f},
            };
            case LAVENDER -> new float[][] {
                    {0.75f, 0.50f, 0.90f},
                    {0.78f, 0.45f, 0.95f},
                    {0.82f, 0.40f, 1.0f},
                    {0.85f, 0.35f, 1.0f},
                    {0.80f, 0.45f, 0.95f},
                    {0.75f, 0.50f, 0.90f},
            };
            case PEACH -> new float[][] {
                    {0.05f, 0.50f, 1.0f},
                    {0.08f, 0.45f, 1.0f},
                    {0.10f, 0.40f, 1.0f},
                    {0.98f, 0.45f, 1.0f},
                    {0.95f, 0.50f, 1.0f},
                    {0.05f, 0.50f, 1.0f},
            };
            case MINT -> new float[][] {
                    {0.42f, 0.55f, 0.95f},
                    {0.45f, 0.50f, 1.0f},
                    {0.48f, 0.45f, 1.0f},
                    {0.50f, 0.40f, 0.95f},
                    {0.47f, 0.50f, 1.0f},
                    {0.42f, 0.55f, 0.95f},
            };
            case CYBER -> new float[][] {
                    {0.85f, 0.75f, 1.0f},
                    {0.90f, 0.70f, 1.0f},
                    {0.95f, 0.65f, 0.95f},
                    {0.55f, 0.75f, 1.0f},
                    {0.50f, 0.70f, 1.0f},
                    {0.85f, 0.75f, 1.0f},
            };
            case DRIFT -> new float[][] {
                    {0.60f, 0.65f, 0.90f},
                    {0.65f, 0.55f, 0.95f},
                    {0.70f, 0.50f, 1.0f},
                    {0.75f, 0.55f, 0.95f},
                    {0.68f, 0.60f, 0.90f},
                    {0.60f, 0.65f, 0.90f},
            };

            // ========== 红色系 ==========
            case CRIMSON -> new float[][] {
                    {0.00f, 0.85f, 0.50f},
                    {0.01f, 0.80f, 0.60f},
                    {0.02f, 0.75f, 0.70f},
                    {0.01f, 0.85f, 0.55f},
                    {0.00f, 0.85f, 0.50f},
            };
            case RUBY -> new float[][] {
                    {0.00f, 0.90f, 0.55f},
                    {0.01f, 0.85f, 0.65f},
                    {0.02f, 0.80f, 0.75f},
                    {0.01f, 0.90f, 0.60f},
                    {0.00f, 0.90f, 0.55f},
            };
            case ROSE -> new float[][] {
                    {0.93f, 0.55f, 0.75f},
                    {0.95f, 0.50f, 0.85f},
                    {0.97f, 0.45f, 0.90f},
                    {0.95f, 0.55f, 0.80f},
                    {0.93f, 0.55f, 0.75f},
            };
            case CANDY -> new float[][] {
                    {0.92f, 0.65f, 0.85f},
                    {0.94f, 0.60f, 0.90f},
                    {0.96f, 0.55f, 0.95f},
                    {0.94f, 0.65f, 0.88f},
                    {0.92f, 0.65f, 0.85f},
            };
            case HOT_PINK -> new float[][] {
                    {0.90f, 0.85f, 0.85f},
                    {0.92f, 0.80f, 0.90f},
                    {0.94f, 0.75f, 0.95f},
                    {0.92f, 0.85f, 0.88f},
                    {0.90f, 0.85f, 0.85f},
            };
            case BURGUNDY -> new float[][] {
                    {0.00f, 0.80f, 0.35f},
                    {0.01f, 0.75f, 0.45f},
                    {0.02f, 0.70f, 0.50f},
                    {0.01f, 0.80f, 0.40f},
                    {0.00f, 0.80f, 0.35f},
            };

            // ========== 橙色系 ==========
            case TANGERINE -> new float[][] {
                    {0.06f, 0.85f, 0.80f},
                    {0.08f, 0.80f, 0.85f},
                    {0.10f, 0.75f, 0.90f},
                    {0.08f, 0.85f, 0.82f},
                    {0.06f, 0.85f, 0.80f},
            };
            case PUMPKIN -> new float[][] {
                    {0.08f, 0.85f, 0.65f},
                    {0.10f, 0.80f, 0.75f},
                    {0.12f, 0.75f, 0.80f},
                    {0.10f, 0.85f, 0.70f},
                    {0.08f, 0.85f, 0.65f},
            };
            case CORAL -> new float[][] {
                    {0.03f, 0.65f, 0.80f},
                    {0.05f, 0.60f, 0.85f},
                    {0.07f, 0.55f, 0.90f},
                    {0.05f, 0.65f, 0.82f},
                    {0.03f, 0.65f, 0.80f},
            };
            case AMBER -> new float[][] {
                    {0.08f, 0.85f, 0.70f},
                    {0.10f, 0.80f, 0.80f},
                    {0.12f, 0.75f, 0.85f},
                    {0.10f, 0.85f, 0.75f},
                    {0.08f, 0.85f, 0.70f},
            };
            case MANGO -> new float[][] {
                    {0.07f, 0.80f, 0.75f},
                    {0.09f, 0.75f, 0.85f},
                    {0.11f, 0.70f, 0.90f},
                    {0.09f, 0.80f, 0.80f},
                    {0.07f, 0.80f, 0.75f},
            };

            // ========== 黄色系 ==========
            case LEMON -> new float[][] {
                    {0.13f, 0.85f, 0.90f},
                    {0.15f, 0.80f, 0.95f},
                    {0.17f, 0.75f, 1.0f},
                    {0.15f, 0.85f, 0.92f},
                    {0.13f, 0.85f, 0.90f},
            };
            case BANANA -> new float[][] {
                    {0.12f, 0.70f, 0.85f},
                    {0.14f, 0.65f, 0.90f},
                    {0.16f, 0.60f, 0.95f},
                    {0.14f, 0.70f, 0.88f},
                    {0.12f, 0.70f, 0.85f},
            };
            case HONEY -> new float[][] {
                    {0.10f, 0.75f, 0.75f},
                    {0.12f, 0.70f, 0.85f},
                    {0.14f, 0.65f, 0.90f},
                    {0.12f, 0.75f, 0.80f},
                    {0.10f, 0.75f, 0.75f},
            };
            case MUSTARD -> new float[][] {
                    {0.12f, 0.75f, 0.55f},
                    {0.14f, 0.70f, 0.65f},
                    {0.16f, 0.65f, 0.70f},
                    {0.14f, 0.75f, 0.60f},
                    {0.12f, 0.75f, 0.55f},
            };
            case BUTTER -> new float[][] {
                    {0.10f, 0.45f, 0.90f},
                    {0.12f, 0.40f, 0.95f},
                    {0.14f, 0.35f, 1.0f},
                    {0.12f, 0.45f, 0.92f},
                    {0.10f, 0.45f, 0.90f},
            };
            // ========== 绿色系 ==========
            case FOREST -> new float[][] {
                    {0.28f, 0.70f, 0.40f},
                    {0.30f, 0.65f, 0.50f},
                    {0.32f, 0.60f, 0.55f},
                    {0.30f, 0.70f, 0.45f},
                    {0.28f, 0.70f, 0.40f},
            };
            case LIME -> new float[][] {
                    {0.25f, 0.80f, 0.65f},
                    {0.27f, 0.75f, 0.75f},
                    {0.29f, 0.70f, 0.85f},
                    {0.27f, 0.80f, 0.70f},
                    {0.25f, 0.80f, 0.65f},
            };
            case OLIVE -> new float[][] {
                    {0.22f, 0.45f, 0.45f},
                    {0.24f, 0.40f, 0.55f},
                    {0.26f, 0.35f, 0.60f},
                    {0.24f, 0.45f, 0.50f},
                    {0.22f, 0.45f, 0.45f},
            };
            case TEAL -> new float[][] {
                    {0.48f, 0.70f, 0.50f},
                    {0.50f, 0.65f, 0.60f},
                    {0.52f, 0.60f, 0.65f},
                    {0.50f, 0.70f, 0.55f},
                    {0.48f, 0.70f, 0.50f},
            };
            case EMERALD -> new float[][] {
                    {0.42f, 0.80f, 0.55f},
                    {0.44f, 0.75f, 0.65f},
                    {0.46f, 0.70f, 0.75f},
                    {0.44f, 0.80f, 0.60f},
                    {0.42f, 0.80f, 0.55f},
            };
            case SAGE -> new float[][] {
                    {0.30f, 0.35f, 0.65f},
                    {0.32f, 0.30f, 0.75f},
                    {0.34f, 0.25f, 0.80f},
                    {0.32f, 0.35f, 0.70f},
                    {0.30f, 0.35f, 0.65f},
            };

            // ========== 蓝色系 ==========
            case SKY -> new float[][] {
                    {0.55f, 0.55f, 0.75f},
                    {0.57f, 0.50f, 0.85f},
                    {0.59f, 0.45f, 0.90f},
                    {0.57f, 0.55f, 0.80f},
                    {0.55f, 0.55f, 0.75f},
            };
            case NAVY -> new float[][] {
                    {0.62f, 0.75f, 0.35f},
                    {0.64f, 0.70f, 0.45f},
                    {0.66f, 0.65f, 0.50f},
                    {0.64f, 0.75f, 0.40f},
                    {0.62f, 0.75f, 0.35f},
            };
            case AZURE -> new float[][] {
                    {0.58f, 0.65f, 0.65f},
                    {0.60f, 0.60f, 0.75f},
                    {0.62f, 0.55f, 0.85f},
                    {0.60f, 0.65f, 0.70f},
                    {0.58f, 0.65f, 0.65f},
            };
            case TURQUOISE -> new float[][] {
                    {0.50f, 0.70f, 0.65f},
                    {0.52f, 0.65f, 0.75f},
                    {0.54f, 0.60f, 0.85f},
                    {0.52f, 0.70f, 0.70f},
                    {0.50f, 0.70f, 0.65f},
            };
            case INDIGO -> new float[][] {
                    {0.68f, 0.65f, 0.45f},
                    {0.70f, 0.60f, 0.55f},
                    {0.72f, 0.55f, 0.60f},
                    {0.70f, 0.65f, 0.50f},
                    {0.68f, 0.65f, 0.45f},
            };
            case SAPPHIRE -> new float[][] {
                    {0.60f, 0.75f, 0.50f},
                    {0.62f, 0.70f, 0.60f},
                    {0.64f, 0.65f, 0.65f},
                    {0.62f, 0.75f, 0.55f},
                    {0.60f, 0.75f, 0.50f},
            };
            case CERULEAN -> new float[][] {
                    {0.55f, 0.65f, 0.60f},
                    {0.57f, 0.60f, 0.70f},
                    {0.59f, 0.55f, 0.75f},
                    {0.57f, 0.65f, 0.65f},
                    {0.55f, 0.65f, 0.60f},
            };

            // ========== 紫色系 ==========
            case PLUM -> new float[][] {
                    {0.75f, 0.55f, 0.45f},
                    {0.77f, 0.50f, 0.55f},
                    {0.79f, 0.45f, 0.60f},
                    {0.77f, 0.55f, 0.50f},
                    {0.75f, 0.55f, 0.45f},
            };
            case LILAC -> new float[][] {
                    {0.78f, 0.35f, 0.75f},
                    {0.80f, 0.30f, 0.85f},
                    {0.82f, 0.25f, 0.90f},
                    {0.80f, 0.35f, 0.80f},
                    {0.78f, 0.35f, 0.75f},
            };
            case AMETHYST -> new float[][] {
                    {0.72f, 0.65f, 0.55f},
                    {0.74f, 0.60f, 0.65f},
                    {0.76f, 0.55f, 0.75f},
                    {0.74f, 0.65f, 0.60f},
                    {0.72f, 0.65f, 0.55f},
            };
            case MAGENTA -> new float[][] {
                    {0.85f, 0.85f, 0.65f},
                    {0.87f, 0.80f, 0.75f},
                    {0.89f, 0.75f, 0.80f},
                    {0.87f, 0.85f, 0.70f},
                    {0.85f, 0.85f, 0.65f},
            };
            case ORCHID -> new float[][] {
                    {0.78f, 0.45f, 0.70f},
                    {0.80f, 0.40f, 0.80f},
                    {0.82f, 0.35f, 0.85f},
                    {0.80f, 0.45f, 0.75f},
                    {0.78f, 0.45f, 0.70f},
            };

            // ========== 粉色系 ==========
            case BLUSH -> new float[][] {
                    {0.93f, 0.35f, 0.90f},
                    {0.95f, 0.30f, 0.95f},
                    {0.97f, 0.25f, 1.0f},
                    {0.95f, 0.35f, 0.92f},
                    {0.93f, 0.35f, 0.90f},
            };
            case BUBBLEGUM -> new float[][] {
                    {0.88f, 0.60f, 0.85f},
                    {0.90f, 0.55f, 0.90f},
                    {0.92f, 0.50f, 0.95f},
                    {0.90f, 0.60f, 0.88f},
                    {0.88f, 0.60f, 0.85f},
            };
            case SALMON -> new float[][] {
                    {0.03f, 0.55f, 0.75f},
                    {0.05f, 0.50f, 0.85f},
                    {0.07f, 0.45f, 0.90f},
                    {0.05f, 0.55f, 0.80f},
                    {0.03f, 0.55f, 0.75f},
            };
            case FLAMINGO -> new float[][] {
                    {0.93f, 0.65f, 0.80f},
                    {0.95f, 0.60f, 0.85f},
                    {0.97f, 0.55f, 0.90f},
                    {0.95f, 0.65f, 0.82f},
                    {0.93f, 0.65f, 0.80f},
            };
            case ROSE_GOLD -> new float[][] {
                    {0.07f, 0.35f, 0.75f},
                    {0.09f, 0.30f, 0.85f},
                    {0.11f, 0.25f, 0.90f},
                    {0.09f, 0.35f, 0.80f},
                    {0.07f, 0.35f, 0.75f},
            };

            // ========== 中性色 ==========
            case SILVER -> new float[][] {
                    {0.00f, 0.00f, 0.75f},
                    {0.00f, 0.00f, 0.85f},
                    {0.00f, 0.00f, 0.90f},
                    {0.00f, 0.00f, 0.80f},
                    {0.00f, 0.00f, 0.75f},
            };
            case CHAMPAGNE -> new float[][] {
                    {0.08f, 0.15f, 0.90f},
                    {0.10f, 0.10f, 0.95f},
                    {0.12f, 0.05f, 1.0f},
                    {0.10f, 0.15f, 0.92f},
                    {0.08f, 0.15f, 0.90f},
            };
            case PEARL -> new float[][] {
                    {0.00f, 0.00f, 0.85f},
                    {0.00f, 0.00f, 0.90f},
                    {0.00f, 0.00f, 0.95f},
                    {0.00f, 0.00f, 0.88f},
                    {0.00f, 0.00f, 0.85f},
            };
            case ONYX -> new float[][] {
                    {0.00f, 0.00f, 0.15f},
                    {0.00f, 0.00f, 0.20f},
                    {0.00f, 0.00f, 0.25f},
                    {0.00f, 0.00f, 0.18f},
                    {0.00f, 0.00f, 0.15f},
            };
            case PLATINUM -> new float[][] {
                    {0.00f, 0.00f, 0.80f},
                    {0.00f, 0.00f, 0.85f},
                    {0.00f, 0.00f, 0.90f},
                    {0.00f, 0.00f, 0.82f},
                    {0.00f, 0.00f, 0.80f},
            };

            // ========== 特殊色 ==========
            case NEON -> new float[][] {
                    {0.85f, 1.0f, 1.0f},
                    {0.55f, 1.0f, 1.0f},
                    {0.25f, 1.0f, 1.0f},
                    {0.55f, 1.0f, 1.0f},
                    {0.85f, 1.0f, 1.0f},
            };
            case PASTEL -> new float[][] {
                    {0.92f, 0.25f, 0.95f},
                    {0.55f, 0.25f, 0.95f},
                    {0.25f, 0.25f, 0.95f},
                    {0.55f, 0.25f, 0.95f},
                    {0.92f, 0.25f, 0.95f},
            };
            case VINTAGE -> new float[][] {
                    {0.08f, 0.45f, 0.60f},
                    {0.10f, 0.40f, 0.70f},
                    {0.12f, 0.35f, 0.75f},
                    {0.10f, 0.45f, 0.65f},
                    {0.08f, 0.45f, 0.60f},
            };
            case RETRO -> new float[][] {
                    {0.80f, 0.65f, 0.75f},
                    {0.82f, 0.60f, 0.85f},
                    {0.84f, 0.55f, 0.90f},
                    {0.82f, 0.65f, 0.80f},
                    {0.80f, 0.65f, 0.75f},
            };
            case GALAXY -> new float[][] {
                    {0.70f, 0.85f, 0.65f},
                    {0.75f, 0.80f, 0.75f},
                    {0.80f, 0.75f, 0.85f},
                    {0.75f, 0.85f, 0.70f},
                    {0.70f, 0.85f, 0.65f},
            };
        };
    }

    /**
     * Smoothstep interpolation for smoother transitions.
     */
    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Get color at a specific position in the gradient (0.0 to 1.0).
     * Uses HSB interpolation for smoother color transitions.
     */
    public Color getColorAt(double position, float saturationScale, float brightnessScale) {
        float[][] colors = getHSBColors();
        if (colors.length == 0) return Color.WHITE;

        // Normalize position to 0.0 - 1.0
        position = position % 1.0;
        if (position < 0) position += 1.0;

        // Find the two colors to interpolate between
        double scaledPos = position * (colors.length - 1);
        int index = (int) Math.floor(scaledPos);
        double fraction = scaledPos - index;

        // Apply smoothstep for smoother transitions
        fraction = smoothstep(fraction);

        if (index >= colors.length - 1) {
            float[] c = colors[colors.length - 1];
            return Color.getHSBColor(c[0], c[1] * saturationScale / 100.0f, c[2] * brightnessScale / 100.0f);
        }

        // Interpolate in HSB space
        float[] c1 = colors[index];
        float[] c2 = colors[index + 1];

        // Handle hue wrapping (shortest path around the color wheel)
        float hue1 = c1[0];
        float hue2 = c2[0];
        float hueDiff = hue2 - hue1;
        if (Math.abs(hueDiff) > 0.5f) {
            if (hueDiff > 0) {
                hue1 += 1.0f;
            } else {
                hue2 += 1.0f;
            }
        }

        float hue = (float) (hue1 + (hue2 - hue1) * fraction) % 1.0f;
        float sat = (float) (c1[1] + (c2[1] - c1[1]) * fraction);
        float bri = (float) (c1[2] + (c2[2] - c1[2]) * fraction);

        // Apply user adjustments
        sat = Math.min(1.0f, sat * saturationScale / 100.0f);
        bri = Math.min(1.0f, bri * brightnessScale / 100.0f);

        return Color.getHSBColor(hue, sat, bri);
    }

    /**
     * Get a theme by its display name.
     */
    public static GradientTheme fromName(String name) {
        for (GradientTheme theme : values()) {
            if (theme.getDisplayName().equalsIgnoreCase(name)) {
                return theme;
            }
        }
        return RAINBOW;
    }

    /**
     * Get all theme names.
     */
    public static String[] getNames() {
        GradientTheme[] themes = values();
        String[] names = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            names[i] = themes[i].getDisplayName();
        }
        return names;
    }
}