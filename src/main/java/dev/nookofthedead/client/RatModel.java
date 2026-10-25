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
 * The infected rat, drawn from nothing of vanilla's (the user, 2026-10-16: "ネズミに見えない…シルバーフィッシュの使い回し
 * ではなく、一から作って"): a long low body hunched at the back, a pointed head with round ears and a snout, four short
 * legs, and a long bare tail in two lengths that swings as it runs and twitches as it waits. Its picture is
 * {@code textures/entity/infected_rat.png} (32 x 32).
 */
public class RatModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(NookOfTheDead.id("infected_rat"), "main");

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart rightFront;
    private final ModelPart leftFront;
    private final ModelPart rightHind;
    private final ModelPart leftHind;

    public RatModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.tail = root.getChild("tail");
        this.tailTip = this.tail.getChild("tip");
        this.rightFront = root.getChild("right_front_leg");
        this.leftFront = root.getChild("left_front_leg");
        this.rightHind = root.getChild("right_hind_leg");
        this.leftHind = root.getChild("left_hind_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // the body, its back end a little higher (the hunch)
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.0F, -4.5F, 5.0F, 4.0F, 9.0F),
                PartPose.offsetAndRotation(0.0F, 19.5F, 0.0F, -0.08F, 0.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 13).addBox(-2.0F, -1.5F, -3.5F, 4.0F, 3.0F, 4.0F)          // the skull
                .texOffs(16, 13).addBox(-1.0F, -0.5F, -5.5F, 2.0F, 2.0F, 2.0F),        // the snout
                PartPose.offset(0.0F, 19.5F, -4.5F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(24, 13).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-1.6F, -1.2F, -1.0F, 0.0F, 0.3F, -0.35F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(24, 13).mirror().addBox(-1.0F, -2.0F, 0.0F, 2.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(1.6F, -1.2F, -1.0F, 0.0F, -0.3F, 0.35F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F);
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-1.6F, 21.0F, -3.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(1.6F, 21.0F, -3.0F));
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-1.8F, 21.0F, 3.2F));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(1.8F, 21.0F, 3.2F));
        // the tail: bare, thinner than the cubes it is drawn with, in two lengths
        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(8, 20)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(-0.15F)),
                PartPose.offsetAndRotation(0.0F, 19.0F, 4.3F, -0.35F, 0.0F, 0.0F));
        tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(8, 20)
                .addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(-0.28F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 5.6F, 0.3F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        float pos = state.walkAnimationPos;
        float speed = Math.min(1.0F, state.walkAnimationSpeed);
        // a scurry: quick short steps
        this.rightFront.xRot = Mth.cos(pos * 1.6F) * 1.2F * speed;
        this.leftHind.xRot = Mth.cos(pos * 1.6F) * 1.2F * speed;
        this.leftFront.xRot = Mth.cos(pos * 1.6F + Mth.PI) * 1.2F * speed;
        this.rightHind.xRot = Mth.cos(pos * 1.6F + Mth.PI) * 1.2F * speed;
        this.body.y = 19.5F - Math.abs(Mth.sin(pos * 1.6F)) * 0.6F * speed;
        // the tail swings side to side as it runs, and twitches at rest
        float twitch = Mth.sin(state.ageInTicks * 0.12F) * 0.25F + (Mth.sin(state.ageInTicks * 0.9F) > 0.95F ? 0.2F : 0.0F);
        this.tail.yRot = Mth.sin(pos * 0.8F) * 0.5F * speed + twitch * (1 - speed);
        this.tailTip.yRot = this.tail.yRot * 0.8F;
        // the nose works
        this.head.zRot = Mth.sin(state.ageInTicks * 0.5F) * 0.03F;
    }
}
