package client.nilore.modules.impl.render;

import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.ModeSetting;

import net.minecraft.resources.ResourceLocation;

@SuppressWarnings({
        "deprecation",
        "removal"
})
public class Cape extends Module {

    public static Cape INSTANCE;

    public final ModeSetting mode =
            new ModeSetting(
                    "Mode",
                    "Jiaran",
                    "M",
                    "O1",
                    "O2",
                    "QX2",
                    "Vape",
                    "BA",
                    "Bilibili",
                    "C",
                    "C1",
                    "Cat",
                    "Cat2",
                    "CherryBlossom",
                    "CS"
            ).withDefault("Jiaran");

    public Cape() {
        super(
                "Cape",
                Category.RENDER
        );

        INSTANCE = this;
    }

    public ResourceLocation getCapeTexture() {

        String fileName;

        if (mode.is("M")) {
            fileName = "m.png";
        } else if (mode.is("O1")) {
            fileName = "o1.png";
        } else if (mode.is("O2")) {
            fileName = "o2.png";
        } else if (mode.is("QX2")) {
            fileName = "qx2.png";
        } else if (mode.is("Vape")) {
            fileName = "vape.png";
        } else if (mode.is("BA")) {
            fileName = "ba.png";
        } else if (mode.is("Bilibili")) {
            fileName = "bilibili.png";
        } else if (mode.is("C")) {
            fileName = "c.png";
        } else if (mode.is("C1")) {
            fileName = "c1.png";
        } else if (mode.is("Cat")) {
            fileName = "cat.png";
        } else if (mode.is("Cat2")) {
            fileName = "cat2.png";
        } else if (mode.is("CherryBlossom")) {
            fileName = "cherryblossom.png";
        } else if (mode.is("CS")) {
            fileName = "cs.png";
        } else {
            fileName = "jiaran.png";
        }

        return new ResourceLocation(
                "nilore",
                "capes/" + fileName
        );
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    @Override
    public void onTick() {

    }
}