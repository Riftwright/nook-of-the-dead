package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.EnumSet;

import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import dev.nookofthedead.registry.ZwBlocks;
import dev.nookofthedead.world.District;
import dev.nookofthedead.world.Region;
import dev.nookofthedead.world.RegionBiomes;
import dev.nookofthedead.world.Terrain;
import dev.nookofthedead.world.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** The fallen world's regions (biomes): the town, the wilds, the players' settlement, the farmland, and their ground. */
final class RegionTests {
    static void register() {
        // every region comes up, each over a run of blocks, the town where one comes in; the dead fewest in the wilds; the
        // country's quarters are its region's
        test("regions_come_in_patches_the_town_at_home", helper -> {
            long seed = 20261015L;
            EnumSet<Region> seen = EnumSet.noneOf(Region.class);
            int same = 0;
            int pairs = 0;
            for (int bx = -30; bx <= 30; bx++) {
                for (int bz = -30; bz <= 30; bz++) {
                    Region r = Region.of(seed, bx, bz);
                    seen.add(r);
                    pairs++;
                    same += r == Region.of(seed, bx + 1, bz) ? 1 : 0;
                    District d = District.of(seed, bx, bz);
                    check(helper, r != Region.FARMLAND || d == District.FARM, "farmland's blocks are fields");
                    check(helper, r != Region.WILDS || d == District.WILD, "the wilds' blocks are wild");
                    check(helper, r != Region.SETTLEMENT || d == District.SETTLEMENT, "the settlement's blocks are its own");
                }
            }
            // all but the capital, which is never rolled: it stands where it stands, over whatever was there
            check(helper, seen.size() == Region.values().length - 1 && !seen.contains(Region.CAPITAL), "every region comes up: " + seen);
            int[] capital = dev.nookofthedead.world.Capital.centre(seed);
            check(helper, Region.at(seed, capital[0], capital[1]) == Region.CAPITAL, "the capital's middle is the capital's");
            StringBuilder row = new StringBuilder();
            for (int bx = -30; bx <= 30; bx++) {
                row.append(Region.of(seed, bx, 10).name().charAt(0));
            }
            check(helper, same > pairs * 0.75, "a region runs over many blocks (" + same + " of " + pairs + ") " + row);
            for (int bx = -Region.HOME; bx <= Region.HOME; bx++) {
                for (int bz = -Region.HOME; bz <= Region.HOME; bz++) {
                    check(helper, Region.of(seed, bx, bz) == Region.CITY, "the town round the middle");
                }
            }
            check(helper, Region.WILDS.dead < Region.FARMLAND.dead && Region.FARMLAND.dead < Region.CITY.dead, "the dead fewest in the wilds");
            helper.succeed();
        });

        // the open country has no grid of streets: in the farmland a lane only where a line goes the whole way, in the
        // wilds a trail; inside such a region, no other line at all
        test("the_country_has_lanes_not_streets", helper -> {
            long seed = 20261015L;
            int lanes = 0;
            int trails = 0;
            int none = 0;
            for (int line = -30; line <= 30; line++) {
                for (int along = -30; along <= 30; along++) {
                    Region a = Region.of(seed, line - 1, along);
                    Region b = Region.of(seed, line, along);
                    if (!Town.rural(a) || !Town.rural(b)) {
                        continue;
                    }
                    Town.Ground g = Town.line(seed, true, line, along);
                    if (!Town.through(seed, true, line)) {
                        check(helper, g == Town.Ground.LOT, "no line inside the country but the through ones");
                        none++;
                    } else if (a == Region.WILDS || b == Region.WILDS) {
                        check(helper, g == Town.Ground.ALLEY, "a trail through the wilds");
                        trails++;
                    } else {
                        check(helper, g == Town.Ground.STREET, "a lane across the farmland");
                        lanes++;
                    }
                }
            }
            check(helper, none > 0 && lanes > 0 && trails > 0, "lanes " + lanes + ", trails " + trails + ", none " + none);
            helper.succeed();
        });

        // the wilds' hills rise from nothing at their edge (no cliff along a region's border), never more than a few blocks
        // over a lot's width; lakes and caves come now and then, small, never together, nothing built on their blocks
        test("the_country_ground_is_gentle_and_small", helper -> {
            long seed = 20261015L;
            int lakes = 0;
            int caves = 0;
            for (int bx = -30; bx <= 30; bx++) {
                for (int bz = -30; bz <= 30; bz++) {
                    Terrain.Lake lake = Terrain.lake(seed, bx, bz);
                    Terrain.Cave cave = Terrain.cave(seed, bx, bz);
                    check(helper, lake == null || cave == null, "a lake and a cave never in one block");
                    if (lake != null || cave != null) {
                        check(helper, Town.lots(seed, bx, bz).isEmpty(), "nothing built on a lake's or a cave's block");
                    }
                    if (lake != null) {
                        lakes++;
                        check(helper, lake.radius() <= 24, "a small lake");
                        check(helper, Region.of(seed, bx, bz) != Region.CITY, "no lake in the town");
                    }
                    caves += cave != null ? 1 : 0;
                    check(helper, cave == null || Region.of(seed, bx, bz) == Region.WILDS, "caves in the wilds");
                }
            }
            check(helper, lakes > 0 && caves > 0, "lakes " + lakes + ", caves " + caves);
            for (int x = -2400; x <= 2400; x += 7) {
                for (int z = -2400; z <= 2400; z += 29) {
                    double h = Town.height(seed, x, z);
                    // up a mountain the ground climbs faster, at most two blocks in one near its top (its face its own rock)
                    Terrain.Peak peak = Terrain.peak(seed, x, z);
                    check(helper, Math.abs(Town.height(seed, x + 1, z) - h) <= (peak == null ? 0.6 : 2.0), "no cliff at " + x + "," + z + ": "
                            + h + " to " + Town.height(seed, x + 1, z) + (peak == null ? "" : " (a mountain)"));
                    check(helper, h - Town.SURFACE <= Town.HILL + Terrain.WILD_HILLS + 2.5 + (peak == null ? 0 : peak.height()),
                            "hills small at " + x + "," + z);
                }
            }
            helper.succeed();
        });

        // now and then a small sea in the country, a mountain in the wilds (the user, 2026-10-13 and 10-25): never in the
        // town nor near where one comes in; a sea's shore holds its water all round, nothing built in it or up a mountain
        test("seas_and_mountains_come_now_and_then", helper -> {
            long seed = 20261015L;
            int seas = 0;
            int peaks = 0;
            for (int qx = -10; qx <= 10; qx++) {
                for (int qz = -10; qz <= 10; qz++) {
                    Object found = Terrain.square(seed, qx, qz);
                    if (found instanceof Terrain.Sea sea) {
                        seas++;
                        check(helper, sea.water() > Town.SURFACE - Town.HILL - 4, "a sea's water near the land's: " + sea.water());
                        for (int i = 0; i < 180; i++) {
                            double a = i * Math.PI / 90;
                            for (int r = 0; r <= sea.radius() * 1.4; r += 3) {
                                int x = (int) Math.round(sea.x() + Math.cos(a) * r);
                                int z = (int) Math.round(sea.z() + Math.sin(a) * r);
                                double in = sea.in(x, z);
                                double h = Town.height(seed, x, z);
                                check(helper, in > 0 ? h < sea.water() : h >= sea.water() - 0.01, "the shore holds the sea at " + x + "," + z
                                        + ": ground " + h + ", water " + sea.water() + ", in " + in);
                                check(helper, Region.of(seed, Math.floorDiv(x, Town.BLOCK), Math.floorDiv(z, Town.BLOCK)) != Region.CITY, "no sea in the town");
                            }
                        }
                    } else if (found instanceof Terrain.Peak peak) {
                        peaks++;
                        check(helper, Region.of(seed, Math.floorDiv(peak.x(), Town.BLOCK), Math.floorDiv(peak.z(), Town.BLOCK)) == Region.WILDS,
                                "a mountain in the wilds");
                        check(helper, Town.height(seed, peak.x(), peak.z()) - Town.SURFACE > 40, "a mountain stands up");
                    }
                }
            }
            check(helper, seas >= 2 && peaks >= 2 && seas + peaks < 21 * 21 / 3, "now and then: seas " + seas + ", mountains " + peaks);
            for (int bx = -90; bx <= 90; bx++) {
                for (int bz = -90; bz <= 90; bz++) {
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        check(helper, Terrain.free(seed, lot.corner().getX(), lot.corner().getZ(), lot.size()), "nothing built in a sea or up a mountain: "
                                + lot.corner());
                    }
                }
            }
            helper.succeed();
        });

        // in the If itself: each region its biome; a cave in the wilds, hollow, its fire out, the dead's markers in it; a
        // lake holds water
        test("if_has_its_regions_caves_and_lakes", 400, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            check(helper, ifLevel.getChunkSource().getGenerator().getBiomeSource() instanceof RegionBiomes, "the If's biomes are the regions'");
            long seed = ifLevel.getSeed();
            Terrain.Cave cave = null;
            Terrain.Lake lake = null;
            for (int r = 0; r <= 30 && (cave == null || lake == null); r++) {
                for (int bx = -r; bx <= r; bx++) {
                    for (int bz = -r; bz <= r; bz++) {
                        if (Math.max(Math.abs(bx), Math.abs(bz)) != r) {
                            continue;
                        }
                        cave = cave != null ? cave : Terrain.cave(seed, bx, bz);
                        lake = lake != null ? lake : Terrain.lake(seed, bx, bz);
                    }
                }
            }
            check(helper, cave != null && lake != null, "a cave and a lake within thirty blocks");
            BlockPos middle = new BlockPos(cave.x(), cave.floor() + 1, cave.z());
            ifLevel.getChunk(middle);
            Region region = Region.looks(seed, cave.x(), cave.z());       // the biome is the region it looks, edges wandering
            check(helper, ifLevel.getBiome(middle).is(region.biome), "the cave's biome is the wilds': " + ifLevel.getBiome(middle));
            check(helper, ifLevel.getBlockState(middle).is(Blocks.CAMPFIRE), "the fire in the middle of the cave: " + ifLevel.getBlockState(middle));
            check(helper, ifLevel.getBlockState(middle.above(2)).isAir(), "hollow over it");
            check(helper, !ifLevel.getBlockState(new BlockPos(cave.x(), cave.floor() + Terrain.Cave.HEIGHT + 2, cave.z())).isAir(), "rock over the hollow");
            int markers = 0;
            for (BlockPos p : BlockPos.betweenClosed(middle.offset(-4, 0, -4), middle.offset(4, 0, 4))) {
                markers += ifLevel.getBlockState(p).is(ZwBlocks.ZOMBIE_MARKER.get()) ? 1 : 0;
            }
            check(helper, markers >= 3, "the dead waiting in it: " + markers + " markers");
            BlockPos inLake = new BlockPos(lake.x(), lake.water(), lake.z());
            ifLevel.getChunk(inLake);
            check(helper, ifLevel.getBlockState(inLake).is(Blocks.WATER), "the lake holds water: " + ifLevel.getBlockState(inLake));
            check(helper, ifLevel.getBlockState(inLake.above()).isAir(), "and nothing over it");
            helper.succeed();
        });
    }

    /** Registered from {@link #register}: the underground and the remains. */
    static void underground() {
        // the galleries: dug in some patches only, never under an institute; in the If a gallery is hollow at its floor, a
        // way up has its ladder, and below the depths line the biome is the depths'
        test("if_has_galleries_and_ways_down", 600, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            long seed = ifLevel.getSeed();
            int[] stretch = null;
            int[] shaft = null;
            for (int r = 0; r <= 40 && (stretch == null || shaft == null); r++) {
                for (int gx = -r; gx <= r; gx++) {
                    for (int gz = -r; gz <= r; gz++) {
                        if (Math.max(Math.abs(gx), Math.abs(gz)) != r) {
                            continue;
                        }
                        if (stretch == null && dev.nookofthedead.world.Tunnels.dug(seed, gx, gz, true)) {
                            stretch = new int[] {gx, gz};
                        }
                        if (shaft == null && dev.nookofthedead.world.Tunnels.dug(seed, gx, gz, true)
                                && !dev.nookofthedead.world.Tunnels.bloomAt(seed, gx, gz)          // a bloom's vault, not a shaft
                                && Math.floorMod(Town.hash(seed, gx, gz, 133), 100) >= dev.nookofthedead.world.Tunnels.ROOMS
                                && Math.floorMod(Town.hash(seed, gx, gz, 133), 100) < dev.nookofthedead.world.Tunnels.ROOMS
                                        + dev.nookofthedead.world.Tunnels.SHAFTS) {
                            boolean open = true;
                            for (int dx = -2; dx <= 2 && open; dx++) {
                                for (int dz = -2; dz <= 2 && open; dz++) {
                                    open = dev.nookofthedead.world.Tunnels.open(seed, gx * dev.nookofthedead.world.Tunnels.GRID + dx,
                                            gz * dev.nookofthedead.world.Tunnels.GRID + dz);
                                }
                            }
                            if (open) {
                                shaft = new int[] {gx, gz};
                            }
                        }
                    }
                }
            }
            check(helper, stretch != null && shaft != null, "galleries and a way down within forty grid steps");
            int g = dev.nookofthedead.world.Tunnels.GRID;
            BlockPos mid = new BlockPos(stretch[0] * g + g / 2, dev.nookofthedead.world.Tunnels.FLOOR + 1, stretch[1] * g);
            ifLevel.getChunk(mid);
            var midState = ifLevel.getBlockState(mid);
            // dug: open, or what a barricade or a trap leaves there - its scrap bales and corrugated sheets are Nookworks' (the
            // review, 2026-10-25: If 241256 failed on a corrugated sheet across the gallery)
            check(helper, midState.isAir() || midState.is(Blocks.TRIPWIRE) || midState.is(Blocks.OAK_FENCE) || midState.is(Blocks.OAK_PLANKS)
                    || midState.is(Blocks.COBBLESTONE) || midState.is(Blocks.SPRUCE_PLANKS) || midState.is(Blocks.BARREL) || midState.is(Blocks.CARPET.brown())
                    || midState.getBlock() instanceof dev.nookworks.recycling.CorrugatedSheetBlock
                    || dev.nookworks.registry.ModRecycling.SCRAP_BALES.values().stream().anyMatch(bale -> midState.is(bale.get())),
                    "the gallery is dug: " + midState);
            check(helper, ifLevel.getBiome(mid).is(dev.nookofthedead.world.RegionBiomes.DEPTHS), "down there the depths' biome");
            BlockPos ladder = new BlockPos(shaft[0] * g, dev.nookofthedead.world.Tunnels.FLOOR + 5, shaft[1] * g);
            ifLevel.getChunk(ladder);
            check(helper, ifLevel.getBlockState(ladder).is(Blocks.LADDER), "a ladder up the way down: " + ifLevel.getBlockState(ladder));
            // a door in a gallery's wall now and then, a hideout behind it: furnished, a rug down, nobody in it (0.3)
            int[] hideout = null;
            for (int r = 0; r <= 60 && hideout == null; r++) {
                for (int gx = -r; gx <= r && hideout == null; gx++) {
                    for (int gz = -r; gz <= r && hideout == null; gz++) {
                        if (Math.max(Math.abs(gx), Math.abs(gz)) == r && dev.nookofthedead.world.Tunnels.hideoutAt(seed, gx, gz, true)
                                && !dev.nookofthedead.world.Tunnels.sewer(seed, gx, gz)) {
                            hideout = new int[] {gx, gz};
                        }
                    }
                }
            }
            check(helper, hideout != null, "a hideout within sixty grid steps");
            int floor = dev.nookofthedead.world.Tunnels.FLOOR;
            BlockPos door = new BlockPos(hideout[0] * g + 13, floor, hideout[1] * g + 2);
            ifLevel.getChunk(door);
            ifLevel.getChunk(door.south(6));
            check(helper, ifLevel.getBlockState(door).is(Blocks.SPRUCE_DOOR), "the hideout's door in the wall: " + ifLevel.getBlockState(door));
            check(helper, ifLevel.getBlockState(door.south(3).above()).isAir(), "a room behind it");
            int rugs = 0;
            boolean chest = false;
            for (int a = -2; a <= 2; a++) {
                for (int s = 3; s <= 7; s++) {
                    var state = ifLevel.getBlockState(new BlockPos(door.getX() + a, floor, hideout[1] * g + s));
                    rugs += state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS) ? 1 : 0;
                    chest |= state.is(Blocks.CHEST);
                }
            }
            check(helper, rugs >= 12 && chest, "a rug down and a chest: " + rugs + " " + chest);
            check(helper, ifLevel.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                    new net.minecraft.world.phys.AABB(door.south(5)).inflate(3)).isEmpty(), "nobody in it");
            helper.succeed();
        });

        // the If is taller than the overworld (0.3): up to y 447, and from the stratosphere's line up, its own biome
        test("if_reaches_the_stratosphere", 200, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            check(helper, ifLevel.getMaxY() >= 447 && ifLevel.getMinY() == -64, "the If from -64 to 447: " + ifLevel.getMinY() + ".." + ifLevel.getMaxY());
            BlockPos high = new BlockPos(0, 420, 0);
            check(helper, ifLevel.getChunkSource().getGenerator().getBiomeSource() instanceof dev.nookofthedead.world.RegionBiomes regions
                    && regions.biome(0, high.getY() >> 2, 0).is(dev.nookofthedead.world.RegionBiomes.THIN_AIR)
                    && !regions.biome(0, 100 >> 2, 0).is(dev.nookofthedead.world.RegionBiomes.THIN_AIR), "the stratosphere above, not below");
            helper.succeed();
        });

        // a [remains] sign becomes a survivor's bones, slumped against the wall behind it; a [zombie_villager] one an
        // ordinary zombie villager kept for good, curable
        test("remains_and_shut_in_villagers", helper -> {
            ServerLevel level = helper.getLevel();
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(3, y, 4), Blocks.STONE_BRICKS);
            }
            dev.nookofthedead.world.Markers.remains(level, helper.absolutePos(new BlockPos(3, 1, 3)), net.minecraft.core.Direction.NORTH,
                    net.minecraft.util.RandomSource.create(1));
            dev.nookofthedead.world.Markers.make(level, helper.absolutePos(new BlockPos(1, 1, 1)), net.minecraft.core.Direction.NORTH,
                    "[zombie_villager]", net.minecraft.util.RandomSource.create(2));
            var bodies = helper.getEntities(dev.nookofthedead.registry.ZwEntities.REMAINS.get(), new BlockPos(3, 1, 3), 2);
            var villagers = helper.getEntities(net.minecraft.world.entity.EntityTypes.ZOMBIE_VILLAGER, new BlockPos(1, 1, 1), 2);
            try {
                check(helper, bodies.size() == 1 && bodies.getFirst().kind() == dev.nookofthedead.entity.Remains.SITTING
                        && bodies.getFirst().isNoAi(), "slumped against the wall, still");
                check(helper, !bodies.getFirst().hurtServer(level, level.damageSources().generic(), 5) && bodies.getFirst().isAlive(),
                        "nothing but a player's blow breaks it");
                check(helper, villagers.size() == 1 && villagers.getFirst().isPersistenceRequired()
                        && !(villagers.getFirst() instanceof dev.nookofthedead.entity.Mutated), "an ordinary zombie villager, kept");
                check(helper, villagers.getFirst().getVehicle() == null && !villagers.getFirst().isBaby(), "an adult, riding nothing");
                check(helper, !bodies.getFirst().canBeLeashed(), "no lead on the dead");
                bodies.getFirst().kill(level);
                check(helper, bodies.getFirst().isRemoved(), "/kill takes it away");
            } finally {
                bodies.forEach(net.minecraft.world.entity.Entity::discard);
                villagers.forEach(net.minecraft.world.entity.Entity::discard);
            }
            helper.succeed();
        });

        // every building of the town has floors under its roof for someone's remains to lie on
        test("buildings_have_floors_for_the_remains", helper -> {
            var manager = helper.getLevel().getServer().getStructureTemplateManager();
            for (String name : new String[] {"s1", "s5", "s12", "s17", "m3", "xs2", "xs9"}) {
                var template = manager.get(dev.nookofthedead.NookOfTheDead.id("town/" + name)).orElseThrow();
                int n = dev.nookofthedead.world.Markers.floors(template).size();
                check(helper, n > 3, name + " has floors indoors: " + n);
            }
            helper.succeed();
        });


        // the town does not repeat itself: in a block, a building comes twice only where its quarter has nothing else to give
        test("town_varies_its_buildings", 600, helper -> {
            long seed = 0x5EED5L;
            int repeats = 0;
            int buildings = 0;
            for (int bx = -40; bx < 40; bx++) {
                for (int bz = -40; bz < 40; bz++) {
                    java.util.Set<net.minecraft.resources.Identifier> seen = new java.util.HashSet<>();
                    for (var lot : Town.lots(seed, bx, bz)) {
                        if (lot.use() == Town.Use.BUILDING && lot.building().isPresent()) {
                            buildings++;
                            repeats += seen.add(lot.building().get()) ? 0 : 1;
                        }
                    }
                }
            }
            check(helper, buildings > 1000 && repeats < buildings / 8, "repeats in a block: " + repeats + " of " + buildings);
            helper.succeed();
        });



        // a data pack's building joins a district's list after the mod's own, and goes again when the pack does
        test("data_packs_add_buildings", helper -> {
            var own = District.CENTRE.sBuildings;
            var bakery = new District.Weighted(net.minecraft.resources.Identifier.parse("mypack:town/bakery"), 3);
            try {
                District.extend(java.util.Map.of(District.CENTRE, java.util.Map.of(dev.nookofthedead.world.Buildings.Kind.S, java.util.List.of(bakery))));
                check(helper, District.CENTRE.sBuildings.size() == own.size() + 1 && District.CENTRE.sBuildings.getLast().equals(bakery)
                        && District.RESIDENTIAL.sBuildings.stream().noneMatch(bakery::equals), "the bakery in the centre only");
            } finally {
                District.extend(java.util.Map.of());
                Town.forget();
            }
            check(helper, District.CENTRE.sBuildings.equals(own), "gone with its pack");
            helper.succeed();
        });

        // now and then a block of the country is a zombie village of the game's own houses, set down on the ground; the
        // templates it and the ruined portals take are all there

        test("if_zombie_villages_stand_in_the_country", 600, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            for (var id : dev.nookofthedead.world.Vanilla.templates()) {
                check(helper, server.getStructureTemplateManager().get(id).isPresent(), "the game has " + id);
            }
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            long seed = ifLevel.getSeed();
            int[] found = null;
            for (int r = 0; r <= 48 && found == null; r++) {
                for (int bx = -r; bx <= r && found == null; bx++) {
                    for (int bz = -r; bz <= r && found == null; bz++) {
                        if (Math.max(Math.abs(bx), Math.abs(bz)) == r && dev.nookofthedead.world.Vanilla.villageAt(seed, bx, bz)) {
                            found = new int[] {bx, bz};
                        }
                    }
                }
            }
            check(helper, found != null, "a zombie village within forty-eight blocks of the grid");
            var lots = Town.lots(seed, found[0], found[1]);
            check(helper, lots.size() >= 4 && lots.stream().allMatch(l -> l.building().map(dev.nookofthedead.world.Vanilla::village).orElse(false)),
                    "the game's houses round a meeting point: " + lots.size());
            int built = 0;
            for (var lot : lots) {
                ifLevel.getChunk(lot.corner().offset(lot.size() / 2, 0, lot.size() / 2));
                for (int dx = 0; dx < lot.size(); dx++) {
                    for (int dz = 0; dz < lot.size(); dz++) {
                        for (int dy = 0; dy <= 6; dy++) {
                            var state = ifLevel.getBlockState(lot.corner().offset(dx, dy, dz));
                            if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.OAK_PLANKS)
                                    || state.is(Blocks.COBWEB) || state.is(Blocks.OAK_LOG)) {
                                built++;
                            }
                        }
                    }
                }
            }
            check(helper, built > 100, "the houses stand: " + built + " blocks of them");
            helper.succeed();
        });
    }

    private RegionTests() {}
}
