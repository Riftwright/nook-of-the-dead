package dev.nookofthedead.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * How a mutated zombie finds and keeps after its prey - shared by {@link MutatedZombie} and {@link MutatedZombieVillager}
 * (the user, 2026-10-15: "ゾンビは壁の向こうは見えない。ただし音は聞こえる").
 * <ul>
 * <li><b>Sight</b>: someone is taken up only when it can see them (nothing solid between), as far as its {@link Traits.Sense}
 * reaches along the ground, and above it only as that sense looks up ({@link #notices}).</li>
 * <li><b>Hearing</b>: a sound ({@link Hearing}) sends it to where the sound was ({@link #lead}): it runs there and looks
 * round, and breaks through a soft wall on the way. A sound made by the one it is after tells it where they are now.</li>
 * <li><b>Losing them</b>: prey it has neither seen nor heard for {@link #LOSE} ticks is let go; it runs to where they were
 * last and looks round there. Prey gone up out of its reach is given up after {@link #GIVES_UP} ticks of getting no higher
 * itself (the user, 2026-10-13), and for {@link #FORGETS} after it does not look up again.</li>
 * <li><b>Rousing</b>: one after someone rouses the idle ones round it that would notice them to the same chase - the dull
 * ones too, as zombified piglins are roused by their own.</li>
 * <li><b>Standing</b>: one left in a building ({@link #stand}) stands where it is, not wandering, until it sees someone or
 * hears something - the one in the dark room that turns round as the door opens.</li>
 * </ul>
 */
public final class Mind {
    /** How far one that has found its prey rouses the others. */
    public static final double ALERT = 16.0;
    /** Ticks without seeing or hearing its prey before it lets them go and looks where they were. */
    public static final int LOSE = 160;
    /** How long it keeps going for a sound, or a place its prey was last, before it gives that up (ticks). */
    public static final int LEAD_TIME = 400;
    /** Prey above it, it getting no higher, this many ticks: given up. */
    public static final int GIVES_UP = 80;
    /** After giving up someone above, ticks it takes no notice of anyone above it. */
    public static final int FORGETS = 300;
    /** Its pace wandering, of its running pace: a shamble. */
    public static final double WANDER = 0.55;

    private final Zombie mob;
    private Traits traits = Traits.PLAIN;
    private boolean rolled;
    private int unreached;
    private double climbedFrom;
    private int forgetting;
    /** Ticks its prey has gone unseen and unheard; where they were last known to be. */
    private int unseen;
    private @Nullable Vec3 lastKnown;
    /** Where it is going to look, and till when; null for nowhere. */
    private @Nullable Vec3 lead;
    private long leadUntil;
    /** Its last prey, to tell a new one (and groan at it) from one it was already after. */
    private @Nullable LivingEntity lastPrey;
    /** Standing still where it was left, till something wakes it. */
    private boolean standing;
    /** Where it was when last it got anywhere, and when ({@link #penned}). */
    private @Nullable Vec3 anchor;
    private long anchoredAt;
    /** How long one may stay within {@link #PEN} blocks of a spot, not left standing, before it counts as penned. */
    public static final int PENNED_AFTER = 20 * 120;
    public static final double PEN = 3.0;

    public Mind(Zombie mob) {
        this.mob = mob;
    }

    public Traits traits() {
        return this.traits;
    }

    /** What {@code entity} thinks with, if it is one of the fallen world's zombies. */
    public static @Nullable Mind of(@Nullable Entity entity) {
        return entity instanceof Mutated mutated ? mutated.mind() : null;
    }

    /** Its traits rolled, if they are not yet, and its pace and sight set by them. */
    public void roll(RandomSource random) {
        if (!this.rolled) {
            this.set(Traits.roll(random, Hardness.of(this.mob.level())));
        }
    }

    /** These traits, and its pace and sight by them. */
    public void set(Traits traits) {
        if ((!(this.mob instanceof MutatedZombie) || this.mob instanceof ThinAirZombie) && traits.voice() == Traits.Voice.SCREAM) {
            // only the plain ones scream: a player must be able to tell the screamer at sight (the user, 2026-10-17:
            // "村人ゾンビは叫ばなくていい…プレイヤーから、見分けがつくのが大事"), and it is drawn as itself only as one
            traits = new Traits(traits.pace(), traits.chase(), traits.sense(), traits.breaker());
        }
        this.traits = traits;
        this.rolled = true;
        this.mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(traits.pace().speed);
        this.mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(this.mob instanceof ThinAirZombie
                ? Math.max(traits.sense().sight, ThinAirZombie.HEARS + 8) : traits.sense().sight);
        this.showVoice();
    }

    /** A screamer shown as one (MutatedZombie's look); a zombie villager keeps its own. */
    private void showVoice() {
        if (this.mob instanceof MutatedZombie zombie) {
            zombie.screamer(this.traits.voice() == Traits.Voice.SCREAM);
        }
    }

    /** Its goals in place of vanilla's zombie's: called from the zombie's own {@code addBehaviourGoals}. */
    public void goals(GoalSelector goals, GoalSelector targets) {
        // vanilla's attack, but patient at a stone wall (RunnerAttackGoal)
        goals.removeAllGoals(goal -> goal instanceof ZombieAttackGoal);
        goals.addGoal(3, new RunnerAttackGoal(this.mob));
        SmashMirrorGoal mirror = new SmashMirrorGoal(this.mob, SmashMirrorGoal::mirrorIn);
        // through soft walls toward its prey, a sound, or the mirror (only while it is going there: r1 fix check)
        goals.addGoal(1, new BreakSoftBlocksGoal(this.mob, this, mirror::heading));
        goals.addGoal(2, new FlankGoal(this.mob, this));
        goals.addGoal(5, mirror);
        goals.addGoal(6, new InvestigateGoal(this.mob, this));
        // vanilla's hunt for turtle eggs searches the blocks round every zombie, over and over: a crowd of them paid for it
        // on the server, and there are no eggs in a fallen town
        goals.removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.ai.goal.RemoveBlockGoal);
        // a player only where it would notice them, and only in sight; nothing else lives in the fallen world to go after.
        // Vanilla's target goals hand their target back every tick it is let go of; once this one lets its prey go, they stop
        targets.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?> || goal instanceof HurtByTargetGoal);
        targets.addGoal(1, new HurtByTargetGoal(this.mob) {
            @Override
            public boolean canContinueToUse() {
                return Mind.this.mob.getTarget() != null && super.canContinueToUse();
            }
        });
        targets.addGoal(2, new NearestAttackableTargetGoal<>(this.mob, Player.class, true, (target, level) -> this.notices(target)) {
            @Override
            public boolean canContinueToUse() {
                return Mind.this.mob.getTarget() != null && super.canContinueToUse();
            }
        });
        // with no one to go after, it shambles about the streets, slower than it runs and more often than vanilla's - unless
        // it is one left standing in a building
        goals.removeAllGoals(goal -> goal instanceof WaterAvoidingRandomStrollGoal);
        goals.addGoal(7, new RandomStrollGoal(this.mob, WANDER, 40) {
            @Override
            public boolean canUse() {
                return !Mind.this.standing && super.canUse();
            }
        });
    }

    /**
     * Whether it notices {@code prey} where they are: anywhere along the ground as far as it sees, but someone more than
     * its sense's {@code looksUp} above it only from within its {@code under} of the spot below.
     */
    public boolean notices(LivingEntity prey) {
        if (prey instanceof net.minecraft.world.entity.player.Player player && dev.nookofthedead.ending.Film.watching(player)) {
            return false;                                       // one watching a film is not there for them (2026-10-21)
        }
        Traits.Sense sense = this.traits.sense();
        if (prey.getY() - this.mob.getY() <= sense.looksUp) {
            return true;
        }
        double dx = prey.getX() - this.mob.getX();
        double dz = prey.getZ() - this.mob.getZ();
        return this.forgetting <= 0 && dx * dx + dz * dz <= sense.under * sense.under;
    }

    /** What it breaks through, by its traits; null for nothing. */
    public @Nullable TagKey<Block> breaks() {
        return this.traits.breaker().breaks;
    }

    /** Sent to look at {@code at}: it runs there and looks round, unless it has prey in sight. */
    public void lead(Vec3 at) {
        this.lead = at;
        this.leadUntil = this.mob.level().getGameTime() + LEAD_TIME;
    }

    /**
     * A bell rung ({@link Hearing#toll}): it goes to it, and keeps going a long while - one it was after and cannot see
     * now it lets go for it.
     */
    public void summoned(Vec3 at) {
        this.standing = false;
        if (this.mob.getTarget() != null && this.unseen > 0) {
            this.mob.setTarget(null);
        }
        if (this.mob.getTarget() == null) {
            this.lead = at;
            this.leadUntil = this.mob.level().getGameTime() + SUMMONED;
        }
    }

    /** How long a bell keeps one coming: a minute. */
    public static final int SUMMONED = 1200;

    /** Where it is going to look, if anywhere still. */
    public @Nullable Vec3 lead() {
        if (this.lead != null && this.mob.level().getGameTime() > this.leadUntil) {
            this.lead = null;
        }
        return this.lead;
    }

    /** Done looking. */
    public void looked() {
        this.lead = null;
    }

    /**
     * A sound heard at {@code at}, made by {@code cause} (if anyone): its own prey's tells it where they are; anyone else's,
     * or a thing's (a thrown bottle landing, a door, an explosion), sends it to look - unless it is after someone it can still
     * see or has heard of late.
     */
    public void heard(Vec3 at, @Nullable Entity cause) {
        this.standing = false;
        LivingEntity prey = this.mob.getTarget();
        if (prey != null && cause == prey) {
            this.unseen = 0;
            this.lastKnown = prey.position();
            return;
        }
        if (prey == null || this.unseen > 40) {
            this.lead(at);
        }
    }

    /** Ticks of thought each server AI step: its prey kept or let go, and the others roused. */
    public void step(ServerLevel level) {
        if (this.anchor == null || this.mob.position().distanceToSqr(this.anchor) > PEN * PEN) {
            this.anchor = this.mob.position();
            this.anchoredAt = level.getGameTime();
        }
        LivingEntity prey = this.mob.getTarget();
        if (prey != null && (prey.level() != level || !prey.isAlive())) {      // home through the mirror, or dead (r1)
            this.mob.setTarget(null);
            prey = null;
        }
        if (this.forgetting > 0) {
            this.forgetting--;
        }
        if (prey != null && prey != this.lastPrey) {                            // a new one: the groan of a zombie that has seen you
            if (this.traits.voice() == Traits.Voice.SCREAM && prey instanceof Player player) {
                this.scream(level, player);
            } else {
                this.mob.playSound(SoundEvents.ZOMBIE_AMBIENT, 1.6F, 1.25F + this.mob.getRandom().nextFloat() * 0.2F);
            }
            this.unseen = 0;
            this.lastKnown = prey.position();
            this.looked();
        }
        this.lastPrey = prey;
        if (prey != null) {
            this.standing = false;
        }
        if (prey == null) {
            this.unreached = 0;
            return;
        }
        // out of sight and quiet: let go, and looked for where it was last
        if (this.mob.getSensing().hasLineOfSight(prey)) {
            this.unseen = 0;
            this.lastKnown = prey.position();
        } else if (++this.unseen > LOSE) {
            Vec3 last = this.lastKnown;
            this.mob.setTarget(null);
            this.lastPrey = null;
            this.unseen = 0;
            if (last != null) {
                this.lead(last);
            }
            return;
        }
        // prey gone up where it finds no way - onto a roof, up a tower - given up after a while, and for a while after it does
        // not look up again (getting higher is how it is told from one at the foot of a wall: the way the pathfinder works out
        // is no help, as it ends somewhere either way)
        if (prey instanceof Player && prey.getY() - this.mob.getY() > this.traits.sense().looksUp) {
            if (this.unreached == 0 || this.mob.getY() > this.climbedFrom + 0.9) {
                this.climbedFrom = this.mob.getY();
                this.unreached = 1;
            } else if (++this.unreached > GIVES_UP) {
                this.mob.setTarget(null);
                this.lastPrey = null;
                this.unreached = 0;
                this.forgetting = FORGETS;
                return;
            }
        } else {
            this.unreached = 0;
        }
        if ((this.mob.tickCount + this.mob.getId()) % 20 == 0 && prey instanceof Player player && this.notices(player) && this.unseen == 0) {
            this.rouse(level, player);
        }
    }

    /** How far a scream carries: every one of them within it comes. */
    public static final double SCREAM_REACH = 48.0;

    /**
     * A screamer ({@link Traits.Voice#SCREAM}) that has seen {@code prey}: it stops, screams, and every one of them within
     * {@link #SCREAM_REACH} not after someone already is after {@code prey} now, wherever they stood. How many came, for tests.
     */
    public int scream(ServerLevel level, Player prey) {
        this.mob.getNavigation().stop();
        level.playSound(null, this.mob.blockPosition(), SoundEvents.GHAST_HURT, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.55F);
        level.playSound(null, this.mob.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_AMBIENT, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 1.6F);
        int n = 0;
        for (Zombie other : level.getEntitiesOfClass(Zombie.class, this.mob.getBoundingBox().inflate(SCREAM_REACH),
                z -> z != this.mob && z.getTarget() == null && z instanceof Mutated && z.isAlive())) {
            if (other.distanceToSqr(this.mob) <= SCREAM_REACH * SCREAM_REACH) {
                other.setTarget(prey);
                n++;
            }
        }
        return n;
    }

    /** The idle ones round it that would notice {@code prey} where they are, set after them too. */
    private void rouse(ServerLevel level, Player prey) {
        for (Zombie other : level.getEntitiesOfClass(Zombie.class, this.mob.getBoundingBox().inflate(ALERT),
                z -> z != this.mob && z.getTarget() == null && z instanceof Mutated)) {
            Mind mind = Mind.of(other);
            if (mind != null && mind.notices(prey)) {
                other.setTarget(prey);
            }
        }
    }

    /**
     * A blow that landed on {@code target}: a player in the fallen world bitten has a little less time (the films' bite,
     * {@link dev.nookofthedead.infection.Infection#bitten}).
     */
    public static void bit(boolean hit, Entity target) {
        bit(hit, target, Float.MAX_VALUE);
    }

    /** As {@link #bit(boolean, Entity)}, counted only if it drew blood: {@code before} is the target's health before the blow. */
    public static void bit(boolean hit, Entity target, float before) {
        if (hit && target instanceof net.minecraft.server.level.ServerPlayer player && player.getHealth() < before) {
            dev.nookofthedead.infection.Infection.bitten(player);
        }
    }

    /**
     * Whether it has got nowhere for two minutes ({@link #PENNED_AFTER}) though not left standing on purpose: shut in a
     * pit or a pen - kept there it keeps no others away ({@link Horde}), and goes when no one is near ({@link Unkept}).
     */
    public boolean penned(ServerLevel level) {
        return !this.standing && this.anchor != null && level.getGameTime() - this.anchoredAt > PENNED_AFTER;
    }

    /** Left standing where it is, in a building, till it sees someone or hears something. */
    public void stand() {
        this.standing = true;
    }

    public boolean standing() {
        return this.standing;
    }

    public void save(ValueOutput output) {
        if (this.rolled) {
            this.traits.save(output);
        }
        output.putBoolean("Standing", this.standing);
    }

    public void load(ValueInput input) {
        if (!input.getStringOr("Pace", "").isEmpty()) {
            this.rolled = true;
            this.set(Traits.load(input));
        }
        this.standing = input.getBooleanOr("Standing", false);
    }

    /** For tests: whether it has been sent to look anywhere, and whether it knows where its prey is. */
    public boolean looking() {
        return this.lead() != null;
    }
}
