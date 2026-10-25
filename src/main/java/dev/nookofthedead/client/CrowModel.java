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
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * The capital's crow, drawn from nothing of vanilla's: a body, a head with its heavy beak, a fanned tail, and two broad
 * wings that beat and, between beats, hold out to glide. Its picture is {@code textures/entity/capital_crow.png} (32 x 32,
 * tools/paint_crow.py).
 */
public class CrowModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(NookOfTheDead.id("capital_crow"), "main");

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart rightTip;
    private final ModelPart leftTip;

    public CrowModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.tail = root.getChild("tail");
        this.rightWing = root.getChild("right_wing");
        this.leftWing = root.getChild("left_wing");
        this.rightTip = this.rightWing.getChild("tip");
        this.leftTip = this.leftWing.getChild("tip");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, -3.0F, 3.0F, 3.0F, 6.0F),
                PartPose.offset(0.0F, 20.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 9).addBox(-1.5F, -1.5F, -3.0F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 19.3F, -2.8F));
        head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(12, 9).addBox(-0.5F, -0.5F, -2.0F, 1.0F, 1.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.4F, -3.0F, 0.15F, 0.0F, 0.0F));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 15).addBox(-1.5F, -0.5F, 0.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F, -0.3F, 0.0F)),
                PartPose.offsetAndRotation(0.0F, 19.8F, 2.8F, -0.15F, 0.0F, 0.0F));
        // the wings: from the shoulder out, then the long hand of the wing
        PartDefinition right = root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-4.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F, -0.35F, 0.0F)), PartPose.offset(-1.5F, 19.0F, -0.5F));
        right.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(16, 20)
                .addBox(-4.0F, -0.5F, -1.5F, 4.0F, 1.0F, 3.0F, new CubeDeformation(0.0F, -0.4F, 0.0F)), PartPose.offset(-4.0F, 0.0F, 0.3F));
        PartDefinition left = root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 20).mirror()
                .addBox(0.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F, -0.35F, 0.0F)), PartPose.offset(1.5F, 19.0F, -0.5F));
        left.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(16, 20).mirror()
                .addBox(0.0F, -0.5F, -1.5F, 4.0F, 1.0F, 3.0F, new CubeDeformation(0.0F, -0.4F, 0.0F)), PartPose.offset(4.0F, 0.0F, 0.3F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
        // a few beats, then a glide on wings held out; every crow its own time
        float t = state.ageInTicks;
        float cycle = (t * 0.07F) % 2.0F;
        boolean beating = cycle < 1.2F;
        float beat = beating ? Mth.sin(t * 0.9F) : 0.12F;
        this.rightWing.zRot = beat * 0.75F;
        this.leftWing.zRot = -beat * 0.75F;
        this.rightTip.zRot = beat * 0.45F;
        this.leftTip.zRot = -beat * 0.45F;
        this.tail.xRot = -0.15F + Mth.sin(t * 0.13F) * 0.08F;
        this.body.xRot = 0.0F;
    }
}
