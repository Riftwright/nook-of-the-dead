package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.Vandal;
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
 * The Vandal (the user, 2026-10-17: "専用の見た目に…タイラントみたいな感じでかなりでかめに", from a picture they gave; "この
 * modの顔ですから"; "ゾンビが変異した果ての姿だから…ゾンビの延長線上にいることがわかるような表現"): one of the first of the
 * dead, gone on changing until it is hardly a man. Bent over on long arms it walks on as much as on its legs - the arm
 * with the other side's leg - the elbows cocked back, the hands up on long fingers that hook into the ground. Its back is
 * humped with muscle, a ridge of bristling hair along it; strips of skin and of the clothes it died in hang from its
 * chest, belly and flanks nearly to the ground. Its head is held up at the shoulders, a man's face gone to a skull: deep
 * sockets under a heavy brow, the cheeks torn, a long jaw that drops open wide. Its eyes glow faintly, red once it is
 * wounded to half ({@code VandalRenderer}). Drawn from nothing of vanilla's: its skin painted by tools/paint_vandal.py,
 * whose rest pose is this one's.
 * <p>
 * Its poses follow what it is doing ({@link Vandal#pose}): calm, it breathes, its jaw working, its claws flexing; before
 * a roar it rears up, throws its head back, the jaw dropping, its arms flung wide, the claws splayed; before a rush it
 * crouches low; in the rush it runs flat out on all fours. A blow is a swipe of its right arm. Dying, it sinks down where
 * it stands, its arms giving under it.
 */
public class VandalModel extends EntityModel<VandalRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(NookOfTheDead.id("vandal"), "main");

    // the rest pose, from which every move is measured (radians)
    private static final float ABDOMEN = 0.6F;
    private static final float CHEST = 0.45F;
    private static final float NECK = 0.3F;
    private static final float HEAD = -1.2F;
    private static final float JAW = 0.3F;
    /** A tooth's thinness: a box a texel square drawn a little under half as wide. */
    private static final CubeDeformation NEEDLE = new CubeDeformation(-0.28F);
    private static final float THIGH = -0.4F;
    private static final float SHIN = 0.75F;
    private static final float FOOT = -0.35F;
    /** The upper arm back from the shoulder (the elbow behind it), splayed out a little. */
    private static final float UPPER = 0.25F;
    private static final float UPPER_OUT = 0.15F;
    /** The forearm forward and down from the elbow, turned in a little. */
    private static final float FORE = -0.8F;
    private static final float FORE_IN = 0.1F;
    private static final float HAND = 0.35F;
    /** A finger's three joints: from the knuckle forward, then down, the claw hooked under. */
    private static final float KNUCKLE = 0.0F;
    private static final float FINGER = 1.1F;
    private static final float CLAW = 0.95F;
    /** Where the shoulders are on the chest (in its frame): the arms hang from there, wherever the trunk bends to. */
    private static final float SHOULDER_X = 8.5F;
    private static final float SHOULDER_Y = -7.082F;
    private static final float SHOULDER_Z = -0.246F;

    /** A strip of skin or cloth, or a lock of hair, hung from the trunk: kept level, so it hangs straight down. */
    private record Hung(String name, float x, float y, float z, int plane, boolean mirror) {
    }

    private static final Hung[] FROM_CHEST = {
            new Hung("under_0", -4.5F, -9.0F, -5.7F, 0, false),
            new Hung("under_1", 0.5F, -10.0F, -5.7F, 2, false),
            new Hung("under_2", 4.5F, -8.0F, -5.7F, 0, true),
            new Hung("under_3", -2.5F, -4.5F, -5.7F, 1, false),
            new Hung("under_4", 3.0F, -3.5F, -5.7F, 1, true),
            new Hung("under_5", -0.5F, -1.0F, -5.7F, 2, true),
            new Hung("flank_0", -8.2F, -3.0F, 3.0F, 3, false),
            new Hung("flank_1", 8.2F, -3.0F, 3.0F, 3, true)};
    private static final Hung[] FROM_BELLY = {
            new Hung("under_0", -2.5F, -5.5F, -3.7F, 1, false),
            new Hung("under_1", 2.5F, -3.0F, -3.7F, 0, true),
            new Hung("flank_0", -5.2F, -5.0F, 1.5F, 3, false),
            new Hung("flank_1", 5.2F, -5.0F, 1.5F, 3, true)};
    private static final Hung[] FROM_HIPS = {
            new Hung("rump", 0.0F, -3.0F, 3.3F, 5, false)};

    /** A tuft of the bristling ridge: where it grows (in its parent's frame), which of the two, how far back it lies, how
     * it is twisted aside (none of them alike, so the ridge is wild, not a comb). */
    private record Tuft(String name, float x, float y, float z, int kind, float lean, float twist) {
    }

    private static final Tuft[] CREST_CHEST = {
            new Tuft("crest_0", 0.0F, -13.0F, 4.2F, 0, 0.8F, 0.1F),
            new Tuft("crest_1", 0.0F, -9.5F, 5.6F, 0, 0.9F, -0.12F),
            new Tuft("crest_2", 0.0F, -5.5F, 5.6F, 1, 1.0F, 0.08F),
            new Tuft("crest_3", 0.0F, -1.5F, 5.6F, 1, 1.1F, -0.06F)};
    private static final Tuft CREST_BELLY = new Tuft("crest", 0.0F, -6.0F, 3.6F, 1, 1.15F, 0.1F);
    /** On the crown: lying nearly flat back, so from before it is only a few strands sticking up, not a comb. */
    private static final Tuft CREST_HEAD = new Tuft("crest", 0.0F, -7.6F, 0.5F, 1, 1.3F, 0.05F);

    private final ModelPart hips;
    private final ModelPart abdomen;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart chin;
    private final ModelPart[] hair;
    private final ModelPart[] chestHung = new ModelPart[FROM_CHEST.length];
    private final ModelPart[] bellyHung = new ModelPart[FROM_BELLY.length];
    private final ModelPart[] hipsHung = new ModelPart[FROM_HIPS.length];
    private final ModelPart[] chestCrest = new ModelPart[CREST_CHEST.length];
    private final ModelPart bellyCrest;
    private final ModelPart headCrest;
    private final ModelPart[] waistRags = new ModelPart[2];
    private final ModelPart[] upper = new ModelPart[2];
    private final ModelPart[] fore = new ModelPart[2];
    private final ModelPart[] hand = new ModelPart[2];
    /** Each hand's four fingers, each in three joints; and its thumb, in two. */
    private final ModelPart[][][] fingers = new ModelPart[2][4][3];
    private final ModelPart[][] thumbs = new ModelPart[2][2];
    /** What hangs from each forearm: a rag of the sleeve, a strip of the bandage; and the chain from the left one's cuff. */
    private final ModelPart[][] armHung = new ModelPart[2][];
    /** The hair hanging over each shoulder, down the outside of the upper arm. */
    private final ModelPart[] shoulderHair = new ModelPart[2];
    private final ModelPart[] thigh = new ModelPart[2];
    private final ModelPart[] shin = new ModelPart[2];
    private final ModelPart[] foot = new ModelPart[2];

    public VandalModel(ModelPart root) {
        super(root);
        this.hips = root.getChild("hips");
        this.waistRags[0] = this.hips.getChild("rag_front");
        this.waistRags[1] = this.hips.getChild("rag_back");
        this.abdomen = root.getChild("abdomen");
        this.chest = this.abdomen.getChild("chest");
        this.neck = this.chest.getChild("neck");
        this.head = this.neck.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.chin = this.jaw.getChild("chin");
        this.hair = new ModelPart[] {this.head.getChild("hair_back"), this.head.getChild("lock_right"), this.head.getChild("lock_left"),
                this.head.getChild("hair_right"), this.head.getChild("hair_left"), this.head.getChild("fringe")};
        for (int i = 0; i < FROM_CHEST.length; i++) {
            this.chestHung[i] = this.chest.getChild(FROM_CHEST[i].name());
        }
        for (int i = 0; i < FROM_BELLY.length; i++) {
            this.bellyHung[i] = this.abdomen.getChild(FROM_BELLY[i].name());
        }
        for (int i = 0; i < FROM_HIPS.length; i++) {
            this.hipsHung[i] = this.hips.getChild(FROM_HIPS[i].name());
        }
        for (int i = 0; i < CREST_CHEST.length; i++) {
            this.chestCrest[i] = this.chest.getChild(CREST_CHEST[i].name());
        }
        this.bellyCrest = this.abdomen.getChild(CREST_BELLY.name());
        this.headCrest = this.head.getChild(CREST_HEAD.name());
        String[] sides = {"right", "left"};
        for (int s = 0; s < 2; s++) {
            this.upper[s] = root.getChild(sides[s] + "_arm");
            this.shoulderHair[s] = this.upper[s].getChild("hair");
            this.fore[s] = this.upper[s].getChild("forearm");
            this.hand[s] = this.fore[s].getChild("hand");
            for (int k = 0; k < 4; k++) {
                this.fingers[s][k][0] = this.hand[s].getChild("finger_" + k);
                this.fingers[s][k][1] = this.fingers[s][k][0].getChild("middle");
                this.fingers[s][k][2] = this.fingers[s][k][1].getChild("claw");
            }
            this.thumbs[s][0] = this.hand[s].getChild("thumb");
            this.thumbs[s][1] = this.thumbs[s][0].getChild("claw");
            this.armHung[s] = s == 0
                    ? new ModelPart[] {this.fore[s].getChild("hang_0"), this.fore[s].getChild("hang_1")}
                    : new ModelPart[] {this.fore[s].getChild("hang_0"), this.fore[s].getChild("hang_1"), this.fore[s].getChild("chain")};
            this.thigh[s] = root.getChild(sides[s] + "_leg");
            this.shin[s] = this.thigh[s].getChild("shin");
            this.foot[s] = this.shin[s].getChild("foot");
        }
    }

    /** The planes that hang: strips of skin and cloth three widths long, two of hair down the flanks, the rump's. */
    private static CubeListBuilder plane(int kind, boolean mirror) {
        CubeListBuilder b = CubeListBuilder.create().mirror(mirror);
        return switch (kind) {
            case 0 -> b.texOffs(0, 64).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 20.0F, 0.0F);
            case 1 -> b.texOffs(16, 64).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 16.0F, 0.0F);
            case 2 -> b.texOffs(28, 64).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 18.0F, 0.0F);
            case 3 -> b.texOffs(48, 64).addBox(0.0F, 0.0F, -4.0F, 0.0F, 20.0F, 8.0F);
            case 4 -> b.texOffs(64, 64).addBox(0.0F, 0.0F, -3.0F, 0.0F, 16.0F, 6.0F);
            default -> b.texOffs(100, 64).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 16.0F, 0.0F);
        };
    }

    private static void hang(PartDefinition parent, Hung[] hung, float tilt) {
        for (Hung h : hung) {
            parent.addOrReplaceChild(h.name(), CubeListBuilder.create(), PartPose.offsetAndRotation(h.x(), h.y(), h.z(), -tilt, 0.0F, 0.0F))
                    .addOrReplaceChild("plane", plane(h.plane(), h.mirror()), PartPose.ZERO);
        }
    }

    /** A tuft of bristling hair: two planes crossed, so it shows from every side, standing up from a level joint. */
    private static void tuft(PartDefinition parent, Tuft t, float tilt) {
        PartDefinition root = parent.addOrReplaceChild(t.name(), CubeListBuilder.create(),
                PartPose.offsetAndRotation(t.x(), t.y(), t.z(), -tilt, 0.0F, 0.0F));
        for (int k = 0; k < 2; k++) {
            CubeListBuilder b = CubeListBuilder.create().mirror(k == 1);
            b = t.kind() == 0 ? b.texOffs(64, 40).addBox(-6.0F, -8.0F, 0.0F, 12.0F, 8.0F, 0.0F) : b.texOffs(88, 40).addBox(-5.0F, -6.0F, 0.0F, 10.0F, 6.0F, 0.0F);
            root.addOrReplaceChild(k == 0 ? "a" : "b", b, PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -t.lean() + (k == 0 ? 0.0F : 0.15F),
                    (k == 0 ? 0.45F : -0.65F) + t.twist(), t.twist() * (k == 0 ? 1.0F : -1.5F)));
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // the hips, narrow, in what is left of the trousers; rags of them before and behind, the hair of the rump over them
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 9.0F, 5.0F));
        hips.addOrReplaceChild("rag_front", CubeListBuilder.create().texOffs(108, 40).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 8.0F, 0.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, -3.2F, -0.15F, 0.0F, 0.0F));
        hips.addOrReplaceChild("rag_back", CubeListBuilder.create().texOffs(108, 40).mirror().addBox(-6.0F, 0.0F, 0.0F, 12.0F, 8.0F, 0.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 3.2F, 0.2F, 0.0F, 0.0F));
        hang(hips, FROM_HIPS, 0.0F);

        // the trunk: a starved belly bent forward over the hips, a great chest bent further, the hump of the shoulders on it;
        // the spine standing out along the back, hair lying over it and a ridge of it bristling up
        PartDefinition abdomen = root.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-5.0F, -8.0F, -3.5F, 10.0F, 8.0F, 7.0F)
                .texOffs(240, 0).addBox(-1.0F, -5.0F, 3.2F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 7.0F, 4.5F, ABDOMEN, 0.0F, 0.0F));
        abdomen.addOrReplaceChild("back_hair", CubeListBuilder.create().texOffs(178, 40).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 14.0F, 0.0F),
                PartPose.offsetAndRotation(0.0F, -8.2F, 3.65F, 0.08F, 0.0F, 0.0F));
        hang(abdomen, FROM_BELLY, ABDOMEN);
        tuft(abdomen, CREST_BELLY, ABDOMEN);
        PartDefinition chest = abdomen.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-8.0F, -11.0F, -5.5F, 16.0F, 11.0F, 11.0F)
                .texOffs(66, 0).addBox(-6.5F, -13.5F, -1.0F, 13.0F, 3.0F, 6.0F)                // the hump
                .texOffs(240, 0).addBox(-1.0F, -4.0F, 5.2F, 2.0F, 2.0F, 2.0F)
                .texOffs(240, 0).addBox(-1.0F, -8.0F, 5.2F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -7.5F, 0.0F, CHEST, 0.0F, 0.0F));
        chest.addOrReplaceChild("back_hair", CubeListBuilder.create().texOffs(150, 40).addBox(-7.0F, 0.0F, 0.0F, 14.0F, 16.0F, 0.0F),
                PartPose.offsetAndRotation(0.0F, -13.2F, 5.65F, 0.06F, 0.0F, 0.0F));
        hang(chest, FROM_CHEST, ABDOMEN + CHEST);
        for (Tuft t : CREST_CHEST) {
            tuft(chest, t, ABDOMEN + CHEST);
        }

        // the neck, short and thick, and the head held up on it at the shoulders
        PartDefinition neck = chest.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(104, 0).addBox(-2.5F, -5.0F, -2.5F, 5.0F, 5.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, -11.0F, -1.5F, NECK, 0.0F, 0.0F));
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(124, 0).addBox(-3.5F, -7.5F, -4.5F, 7.0F, 7.0F, 7.0F)                 // the skull, a man's face on it
                .texOffs(152, 0).addBox(-4.0F, -6.6F, -5.3F, 8.0F, 2.0F, 2.0F)                 // the brow, heavy over the sockets
                .texOffs(180, 0).addBox(-2.5F, -1.5F, -5.0F, 5.0F, 2.0F, 3.0F)                 // the upper jaw, the lip gone
                .texOffs(218, 0).addBox(-2.5F, -0.6F, -4.2F, 5.0F, 2.0F, 4.0F)                 // the throat, seen when it gapes
                // a mouthful of thin teeth, uneven, not two fangs (the user, 2026-10-18: two fangs read as buck teeth)
                .texOffs(180, 8).addBox(-2.4F, 0.1F, -5.1F, 1.0F, 3.0F, 1.0F, NEEDLE)
                .texOffs(186, 8).addBox(-1.3F, 0.2F, -5.1F, 1.0F, 2.0F, 1.0F, NEEDLE)
                .texOffs(180, 8).addBox(-0.3F, 0.3F, -5.1F, 1.0F, 3.0F, 1.0F, NEEDLE)
                .texOffs(186, 8).addBox(0.6F, 0.1F, -5.1F, 1.0F, 2.0F, 1.0F, NEEDLE)
                .texOffs(180, 8).addBox(1.5F, 0.0F, -5.1F, 1.0F, 3.0F, 1.0F, NEEDLE),
                PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, HEAD, 0.0F, 0.0F));
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(196, 0).addBox(-3.0F, 0.0F, -5.5F, 6.0F, 2.0F, 5.0F)
                .texOffs(186, 8).addBox(-2.3F, -1.7F, -5.3F, 1.0F, 2.0F, 1.0F, NEEDLE)       // the lower teeth, up
                .texOffs(180, 8).addBox(-0.8F, -2.6F, -5.3F, 1.0F, 3.0F, 1.0F, NEEDLE)
                .texOffs(186, 8).addBox(1.2F, -1.8F, -5.3F, 1.0F, 2.0F, 1.0F, NEEDLE),
                PartPose.offsetAndRotation(0.0F, -0.5F, 0.5F, JAW, 0.0F, 0.0F));
        jaw.addOrReplaceChild("chin", CubeListBuilder.create().texOffs(46, 40).addBox(-2.5F, 0.0F, 0.0F, 5.0F, 6.0F, 0.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, -5.0F, -JAW, 0.0F, 0.0F));
        // the cheeks torn open: strips of them hanging either side of the mouth
        head.addOrReplaceChild("cheek_right", CubeListBuilder.create().texOffs(56, 40).addBox(0.0F, 0.0F, -4.0F, 0.0F, 5.0F, 4.0F),
                PartPose.offsetAndRotation(-2.6F, -1.4F, -0.8F, 0.0F, 0.0F, 0.05F));
        head.addOrReplaceChild("cheek_left", CubeListBuilder.create().texOffs(56, 40).mirror().addBox(0.0F, 0.0F, -4.0F, 0.0F, 5.0F, 4.0F),
                PartPose.offsetAndRotation(2.6F, -1.4F, -0.8F, 0.0F, 0.0F, -0.05F));
        // the hair: long, black, hanging in hanks from the crown down the back and the sides, a lock either side of the
        // face, a few strands over the brow
        head.addOrReplaceChild("hair_back", CubeListBuilder.create().texOffs(0, 40).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 18.0F, 0.0F),
                PartPose.offsetAndRotation(0.0F, -7.6F, 2.6F, 0.12F, 0.0F, 0.0F));
        head.addOrReplaceChild("lock_right", CubeListBuilder.create().texOffs(198, 40).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 16.0F, 0.0F),
                PartPose.offsetAndRotation(-4.3F, -7.2F, -3.4F, 0.0F, 0.3F, 0.08F));
        head.addOrReplaceChild("lock_left", CubeListBuilder.create().texOffs(198, 40).mirror().addBox(-2.0F, 0.0F, 0.0F, 4.0F, 16.0F, 0.0F),
                PartPose.offsetAndRotation(4.3F, -7.2F, -3.4F, 0.0F, -0.3F, -0.08F));
        CubeListBuilder side = CubeListBuilder.create().texOffs(16, 40).addBox(0.0F, 0.0F, -7.0F, 0.0F, 16.0F, 7.0F);
        head.addOrReplaceChild("hair_right", side, PartPose.offsetAndRotation(-3.75F, -7.3F, 2.6F, 0.0F, 0.0F, 0.1F));
        head.addOrReplaceChild("hair_left", side, PartPose.offsetAndRotation(3.75F, -7.3F, 2.6F, 0.0F, 0.0F, -0.1F));
        head.addOrReplaceChild("fringe", CubeListBuilder.create().texOffs(30, 40).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 4.0F, 0.0F),
                PartPose.offsetAndRotation(0.0F, -7.7F, -5.45F, -0.1F, 0.0F, 0.0F));
        tuft(head, CREST_HEAD, ABDOMEN + CHEST + NECK + HEAD);

        // the arms, long enough to walk on: the upper arm back from the shoulder, the forearm forward from the elbow in what
        // is left of a bandage, the hand up on four long fingers that hook down into the ground, a thumb inside them
        for (int s = 0; s < 2; s++) {
            float x = s == 0 ? -1.0F : 1.0F;
            boolean mirror = s == 1;
            PartDefinition upper = root.addOrReplaceChild(s == 0 ? "right_arm" : "left_arm", CubeListBuilder.create().texOffs(54, 16)
                    .mirror(mirror).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 13.0F, 7.0F),
                    PartPose.offsetAndRotation(x * SHOULDER_X, -2.5F, -6.0F, UPPER, 0.0F, -x * UPPER_OUT));
            upper.addOrReplaceChild("hair", CubeListBuilder.create(), PartPose.offsetAndRotation(x * 3.7F, -2.0F, 0.0F, -UPPER, 0.0F, x * UPPER_OUT))
                    .addOrReplaceChild("plane", plane(4, mirror), PartPose.ZERO);
            PartDefinition fore = upper.addOrReplaceChild("forearm", CubeListBuilder.create().mirror(mirror)
                    .texOffs(82, 16).addBox(-2.5F, -0.5F, -2.5F, 5.0F, 7.0F, 5.0F)
                    .texOffs(102, 16).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 6.0F, 4.0F),
                    PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, FORE, 0.0F, x * FORE_IN));
            fore.addOrReplaceChild("hang_0", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, 2.6F))
                    .addOrReplaceChild("plane", CubeListBuilder.create().texOffs(132, 40).mirror(mirror).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F), PartPose.ZERO);
            fore.addOrReplaceChild("hang_1", CubeListBuilder.create(), PartPose.offset(x * 1.6F, 6.0F, 2.1F))
                    .addOrReplaceChild("plane", CubeListBuilder.create().texOffs(144, 40).mirror(mirror).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 10.0F, 0.0F), PartPose.ZERO);
            if (s == 1) {
                // an iron cuff on the left wrist, the chain from it broken off short
                fore.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(200, 16).addBox(-2.5F, 8.5F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.ZERO);
                fore.addOrReplaceChild("chain", CubeListBuilder.create(), PartPose.offset(0.0F, 10.0F, 2.6F))
                        .addOrReplaceChild("plane", CubeListBuilder.create().texOffs(220, 16).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 6.0F, 0.0F), PartPose.ZERO);
            }
            PartDefinition hand = fore.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(118, 16)
                    .mirror(mirror).addBox(-2.5F, -0.5F, -1.5F, 5.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, HAND, 0.0F, 0.0F));
            for (int k = 0; k < 4; k++) {
                PartDefinition finger = hand.addOrReplaceChild("finger_" + k, CubeListBuilder.create().texOffs(152, 8)
                        .addBox(-0.5F, -0.5F, -3.5F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.2F)),
                        PartPose.offsetAndRotation(-1.8F + k * 1.2F, 2.2F, -1.0F, KNUCKLE, (k - 1.5F) * 0.14F, 0.0F));
                PartDefinition middle = finger.addOrReplaceChild("middle", CubeListBuilder.create().texOffs(162, 8)
                        .addBox(-0.5F, -0.5F, -2.6F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.12F)), PartPose.offsetAndRotation(0.0F, 0.0F, -3.3F, FINGER, 0.0F, 0.0F));
                middle.addOrReplaceChild("claw", CubeListBuilder.create().texOffs(170, 8)
                        .addBox(-0.5F, -0.5F, -2.8F, 1.0F, 1.0F, 3.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, -2.4F, CLAW, 0.0F, 0.0F));
            }
            PartDefinition thumb = hand.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(162, 8)
                    .addBox(-0.5F, -0.5F, -2.6F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.12F)),
                    PartPose.offsetAndRotation(-x * 2.4F, 1.6F, -0.6F, 0.5F, x * 0.55F, 0.0F));
            thumb.addOrReplaceChild("claw", CubeListBuilder.create().texOffs(170, 8).addBox(-0.5F, -0.5F, -2.8F, 1.0F, 1.0F, 3.0F, new CubeDeformation(-0.04F)),
                    PartPose.offsetAndRotation(0.0F, 0.0F, -2.4F, 0.9F, 0.0F, 0.0F));

            // the legs, bent: thigh forward, shin back, the foot flat, three claws to it
            PartDefinition thigh = root.addOrReplaceChild(s == 0 ? "right_leg" : "left_leg", CubeListBuilder.create().texOffs(136, 16)
                    .mirror(mirror).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 8.0F, 6.0F), PartPose.offsetAndRotation(x * 4.5F, 9.6F, 5.0F, THIGH, 0.0F, 0.0F));
            PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(160, 16)
                    .mirror(mirror).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 7.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, SHIN, 0.0F, 0.0F));
            PartDefinition foot = shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(180, 16)
                    .mirror(mirror).addBox(-2.0F, -0.5F, -5.0F, 4.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, FOOT, 0.0F, 0.0F));
            for (int k = 0; k < 3; k++) {
                foot.addOrReplaceChild("toe_" + k, CubeListBuilder.create().texOffs(248, 0).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F),
                        PartPose.offsetAndRotation(-1.4F + k * 1.4F, 1.0F, -4.8F, 0.3F, (k - 1) * 0.2F, 0.0F));
            }
        }
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public void setupAnim(VandalRenderer.State state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        float speed = Math.min(1.0F, state.walkAnimationSpeed);
        float pos = state.walkAnimationPos * 0.45F;
        float roar = state.roar;
        float crouch = state.crouch;
        float rush = state.rush;
        float rage = state.rage;
        float dying = Mth.clamp(state.deathTime / 16.0F, 0.0F, 1.0F);
        float slump = dying * dying * (3.0F - 2.0F * dying);
        float breath = Mth.sin(age * (0.07F + rage * 0.05F));

        // the gait: diagonal pairs, the arm with the other side's leg; a rush, longer strides and lower
        float stride = 0.5F + rush * 0.35F;
        for (int s = 0; s < 2; s++) {
            float phase = pos + (s == 0 ? 0.0F : Mth.PI);
            float swing = Mth.cos(phase) * stride * speed;
            float lift = Math.max(0.0F, -Mth.sin(phase)) * 0.55F * speed;
            this.thigh[s].xRot = THIGH + swing - crouch * 0.3F + roar * 0.15F - slump * 0.7F;
            this.thigh[s].zRot = (s == 0 ? 1.0F : -1.0F) * slump * 0.3F;
            this.shin[s].xRot = SHIN + lift + crouch * 0.45F - roar * 0.15F + slump * 1.1F;
            this.foot[s].xRot = FOOT - lift * 0.6F - crouch * 0.15F - slump * 0.4F;
        }

        // the trunk: it breathes (harder, wounded), rocks with its steps; rears for a roar, drops for a rush; dying, sinks
        float bob = Math.abs(Mth.sin(pos)) * 0.7F * speed;
        float drop = -bob + crouch * 1.0F + rush * 0.8F - roar * 0.6F + slump * 6.5F;
        this.hips.y = 9.0F + drop;
        this.abdomen.y = 7.0F + drop;
        for (int s = 0; s < 2; s++) {
            this.thigh[s].y = 9.6F + drop;
        }
        this.abdomen.xRot = ABDOMEN + crouch * 0.15F + rush * 0.1F - roar * 0.35F + slump * 0.2F;
        this.chest.xRot = CHEST + breath * (0.03F + rage * 0.02F) + crouch * 0.2F + rush * 0.1F - roar * 0.3F + slump * 0.35F;
        this.chest.zRot = Mth.sin(pos) * 0.07F * speed + slump * 0.12F;
        // the shoulders ride on the chest, wherever the trunk bends or rears to
        float cy = SHOULDER_Y * Mth.cos(this.chest.xRot) - SHOULDER_Z * Mth.sin(this.chest.xRot) + this.chest.y;
        float cz = SHOULDER_Y * Mth.sin(this.chest.xRot) + SHOULDER_Z * Mth.cos(this.chest.xRot) + this.chest.z;
        float shoulderY = cy * Mth.cos(this.abdomen.xRot) - cz * Mth.sin(this.abdomen.xRot) + this.abdomen.y;
        float shoulderZ = cy * Mth.sin(this.abdomen.xRot) + cz * Mth.cos(this.abdomen.xRot) + this.abdomen.z;

        // the arms: the hand set down ahead and walked under, lifted and swung on, the fingers curling as it comes up; a
        // roar flings them wide and splays the claws; a crouch cocks the elbows; dying, they give under it
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? 1.0F : -1.0F;
            float armPhase = pos + (s == 0 ? Mth.PI : 0.0F);
            float armSwing = Mth.cos(armPhase) * (stride + 0.05F) * speed;
            float lifted = Math.max(0.0F, Mth.sin(armPhase)) * speed;
            float free = 1.0F - roar;
            this.upper[s].y = shoulderY;
            this.upper[s].z = shoulderZ;
            this.upper[s].xRot = UPPER + armSwing * free - roar * 0.7F + crouch * 0.3F + slump * 0.3F;
            this.upper[s].zRot = side * (UPPER_OUT + roar * 0.85F + crouch * 0.2F + slump * 1.0F);
            this.fore[s].xRot = FORE - lifted * 0.5F * free + roar * 0.65F - crouch * 0.35F + slump * 0.25F;
            this.fore[s].zRot = -side * (FORE_IN + roar * 0.45F + slump * 1.2F);
            this.hand[s].xRot = HAND + lifted * 0.3F + roar * 0.2F - slump * 0.3F;
            float open = roar;
            float curl = lifted * 0.8F + crouch * 0.4F + slump * 0.5F;
            if (s == 0 && state.swingAnimation > 0.0F) {                 // a blow: the right arm swept up and down across
                float swipe = Mth.sin(state.swingAnimation * Mth.PI);
                this.upper[s].xRot -= swipe * 1.6F;
                this.upper[s].zRot += swipe * 0.4F;
                this.fore[s].xRot += swipe * 0.7F;
                open = Math.max(open, swipe);
            }
            float flex = Mth.sin(age * 0.06F + s * 2.0F) * 0.06F * (1.0F - speed);
            for (int k = 0; k < 4; k++) {
                ModelPart[] f = this.fingers[s][k];
                float own = Mth.sin(age * 0.06F + s * 2.0F + k * 0.7F) * 0.03F;
                f[0].xRot = KNUCKLE + curl * 0.2F - open * 0.45F + flex + own;
                f[0].yRot = (k - 1.5F) * (0.14F + open * 0.22F);
                f[1].xRot = FINGER + curl * 0.35F - open * 0.6F + flex;
                f[2].xRot = CLAW + curl * 0.3F - open * 0.35F + flex;
            }
            this.thumbs[s][0].xRot = 0.5F + curl * 0.3F - open * 0.4F;
            this.thumbs[s][1].xRot = 0.9F + curl * 0.2F - open * 0.3F;
            // the shoulder's hair, and what hangs from the forearm, hang down, swinging
            this.shoulderHair[s].xRot = -this.upper[s].xRot + Mth.sin(age * 0.08F + s * 2.0F) * 0.05F + speed * 0.25F;
            this.shoulderHair[s].zRot = -this.upper[s].zRot;
            float tilt = this.upper[s].xRot + this.fore[s].xRot;
            float roll = this.upper[s].zRot + this.fore[s].zRot;
            for (int h = 0; h < this.armHung[s].length; h++) {
                this.armHung[s][h].xRot = -tilt + 0.08F + Mth.sin(age * 0.11F + s + h * 1.9F) * 0.08F + speed * 0.3F;
                this.armHung[s][h].zRot = -roll + Mth.sin(age * 0.09F + h) * 0.05F;
            }
        }

        // the head: it looks where it means to go; a roar throws it back, the jaw wide and shaking; dying, it drops
        float look = Mth.clamp(state.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD * (1.0F - slump);
        this.neck.yRot = look * 0.5F;
        this.neck.xRot = NECK - roar * 0.45F + crouch * 0.05F + rush * 0.15F + slump * 0.9F;
        this.head.yRot = look * 0.35F;
        this.head.xRot = HEAD + state.xRot * Mth.DEG_TO_RAD * 0.4F * (1.0F - slump) + roar * 0.25F - crouch * 0.35F - rush * 0.15F + slump * 0.3F;
        // now and then a twitch of the head, as the dead have
        float twitch = Math.max(0.0F, Mth.sin(age * 0.23F) * Mth.sin(age * 0.071F) - 0.85F) * 4.0F * (1.0F - slump);
        this.head.yRot += twitch * 0.3F;
        this.head.zRot = Mth.sin(age * 0.045F) * 0.05F + twitch * 0.45F + slump * 0.35F;
        float working = Mth.sin(age * 0.12F) * 0.06F;
        float gape = state.isAggressive ? 0.25F : 0.0F;
        this.jaw.xRot = JAW + working + gape + rage * 0.1F + roar * (0.9F + Mth.sin(age * 1.7F) * 0.06F) + slump * 0.6F;
        this.chin.xRot = -this.jaw.xRot + Mth.sin(age * 0.1F) * 0.05F + speed * 0.2F;

        // the hair stirs as it breathes and streams back as it runs
        for (int k = 0; k < this.hair.length; k++) {
            ModelPart lock = this.hair[k];
            float sway = Mth.sin(age * 0.08F + k * 1.3F) * 0.05F + speed * 0.25F + rush * 0.2F + roar * 0.3F;
            float rest = k == 0 ? 0.12F : k < 3 ? 0.15F : k < 5 ? 0.0F : -0.1F;
            lock.xRot = rest + (k == 5 ? -sway * 0.3F : sway);
        }

        // what hangs from the trunk hangs straight down whatever its lean, swaying, streaming back as it moves
        float trunk = this.abdomen.xRot + this.chest.xRot;
        for (int i = 0; i < this.chestHung.length; i++) {
            this.chestHung[i].xRot = -trunk + this.swing(age, i, speed, rush);
            this.chestHung[i].zRot = Mth.sin(age * 0.07F + i * 2.3F) * 0.04F - this.chest.zRot;
        }
        for (int i = 0; i < this.bellyHung.length; i++) {
            this.bellyHung[i].xRot = -this.abdomen.xRot + this.swing(age, i + 7, speed, rush);
            this.bellyHung[i].zRot = Mth.sin(age * 0.07F + i * 1.9F) * 0.04F;
        }
        for (int i = 0; i < this.hipsHung.length; i++) {
            this.hipsHung[i].xRot = this.swing(age, i + 13, speed, rush) * 0.6F;
        }
        this.waistRags[0].xRot = -0.15F - speed * 0.3F + Mth.sin(age * 0.1F) * 0.05F;
        this.waistRags[1].xRot = 0.2F + speed * 0.35F + Mth.sin(age * 0.1F + 1.5F) * 0.05F;

        // the ridge of hair along its back bristles up as it roars, and when it is wounded; lies back as it runs
        float bristle = roar * 0.45F + rage * 0.25F + (state.isAggressive ? 0.1F : 0.0F) + breath * 0.03F - speed * 0.2F - rush * 0.15F - slump * 0.5F;
        for (ModelPart tuft : this.chestCrest) {
            tuft.xRot = -trunk + bristle;
        }
        this.bellyCrest.xRot = -this.abdomen.xRot + bristle;
        this.headCrest.xRot = -(trunk + this.neck.xRot + this.head.xRot) + bristle * 0.5F;
    }

    /** How far a hanging strip swings out: a slow sway of its own, back with its going. */
    private float swing(float age, int i, float speed, float rush) {
        return Mth.sin(age * 0.09F + i * 1.7F) * 0.05F + speed * 0.3F + rush * 0.25F;
    }
}
