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
 * The infected scorpion, drawn from nothing of vanilla's (the user, 2026-10-18: "感染サソリ"): a flat body low to the sand,
 * the carapace before it, two arms reaching forward with a claw each that opens and shuts, four legs a side, and the
 * tail in five segments curled up over its back to the sting. Its legs step in waves as it runs; at rest the tail sways
 * and the claws work; when it strikes the tail comes down over its head. Its picture is
 * {@code textures/entity/infected_scorpion.png} (64 x 32, tools/paint_desert.py).
 */
public class ScorpionModel extends EntityModel<ScorpionRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(NookOfTheDead.id("infected_scorpion"), "main");
    private static final float[] LEG_Z = {-2.6F, -0.9F, 0.8F, 2.5F};
    private static final float[] LEG_SPREAD = {-0.55F, -0.18F, 0.18F, 0.55F};
    private static final float TAIL_CURL = 0.9F;
    private static final float TAIL_STEP = 0.45F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart[] arms = new ModelPart[2];
    private final ModelPart[] fingers = new ModelPart[2];
    private final ModelPart[][] legs = new ModelPart[2][4];
    private final ModelPart[] tail = new ModelPart[5];

    public ScorpionModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        String[] sides = {"right", "left"};
        for (int s = 0; s < 2; s++) {
            this.arms[s] = root.getChild(sides[s] + "_arm");
            this.fingers[s] = this.arms[s].getChild("claw").getChild("finger");
            for (int k = 0; k < 4; k++) {
                this.legs[s][k] = root.getChild(sides[s] + "_leg_" + k);
            }
        }
        ModelPart part = root.getChild("tail");
        for (int k = 0; k < 5; k++) {
            this.tail[k] = part;
            if (k < 4) {
                part = part.getChild("next");
            }
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -1.5F, -4.5F, 6.0F, 3.0F, 9.0F),
                PartPose.offset(0.0F, 21.2F, 0.5F));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(30, 0).addBox(-2.5F, -1.0F, -3.0F, 5.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 21.4F, -4.0F));
        for (int s = 0; s < 2; s++) {
            float x = s == 0 ? -1.0F : 1.0F;
            boolean mirror = s == 1;
            // an arm forward and out from beside the head, its claw at the end, a finger that opens
            PartDefinition arm = root.addOrReplaceChild(s == 0 ? "right_arm" : "left_arm", CubeListBuilder.create().texOffs(46, 0).mirror(mirror)
                    .addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(-0.25F)),
                    PartPose.offsetAndRotation(x * 2.2F, 21.2F, -5.0F, -0.1F, x * -0.55F, 0.0F));
            PartDefinition claw = arm.addOrReplaceChild("claw", CubeListBuilder.create().texOffs(30, 6).mirror(mirror)
                    .addBox(-1.5F, -1.0F, -3.0F, 3.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -3.6F, 0.0F, x * 0.75F, 0.0F));
            claw.addOrReplaceChild("finger", CubeListBuilder.create().texOffs(42, 6).mirror(mirror)
                    .addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(x * -1.0F, 0.0F, -2.6F, 0.0F, x * -0.35F, 0.0F));
            // four legs a side, down to the sand and out, the front ones forward, the back ones back
            for (int k = 0; k < 4; k++) {
                CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 12).mirror(mirror)
                        .addBox(s == 0 ? -6.0F : 0.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F));
                root.addOrReplaceChild((s == 0 ? "right_leg_" : "left_leg_") + k, leg,
                        PartPose.offsetAndRotation(x * 2.8F, 21.6F, LEG_Z[k], 0.0F, x * LEG_SPREAD[k], x * 0.5F));
            }
        }
        // the tail: five segments, each curled up a little more than the last, over its back to the sting
        PartDefinition part = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(14, 12)
                .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 20.6F, 4.6F, TAIL_CURL, 0.0F, 0.0F));
        for (int k = 1; k < 5; k++) {
            float shrink = -0.05F * k;
            part = part.addOrReplaceChild("next", CubeListBuilder.create().texOffs(14, 12)
                    .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(shrink)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.6F, TAIL_STEP, 0.0F, 0.0F));
        }
        part.addOrReplaceChild("sting", CubeListBuilder.create().texOffs(24, 12).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(-0.05F)),
                PartPose.offsetAndRotation(0.0F, 0.2F, 2.6F, 0.7F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(ScorpionRenderer.State state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        float pos = state.walkAnimationPos;
        float speed = Math.min(1.0F, state.walkAnimationSpeed);
        // the legs in waves, alternate ones together
        for (int s = 0; s < 2; s++) {
            float x = s == 0 ? -1.0F : 1.0F;
            for (int k = 0; k < 4; k++) {
                float phase = pos * 1.8F + k * Mth.PI / 2 + (s == 0 ? 0.0F : Mth.PI);
                this.legs[s][k].yRot = x * LEG_SPREAD[k] + Mth.cos(phase) * 0.4F * speed;
                this.legs[s][k].zRot = x * (0.5F - Math.max(0.0F, Mth.sin(phase)) * 0.35F * speed);
            }
        }
        this.body.y = 21.2F - Math.abs(Mth.sin(pos * 1.8F)) * 0.25F * speed;
        // the claws work as it waits, open wide as it strikes
        float strike = state.strike > 0 ? Mth.sin(state.strike * Mth.PI) : 0.0F;
        for (int s = 0; s < 2; s++) {
            float x = s == 0 ? -1.0F : 1.0F;
            float open = 0.2F + Mth.sin(age * 0.2F + s) * 0.15F + strike * 0.4F;
            this.fingers[s].yRot = x * -open;
            this.arms[s].xRot = -0.1F - strike * 0.3F + Mth.sin(age * 0.07F + s * 2.0F) * 0.05F;
        }
        // the tail sways, curled; as it strikes it comes down over the head
        this.tail[0].yRot = Mth.sin(age * 0.06F) * 0.12F;
        for (int k = 0; k < 5; k++) {
            float rest = k == 0 ? TAIL_CURL : TAIL_STEP;
            this.tail[k].xRot = rest + Mth.sin(age * 0.09F - k * 0.6F) * 0.04F + strike * (k < 2 ? 0.25F : 0.12F);
        }
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.3F;
    }
}
