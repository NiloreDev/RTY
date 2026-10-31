package client.nilore.modules.impl.misc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.ForgeHooksClient;
import client.nilore.event.impl.SprintEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.game.CpsUtil;
import client.nilore.utils.misc.ReflectionUtil;
import client.nilore.utils.misc.UnsafeUtil;
import client.nilore.event.EventTarget;

public class AutoClicker extends Module {
    private final NumberSetting cps = new NumberSetting("CPS", 25, 4, 25, 1);
    private final ModeSetting clickSide = new ModeSetting("Mode", "Left", "Right", "Both").withDefault("Right");
    private final ModeSetting clickMethod = new ModeSetting("Click Mode", "Method", "Key", "Mouse").withDefault("Mouse");
    private final ModeSetting cpsMode = new ModeSetting("CPS Mode", "Normal", "DBC", "Human").withDefault("Normal");
    private final BooleanSetting breakBlock = new BooleanSetting("Break Block", true);
    private final Timer leftClickTimer = new Timer();
    private final Timer rightClickTimer = new Timer();
    private final Random humanRandom = new Random();
    private double humanCps = -1.0;

    public AutoClicker() {
        super("AutoClicker", Category.MISC);
    }

    @Override
    public void onEnable() {
        this.leftClickTimer.reset();
        this.rightClickTimer.reset();
        this.humanCps = -1.0;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public String getModuleName() {
        return "AutoClicker";
    }

    // 与 KeepSprint 对齐：displayName 带 §7[mode]，mode 为空时回退到纯名字。
    @Override
    public String getDisplayName() {
        String m = this.cpsMode.getValue();
        if (m == null || m.isEmpty()) {
            return "§fAutoClicker";
        }
        return "§fAutoClicker §7[" + m + "]";
    }

    // 与 KeepSprint 对齐：suffix 只保留 [mode]，不带 §7 颜色前缀。
    @Override
    public String getSuffix() {
        return "[" + this.cpsMode.getValue() + "]";
    }

    @Override
    public void onTick() {

    }

    @EventTarget
    public void onSprint(SprintEvent sprintEvent) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (this.clickSide.is("Normal")) {
            this.clickSide.setValue("Key");
        }
        if (this.clickSide.is("Left")) {
            this.doLeftClick();
        } else if (this.clickSide.is("Right")) {
            this.doRightClick();
        } else {
            this.doLeftClick();
            this.doRightClick();
        }
    }

    private long currentDelayMs() {
        return this.cpsMode.is("Human")
                ? this.humanDelayMs()
                : CpsUtil.toDelayMs(this.cpsMode.getValue(), this.cps.getValue().doubleValue());
    }

    private long humanDelayMs() {
        double target = this.cps.getValue().doubleValue();
        if (this.humanCps < 0.0) {
            this.humanCps = target;
        }
        double pull = (target - this.humanCps) * 0.25;
        double noise = (this.humanRandom.nextDouble() - 0.5) * 1.6;
        this.humanCps = Math.min(Math.max(this.humanCps + pull + noise, target * 0.75), target * 1.25);
        double delay = 1000.0 / this.humanCps;
        if (this.humanRandom.nextDouble() < 0.12) {
            delay *= 0.55;
        }
        return Math.max(20L, (long) delay);
    }

    private void doLeftClick() {
        if (mc.player.isUsingItem()
                || !mc.options.keyAttack.isDown()
                || mc.screen != null
                || !this.leftClickTimer.hasPassed(this.currentDelayMs())) {
            return;
        }
        if (this.breakBlock.getValue()
                && mc.hitResult instanceof BlockHitResult blockHitResult
                && blockHitResult.getType() == HitResult.Type.BLOCK) {
            return;
        }
        ReflectionUtil.setMissTime(0);
        switch ((String) this.clickMethod.getValue()) {
            case "Method":
                this.invokeStartAttack();
                break;
            case "Key":
                KeyMapping.click(mc.options.keyAttack.getKey());
                break;
            case "Mouse":
                ForgeHooksClient.onMouseButtonPre(mc.options.keyAttack.getKey().getValue(), 1, 0);
                KeyMapping.click(mc.options.keyAttack.getKey());
                ForgeHooksClient.onMouseButtonPost(mc.options.keyAttack.getKey().getValue(), 1, 0);
                break;
        }
        this.leftClickTimer.reset();
    }

    private void invokeStartUseItem() {
        try {
            Method method = mc.getClass().getDeclaredMethod(ReflectionUtil.getMappedMethodName(mc.getClass(), "startUseItem", "()V"));
            UnsafeUtil.makeAccessible(method);
            method.invoke(mc);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private void invokeStartAttack() {
        try {
            Method method = mc.getClass().getDeclaredMethod(ReflectionUtil.getMappedMethodName(mc.getClass(), "startAttack", "()V"));
            UnsafeUtil.makeAccessible(method);
            method.invoke(mc);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private void doRightClick() {
        if (mc.player.isUsingItem()
                || !(mc.hitResult instanceof BlockHitResult)
                || !(mc.player.getMainHandItem().getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() instanceof DoorBlock
                || !mc.options.keyUse.isDown()
                || mc.screen != null
                || !this.rightClickTimer.hasPassed(this.currentDelayMs())) {
            return;
        }
        switch ((String) this.clickMethod.getValue()) {
            case "Method":
                this.invokeStartUseItem();
                break;
            case "Key":
                KeyMapping.click(mc.options.keyUse.getKey());
                break;
            case "Mouse":
                ForgeHooksClient.onMouseButtonPre(mc.options.keyUse.getKey().getValue(), 1, 0);
                KeyMapping.click(mc.options.keyUse.getKey());
                ForgeHooksClient.onMouseButtonPost(mc.options.keyUse.getKey().getValue(), 1, 0);
                break;
        }
        ReflectionUtil.setRightClickDelay(0);
        this.rightClickTimer.reset();
    }
}