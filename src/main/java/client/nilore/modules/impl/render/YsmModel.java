package client.nilore.modules.impl.render;

import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.SettingVisibility;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.settings.impl.TextSetting;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.ysm.YsmAnimationPlayer;
import client.nilore.ysm.YsmModelData;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * YsmModel —— YSM 模型渲染（重写版）。
 *
 * 架构：渲染由 LivingEntityRendererPatch 直接调用 renderInto()——
 * 每帧尝试渲染，成功才取消原版；失败自动回退原版（无状态机、无事件顺序问题）。
 *
 * 数据链：模型文件夹/文件 → YSMFolderDeserializer/YSMBinaryDeserializer → RawYsmModel
 * → YsmModelData.build（骨骼+四边形，顶点已 ÷16 为方块单位）→ 渲染。
 * <p>
 * 动画：原版玩家参数驱动（头 yaw/pitch、四肢 walkAnimation、攻击 swing）。
 */
public class YsmModel
extends Module {
    public static YsmModel INSTANCE;
    private static final String[] CHARACTER_ONLY_AUXILIARY_TOKENS = {
            "background", "sign", "ground", "flower", "bed", "ghost", "cat",
            "particle", "effect", "beam", "trail", "arrow", "projectile",
            "pole", "rail", "board", "vehicle", "car", "wheel", "tyre", "door",
            "computer", "monitor", "suitcase", "lamp", "broom", "gui"
    };
    public final TextSetting path = new TextSetting("Path", "config/openzen/model.ysm");
    public final NumberSetting scale = new NumberSetting("Scale", 1.0f, 0.1f, 5.0f, 0.05f);
    public final ModeSetting animationMode = new ModeSetting(
            "Animation", "Vanilla Animation", "OpenYSM Animation").withDefault("Vanilla Animation");
    private final SettingVisibility vanillaAnimationOnly = () -> this.animationMode.is("Vanilla Animation");
    /** 骨骼过滤：空 = 全部；"关键词" = 只渲染包含关键词；"!关键词" = 排除 */
    public final TextSetting boneFilter = new TextSetting("Bone Filter", "");
    /**
     * Only render the character hierarchy by default.  YSM exports may also
     * contain a scene, sign, particles, or other showcase geometry in the
     * same mainModel tree; those branches should not surround the player in
     * game.  Disable this to restore the legacy full-model rendering.
     */
    public final client.nilore.settings.impl.BooleanSetting characterOnly =
            new client.nilore.settings.impl.BooleanSetting("Character Only", true);
    // ===== 手持物品渲染 =====
    public final client.nilore.settings.impl.BooleanSetting showItem =
            new client.nilore.settings.impl.BooleanSetting("Show Item", true);
    public final NumberSetting itemScale = new NumberSetting("Item Scale", 1.0f, 0.1f, 5.0f, 0.05f);
    /** 模型模式：内置模型固定模式；Auto 仍支持外部 .zen/configs 模型。 */
    public final client.nilore.settings.impl.ModeSetting modelMode =
            new client.nilore.settings.impl.ModeSetting("Model", "Auto") {
                @Override
                public void onChanged(String previous, String current) {
                    // Model is a live selector in the UI. Rebuild the model
                    // while the module is enabled so changing the value does
                    // not require toggling YsmModel off and on again.
                    if (YsmModel.this.isEnabled() && !YsmModel.this.loadingModel) {
                        YsmModel.this.loadModel();
                    }
                }
            };

    // ===== 方向开关 =====
    /** 开关一：走路时左手摆臂方向 */
    public final client.nilore.settings.impl.BooleanSetting flipLeftWalk =
            new client.nilore.settings.impl.BooleanSetting("Flip Left Walk", false, this.vanillaAnimationOnly);
    /** 开关二：走路时右手摆臂方向 */
    public final client.nilore.settings.impl.BooleanSetting flipRightWalk =
            new client.nilore.settings.impl.BooleanSetting("Flip Right Walk", false, this.vanillaAnimationOnly);
    /** 开关三：除走路摆臂外的所有动作方向（攻击/持物等） */
    public final client.nilore.settings.impl.BooleanSetting flipOther =
            new client.nilore.settings.impl.BooleanSetting("Flip Other Anim", false, this.vanillaAnimationOnly);

    private YsmModelData model;
    /** Dedicated YSM arm geometry used by Forge's RenderArmEvent. */
    private YsmModelData armModel;
    private final java.util.IdentityHashMap<YsmModelData, boolean[]> characterFilterCache =
            new java.util.IdentityHashMap<>();
    private YsmAnimationPlayer animationPlayer;
    private YsmAnimationPlayer armAnimationPlayer;
    private boolean loadingModel;
    private ResourceLocation textureLocation;
    private boolean failedLogged;
    private boolean atkDiagLogged;
    private int failCount;
    private float modelHorizontalScale = 1.0f;
    private float modelVerticalScale = 1.0f;
    private float[] poseRotationX = new float[0];
    private float[] poseRotationY = new float[0];
    private float[] poseRotationZ = new float[0];
    private float[] posePositionX = new float[0];
    private float[] posePositionY = new float[0];
    private float[] posePositionZ = new float[0];
    private float[] poseScaleX = new float[0];
    private float[] poseScaleY = new float[0];
    private float[] poseScaleZ = new float[0];
    private float[] armPoseRotationX = new float[0];
    private float[] armPoseRotationY = new float[0];
    private float[] armPoseRotationZ = new float[0];
    private float[] armPosePositionX = new float[0];
    private float[] armPosePositionY = new float[0];
    private float[] armPosePositionZ = new float[0];
    private float[] armPoseScaleX = new float[0];
    private float[] armPoseScaleY = new float[0];
    private float[] armPoseScaleZ = new float[0];

    public YsmModel() {
        super("YsmModel", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        this.loadingModel = true;
        try {
            this.scanAndSetModels();
        } finally {
            this.loadingModel = false;
        }
        this.loadModel();
        super.onEnable();
    }

    /** 扫描 .zen/configs 下的模型文件夹 → 模式列表（模式名 = 文件夹名，支持中文） */
    private void scanAndSetModels() {
        try {
            java.util.List<String> names = new java.util.ArrayList<>();
            names.add("Auto");
            names.add("Alice");
            names.add("boqijiang");
            names.add("toyota_ae86");
            names.add("yatuoya");
            String home = System.getProperty("user.home");
            if (home == null || home.isEmpty()) {
                home = System.getenv("USERPROFILE");
            }
            java.nio.file.Path configDir = java.nio.file.Paths.get(home, ".zen", "configs");
            if (java.nio.file.Files.isDirectory(configDir)) {
                try (java.util.stream.Stream<java.nio.file.Path> dirs = java.nio.file.Files.list(configDir)) {
                    for (java.nio.file.Path d : dirs.filter(java.nio.file.Files::isDirectory)
                            .collect(java.util.stream.Collectors.toList())) {
                        if (java.nio.file.Files.exists(d.resolve("ysm.json"))
                                || java.nio.file.Files.exists(d.resolve("models").resolve("main.json"))) {
                            names.add(d.getFileName().toString());
                        }
                    }
                }
            }
            this.modelMode.setModes(names.toArray(new String[0]));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDisable() {
        this.model = null;
        this.armModel = null;
        this.characterFilterCache.clear();
        this.animationPlayer = null;
        this.armAnimationPlayer = null;
        this.modelHorizontalScale = 1.0f;
        this.modelVerticalScale = 1.0f;
        if (this.textureLocation != null) {
            mc.getTextureManager().release(this.textureLocation);
            this.textureLocation = null;
        }
        super.onDisable();
    }

    // ================= 加载 =================
    private void loadModel() {
        if (this.loadingModel) {
            return;
        }
        this.loadingModel = true;
        this.model = null;
        this.armModel = null;
        this.characterFilterCache.clear();
        if (this.textureLocation != null) {
            mc.getTextureManager().release(this.textureLocation);
            this.textureLocation = null;
        }
        try {
            Path file = this.findFile(this.path.getValue());
            if (file == null) {
                ChatUtil.print("YsmModel: file not found: " + this.path.getValue()
                        + " (Model=" + this.modelModeName() + ")");
                this.toggle();
                return;
            }
            RawYsmModel raw;
            if (Files.isDirectory(file)) {
                try (com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer deser =
                             new com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer(file)) {
                    raw = deser.deserialize();
                }
            } else {
                byte[] data = Files.readAllBytes(file);
                raw = this.tryDeserialize(data);
            }
            if (raw == null) {
                ChatUtil.print("YsmModel: cannot parse (maybe encrypted YSGP). File: " + file);
                this.toggle();
                return;
            }
            // Most YSM exports contain showcase geometry beside the player.  It
            // is stored as independent branches below Root, so name-token
            // filtering alone cannot remove it reliably.  Keep the character
            // branches at load time while preserving the two requested models.
            this.stripNonCharacterGeometry(raw);
            this.model = YsmModelData.build(raw);
            RawYsmModel.RawGeometry rawArm = raw.mainEntity == null ? null : raw.mainEntity.armModel;
            // OpenYSM uses the main mesh as the arm mesh when an export does
            // not contain a dedicated armModel. Keep that compatibility path
            // so older models still replace the vanilla first-person arm.
            if (rawArm == null || rawArm.bones.isEmpty()) {
                rawArm = raw.mainEntity == null ? null : raw.mainEntity.mainModel;
            }
            this.armModel = rawArm == null ? null : YsmModelData.buildGeometry(rawArm);
            this.animationPlayer = new YsmAnimationPlayer(raw, this.model.boneIndex);
            this.armAnimationPlayer = this.armModel == null ? null
                    : new YsmAnimationPlayer(raw, this.armModel.boneIndex, true);
            this.ensurePoseCapacity(this.model.bones.size());
            this.ensureArmPoseCapacity(this.armModel == null ? 0 : this.armModel.bones.size());
            this.modelHorizontalScale = raw.properties == null ? 1.0f : raw.properties.heightScale;
            this.modelVerticalScale = raw.properties == null ? 1.0f : raw.properties.widthScale;
            String defaultTexture = raw.properties == null ? null : raw.properties.defaultTexture;
            this.textureLocation = this.registerTexture(defaultTexture);
            ChatUtil.print("YsmModel: loaded " + this.model.bones.size() + " bones, "
                    + this.model.textures.size() + " textures"
                    + (this.armModel == null ? " (no arm model)" : ", " + this.armModel.bones.size() + " arm bones"));
        } catch (Exception e) {
            ChatUtil.print("YsmModel: load failed: " + e);
            this.toggle();
        } finally {
            this.loadingModel = false;
        }
    }

    private RawYsmModel tryDeserialize(byte[] data) {
        try {
            return new YSMBinaryDeserializer(data).deserialize();
        } catch (Exception ignored) {
        }
        try {
            byte[] decompressed = rip.ysm.algorithms.YsmZstd.decompress(data);
            return new YSMBinaryDeserializer(decompressed).deserialize();
        } catch (Exception ignored) {
        }
        return null;
    }

    private void stripNonCharacterGeometry(RawYsmModel raw) {
        if (raw == null || raw.mainEntity == null || this.isPreservedModel(raw)) {
            return;
        }
        this.stripNonCharacterBranches(raw.mainEntity.mainModel);
        this.stripNonCharacterBranches(raw.mainEntity.armModel);
    }

    private boolean isPreservedModel(RawYsmModel raw) {
        String mode = this.modelModeName().toLowerCase(java.util.Locale.ROOT);
        String metadata = raw.metadata == null || raw.metadata.name == null
                ? "" : raw.metadata.name.toLowerCase(java.util.Locale.ROOT);
        return mode.contains("boqijiang") || mode.contains("toyota") || mode.contains("ae86")
                || metadata.contains("boqijiang") || metadata.contains("波奇")
                || metadata.contains("toyota") || metadata.contains("ae86");
    }

    private void stripNonCharacterBranches(RawYsmModel.RawGeometry geometry) {
        if (geometry == null || geometry.bones == null || geometry.bones.isEmpty()) {
            return;
        }
        java.util.Map<String, RawYsmModel.RawBone> byName = new java.util.HashMap<>();
        boolean hasRoot = false;
        for (RawYsmModel.RawBone bone : geometry.bones) {
            if (bone == null || bone.name == null) continue;
            byName.putIfAbsent(bone.name, bone);
            if ("root".equals(normalizeBoneName(bone.name))) hasRoot = true;
        }
        // Vehicle and other non-player geometries do not have the standard
        // Root hierarchy; leave those files untouched.
        if (!hasRoot) return;

        java.util.Map<String, String> branchByBone = new java.util.HashMap<>();
        java.util.Set<String> characterBranches = new java.util.HashSet<>();
        boolean foundAnchor = false;
        for (RawYsmModel.RawBone bone : geometry.bones) {
            if (bone == null || bone.name == null) continue;
            String branch = findRootBranch(bone, byName);
            branchByBone.put(bone.name, branch);
            if (isCharacterAnchor(bone.name)) {
                foundAnchor = true;
                if (branch != null) characterBranches.add(branch);
            }
        }
        if (!foundAnchor || characterBranches.isEmpty()) return;

        java.util.Set<String> removedNames = new java.util.HashSet<>();
        geometry.bones.removeIf(bone -> {
            if (bone == null || bone.name == null) return true;
            String normalized = normalizeBoneName(bone.name);
            if ("root".equals(normalized)) return false;
            String branch = branchByBone.get(bone.name);
            boolean remove = branch == null || !characterBranches.contains(branch);
            if (remove) removedNames.add(bone.name);
            return remove;
        });
        // Parent names are resolved again by YsmModelData.buildMesh.  Clear a
        // dangling parent rather than allowing a removed scene bone to affect
        // the character transform.
        for (RawYsmModel.RawBone bone : geometry.bones) {
            if (bone.parentName != null && removedNames.contains(bone.parentName)) {
                bone.parentName = "";
            }
        }
    }

    private static String findRootBranch(RawYsmModel.RawBone bone,
                                         java.util.Map<String, RawYsmModel.RawBone> byName) {
        RawYsmModel.RawBone current = bone;
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (current != null && current.name != null && visited.add(current.name)) {
            String parentName = current.parentName;
            if (parentName == null || parentName.isEmpty()) {
                return current.name;
            }
            RawYsmModel.RawBone parent = byName.get(parentName);
            if (parent == null) return current.name;
            if ("root".equals(normalizeBoneName(parent.name))) {
                return current.name;
            }
            current = parent;
        }
        return bone.name;
    }

    private static boolean isCharacterAnchor(String name) {
        String n = normalizeBoneName(name);
        return n.equals("mallbody") || n.equals("allbody") || n.equals("upbody")
                || n.equals("mupbody") || n.equals("mupperbody") || n.equals("upperbody")
                || n.equals("allhead") || n.equals("head") || n.equals("arm")
                || n.equals("leftarm") || n.equals("rightarm") || n.equals("downbody")
                || n.equals("leg") || n.equals("leftleg") || n.equals("rightleg");
    }

    private static String normalizeBoneName(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
    }

    /** 当前模式名（Model 设置值；无效时回退 Auto） */
    private String modelModeName() {
        try {
            String v = this.modelMode.getValue();
            return v != null && !v.isEmpty() ? v : "Auto";
        } catch (Exception e) {
            return "Auto";
        }
    }

    private Path findFile(String p) {
        String mode = this.modelModeName();
        if (!mode.equals("Auto")) {
            Path builtin = this.findBuiltinModel(mode);
            if (builtin != null) return builtin;
        }
        // 模式指定：直接加载 .zen/configs/<模式名>（支持中文文件夹名）
        if (!mode.equals("Auto")) {
            for (String home : new String[]{System.getProperty("user.home"), System.getenv("USERPROFILE")}) {
                if (home == null || home.isEmpty()) {
                    continue;
                }
                try {
                    Path modeDir = Paths.get(home, ".zen", "configs", mode);
                    if (Files.isDirectory(modeDir)) {
                        return modeDir;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        try {
            Path direct = Paths.get(p);
            if (Files.exists(direct)) {
                return direct;
            }
        } catch (Exception ignored) {
        }
        try {
            Path rel = Paths.get(System.getProperty("user.dir"), p);
            if (Files.exists(rel)) {
                return rel;
            }
        } catch (Exception ignored) {
        }
        for (String home : new String[]{System.getProperty("user.home"), System.getenv("USERPROFILE")}) {
            if (home == null || home.isEmpty()) {
                continue;
            }
            try {
                Path configDir = Paths.get(home, ".zen", "configs");
                if (!Files.isDirectory(configDir)) {
                    continue;
                }
                try (java.util.stream.Stream<Path> dirs = Files.list(configDir)) {
                    for (Path d : dirs.filter(Files::isDirectory).collect(java.util.stream.Collectors.toList())) {
                        if (Files.exists(d.resolve("ysm.json"))
                                || Files.exists(d.resolve("models").resolve("main.json"))) {
                            return d;
                        }
                    }
                }
                try (java.util.stream.Stream<Path> files = Files.list(configDir)) {
                    for (Path f : files.filter(x -> x.toString().toLowerCase().endsWith(".ysm"))
                            .collect(java.util.stream.Collectors.toList())) {
                        return f;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Path findBuiltinModel(String mode) {
        try {
            Path cache = Paths.get(System.getProperty("java.io.tmpdir"), "nilore-ysm-builtin", mode);
            if (Files.isDirectory(cache)) return cache;
            java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(
                    YsmModel.class.getProtectionDomain().getCodeSource().getLocation().toURI(),
                    java.util.Collections.emptyMap());
            Path root = fs.getPath("/assets/nilore/ysm/builtin", mode);
            if (!Files.isDirectory(root)) { fs.close(); return null; }
            Files.createDirectories(cache);
            try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile).forEach(source -> {
                    try {
                        Path target = cache.resolve(root.relativize(source).toString());
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) { }
                });
            }
            fs.close();
            return cache;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ResourceLocation registerTexture(String defaultTexture) {
        if (this.model == null || this.model.textures.isEmpty()) {
            return null;
        }
        try {
            byte[] png = defaultTexture == null ? null : this.model.textures.get(defaultTexture);
            if (png == null) {
                png = this.model.textures.values().iterator().next();
            }
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    "openzen",
                    "ysm_model_" + System.currentTimeMillis()
            );
            mc.getTextureManager().register(loc, tex);
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    // ================= 渲染（patch 直接调用）=================
    /** 当前过滤下可见骨骼数 */
    public int visibleBoneCount() {
        if (this.model == null) {
            return 0;
        }
        String filter = this.boneFilter.getValue();
        boolean exclude = filter.startsWith("!");
        String fw = exclude ? filter.substring(1) : filter;
        int count = 0;
        for (int i = 0; i < this.model.bones.size(); i++) {
            YsmModelData.YsmBone b = this.model.bones.get(i);
            if (!this.shouldRenderBone(this.model, i)) {
                continue;
            }
            if (!fw.isEmpty()) {
                boolean match = b.name != null
                        && b.name.toLowerCase().contains(fw.toLowerCase());
                if (exclude ? match : !match) {
                    continue;
                }
            }
            count++;
        }
        return count;
    }

    /**
     * 渲染 ysm 模型。返回 true = 渲染成功（调用方应取消原版）；false = 失败/无内容（回退原版）。
     * 由 LivingEntityRendererPatch 每帧调用。
     */
    public boolean renderInto(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight) {
        if (this.model == null || this.visibleBoneCount() == 0) {
            return false;
        }
        LocalPlayer p = mc.player;
        if (p == null) {
            return false;
        }
        try {
            float s = this.scale.getValue().floatValue();
            poseStack.pushPose();
            // 位置已由 EntityRenderDispatcher 在调用 renderer 前平移到实体——
            // 绝不能手动再加世界坐标（否则双重平移 → 视锥外不可见）。
            float bodyYaw = Mth.rotLerp(partialTick, p.yBodyRotO, p.yBodyRot);
            this.applyEntityRotations(p, poseStack, bodyYaw, partialTick);
            poseStack.translate(0.0f, 0.01f, 0.0f);
            poseStack.scale(s * this.modelHorizontalScale, s * this.modelVerticalScale,
                    s * this.modelHorizontalScale);

            RenderType renderType = this.textureLocation != null
                    ? RenderType.entityCutoutNoCull(this.textureLocation):
            RenderType.entityCutoutNoCull(
                    ResourceLocation.withDefaultNamespace("textures/entity/steve.png")
            );

            VertexConsumer vc = bufferSource.getBuffer(renderType);

            int boneCount = this.model.bones.size();
            this.ensurePoseCapacity(boneCount);
            this.prepareBindPose();
            if (!this.animationMode.is("OpenYSM Animation") && this.animationPlayer != null) {
                this.animationPlayer.reset();
            }
            boolean openYsmAnimation = this.animationMode.is("OpenYSM Animation")
                    && this.animationPlayer != null
                    && this.animationPlayer.hasAnimations()
                    && this.animationPlayer.apply(p, partialTick,
                    this.poseRotationX, this.poseRotationY, this.poseRotationZ,
                    this.posePositionX, this.posePositionY, this.posePositionZ,
                    this.poseScaleX, this.poseScaleY, this.poseScaleZ);
            if (openYsmAnimation) {
                // Keep OpenYSM locomotion authoritative. Only head tracking and
                // the vanilla swing delta are layered on top, avoiding the
                // doubled arm/leg motion caused by copying the entire vanilla pose.
                this.applyHeadTracking();
                if (!YsmAnimationPlayer.isTaczGun(p.getMainHandItem())) {
                    this.applyVanillaSwing(p, partialTick);
                }
            } else {
                this.applyVanillaAnimation(p, partialTick);
            }

            Matrix4f[] local = new Matrix4f[boneCount];
            for (int i = 0; i < boneCount; i++) {
                YsmModelData.YsmBone bone = this.model.bones.get(i);
                float px = bone.pivotX / 16.0f;
                float py = bone.pivotY / 16.0f;
                float pz = bone.pivotZ / 16.0f;
                local[i] = new Matrix4f().identity()
                        .translate(-this.posePositionX[i] / 16.0f,
                                this.posePositionY[i] / 16.0f,
                                this.posePositionZ[i] / 16.0f)
                        .translate(px, py, pz)
                        .rotateZ(this.poseRotationZ[i])
                        .rotateY(this.poseRotationY[i])
                        .rotateX(this.poseRotationX[i])
                        .scale(this.poseScaleX[i], this.poseScaleY[i], this.poseScaleZ[i])
                        .translate(-bone.pivotX / 16.0f, -bone.pivotY / 16.0f, -bone.pivotZ / 16.0f);
            }

            // 递归合成全局矩阵（父链顺序无关；parent × local——子先绕自己 pivot 转再跟随父）
            Matrix4f[] globalCache = new Matrix4f[boneCount];
            for (int i = 0; i < boneCount; i++) {
                this.computeGlobal(i, local, globalCache);
            }

            // 渲染四边形（骨骼过滤）
            String filter = this.boneFilter.getValue();
            boolean exclude = filter.startsWith("!");
            String fw = exclude ? filter.substring(1) : filter;
            for (int i = 0; i < boneCount; i++) {
                YsmModelData.YsmBone bone = this.model.bones.get(i);
                if (!this.shouldRenderBone(this.model, i)) {
                    continue;
                }
                // 吉他骨骼跳过渲染（bone2 = 手拿吉他，bone3 = 背背吉他）
                if (bone.name != null && (bone.name.equals("bone2") || bone.name.equals("bone3"))) {
                    continue;
                }
                if (!fw.isEmpty()) {
                    boolean match = bone.name != null
                            && bone.name.toLowerCase().contains(fw.toLowerCase());
                    if (exclude ? match : !match) {
                        continue;
                    }
                }
                Matrix4f global = globalCache[i];
                if (global == null) {
                    continue;
                }
                Matrix3f boneNormalMatrix = new Matrix3f();
                global.normal(boneNormalMatrix);
                Vector3f transformedPosition = new Vector3f();
                Vector3f transformedNormal = new Vector3f();
                for (YsmModelData.YsmCube cube : bone.cubes) {
                    for (YsmModelData.YsmQuad quad : cube.quads) {
                        boneNormalMatrix.transform(quad.normal, transformedNormal).normalize();
                        for (int j = 0; j < 4; j++) {
                            global.transformPosition(quad.positions[j], transformedPosition);
                            vc.vertex(poseStack.last().pose(), transformedPosition.x, transformedPosition.y, transformedPosition.z)
                                    .color(255, 255, 255, 255)
                                    .uv(quad.uvs[j].x, quad.uvs[j].y)
                                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                                    .uv2(packedLight)
                                    .normal(poseStack.last().normal(), transformedNormal.x, transformedNormal.y, transformedNormal.z)
                                    .endVertex();
                        }
                    }
                }
            }

            // ===== 手持物品渲染：使用 YSM 的左右手 locator 链（在 popPose 之前）=====
            try {
                if (this.showItem.getValue() && mc.gameRenderer != null
                        && mc.gameRenderer.itemInHandRenderer != null) {
                    float itemSize = this.itemScale.getValue().floatValue();
                    net.minecraft.world.entity.HumanoidArm mainArm = p.getMainArm();
                    net.minecraft.world.entity.HumanoidArm offArm = mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT
                            ? net.minecraft.world.entity.HumanoidArm.LEFT
                            : net.minecraft.world.entity.HumanoidArm.RIGHT;
                    this.renderHeldItem(p, p.getMainHandItem(),
                            mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT
                                    ? "RightHandLocator" : "LeftHandLocator",
                            mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT
                                    ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                                    : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                            mainArm == net.minecraft.world.entity.HumanoidArm.LEFT,
                            itemSize, poseStack, bufferSource, packedLight, local, globalCache);
                    this.renderHeldItem(p, p.getOffhandItem(),
                            offArm == net.minecraft.world.entity.HumanoidArm.RIGHT
                                    ? "RightHandLocator" : "LeftHandLocator",
                            offArm == net.minecraft.world.entity.HumanoidArm.RIGHT
                                    ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                                    : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                            offArm == net.minecraft.world.entity.HumanoidArm.LEFT,
                            itemSize, poseStack, bufferSource, packedLight, local, globalCache);
                }
            } catch (Exception ignored) {
            }
            poseStack.popPose();
            return true;
        } catch (Exception e) {
            try {
                poseStack.popPose();
            } catch (Exception ignored) {
            }
            // 连续渲染失败 → 自动关闭模块（防崩溃循环——两个注入客户端渲染冲突时）
            if (++this.failCount > 20) {
                ChatUtil.print("YsmModel render failed repeatedly, disabling");
                this.toggle();
                return false;
            }
            if (!this.failedLogged) {
                this.failedLogged = true;
                ChatUtil.print("YsmModel render failed: " + e);
            }
            return false; // 失败 → 回退原版渲染
        }
    }

    /**
     * Render one side of the dedicated YSM first-person arm model.  Forge fires
     * RenderArmEvent before vanilla applies its arm mesh transforms, so this
     * method intentionally uses the same translation/axis convention as
     * OpenYSM's HandItemRenderer.
     */
    public boolean renderFirstPersonArm(HumanoidArm arm, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int packedLight,
                                        float partialTick) {
        YsmModelData data = this.armModel;
        LocalPlayer player = mc.player;
        if (data == null || data.bones.isEmpty() || player == null) {
            return false;
        }

        int boneCount = data.bones.size();
        this.ensureArmPoseCapacity(boneCount);
        this.prepareBindPose(data, this.armPoseRotationX, this.armPoseRotationY,
                this.armPoseRotationZ, this.armPosePositionX, this.armPosePositionY,
                this.armPosePositionZ, this.armPoseScaleX, this.armPoseScaleY,
                this.armPoseScaleZ);

        if (this.armAnimationPlayer != null
                && this.armAnimationPlayer.hasAnimations()) {
            this.armAnimationPlayer.apply(player, partialTick,
                    this.armPoseRotationX, this.armPoseRotationY, this.armPoseRotationZ,
                    this.armPosePositionX, this.armPosePositionY, this.armPosePositionZ,
                    this.armPoseScaleX, this.armPoseScaleY, this.armPoseScaleZ);
        }
        // The Forge event pose stack already contains vanilla first-person
        // equip/swing transforms. If no dedicated arm clip exists, keep the
        // mesh in its bind pose instead of reusing third-person bone angles.

        Matrix4f[] local = this.buildLocalMatrices(data, this.armPoseRotationX,
                this.armPoseRotationY, this.armPoseRotationZ, this.armPosePositionX,
                this.armPosePositionY, this.armPosePositionZ, this.armPoseScaleX,
                this.armPoseScaleY, this.armPoseScaleZ);
        Matrix4f[] global = new Matrix4f[boneCount];
        for (int i = 0; i < boneCount; i++) {
            this.computeGlobal(data, i, local, global);
        }

        String filter = this.boneFilter.getValue();
        boolean exclude = filter.startsWith("!");
        String filterWord = exclude ? filter.substring(1) : filter;
        RenderType renderType = this.textureLocation != null
                ? RenderType.entityCutoutNoCull(this.textureLocation):
        RenderType.entityCutoutNoCull(
                ResourceLocation.fromNamespaceAndPath(
                        "minecraft",
                        "textures/entity/steve.png"
                )
        );
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
        boolean rendered = false;
        poseStack.pushPose();
        try {
            if (arm == HumanoidArm.LEFT) {
                poseStack.translate(0.25d, 1.8d, 0.0d);
            } else {
                poseStack.translate(-0.25d, 1.8d, 0.0d);
            }
            poseStack.scale(-1.0f, -1.0f, 1.0f);

            Matrix3f normalMatrix = new Matrix3f();
            Vector3f transformedPosition = new Vector3f();
            Vector3f transformedNormal = new Vector3f();
            for (int i = 0; i < boneCount; i++) {
                YsmModelData.YsmBone bone = data.bones.get(i);
                if (!this.shouldRenderBone(data, i)
                        || !this.isHandBone(data, i, arm)
                        || !this.matchesFilter(bone, filterWord, exclude)) {
                    continue;
                }
                Matrix4f boneMatrix = global[i];
                if (boneMatrix == null) {
                    continue;
                }
                boneMatrix.normal(normalMatrix);
                for (YsmModelData.YsmCube cube : bone.cubes) {
                    for (YsmModelData.YsmQuad quad : cube.quads) {
                        normalMatrix.transform(quad.normal, transformedNormal).normalize();
                        for (int vertex = 0; vertex < 4; vertex++) {
                            boneMatrix.transformPosition(quad.positions[vertex], transformedPosition);
                            vertexConsumer.vertex(poseStack.last().pose(), transformedPosition.x,
                                            transformedPosition.y, transformedPosition.z)
                                    .color(255, 255, 255, 255)
                                    .uv(quad.uvs[vertex].x, quad.uvs[vertex].y)
                                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                                    .uv2(packedLight)
                                    .normal(poseStack.last().normal(), transformedNormal.x,
                                            transformedNormal.y, transformedNormal.z)
                                    .endVertex();
                            rendered = true;
                        }
                    }
                }
            }
        } finally {
            poseStack.popPose();
        }
        return rendered;
    }

    private void ensureArmPoseCapacity(int boneCount) {
        if (this.armPoseRotationX.length == boneCount) {
            return;
        }
        this.armPoseRotationX = new float[boneCount];
        this.armPoseRotationY = new float[boneCount];
        this.armPoseRotationZ = new float[boneCount];
        this.armPosePositionX = new float[boneCount];
        this.armPosePositionY = new float[boneCount];
        this.armPosePositionZ = new float[boneCount];
        this.armPoseScaleX = new float[boneCount];
        this.armPoseScaleY = new float[boneCount];
        this.armPoseScaleZ = new float[boneCount];
    }

    private static void prepareBindPose(YsmModelData data, float[] rotationX, float[] rotationY,
                                        float[] rotationZ, float[] positionX, float[] positionY,
                                        float[] positionZ, float[] scaleX, float[] scaleY,
                                        float[] scaleZ) {
        for (int i = 0; i < data.bones.size(); i++) {
            YsmModelData.YsmBone bone = data.bones.get(i);
            rotationX[i] = bone.rotX;
            rotationY[i] = bone.rotY;
            rotationZ[i] = bone.rotZ;
            positionX[i] = positionY[i] = positionZ[i] = 0.0f;
            scaleX[i] = scaleY[i] = scaleZ[i] = 1.0f;
        }
    }

    private Matrix4f[] buildLocalMatrices(YsmModelData data, float[] rotationX,
                                          float[] rotationY, float[] rotationZ,
                                          float[] positionX, float[] positionY,
                                          float[] positionZ, float[] scaleX,
                                          float[] scaleY, float[] scaleZ) {
        Matrix4f[] local = new Matrix4f[data.bones.size()];
        for (int i = 0; i < data.bones.size(); i++) {
            YsmModelData.YsmBone bone = data.bones.get(i);
            float px = bone.pivotX / 16.0f;
            float py = bone.pivotY / 16.0f;
            float pz = bone.pivotZ / 16.0f;
            local[i] = new Matrix4f().identity()
                    .translate(-positionX[i] / 16.0f, positionY[i] / 16.0f, positionZ[i] / 16.0f)
                    .translate(px, py, pz)
                    .rotateZ(rotationZ[i])
                    .rotateY(rotationY[i])
                    .rotateX(rotationX[i])
                    .scale(scaleX[i], scaleY[i], scaleZ[i])
                    .translate(-bone.pivotX / 16.0f, -bone.pivotY / 16.0f, -bone.pivotZ / 16.0f);
        }
        return local;
    }

    private Matrix4f computeGlobal(YsmModelData data, int index, Matrix4f[] local, Matrix4f[] cache) {
        if (cache[index] != null) {
            return cache[index];
        }
        Matrix4f result = new Matrix4f(local[index]);
        YsmModelData.YsmBone bone = data.bones.get(index);
        if (bone.parentIdx >= 0 && bone.parentIdx < local.length && bone.parentIdx != index) {
            result = new Matrix4f(this.computeGlobal(data, bone.parentIdx, local, cache)).mul(local[index]);
        }
        cache[index] = result;
        return result;
    }

    private boolean isHandBone(YsmModelData data, int index, HumanoidArm arm) {
        int current = index;
        String target = arm == HumanoidArm.LEFT ? "left" : "right";
        while (current >= 0 && current < data.bones.size()) {
            String name = data.bones.get(current).name == null
                    ? "" : data.bones.get(current).name.toLowerCase();
            if (name.equals(arm == HumanoidArm.LEFT ? "leftarm" : "rightarm")
                    || name.equals(arm == HumanoidArm.LEFT ? "arml" : "armr")) {
                return true;
            }
            current = data.bones.get(current).parentIdx;
        }
        // Some hand-only exports omit the LeftArm/RightArm root and name the
        // branch directly.  Keep those valid models renderable as well.
        String name = data.bones.get(index).name == null
                ? "" : data.bones.get(index).name.toLowerCase();
        return name.contains(target + "arm") || name.contains(target + "hand")
                || name.contains(target + "forearm");
    }

    private boolean matchesFilter(YsmModelData.YsmBone bone, String filter, boolean exclude) {
        if (filter.isEmpty()) {
            return true;
        }
        boolean match = bone.name != null && bone.name.toLowerCase().contains(filter.toLowerCase());
        return exclude ? !match : match;
    }

    /**
     * Returns whether a bone belongs to the in-game character render.  YSM
     * scene exports commonly use a root named Background/background and put
     * signs, flowers, pets, particles, or helper locators below it.  Checking
     * the complete parent chain also handles unnamed child bones safely.
     */
    private boolean shouldRenderBone(YsmModelData data, int index) {
        if (!this.characterOnly.getValue()) {
            return true;
        }
        boolean[] visibility = this.characterFilterCache.get(data);
        if (visibility == null || visibility.length != data.bones.size()) {
            visibility = new boolean[data.bones.size()];
            for (int i = 0; i < data.bones.size(); i++) {
                int current = i;
                boolean visible = true;
                while (current >= 0 && current < data.bones.size()) {
                    YsmModelData.YsmBone bone = data.bones.get(current);
                    if (isCharacterOnlyExcludedName(bone.name)) {
                        visible = false;
                        break;
                    }
                    current = bone.parentIdx;
                }
                visibility[i] = visible;
            }
            this.characterFilterCache.put(data, visibility);
        }
        return index >= 0 && index < visibility.length && visibility[index];
    }

    private static boolean isCharacterOnlyExcludedName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String normalized = name.toLowerCase(java.util.Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        if (normalized.endsWith("locator") || normalized.startsWith("molang")) {
            return true;
        }
        for (String token : CHARACTER_ONLY_AUXILIARY_TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private void ensurePoseCapacity(int boneCount) {
        if (this.poseRotationX.length == boneCount) {
            return;
        }
        this.poseRotationX = new float[boneCount];
        this.poseRotationY = new float[boneCount];
        this.poseRotationZ = new float[boneCount];
        this.posePositionX = new float[boneCount];
        this.posePositionY = new float[boneCount];
        this.posePositionZ = new float[boneCount];
        this.poseScaleX = new float[boneCount];
        this.poseScaleY = new float[boneCount];
        this.poseScaleZ = new float[boneCount];
    }

    private void prepareBindPose() {
        for (int i = 0; i < this.model.bones.size(); i++) {
            YsmModelData.YsmBone bone = this.model.bones.get(i);
            this.poseRotationX[i] = bone.rotX;
            this.poseRotationY[i] = bone.rotY;
            this.poseRotationZ[i] = bone.rotZ;
            this.posePositionX[i] = 0.0f;
            this.posePositionY[i] = 0.0f;
            this.posePositionZ[i] = 0.0f;
            this.poseScaleX[i] = 1.0f;
            this.poseScaleY[i] = 1.0f;
            this.poseScaleZ[i] = 1.0f;
        }
    }

    private int findVanillaTorsoBone() {
        String[] names = {"UpperBody", "Body", "UpBody", "MUpperBody", "MUpBody"};
        for (String wanted : names) {
            for (int i = 0; i < this.model.bones.size(); i++) {
                String actual = this.model.bones.get(i).name;
                if (actual != null && actual.equalsIgnoreCase(wanted)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean isDescendantOf(int index, int ancestor) {
        int current = index;
        while (current >= 0 && current < this.model.bones.size()) {
            YsmModelData.YsmBone bone = this.model.bones.get(current);
            current = bone.parentIdx;
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private void applyVanillaAnimation(LocalPlayer player, float partialTick) {
        float[] rotation = client.nilore.patch.LivingEntityRendererPatch.SELF_BONE_ROT;
        // YSM uses the same axis convention as OpenYSM: X/Y are inverted, Z is preserved.
        float headX = -rotation[0];
        float headY = -rotation[1];
        float headZ = rotation[2];
        float bodyX = -rotation[3];
        float bodyY = -rotation[4];
        float bodyZ = rotation[5];
        float leftArmX = -rotation[6];
        float leftArmY = -rotation[7];
        float leftArmZ = rotation[8];
        float rightArmX = -rotation[9];
        float rightArmY = -rotation[10];
        float rightArmZ = rotation[11];
        float leftLegX = -rotation[12];
        float leftLegY = -rotation[13];
        float leftLegZ = rotation[14];
        float rightLegX = -rotation[15];
        float rightLegY = -rotation[16];
        float rightLegZ = rotation[17];
        int torsoBone = this.findVanillaTorsoBone();

        // SELF_BONE_ROT is sampled from the real PlayerModel after vanilla's complete
        // setupAnim call. Do not recreate attack or item poses here; doing so applies
        // those rotations a second time and diverges most visibly while Killaura attacks.
        if (this.flipLeftWalk.getValue()) leftArmX = -leftArmX;
        if (this.flipRightWalk.getValue()) rightArmX = -rightArmX;
        if (this.flipOther.getValue()) {
            bodyY = -bodyY;
            leftArmX = -leftArmX;
            leftArmY = -leftArmY;
            leftArmZ = -leftArmZ;
            rightArmX = -rightArmX;
            rightArmY = -rightArmY;
            rightArmZ = -rightArmZ;
        }

        for (int i = 0; i < this.model.bones.size(); i++) {
            YsmModelData.YsmBone bone = this.model.bones.get(i);
            String name = bone.name == null ? "" : bone.name;
            // YSM player models commonly wrap the torso in several empty
            // bones (UpBody/MUpperBody/UpperBody). Vanilla's body rotation
            // belongs to one pivot only; applying it to every wrapper doubles
            // the crouch angle. Child head/arm rotations are made relative to
            // that pivot because vanilla PlayerModel keeps them as siblings.
            boolean underTorso = torsoBone >= 0 && i != torsoBone && this.isDescendantOf(i, torsoBone);
            float childBodyX = underTorso ? bodyX : 0.0f;
            float childBodyY = underTorso ? bodyY : 0.0f;
            float childBodyZ = underTorso ? bodyZ : 0.0f;
            // Geometry rotation is the bind pose.  Vanilla animation is an offset
            // on top of it; replacing it loses model-authored leg/arm alignment.
            if (i == torsoBone) {
                this.poseRotationX[i] = bone.rotX + bodyX;
                this.poseRotationY[i] = bone.rotY + bodyY;
                this.poseRotationZ[i] = bone.rotZ + bodyZ;
            } else if (name.equals("LeftLeg") || name.equals("LegL")) {
                this.poseRotationX[i] = bone.rotX + leftLegX;
                this.poseRotationY[i] = bone.rotY + leftLegY;
                this.poseRotationZ[i] = bone.rotZ + leftLegZ;
            } else if (name.equals("RightLeg") || name.equals("LegR")) {
                this.poseRotationX[i] = bone.rotX + rightLegX;
                this.poseRotationY[i] = bone.rotY + rightLegY;
                this.poseRotationZ[i] = bone.rotZ + rightLegZ;
            } else if (name.equals("LeftArm") || name.equals("ArmL")) {
                this.poseRotationX[i] = bone.rotX + leftArmX - childBodyX;
                this.poseRotationY[i] = bone.rotY + leftArmY - childBodyY;
                this.poseRotationZ[i] = bone.rotZ + leftArmZ - childBodyZ;
            } else if (name.equals("RightArm") || name.equals("ArmR")) {
                this.poseRotationX[i] = bone.rotX + rightArmX - childBodyX;
                this.poseRotationY[i] = bone.rotY + rightArmY - childBodyY;
                this.poseRotationZ[i] = bone.rotZ + rightArmZ - childBodyZ;
            } else if (name.equals("Head") || name.equals("head")) {
                this.poseRotationX[i] = bone.rotX + headX - childBodyX;
                this.poseRotationY[i] = bone.rotY + headY - childBodyY;
                this.poseRotationZ[i] = bone.rotZ + headZ - childBodyZ;
            }
        }
    }

    private void applyHeadTracking() {
        float[] rotation = client.nilore.patch.LivingEntityRendererPatch.SELF_BONE_ROT;
        for (int i = 0; i < this.model.bones.size(); i++) {
            YsmModelData.YsmBone bone = this.model.bones.get(i);
            String name = bone.name == null ? "" : bone.name;
            if (name.equals("Head") || name.equals("head")) {
                this.poseRotationX[i] -= rotation[0];
                this.poseRotationY[i] -= rotation[1];
                this.poseRotationZ[i] += rotation[2];
            }
        }
    }

    /**
     * Applies only HumanoidModel's attack delta. OpenYSM remains responsible
     * for idle/walk/run/crouch, so those tracks are never doubled.
     */
    private void applyVanillaSwing(LocalPlayer player, float partialTick) {
        float attack = player.getAttackAnim(partialTick);
        if (attack <= 0.001f) return;

        net.minecraft.world.entity.HumanoidArm mainArm = player.getMainArm();
        InteractionHand swingHand = player.swingingArm;
        net.minecraft.world.entity.HumanoidArm attackArm = swingHand == InteractionHand.MAIN_HAND
                ? mainArm
                : (mainArm == net.minecraft.world.entity.HumanoidArm.LEFT
                ? net.minecraft.world.entity.HumanoidArm.RIGHT
                : net.minecraft.world.entity.HumanoidArm.LEFT);

        float bodyYaw = Mth.sin(Mth.sqrt(attack) * Mth.TWO_PI) * 0.2f;
        if (attackArm == net.minecraft.world.entity.HumanoidArm.LEFT) {
            bodyYaw = -bodyYaw;
        }
        float eased = 1.0f - attack;
        eased *= eased;
        eased *= eased;
        eased = 1.0f - eased;
        float armX = -(Mth.sin(eased * Mth.PI) * 1.2f
                + Mth.sin(attack * Mth.PI)
                * -(client.nilore.patch.LivingEntityRendererPatch.SELF_BONE_ROT[0] - 0.7f) * 0.75f);
        float armY = bodyYaw * 2.0f;
        float armZ = Mth.sin(attack * Mth.PI) * -0.4f;
        int torsoBone = this.findVanillaTorsoBone();

        for (int i = 0; i < this.model.bones.size(); i++) {
            YsmModelData.YsmBone bone = this.model.bones.get(i);
            String name = bone.name == null ? "" : bone.name;
            if (i == torsoBone) {
                this.poseRotationY[i] -= bodyYaw;
                continue;
            }
            boolean leftArm = name.equalsIgnoreCase("LeftArm") || name.equalsIgnoreCase("ArmL");
            boolean rightArm = name.equalsIgnoreCase("RightArm") || name.equalsIgnoreCase("ArmR");
            if (leftArm) {
                // Vanilla adds the body's attack yaw to the left arm's X axis.
                this.poseRotationX[i] -= bodyYaw;
            }
            if ((leftArm || rightArm)
                    && (torsoBone < 0 || !this.isDescendantOf(i, torsoBone))) {
                // Normal YSM rigs inherit this from UpperBody. Flat rigs need
                // the equivalent local yaw explicitly.
                this.poseRotationY[i] -= bodyYaw;
            }
            boolean selectedArm = attackArm == net.minecraft.world.entity.HumanoidArm.LEFT
                    ? leftArm : rightArm;
            if (selectedArm) {
                // YSM/OpenYSM invert X/Y relative to vanilla and preserve Z.
                this.poseRotationX[i] -= armX;
                this.poseRotationY[i] -= armY;
                this.poseRotationZ[i] += armZ;
            }
        }
    }

    private void applyEntityRotations(LocalPlayer player, PoseStack poseStack,
                                      float bodyYaw, float partialTick) {
        if (player.getPose() == net.minecraft.world.entity.Pose.SLEEPING) {
            net.minecraft.core.Direction bedDirection = player.getBedOrientation();
            if (bedDirection != null) {
                float eyeHeight = player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING) - 0.1f;
                poseStack.translate(-bedDirection.getStepX() * eyeHeight, 0.0f,
                        -bedDirection.getStepZ() * eyeHeight);
            }
        }
        if (player.isFullyFrozen()) {
            bodyYaw += Mth.cos(player.tickCount * 3.25f) * (float)Math.PI * 0.4f;
        }
        if (player.getPose() != net.minecraft.world.entity.Pose.SLEEPING) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f - bodyYaw));
        }
        if (player.deathTime > 0) {
            float death = ((float)player.deathTime + partialTick - 1.0f) / 20.0f * 1.6f;
            death = Mth.sqrt(death);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Math.min(death, 1.0f) * 90.0f));
        } else if (player.isAutoSpinAttack()) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0f - player.getXRot()));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                    ((float)player.tickCount + partialTick) * -75.0f));
        } else if (player.getPose() == net.minecraft.world.entity.Pose.SLEEPING) {
            net.minecraft.core.Direction direction = player.getBedOrientation();
            float rotation = direction == null ? bodyYaw : switch (direction) {
                case SOUTH -> 90.0f;
                case WEST -> 0.0f;
                case NORTH -> 270.0f;
                case EAST -> 180.0f;
                default -> bodyYaw;
            };
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotation));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0f));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(270.0f));
        } else if (net.minecraft.client.renderer.entity.LivingEntityRenderer.isEntityUpsideDown(player)) {
            poseStack.translate(0.0f, player.getBbHeight() + 0.1f, 0.0f);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0f));
        }

        float swimAmount = player.getSwimAmount(partialTick);
        if (player.isFallFlying()) {
            float flyingTicks = (float)player.getFallFlyingTicks() + partialTick;
            float flyingAmount = Mth.clamp(flyingTicks * flyingTicks / 100.0f, 0.0f, 1.0f);
            if (!player.isAutoSpinAttack()) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(
                        flyingAmount * (-90.0f - player.getXRot())));
            }
            net.minecraft.world.phys.Vec3 view = player.getViewVector(partialTick);
            net.minecraft.world.phys.Vec3 movement = player.getDeltaMovementLerped(partialTick);
            double movementHorizontal = movement.horizontalDistanceSqr();
            double viewHorizontal = view.horizontalDistanceSqr();
            if (movementHorizontal > 0.0 && viewHorizontal > 0.0) {
                double dot = (movement.x * view.x + movement.z * view.z)
                        / Math.sqrt(movementHorizontal * viewHorizontal);
                double cross = movement.x * view.z - movement.z * view.x;
                poseStack.mulPose(com.mojang.math.Axis.YP.rotation(
                        (float)(Math.signum(cross) * Math.acos(Mth.clamp(dot, -1.0, 1.0)))));
            }
        } else if (swimAmount > 0.0f) {
            float targetPitch = player.isInWater() ? -90.0f - player.getXRot() : -90.0f;
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(
                    Mth.lerp(swimAmount, 0.0f, targetPitch)));
            if (player.isVisuallySwimming()) {
                poseStack.translate(0.0f, -1.0f, 0.3f);
            }
        }
    }

    private Matrix4f computeGlobal(int index, Matrix4f[] local, Matrix4f[] cache) {
        if (cache[index] != null) {
            return cache[index];
        }
        Matrix4f g = new Matrix4f(local[index]);
        YsmModelData.YsmBone bone = this.model.bones.get(index);
        if (bone.parentIdx >= 0 && bone.parentIdx < local.length && index != bone.parentIdx) {
            Matrix4f pg = this.computeGlobal(bone.parentIdx, local, cache);
            g = new Matrix4f(pg).mul(local[index]);
        }
        cache[index] = g;
        return g;
    }

    /**
     * Locator 的末端没有普通骨骼的 pivot 回退平移；这是 OpenYSM
     * RenderUtils.prepMatrixForLocator 的关键区别，物品因此会落在预设手持点。
     */
    private Matrix4f computeLocatorGlobal(int index, Matrix4f[] local, Matrix4f[] cache) {
        Matrix4f parent = null;
        YsmModelData.YsmBone bone = this.model.bones.get(index);
        if (bone.parentIdx >= 0 && bone.parentIdx < local.length && bone.parentIdx != index) {
            parent = this.computeGlobal(bone.parentIdx, local, cache);
        }
        Matrix4f locatorLocal = new Matrix4f().identity()
                .translate(-this.posePositionX[index] / 16.0f,
                        this.posePositionY[index] / 16.0f,
                        this.posePositionZ[index] / 16.0f)
                .translate(bone.pivotX / 16.0f, bone.pivotY / 16.0f, bone.pivotZ / 16.0f)
                .rotateZ(this.poseRotationZ[index])
                .rotateY(this.poseRotationY[index])
                .rotateX(this.poseRotationX[index])
                .scale(this.poseScaleX[index], this.poseScaleY[index], this.poseScaleZ[index]);
        return parent == null ? locatorLocal : new Matrix4f(parent).mul(locatorLocal);
    }

    private void renderHeldItem(LocalPlayer player, ItemStack item, String locatorName,
                                 net.minecraft.world.item.ItemDisplayContext displayContext,
                                 boolean leftHand, float itemSize, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight,
                                 Matrix4f[] local, Matrix4f[] globalCache) {
        if (item == null || item.isEmpty()) {
            return;
        }
        // Gun poses animate around the dedicated PistolLocator/RifleLocator,
        // rather than the ordinary hand locator. Clay weapons share those
        // OpenYSM poses even though their item model is not an actual TACZ IGun.
        if (this.renderGunItem(player, item, displayContext, leftHand, itemSize,
                poseStack, bufferSource, packedLight, local, globalCache)) {
            return;
        }
        int locator = this.findBone(locatorName);
        if (locator < 0) {
            return;
        }
        Matrix4f locatorMatrix = this.computeLocatorGlobal(locator, local, globalCache);
        poseStack.pushPose();
        // OpenYSM applies the complete locator chain (translation, rotation and
        // scale).  Applying only the origin position leaves every item facing the
        // model's default direction instead of following the animated hand.
        poseStack.mulPoseMatrix(locatorMatrix);
        poseStack.translate(0.0d, -0.0625d, -0.1d);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0f));
        poseStack.scale(itemSize, itemSize, itemSize);
        mc.gameRenderer.itemInHandRenderer.renderItem(player, item, displayContext, leftHand,
                poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    private boolean renderGunItem(LocalPlayer player, ItemStack item,
                                  net.minecraft.world.item.ItemDisplayContext displayContext,
                                  boolean leftHand, float itemSize, PoseStack poseStack,
                                  MultiBufferSource bufferSource, int packedLight,
                                  Matrix4f[] local, Matrix4f[] globalCache) {
        if (!YsmAnimationPlayer.isTaczGun(item)) {
            return false;
        }
        boolean nativeTacz = YsmAnimationPlayer.isNativeTaczGun(item);
        if (!nativeTacz && !this.animationMode.is("OpenYSM Animation")) {
            return false;
        }
        String gunType = YsmAnimationPlayer.taczGunType(item);
        if (gunType == null) {
            return false;
        }
        String locatorName = gunType.equals("pistol") ? "PistolLocator" : "RifleLocator";
        int locator = this.findBone(locatorName);
        if (locator < 0) {
            return false;
        }
        Matrix4f locatorMatrix = this.computeLocatorGlobal(locator, local, globalCache);
        poseStack.pushPose();
        poseStack.mulPoseMatrix(locatorMatrix);
        if (nativeTacz) {
            // 原生 TACZ：模型按 FIXED 上下文建模，套用枪位专用朝向
            if (gunType.equals("pistol")) {
                poseStack.translate(0.0d, -0.125d, 0.0d);
                poseStack.scale(0.65f * itemSize, 0.65f * itemSize, 0.65f * itemSize);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90.0f));
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0f));
            } else {
                poseStack.scale(0.65f * itemSize, 0.65f * itemSize, 0.65f * itemSize);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-180.0f));
            }
            mc.getItemRenderer().renderStatic(item,
                    net.minecraft.world.item.ItemDisplayContext.FIXED,
                    packedLight, OverlayTexture.NO_OVERLAY, poseStack, bufferSource,
                    player.level(), player.getId());
        } else {
            // Clay（非原生 TACZ）：物品自带三视角变换，不能套 TACZ 专用的枪位旋转
            // （否则朝向被多转 180°/侧翻 → “朝向和原版不一致”）。这里改用与
            // "Vanilla Animation" 手部路径一致的方向，但仍锚定枪位 locator 以跟随
            // TACZ 上半身动画。
            poseStack.translate(0.0d, -0.0625d, -0.1d);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0f));
            poseStack.scale(itemSize, itemSize, itemSize);
            mc.gameRenderer.itemInHandRenderer.renderItem(player, item, displayContext,
                    leftHand, poseStack, bufferSource, packedLight);
        }
        poseStack.popPose();
        return true;
    }

    private int findBone(String name) {
        Integer exact = this.model.boneIndex.get(name);
        if (exact != null) {
            return exact;
        }
        String lower = name.toLowerCase();
        for (int i = 0; i < this.model.bones.size(); i++) {
            String boneName = this.model.bones.get(i).name;
            if (boneName != null && boneName.toLowerCase().contains(lower)) {
                return i;
            }
        }
        return -1;
    }


    @Override
    public String getDisplayName() {
        return "YsmModel";
    }

    @Override
    public String getModuleName() {
        return "YsmModel";
    }

    @Override
    public void onTick() {
    }
}
