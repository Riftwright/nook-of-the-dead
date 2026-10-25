package dev.nookofthedead.entity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.registry.ZwEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What makes the dead swarm (the user, 2026-10-13: "同じくらいの量、スピード感でゾンビが群がってくるように"; then
 * "昼ももっと増やしていい", 10-13): natural spawning alone spreads its runners over
 * the whole loaded town, a handful near anyone. So every {@link #EVERY} ticks, round each player in the fallen world who
 * can be hunted, the runners within {@link #NEAR} are counted; short of {@link #DAY} ({@link #NIGHT} after dark), a few
 * more come out of sight {@link #MIN_FROM} to {@link #MAX_FROM} blocks off - round a corner, behind a house, out of a
 * doorway - making for where that player is (drawn by the living, they come to see: {@link Mind#lead}; whether they find
 * them is up to their eyes and ears), or, if that player is up out of their notice ({@link Mind#notices}: on a roof),
 * wandering the streets. One in {@link #VILLAGERS} was a villager. They are never moved from elsewhere, and never put where the player is looking
 * (unless no hidden place is found, and then only far off), nor in a roofed, lit place.
 * <p>
 * Someone getting away (the user, 2026-10-13: running, the crowd all behind took every place, and ahead there were
 * none - "もうちょっとゾンビの湧きの代謝をよくできないかな"): a runner left {@link #BEHIND} blocks behind the way they are going,
 * out of their sight and after nobody else, has lost them and goes; and of those that come out, most come out ahead
 * ({@link #AHEAD} in a hundred, within {@link #AHEAD_ANGLE} of their way). Which way is told from where they were at the
 * last wave.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Horde {
    public static final int EVERY = 40;
    public static final double NEAR = 48.0;
    /**
     * By day, a little over half the night's (the user, 2026-10-19, having played it through: "すげー疲れた。ずっとゾンビに
     * 追われてるんだもん"; the review found the day only a quarter lighter than the night - 55 to 75 - and nothing the
     * player did buying a lull). Night is as it was.
     */
    public static final int DAY = 40;
    public static final int NIGHT = 75;
    /**
     * Each of the dead a player puts down near them takes one off what comes round them, for {@link #CALM} ticks, down to
     * half: a street cleared stays clear a while (the review: a cleared street was filled again in twenty seconds).
     */
    public static final int CALM = 90 * 20;
    /** The game times of the dead each player put down lately (the server's thread only). */
    private static final java.util.Map<ServerPlayer, java.util.ArrayDeque<Long>> KILLS = new java.util.WeakHashMap<>();

    /**
     * One of the dead put down by a player: one fewer to come round every player near it (the review, 2026-10-19: with
     * only the killer's count lowered, a friend beside them who did not strike called the street full again).
     */
    @SubscribeEvent
    static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof Mutated && event.getSource().getEntity() instanceof ServerPlayer
                && event.getEntity().level() instanceof ServerLevel level && NookOfTheDead.fallen(level)) {
            for (ServerPlayer near : level.players()) {
                if (near.distanceToSqr(event.getEntity()) <= NEAR * NEAR) {
                    KILLS.computeIfAbsent(near, p -> new java.util.ArrayDeque<>()).addLast(level.getGameTime());
                }
            }
        }
    }

    /** How many of the dead {@code player} put down in the last {@link #CALM} ticks. */
    static int calm(ServerLevel level, ServerPlayer player) {
        java.util.ArrayDeque<Long> kills = KILLS.get(player);
        if (kills == null) {
            return 0;
        }
        while (!kills.isEmpty() && level.getGameTime() - kills.peekFirst() > CALM) {
            kills.pollFirst();
        }
        return kills.size();
    }

    /**
     * Whether {@code player} is where the ones coming out are not sent to them: in a room that is roofed and lit, in a
     * Dark Matter's reach or in the institute's refuge (the review: the newly come were led straight to anyone, so no
     * shelter ever was one). They still come for what they hear or see. On Hard, and in Hardcore, a room is no shelter
     * from them: only Dark Matter's reach and the refuge are (the user, 2026-10-19: "ゲーム自体の難易度がハードやハード
     * コアのときだけ復活できないかな").
     */
    public static boolean sheltered(ServerLevel level, ServerPlayer player) {
        return sheltered(level, BlockPos.containing(player.getX(), player.getY() + 0.5, player.getZ()), hard(level));
    }

    /** Whether one standing at {@code at} is sheltered so, the game {@code hard} or not. */
    public static boolean sheltered(ServerLevel level, BlockPos at, boolean hard) {
        if (dev.nookofthedead.world.Refuge.guarded(level, at)) {
            return true;
        }
        if (hard) {
            return false;
        }
        // a room, not a block held over one's head in the street (the review, 2026-10-19: the sky's light under a single
        // block or a tree is 14, under a roof with walls far less)
        return level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at) <= 10
                && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at) >= 8;
    }

    /** The game set to Hard, or Hardcore (whose difficulty is Hard and locked). */
    public static boolean hard(ServerLevel level) {
        return level.getDifficulty() == net.minecraft.world.Difficulty.HARD || level.getLevelData().isHardcore();
    }

    /** Of the dead about a player, how many more the weather brings: a quarter more in the rain, two fifths in a storm. */
    public static double weather(ServerLevel level) {
        return 1.0 + 0.25 * level.getRainLevel(1.0F) + 0.15 * level.getThunderLevel(1.0F);
    }
    /** At most this many come out round one player at a time: the swarm builds over some seconds, not all at once. */
    public static final int PER_WAVE = 6;
    public static final double MIN_FROM = 24.0;
    public static final double MAX_FROM = 46.0;
    /** However many play, no more than this many runners in the fallen world from the horde. */
    public static final int WORLD_MAX = 220;
    /** Moving at least this fast (blocks a second, flat) is getting away: walking (4.3) and running (5.6) both are. */
    public static final double MOVING = 2.0;
    /** A runner this far behind someone getting away, along their way, out of their sight, has lost them. */
    public static final double BEHIND = 20.0;
    /** Of the runners coming out round someone getting away, how many in a hundred come out ahead of them. */
    public static final int AHEAD = 75;
    /** How far to either side of their way "ahead" reaches. */
    public static final double AHEAD_ANGLE = Math.toRadians(55);
    /** On Hard, how far the idle ones smell one wounded to half ({@link Hardness#smellsBlood}). */
    public static final double BLOOD = 24.0;
    /** One in this many that come out was a villager (the user, 2026-10-15: "村人ゾンビも時々混ぜて"). */
    public static final int VILLAGERS = 8;

    /** Where each player was at the last wave, to tell which way they are going (the server's thread only). */
    private static final java.util.Map<ServerPlayer, Vec3> LAST = new java.util.WeakHashMap<>();

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !NookOfTheDead.fallen(level) || level.getGameTime() % EVERY != 0
                || level.getDifficulty() == Difficulty.PEACEFUL || !level.isSpawningMonsters()
                || dev.nookofthedead.ending.Cure.cured(level.getServer())) {
            return;                                                     // and none in a cured If (0.3)
        }
        int total = level.getEntities(ZwEntities.MUTATED_ZOMBIE.get(), r -> true).size()
                + level.getEntities(ZwEntities.MUTATED_ZOMBIE_VILLAGER.get(), r -> true).size()
                + level.getEntities(ZwEntities.MUTATED_HUSK.get(), r -> true).size();
        List<ServerPlayer> players = level.players();
        if (total >= WORLD_MAX) {
            // full: the ones far from everyone, after nobody, make room (a player come late found the cap taken by the
            // crowds of those before, and none came for them)
            total -= thin(level, players);
        }
        // starting with a different player each wave, so that under the cap nobody is always the one left out (r1)
        int first = players.isEmpty() ? 0 : (int) (level.getGameTime() / EVERY % players.size());
        for (int i = 0; i < players.size(); i++) {
            if (total >= WORLD_MAX) {
                return;
            }
            ServerPlayer player = players.get((first + i) % players.size());
            if (!dev.nookofthedead.ending.Film.watching(player)) {      // none come for one watching a film
                total += gather(level, player);
            }
        }
    }

    /** Up to {@link #PER_WAVE} twice over of the dead far from every player and after nobody, gone; how many. */
    public static int thin(ServerLevel level, List<ServerPlayer> players) {
        int gone = 0;
        for (Zombie zombie : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Zombie.class), z -> z instanceof Mutated)) {
            if (gone >= PER_WAVE * 2) {
                break;
            }
            if (zombie.isPersistenceRequired() || zombie.getTarget() != null) {
                continue;
            }
            boolean far = true;
            for (ServerPlayer player : players) {
                far &= zombie.distanceToSqr(player) > (NEAR + 16) * (NEAR + 16);
            }
            if (far) {
                zombie.discard();
                gone++;
            }
        }
        return gone;
    }

    /** One wave round {@code player}: how many came. */
    public static int gather(ServerLevel level, ServerPlayer player) {
        return gather(level, player, heading(player));
    }

    /** Which way {@code player} has gone since the last wave (flat, of length one), or null if they have hardly moved. */
    static @Nullable Vec3 heading(ServerPlayer player) {
        Vec3 now = player.position();
        Vec3 was = LAST.put(player, now);
        if (was == null) {
            return null;
        }
        Vec3 moved = new Vec3(now.x - was.x, 0, now.z - was.z);
        double perSecond = moved.length() / (EVERY / 20.0);
        // faster than anyone runs is a jump (a mirror, a command), not a way
        return perSecond >= MOVING && perSecond <= 20.0 ? moved.normalize() : null;
    }

    /**
     * One wave round {@code player}, going {@code heading} (or standing about, null): the runners that have lost them sent
     * off, and how many came.
     */
    public static int gather(ServerLevel level, ServerPlayer player, @Nullable Vec3 heading) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return 0;
        }
        if (NookOfTheDead.fallen(level) && player.getY() < dev.nookofthedead.world.Tunnels.DEPTHS) {
            rats(level, player);                                        // the galleries' own: the rats come out of the dark
            // down in the galleries: the dead there are their own, and the ones up top that were after them, out of
            // sight now and far above, let them go (they would stand over the shaft for ever)
            for (Zombie up : level.getEntitiesOfClass(Zombie.class, new AABB(player.blockPosition()).inflate(NEAR, 96, NEAR),
                    z -> z instanceof Mutated && z.getTarget() == player && z.getY() > dev.nookofthedead.world.Tunnels.DEPTHS + 4)) {
                if (!up.getSensing().hasLineOfSight(player)) {
                    up.setTarget(null);
                }
            }
            return 0;
        }
        // as many as the region holds: the town the most, the wilds the fewest (Region#dead)
        // and more in the rain, more still in a storm (the user, 2026-10-17: "天気で群れが変わる")
        // and as many more or fewer as the game is hard (Hardness, 2026-10-23)
        Hardness hardness = Hardness.of(level);
        int want = (int) Math.round((level.isDarkOutside() ? NIGHT : DAY)
                * dev.nookofthedead.world.Region.at(level.getSeed(), player.getBlockX(), player.getBlockZ()).dead * weather(level)
                * hardness.crowd());
        // what they put down buys them a while
        want = Math.max((int) Math.round(want * hardness.calmFloor()), want - calm(level, player));
        boolean sheltered = sheltered(level, player);
        // the ones held in a boat, on a lead, named are not counted: they keep no others away (Unkept)
        List<Zombie> near = level.getEntitiesOfClass(Zombie.class, new AABB(player.blockPosition()).inflate(NEAR),
                z -> z instanceof Mutated && !Unkept.held(z));
        if (hardness.smellsBlood() && player.getHealth() <= player.getMaxHealth() / 2 && !sheltered) {
            // on Hard, one bleeding draws the idle ones round them, seen or not
            for (Zombie idle : near) {
                Mind mind = Mind.of(idle);
                if (mind != null && idle.getTarget() == null && idle.distanceToSqr(player) <= BLOOD * BLOOD && mind.lead() == null) {
                    mind.lead(player.position());
                }
            }
        }
        int lost = 0;
        if (heading != null) {
            Vec3 eyes = player.getEyePosition();
            for (Zombie runner : near) {
                if (lost >= hardness.perWave() * 2) {
                    break;
                }
                Vec3 off = runner.position().subtract(player.position());
                if (off.x * heading.x + off.z * heading.z < -hardness.behind() && (runner.getTarget() == null || runner.getTarget() == player)
                        && !runner.isPersistenceRequired() && !seen(level, player, eyes, runner.blockPosition())) {
                    runner.discard();
                    lost++;
                }
            }
        }
        int come = Math.min(hardness.perWave(), want - (near.size() - lost));
        int came = 0;
        for (int i = 0; i < come; i++) {
            BlockPos at = hiddenSpot(level, player, level.getRandom(), heading);
            if (at == null) {
                break;
            }
            if (animal(level, at)) {
                came++;
                continue;
            }
            Zombie runner = runner(level, at, true).create(level, EntitySpawnReason.EVENT);
            if (runner == null) {
                break;
            }
            runner.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            runner.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
            Mind mind = Mind.of(runner);
            // up on a roof, or holed up somewhere safe, they only come out to wander the streets
            if (mind != null && mind.notices(player) && !sheltered) {
                mind.lead(player.position());
            }
            level.addFreshEntity(runner);
            came++;
        }
        return came;
    }

    /**
     * Which of the dead comes at {@code at}: in the desert a husk four times in five ({@link MutatedHusk}), else a mutated
     * zombie - one in {@link #VILLAGERS} a villager, if {@code villagers}.
     */
    public static net.minecraft.world.entity.EntityType<? extends Zombie> runner(ServerLevel level, BlockPos at, boolean villagers) {
        RandomSource random = level.getRandom();
        if (dev.nookofthedead.world.Desert.at(level.getSeed(), at.getX(), at.getZ()) && random.nextInt(5) != 0) {
            return ZwEntities.MUTATED_HUSK.get();
        }
        return villagers && random.nextInt(VILLAGERS) == 0 ? ZwEntities.MUTATED_ZOMBIE_VILLAGER.get() : ZwEntities.MUTATED_ZOMBIE.get();
    }

    /**
     * Now and then, out in the country, one of the animals the plague took comes instead ({@link Infected}): in the
     * farmland a cow (a horse one time in three), one in {@link #FARM_ANIMALS}; in the wilds a wolf, one in
     * {@link #WILD_ANIMALS}; in a zombie village a cat ({@link #cats}). It comes not knowing anyone: it finds them by sight. Whether one came.
     */
    static boolean animal(ServerLevel level, BlockPos at) {
        dev.nookofthedead.world.Region region = dev.nookofthedead.world.Region.at(level.getSeed(), at.getX(), at.getZ());
        RandomSource random = level.getRandom();
        net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type;
        if (region == dev.nookofthedead.world.Region.FARMLAND && random.nextInt(FARM_ANIMALS) == 0) {
            type = random.nextInt(3) == 0 ? ZwEntities.MUTATED_ZOMBIE_HORSE.get() : ZwEntities.ZOMBIE_COW.get();
        } else if (region == dev.nookofthedead.world.Region.WILDS && random.nextInt(WILD_ANIMALS) == 0) {
            type = ZwEntities.ZOMBIE_WOLF.get();
        } else if (dev.nookofthedead.world.Vanilla.villageAt(level.getSeed(), Math.floorDiv(at.getX(), dev.nookofthedead.world.Town.BLOCK),
                Math.floorDiv(at.getZ(), dev.nookofthedead.world.Town.BLOCK)) && random.nextInt(cats(level)) == 0) {
            type = ZwEntities.INFECTED_CAT.get();                       // the village's cats (2026-10-23)
        } else if ((region == dev.nookofthedead.world.Region.CITY || region == dev.nookofthedead.world.Region.SETTLEMENT)
                && random.nextInt(TOWN_RATS) == 0) {
            return pack(level, at, ZwEntities.INFECTED_RAT.get(), 3 + random.nextInt(3));   // a pack of rats out of a drain
        } else if (region == dev.nookofthedead.world.Region.DESERT && random.nextInt(DESERT_ANIMALS) == 0) {
            type = random.nextInt(4) == 0 ? ZwEntities.INFECTED_CAMEL.get() : ZwEntities.INFECTED_SCORPION.get();
        } else {
            return false;
        }
        // not more than a few round anyone: they are not counted with the dead, and would pile up behind a traveller
        if (level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(at).inflate(NEAR), m -> m instanceof Quiet && !(m instanceof Vandal))
                .size() >= ANIMALS_NEAR) {
            return false;
        }
        net.minecraft.world.entity.Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return false;
        }
        mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(mob)) {
            return false;                                                // a camel or a horse where only one of the dead fits
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
        level.addFreshEntity(mob);
        return true;
    }

    /** One in this many that come out in the farmland is an animal; in the wilds. */
    public static final int FARM_ANIMALS = 7;
    public static final int WILD_ANIMALS = 3;
    /** In a zombie village, one in this many of those who come is a cat: fewer on Easy, more on Hard. */
    public static final int VILLAGE_CATS = 4;

    static int cats(ServerLevel level) {
        Hardness hardness = Hardness.of(level);
        return hardness == Hardness.EASY ? VILLAGE_CATS + 2 : hardness == Hardness.HARD ? VILLAGE_CATS - 1 : VILLAGE_CATS;
    }

    /**
     * In the town, one in this many of those who come is a pack of rats (the user, 2026-10-24: "ネズミが全然でない" - their
     * natural spawns never found room beside the horde).
     */
    public static final int TOWN_RATS = 24;
    /** Below, how often a wave brings rats (one in this many), and how many may be about a player before none come. */
    public static final int GALLERY_RATS = 6;
    public static final int RATS_NEAR = 4;

    /** A pack of {@code n} of {@code type} round {@code at}, where they fit; whether any came. */
    static boolean pack(ServerLevel level, BlockPos at, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, int n) {
        if (level.getEntities(type, new AABB(at).inflate(NEAR), m -> true).size() >= ANIMALS_NEAR) {
            return false;                                               // of their own kind: the bats below are many
        }
        int came = 0;
        for (int i = 0; i < n; i++) {
            net.minecraft.world.entity.Mob mob = type.create(level, EntitySpawnReason.EVENT);
            if (mob == null) {
                break;
            }
            mob.snapTo(at.getX() + 0.5 + level.getRandom().nextInt(3) - 1, at.getY(), at.getZ() + 0.5 + level.getRandom().nextInt(3) - 1,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            if (level.noCollision(mob)) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
                level.addFreshEntity(mob);
                came++;
            }
        }
        return came > 0;
    }

    /**
     * Down in the galleries, now and then a few rats out of the dark somewhere out of {@code player}'s sight, not too near
     * - never in the refuge, nor round anyone in it (the user, 2026-10-24: rats in the refuge now and then).
     */
    static void rats(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        if (dev.nookofthedead.world.Refuge.guarded(level, player.blockPosition()) || random.nextInt(GALLERY_RATS) != 0 || level.getEntities(ZwEntities.INFECTED_RAT.get(),
                r -> r.distanceToSqr(player) < 32 * 32).size() >= RATS_NEAR) {
            return;
        }
        Vec3 eyes = player.getEyePosition();
        for (int tries = 0; tries < 16; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 12 + random.nextDouble() * 14;
            BlockPos at = BlockPos.containing(player.getX() + Math.cos(a) * d, player.getY(), player.getZ() + Math.sin(a) * d);
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos p = at.above(dy);
                if (level.isLoaded(p) && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                        && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p) < 8 && !seen(level, player, eyes, p)
                        && !dev.nookofthedead.world.Refuge.guarded(level, p)) {
                    pack(level, p, ZwEntities.INFECTED_RAT.get(), 2 + random.nextInt(3));
                    return;
                }
            }
        }
    }

    /** In the desert, one in this many of those who come is a scorpion or (one time in four) a camel. */
    static final int DESERT_ANIMALS = 6;
    /** At most this many of the plague's animals within {@link #NEAR} of where one would come. */
    public static final int ANIMALS_NEAR = 8;

    /**
     * A place a runner could come from: at the right distance, loaded, room to stand, not safe (roofed and lit), and out of
     * the player's sight - the first tries insist on that; the last few take anything far enough off.
     */
    static @Nullable BlockPos hiddenSpot(ServerLevel level, ServerPlayer player, RandomSource random, @Nullable Vec3 heading) {
        Vec3 eyes = player.getEyePosition();
        for (int tries = 0; tries < 16; tries++) {
            // someone getting away: mostly ahead of them
            double angle = heading != null && random.nextInt(100) < AHEAD
                    ? Math.atan2(heading.z, heading.x) + (random.nextDouble() * 2 - 1) * AHEAD_ANGLE
                    : random.nextDouble() * Math.PI * 2;
            double from = MIN_FROM + random.nextDouble() * (MAX_FROM - MIN_FROM);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * from);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * from);
            if (!level.isLoaded(new BlockPos(x, player.getBlockY(), z))) {
                continue;
            }
            // from the street up, as well as round the player's own height: one up a tower is come for from below (r1:
            // only roofs as high as them were looked at, and a pillar kept the swarm off)
            BlockPos at = standing(level, x, z, Math.min(player.getBlockY() - 6,
                    dev.nookofthedead.world.Town.SURFACE - dev.nookofthedead.world.Town.HILL - 1));   // the town's lowest street
            if (at == null || MutatedZombie.safe(level, at)) {
                continue;
            }
            if (tries < 12 && seen(level, player, eyes, at)) {
                continue;
            }
            return at;
        }
        return null;
    }

    /** Where in column ({@code x}, {@code z}) one could stand, from {@code from} up: the street's or a floor's level, lowest first. */
    private static @Nullable BlockPos standing(ServerLevel level, int x, int z, int from) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int y = Math.max(level.getMinY() + 1, from); y <= top; y++) {
            BlockPos at = new BlockPos(x, y, z);
            if (Footing.runner(level, at, ZwEntities.MUTATED_ZOMBIE.get())) {         // glass, slabs, stairs too (2026-10-21)
                return at;
            }
        }
        return null;
    }

    /** Whether {@code player} could see a runner standing at {@code at} (nothing solid between their eyes and its head). */
    private static boolean seen(ServerLevel level, ServerPlayer player, Vec3 eyes, BlockPos at) {
        Vec3 head = Vec3.atBottomCenterOf(at).add(0, 1.6, 0);
        return level.clip(new ClipContext(eyes, head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private Horde() {}
}
