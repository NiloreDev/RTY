package client.nilore.utils.epsilon.player;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Map;

/** Epsilon enchantment helper adapted to the Minecraft/Forge API used by Nilore. */
public final class EnchantmentUtils {
    private EnchantmentUtils() {}

    public static Map<Enchantment, Integer> getEnchantments(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Map.of() : EnchantmentHelper.getEnchantments(stack);
    }

    public static int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        if (stack == null || stack.isEmpty() || enchantment == null) return 0;
        return EnchantmentHelper.getTagEnchantmentLevel(enchantment, stack);
    }

    public static boolean hasEnchantments(ItemStack stack, Enchantment... enchantments) {
        if (stack == null || stack.isEmpty()) return false;
        for (Enchantment enchantment : enchantments) {
            if (getEnchantmentLevel(stack, enchantment) <= 0) return false;
        }
        return true;
    }

    public static boolean hasEnchantment(ItemStack stack, Enchantment enchantment) {
        return getEnchantmentLevel(stack, enchantment) > 0;
    }
}
