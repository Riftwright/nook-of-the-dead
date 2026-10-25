package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The sewer bloom, drawn from nothing of vanilla's: roots splayed over the floor, a thick stalk, on it a bulb whose four
 * petals stand shut over its mouth as a bud, and open when it wakes, the head leaning to what it wants, the mouth
 * turned to it - a ring of teeth - and four vines
 * from the stalk in three lengths each, swaying. A lash brings the front vines up and over; a thrown vine one of them
 * straight out. Its picture is {@code textures/entity/sewer_bloom.png} (128 x 64, tools/paint_bloom.py). Drawn at twice
 * this size ({@link BloomRenderer}).
 */
public class BloomModel extends EntityModel<BloomRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(NookOfTheDead.id("sewer_bloom"), "main");
    /** The petals' lean: shut over the mouth, and fallen back open. */
    private static final float SHUT = -0.42F;
    private static final float OPENED = 1.3F;
    /** How far the open head leans toward what it wants, the mouth to it. */
    private static final float LEAN = 0.75F;
    private static final float VINE_DROOP = 0.55F;
    private static final float VINE_CURL = -0.32F;

    private final ModelPart stalk;
    private final ModelPart bulb;
    private final ModelPart[] petals = new ModelPart[4];
    private final ModelPart[][] vines = new ModelPart[4][3];

    public BloomModel(ModelPart root) {
        super(root);
        this.stalk = root.getChild("stalk");
        this.bulb = this.stalk.getChild("bulb");
        for (int k = 0; k < 4; k++) {
            this.petals[k] = this.bulb.getChild("petal_" + k);
            ModelPart part = root.getChild("vine_" + k);
            for (int s = 0; s < 3; s++) {
                this.vines[k][s] = part;
                if (s < 2) {
                    part = part.getChild("next");
                }
            }
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // the roots, splayed over the floor
        for (int k = 0; k < 5; k++) {
            float yaw = k * Mth.TWO_PI / 5 + 0.3F;
            root.addOrReplaceChild("root_" + k, CubeListBuilder.create().texOffs(24, 0).addBox(-1.5F, -2.0F, -8.0F, 3.0F, 2.0F, 8.0F,
                    new CubeDeformation(k % 2 == 0 ? 0.0F : -0.3F)), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.08F, yaw, 0.0F));
        }
        // the stalk, the bulb on it, the mouth on the bulb, the teeth round the mouth
        PartDefinition stalk = root.addOrReplaceChild("stalk", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -14.0F, -3.0F, 6.0F, 14.0F, 6.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition bulb = stalk.addOrReplaceChild("bulb", CubeListBuilder.create().texOffs(0, 20).addBox(-7.0F, -6.0F, -7.0F, 14.0F, 6.0F, 14.0F),
                PartPose.offset(0.0F, -13.0F, 0.0F));
        PartDefinition mouth = bulb.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(56, 20).addBox(-6.0F, -1.0F, -6.0F, 12.0F, 1.0F, 12.0F),
                PartPose.offset(0.0F, -5.5F, 0.0F));
        for (int t = 0; t < 10; t++) {
            float a = t * Mth.TWO_PI / 10;
            // hooked inward over the throat, not splayed (they read as candles on a cake - the visual review, 2026-10-19)
            mouth.addOrReplaceChild("tooth_" + t, CubeListBuilder.create().texOffs(26, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                    PartPose.offsetAndRotation(Mth.sin(a) * 4.8F, -0.8F, Mth.cos(a) * 4.8F, Mth.cos(a) * 0.5F, 0.0F, Mth.sin(a) * -0.5F));
        }
        // four petals hinged on the bulb's top edges, each leaning in over the mouth: a bud
        for (int k = 0; k < 4; k++) {
            float yaw = -k * Mth.HALF_PI;
            bulb.addOrReplaceChild("petal_" + k, CubeListBuilder.create().texOffs(0, 40).addBox(-6.0F, -12.0F, 0.0F, 12.0F, 12.0F, 1.0F),
                    PartPose.offsetAndRotation(-Mth.sin(yaw) * 7.0F, -6.0F, -Mth.cos(yaw) * 7.0F, SHUT, yaw, 0.0F));
        }
        // four vines from the stalk, each in three lengths, outward between the petals
        for (int k = 0; k < 4; k++) {
            float yaw = -(k * Mth.HALF_PI + Mth.HALF_PI / 2);
            PartDefinition part = root.addOrReplaceChild("vine_" + k, CubeListBuilder.create().texOffs(48, 0)
                    .addBox(-1.0F, -1.0F, -7.0F, 2.0F, 2.0F, 7.0F), PartPose.offsetAndRotation(-Mth.sin(yaw) * 2.5F, 15.0F, -Mth.cos(yaw) * 2.5F,
                    VINE_DROOP, yaw, 0.0F));
            part = part.addOrReplaceChild("next", CubeListBuilder.create().texOffs(48, 0)
                    .addBox(-1.0F, -1.0F, -7.0F, 2.0F, 2.0F, 7.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, -6.5F, VINE_CURL, 0.0F, 0.0F));
            part.addOrReplaceChild("next", CubeListBuilder.create().texOffs(66, 0)
                    .addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -6.5F, VINE_CURL, 0.0F, 0.0F));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(BloomRenderer.State state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        float open = state.openness;
        // the petals: from shut to fallen back, trembling a little as they go; the bulb breathes
        float strike = state.swing > 0 ? Mth.sin(state.swing * Mth.PI) : 0.0F;
        // shut, now and then it parts a moment - the ember shows between the petals; a lash snaps them toward shut, a bite
        float peek = (float) Math.pow(Math.max(0.0F, Mth.sin(age * 0.045F)), 6) * 0.18F * (1.0F - open);
        float bite = state.lash ? strike * (OPENED - SHUT) * 0.6F : 0.0F;
        for (int k = 0; k < 4; k++) {
            float tremble = Mth.sin(age * 0.3F + k * 1.7F) * 0.03F * (1.0F - Math.abs(open - 0.5F) * 2.0F + 0.3F);
            this.petals[k].xRot = Mth.lerp(open, SHUT, OPENED) + tremble + Mth.sin(age * 0.05F + k) * 0.04F * open + peek - bite;
        }
        this.bulb.xRot = open * LEAN + (state.lash ? strike * 0.35F : 0.0F) + Mth.sin(age * 0.04F - 0.6F) * 0.03F;
        float breath = 1.0F + Mth.sin(age * 0.06F) * 0.06F;
        this.bulb.xScale = breath;
        this.bulb.zScale = breath;
        this.stalk.xRot = Mth.sin(age * 0.023F) * 0.03F;
        this.stalk.zRot = Mth.cos(age * 0.031F) * 0.03F;
        // the vines sway, more when it is awake; a lash brings the two in front up and over, a thrown vine the first straight out
        for (int k = 0; k < 4; k++) {
            float sway = Mth.sin(age * (0.05F + 0.04F * open) + k * 1.9F);
            boolean front = k == 0 || k == 3;
            float lash = state.lash && front ? strike : 0.0F;
            float thrown = !state.lash && k == 0 ? strike : 0.0F;
            this.vines[k][0].xRot = VINE_DROOP + sway * 0.12F - lash * 1.9F - thrown * 0.6F - open * 0.25F;
            this.vines[k][0].zRot = sway * 0.1F;
            this.vines[k][1].xRot = VINE_CURL + Mth.sin(age * 0.07F + k) * 0.15F + lash * 0.9F + thrown * 0.3F;
            this.vines[k][2].xRot = VINE_CURL + Mth.sin(age * 0.09F + k * 2) * 0.2F + lash * 0.6F + thrown * 0.3F;
        }
    }
}
