package dev.nookofthedead.entity;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;

/**
 * What the dead hear (the user, 2026-10-15: "壁の向こうは見えない。ただし音は聞こえるので、物音を立てると気づいて襲ってくる…
 * スニークとかカーペット上を移動しているときには、壁越しなら気づかないように"). Vanilla's game events - the same the warden
 * and sculk sensors feel - are the sounds: a step, a landing, a door, a chest, a block broken or put down, a blow, a thing
 * thrown landing, an explosion. Each carries {@link #LOUDNESS} blocks, by the hearer's {@link Traits.Sense#hearing}; half
 * as far through a wall (to a hearer that cannot see where it was), and not at all through wool (vanilla's
 * {@code #occludes_vibration_signals}). As vanilla's listeners do, it takes no notice of a step or a landing someone
 * sneaking makes ({@code #ignore_vibrations_sneaking}) or of steps on wool or a carpet ({@code #dampens_vibrations}): so one
 * creeping past a house, or across a carpeted floor, goes unheard behind its walls - though in sight they are still seen.
 * <p>
 * A zombie that hears sets off for where the sound was ({@link Mind#heard}): a bottle thrown into the street draws the
 * crowd off, as in the films; a door slammed brings them to it. The dead's own sounds are nothing to them.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Hearing {
    /** How far each sound carries in the open, in blocks; sounds not here are not heard. */
    public static final Map<Holder<GameEvent>, Double> LOUDNESS = Map.ofEntries(
            Map.entry(GameEvent.STEP, 6.0),
            Map.entry(GameEvent.HIT_GROUND, 10.0),
            Map.entry(GameEvent.SPLASH, 10.0),
            Map.entry(GameEvent.SWIM, 6.0),
            Map.entry(GameEvent.BLOCK_DESTROY, 16.0),
            Map.entry(GameEvent.BLOCK_PLACE, 10.0),
            Map.entry(GameEvent.BLOCK_OPEN, 14.0),
            Map.entry(GameEvent.BLOCK_CLOSE, 14.0),
            Map.entry(GameEvent.BLOCK_ACTIVATE, 8.0),
            Map.entry(GameEvent.BLOCK_DEACTIVATE, 8.0),
            Map.entry(GameEvent.BLOCK_CHANGE, 10.0),
            Map.entry(GameEvent.CONTAINER_OPEN, 10.0),
            Map.entry(GameEvent.CONTAINER_CLOSE, 10.0),
            Map.entry(GameEvent.ENTITY_DAMAGE, 16.0),
            Map.entry(GameEvent.ENTITY_DIE, 12.0),
            Map.entry(GameEvent.PROJECTILE_SHOOT, 10.0),
            Map.entry(GameEvent.PROJECTILE_LAND, 18.0),
            Map.entry(GameEvent.PRIME_FUSE, 12.0),
            Map.entry(GameEvent.EXPLODE, 64.0),
            Map.entry(GameEvent.INSTRUMENT_PLAY, 64.0),
            Map.entry(GameEvent.NOTE_BLOCK_PLAY, 24.0),
            Map.entry(GameEvent.JUKEBOX_PLAY, 32.0),
            Map.entry(GameEvent.EAT, 4.0),
            Map.entry(GameEvent.DRINK, 4.0),
            Map.entry(GameEvent.EQUIP, 4.0),
            Map.entry(GameEvent.SHEAR, 6.0),
            Map.entry(GameEvent.FLUID_PLACE, 8.0),
            Map.entry(GameEvent.FLUID_PICKUP, 6.0));
    /** A sprinting step carries this much further than a walking one. */
    public static final double SPRINTING = 2.0;
    /** The farthest anything is heard, for the search. */
    private static final double FARTHEST = 64.0 * Traits.Sense.KEEN.hearing;

    @SubscribeEvent
    static void onGameEvent(VanillaGameEvent event) {
        if (event.getLevel() instanceof ServerLevel level && NookOfTheDead.fallen(level)) {
            Vec3 at = event.getEventPosition();
            if (event.getVanillaEvent().is(GameEvent.BLOCK_CHANGE) && level.getBlockState(net.minecraft.core.BlockPos.containing(at)).is(net.minecraft.world.level.block.Blocks.BELL)) {
                toll(level, at);
                return;
            }
            sound(level, event.getVanillaEvent(), at, event.getCause(), event.getContext().affectedState());
        }
    }

    /** How far a bell is heard: over the roofs, through the walls. */
    public static final double BELL = 72.0;

    /**
     * A bell rung (the user, 2026-10-17: "鐘ならしたら周囲のゾンビが一斉に集まってくる"): every one of them within
     * {@link #BELL}, walls or none, sets off for it at once - those after someone out of their sight too. A way to call
     * them off, or down on oneself. How many heard, for tests.
     */
    public static int toll(ServerLevel level, Vec3 at) {
        int n = 0;
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(at, at).inflate(BELL), z -> z instanceof Mutated && z.isAlive())) {
            Mind mind = Mind.of(zombie);
            if (mind != null && zombie.distanceToSqr(at) <= BELL * BELL) {
                mind.summoned(at);
                n++;
            }
        }
        return n;
    }

    /** A sound in {@code level}: every zombie of ours in earshot told of it. How far it carried, for tests (0 if not at all). */
    public static double sound(ServerLevel level, Holder<GameEvent> event, Vec3 at, @Nullable Entity cause,
            net.minecraft.world.level.block.state.@Nullable BlockState affected) {
        double loud = loudness(event, cause, affected);
        if (loud <= 0) {
            return 0;
        }
        double reach = Math.min(FARTHEST * Hardness.HARD.hearing(), loud * Traits.Sense.KEEN.hearing * Hardness.of(level).hearing());
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(at, at).inflate(reach), z -> z instanceof Mutated && z.isAlive())) {
            Mind mind = Mind.of(zombie);
            if (mind != null && hears(level, zombie, mind, at, loud)) {
                mind.heard(at, cause);
            }
        }
        return loud;
    }

    /** How far a sound carries in the open: 0 for one not heard at all. */
    public static double loudness(Holder<GameEvent> event, @Nullable Entity cause, net.minecraft.world.level.block.state.@Nullable BlockState affected) {
        Double loud = LOUDNESS.get(event);
        if (loud == null || cause instanceof Mutated || cause instanceof Quiet) {
            return 0;
        }
        if (cause instanceof Player player && (player.isSpectator() || player.isCreative() || dev.nookofthedead.ending.Film.watching(player))) {
            return 0;
        }
        if (cause != null && cause.isSteppingCarefully() && event.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
            return 0;
        }
        if (affected != null && affected.is(BlockTags.DAMPENS_VIBRATIONS) && (event.is(GameEvent.STEP) || event.is(GameEvent.HIT_GROUND))) {
            return 0;
        }
        if (cause != null && cause.isSprinting() && event.is(GameEvent.STEP)) {
            return loud * SPRINTING;
        }
        return loud;      // a thing thrown landing is heard where it lands, whoever threw it
    }

    /** Whether {@code zombie} hears a sound of {@code loud} at {@code at}: half as far unseen, never through wool. */
    public static boolean hears(ServerLevel level, Zombie zombie, Mind mind, Vec3 at, double loud) {
        Vec3 ears = zombie.getEyePosition();
        double reach = loud * mind.traits().sense().hearing * Hardness.of(level).hearing();
        double far = ears.distanceToSqr(at);
        if (far > reach * reach) {
            return false;
        }
        if (muffled(level, zombie, ears, at)) {
            if (far > reach * reach / 4) {
                return false;
            }
            return !throughWool(level, ears, at);
        }
        return true;
    }

    /** Whether something solid stands between {@code ears} and {@code at}. */
    private static boolean muffled(ServerLevel level, Zombie zombie, Vec3 ears, Vec3 at) {
        Vec3 to = at.add(0, 0.5, 0);
        return level.clip(new ClipContext(ears, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, zombie)).getType() != HitResult.Type.MISS;
    }

    /** Whether a block of {@code #occludes_vibration_signals} (wool) lies on the line from {@code from} to {@code to}. */
    static boolean throughWool(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int n = (int) Math.ceil(step.length() * 2);
        BlockPos last = null;
        for (int i = 1; i < n; i++) {
            BlockPos at = BlockPos.containing(from.add(step.scale((double) i / n)));
            if (!at.equals(last)) {
                last = at;
                if (level.getBlockState(at).is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Hearing() {}
}
