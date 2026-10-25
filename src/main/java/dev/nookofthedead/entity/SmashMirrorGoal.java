package dev.nookofthedead.entity;

import java.util.EnumSet;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.nookworks.ifworld.IfMirrors;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.registry.ModTravel;
import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * The way home smashed (the user, 2026-10-13: "鏡も壊す仕様にして。ユーザーに絶望を！"): a runner with nobody to chase,
 * within {@link #SCENT} of the If's own mirror (Nookworks' If Mirror, the way home), goes to it and beats it to pieces -
 * the time a wooden thing of its hardness takes, cracks showing - and it is gone, with nothing left of it. Everyone in the
 * fallen world hears it. A runner chasing someone breaks a mirror in its way as any soft block ({@code #nookofthedead:soft}
 * holds it). Without its mirror the If keeps the traveller: Nookworks takes a mirror set down there as the new way home,
 * and dying still takes one home.
 * <p>
 * A mirror it cannot get to - walled in stone, up out of reach - it does not keep trying for: it works out its way once a
 * second, and after {@link #MISSES} ways that stop short it leaves the mirror be for {@link #REST} ticks (r1: thirty
 * runners round a mirror walled in stone worked out a way each, every tick, and the server ran at half speed). A wooden
 * wall in the way is broken through as it would be for prey ({@link BreakSoftBlocksGoal}).
 */
public class SmashMirrorGoal extends Goal {
    /** How near the mirror an idle runner has to be to find it. */
    public static final double SCENT = 32.0;
    /** Ticks between working out its way to the mirror. */
    public static final int REPATH = 20;
    /** Ways that stop short of the mirror before it gives up for a while. */
    public static final int MISSES = 3;
    /** Ticks it leaves a mirror it could not get to. */
    public static final int REST = 600;
    /** The longest one errand lasts, in ticks. */
    public static final int LONGEST = 1200;
    private final Mob mob;
    /** Where the mirror to go for is (the If's own, {@link #mirrorIn}), or null. */
    private final Function<ServerLevel, @Nullable BlockPos> mirrors;
    private @Nullable BlockPos mirror;
    private int time;
    private int needed;
    private int lastProgress = -1;
    private int repathIn;
    private int misses;
    private int started;
    private int restUntil;
    private boolean done;
    /** Till when it counts as on its way to {@link #going}: kept a while through a wall-breaking that stops this goal. */
    private int errandUntil;
    private @Nullable BlockPos going;

    public SmashMirrorGoal(Mob mob, Function<ServerLevel, @Nullable BlockPos> mirrors) {
        this.mob = mob;
        this.mirrors = mirrors;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** The If's mirror (its lower half), if it stands, loaded, in {@code level}; null elsewhere. */
    public static @Nullable BlockPos mirrorIn(ServerLevel level) {
        if (!NookOfTheDead.fallen(level)) {
            return null;
        }
        BlockPos pos = IfWorld.get(level.getServer()).ifMirror();
        return pos != null && level.isLoaded(pos) && IfMirrors.standsAt(level, pos) ? pos : null;
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null || (this.mob.tickCount + this.mob.getId()) % 20 != 0 || this.mob.tickCount < this.restUntil
                || !(this.mob.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos pos = this.mirrors.apply(level);
        if (pos == null || !level.isLoaded(pos) || !IfMirrors.standsAt(level, pos) || pos.distToCenterSqr(this.mob.position()) > SCENT * SCENT || !CommonHooks.canEntityDestroy(level, pos, this.mob)
                || dev.nookofthedead.world.Refuge.guarded(level, pos)) {      // a mirror in Dark Matter's reach is left be
            return false;
        }
        this.mirror = pos;
        return true;
    }

    @Override
    public void start() {
        this.time = 0;
        this.lastProgress = -1;
        this.repathIn = REPATH;
        this.misses = 0;
        this.started = this.mob.tickCount;
        this.done = false;
        this.going = this.mirror;
        this.errandUntil = this.mob.tickCount + 200;
        this.needed = BreakSoftBlocksGoal.breakTicks(this.mob.level().getBlockState(this.mirror), this.mob.level(), this.mirror);
        this.mob.getNavigation().moveTo(this.mirror.getX() + 0.5, this.mirror.getY(), this.mirror.getZ() + 0.5, 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        return this.mirror != null && !this.done && this.mob.getTarget() == null && this.mob.tickCount - this.started < LONGEST
                && this.mob.level().getBlockState(this.mirror).is(ModTravel.IF_MIRROR.get());
    }

    @Override
    public void stop() {
        if (this.mirror != null) {
            this.mob.level().destroyBlockProgress(this.mob.getId(), this.mirror, -1);
        }
        this.mirror = null;
        this.done = false;
    }

    /**
     * Whether nothing solid stands between its eyes and the mirror: near enough to reach it is not enough through a wall
     * (r1: a mirror walled in stone was beaten through the stone).
     */
    private boolean inSight(BlockPos at) {
        var eyes = this.mob.getEyePosition();
        for (double y : new double[] {0.5, 1.5}) {
            var aim = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY() + y, at.getZ() + 0.5);
            var hit = this.mob.level().clip(new net.minecraft.world.level.ClipContext(eyes, aim, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this.mob));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || this.mob.level().getBlockState(hit.getBlockPos()).is(ModTravel.IF_MIRROR.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The mirror it is on its way to, while it is (and a few seconds on, through a wall it stops to break): its errand
     * for {@link BreakSoftBlocksGoal}. Null when it has none, or has given the mirror up for now.
     */
    public @Nullable BlockPos heading(ServerLevel level) {
        if (this.going == null || this.mob.tickCount >= this.errandUntil || this.resting()
                || !level.getBlockState(this.going).is(ModTravel.IF_MIRROR.get()) || this.mob.getTarget() != null) {
            return null;
        }
        return this.going;
    }

    /** Whether it is leaving a mirror it could not get to be for now. For tests. */
    public boolean resting() {
        return this.mob.tickCount < this.restUntil;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos at = this.mirror;
        if (at == null || this.done) {
            return; // smashed or given up last tick, ticked once more before stop (see BreakSoftBlocksGoal#tick)
        }
        this.mob.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
        if (!at.closerToCenterThan(this.mob.position(), 2.2) || !this.inSight(at)) {
            if (this.mob.getNavigation().isDone() && --this.repathIn <= 0) {
                this.repathIn = REPATH;
                this.mob.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0);
                var path = this.mob.getNavigation().getPath();
                if ((path == null || !path.canReach()) && ++this.misses >= MISSES) {
                    this.restUntil = this.mob.tickCount + REST; // out of reach: let it be for a while
                    this.errandUntil = 0;
                    this.done = true;
                } else {
                    this.errandUntil = this.mob.tickCount + 200;
                }
            }
            return;
        }
        this.mob.getNavigation().stop();
        ServerLevel level = (ServerLevel) this.mob.level();
        if (this.mob.getRandom().nextInt(10) == 0) {
            level.playSound(null, at, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 1.0F, 0.8F + this.mob.getRandom().nextFloat() * 0.3F);
            if (!this.mob.isSwinging()) {
                this.mob.swingForAttack(this.mob.getUsedItemHand());
            }
        }
        this.time++;
        int progress = (int) ((float) this.time / this.needed * 10.0F);
        if (progress != this.lastProgress) {
            level.destroyBlockProgress(this.mob.getId(), at, progress);
            this.lastProgress = progress;
        }
        if (this.time >= this.needed) {
            smash(level, at, this.mob);
            this.errandUntil = 0;
            this.done = true;
        }
    }

    /**
     * The mirror at {@code at} smashed, nothing of it left. Everyone in the fallen world hears glass break somewhere far
     * off - only that, nothing said (the user, 2026-10-13: "鏡の説明とかがチャット欄…に出てくる必要はないよ").
     */
    public static void smash(ServerLevel level, BlockPos at, @Nullable Mob by) {
        BlockState state = level.getBlockState(at);
        level.destroyBlock(at, false, by);
        level.destroyBlockProgress(by != null ? by.getId() : 0, at, -1);
        level.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
        if (state.is(ModTravel.IF_MIRROR.get())) {
            for (ServerPlayer player : level.players()) {
                if (player.blockPosition().distSqr(at) > 32 * 32) {   // nearer, the smash itself is heard
                    player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.GLASS_BREAK),
                            SoundSource.AMBIENT, player.getX(), player.getY() + 8, player.getZ(), 0.35F, 0.5F, level.getRandom().nextLong()));
                }
            }
        }
    }
}
