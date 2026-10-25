package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.List;
import java.util.Optional;

import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import dev.nookofthedead.world.FallenTownGenerator;
import dev.nookofthedead.world.Markers;
import dev.nookofthedead.world.Town;
import dev.nookofthedead.world.ZombieMarkerBlock;
import dev.nookofthedead.registry.ZwBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** The fallen town: its lots, its buildings and their markers, and the If made over as it. */
final class TownTests {
    static void register() {
        // every lot of a block within its lines, none on another, no street or alley under one wherever the streets step;
        // and all three layouts come up - rows back to back, a ring round a court, one M building
        test("town_lots_fill_their_blocks", helper -> {
            long seed = 20261013L;
            int rings = 0;
            int mediums = 0;
            int rows = 0;
            for (int bx = -8; bx <= 8; bx++) {
                for (int bz = -8; bz <= 8; bz++) {
                    if (dev.nookofthedead.world.Region.of(seed, bx, bz) != dev.nookofthedead.world.Region.CITY
                            || dev.nookofthedead.world.Capital.near(seed, bx * Town.BLOCK, bz * Town.BLOCK, Town.BLOCK, 2)) {
                        continue;                                       // the town's own blocks (the country: RegionTests)
                    }
                    List<Town.Lot> lots = Town.lots(seed, bx, bz);
                    check(helper, !lots.isEmpty(), "a block holds lots");
                    if (lots.stream().anyMatch(l -> l.size() == Town.COURT)) {
                        rings++;
                    } else if (lots.size() == 1 && lots.getFirst().size() == Town.M_LOT) {
                        mediums++;
                    } else {
                        rows++;
                    }
                    if (Math.abs(bx) > 3 || Math.abs(bz) > 3) {
                        continue;                                       // the cells looked at one by one near the middle only
                    }
                    java.util.Set<Long> taken = new java.util.HashSet<>();
                    for (Town.Lot lot : lots) {
                        int x0 = lot.corner().getX();
                        int z0 = lot.corner().getZ();
                        check(helper, x0 >= bx * Town.BLOCK - Town.JOG && x0 + lot.size() <= (bx + 1) * Town.BLOCK + Town.JOG
                                && z0 >= bz * Town.BLOCK - Town.JOG && z0 + lot.size() <= (bz + 1) * Town.BLOCK + Town.JOG,
                                "the lot at " + lot.corner() + " within its block's lines, give or take a jog");
                        for (int x = x0; x < x0 + lot.size(); x++) {
                            for (int z = z0; z < z0 + lot.size(); z++) {
                                check(helper, taken.add(BlockPos.asLong(x, 0, z)), "the lot at " + lot.corner() + " overlaps another");
                                check(helper, Town.ground(seed, x, z) == Town.Ground.LOT, "no street or alley under the lot at " + lot.corner()
                                        + " (at " + x + "," + z + ")");
                            }
                        }
                    }
                }
            }
            check(helper, rings > 0 && mediums > 0 && rows > rings, "rows mostly, rings and M blocks among them: " + rows + " / " + rings + " / " + mediums);
            helper.succeed();
        });

        // the streets step aside and go on (the user, 2026-10-13: "道路も直線ばかりじゃなくて、こんなふうにジグザク"): from one
        // crossing to the next a street mostly stands off its line differently, stepping across halfway; and it never breaks:
        // all along, the middle of it is street or alley
        test("streets_step_aside_and_go_on", helper -> {
            long seed = 20261013L;
            int stretches = 0;
            int stepping = 0;
            for (boolean northSouth : new boolean[] {true, false}) {
                for (int line = -6; line <= 6; line++) {
                    for (int at = -6; at <= 6; at++) {
                        if (Town.line(seed, northSouth, line, at) == Town.Ground.LOT) {
                            continue;                                   // no line here: the open country
                        }
                        stretches++;
                        int a = Town.offset(seed, northSouth, line, at);
                        int b = Town.offset(seed, northSouth, line, at + 1);
                        stepping += a != b ? 1 : 0;
                        check(helper, Math.abs(a) <= Town.JOG, "within its jog");
                        for (int along = at * Town.BLOCK; along < (at + 1) * Town.BLOCK; along++) {
                            int[] band = Town.band(seed, northSouth, line, along);
                            for (int across = band[0]; across < band[1]; across++) {
                                int x = northSouth ? across : along;
                                int z = northSouth ? along : across;
                                check(helper, Town.ground(seed, x, z) != Town.Ground.LOT, "the street of line " + line + " goes on at " + x + "," + z);
                            }
                        }
                    }
                }
            }
            check(helper, stepping > stretches / 2, "most stretches step: " + stepping + " of " + stretches);
            helper.succeed();
        });

        // the ground changes over hundreds of blocks, nothing smaller (the user, 2026-10-13: "もっと広い範囲でゆったりと高さが
        // 変わるイメージ"; "直線的だったり、長方形で一段高くなったりしていると、いかにも生成の都合にみえる"): two blocks at most
        // across a lot's width; the open ground beside a levelled lot at that lot's level (its steps away from the edge),
        // never more than one step between two columns of it side by side
        test("town_ground_is_broad_and_meets_its_lots", helper -> {
            long seed = 20261013L;
            for (int x = -800; x <= 800; x += 8) {
                for (int z = -800; z <= 800; z += 8) {
                    if (dev.nookofthedead.world.Region.at(seed, x, z) != dev.nookofthedead.world.Region.CITY || dev.nookofthedead.world.Region.at(seed, x + 32, z) != dev.nookofthedead.world.Region.CITY || dev.nookofthedead.world.Region.at(seed, x, z + 32) != dev.nookofthedead.world.Region.CITY) {
                        continue;                                       // the town's ground (the wilds' hills: RegionTests)
                    }
                    double h = Town.height(seed, x, z);
                    check(helper, Math.abs(Town.height(seed, x + 32, z) - h) <= 2.2 && Math.abs(Town.height(seed, x, z + 32) - h) <= 2.2,
                            "broad at " + x + "," + z);
                }
            }
            int edges = 0;
            int flush = 0;
            for (int x0 = -240; x0 < 240; x0 += 16) {
                for (int z0 = -240; z0 < 240; z0 += 16) {
                    Town.Plan plan = Town.plan(seed, x0 - 1, z0 - 1);
                    for (int a = 1; a <= 16; a++) {
                        for (int b = 1; b <= 16; b++) {
                            if (plan.kind()[a][b] != Town.Ground.LOT || fixed(plan, a, b) || plan.paved()[a][b] || plan.water()[a][b] > 0
                                    || dev.nookofthedead.world.Region.at(seed, x0 - 1 + a, z0 - 1 + b) != dev.nookofthedead.world.Region.CITY) {
                                continue;
                            }
                            Integer only = null;
                            boolean mixed = false;
                            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                int na = a + d[0];
                                int nb = b + d[1];
                                if (fixed(plan, na, nb)) {
                                    int lv = plan.top()[na][nb];
                                    mixed |= only != null && only != lv;
                                    only = only == null ? lv : only;
                                } else if (plan.kind()[na][nb] == Town.Ground.LOT && !plan.paved()[na][nb] && plan.water()[na][nb] == 0) {
                                    // (a canal's water stands in its banks: a wall down to it, as the town's own drops)
                                    check(helper, Math.abs(plan.top()[na][nb] - plan.top()[a][b]) <= 1,
                                            "one step at most at " + (x0 - 1 + a) + "," + (z0 - 1 + b) + ": " + plan.top()[a][b] + " by " + plan.top()[na][nb] + " at " + (x0 - 1 + na) + "," + (z0 - 1 + nb) + " water " + plan.water()[na][nb] + " " + dev.nookofthedead.world.Region.at(seed, x0 - 1 + na, z0 - 1 + nb));
                                }
                            }
                            if (only != null && !mixed) {
                                edges++;
                                flush += plan.top()[a][b] == only ? 1 : 0;
                                check(helper, Math.abs(plan.top()[a][b] - only) <= 1, "close to its lot at " + (x0 - 1 + a) + "," + (z0 - 1 + b));
                            }
                        }
                    }
                }
            }
            check(helper, edges > 500 && flush >= edges * 0.95, "the open ground meets its lots: " + flush + " of " + edges);
            helper.succeed();
        });

        // the ground rises and falls gently (the user: "多少起伏があったり"): some blocks' worth between high and low, never
        // more than a half step between two columns side by side, a lot's level within half a block of its street's
        test("town_ground_rises_and_falls_gently", helper -> {
            long seed = 20261013L;
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            for (int x = -600; x <= 600; x += 3) {
                for (int z = -600; z <= 600; z += 3) {
                    if (dev.nookofthedead.world.Region.at(seed, x, z) != dev.nookofthedead.world.Region.CITY || dev.nookofthedead.world.Region.at(seed, x + 1, z) != dev.nookofthedead.world.Region.CITY || dev.nookofthedead.world.Region.at(seed, x, z + 1) != dev.nookofthedead.world.Region.CITY) {
                        continue;
                    }
                    double h = Town.height(seed, x, z);
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                    check(helper, Math.abs(Town.streetHeight(seed, x + 1, z) - Town.streetHeight(seed, x, z)) <= 0.5
                            && Math.abs(Town.streetHeight(seed, x, z + 1) - Town.streetHeight(seed, x, z)) <= 0.5, "a half step at most at " + x + "," + z);
                }
            }
            check(helper, hi - lo >= 4 && hi - Town.SURFACE <= Town.HILL && Town.SURFACE - lo <= Town.HILL, "rises and falls: " + lo + " to " + hi);
            for (int bx = -3; bx <= 3; bx++) {
                for (int bz = -3; bz <= 3; bz++) {
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        if (lot.use() != Town.Use.BUILDING || lot.size() == Town.COURT || Town.rural(dev.nookofthedead.world.Region.of(seed, bx, bz))) {
                            continue;
                        }
                        Direction front = lot.front();
                        int fx = lot.corner().getX() + lot.size() / 2 + front.getStepX() * (lot.size() / 2 + 1);
                        int fz = lot.corner().getZ() + lot.size() / 2 + front.getStepZ() * (lot.size() / 2 + 1);
                        check(helper, Math.abs(lot.corner().getY() - Town.streetHeight(seed, fx, fz)) <= 0.5,
                                "the lot at " + lot.corner() + " level with the street before it");
                    }
                }
            }
            helper.succeed();
        });

        // streets are not a plain grid (the user, 2026-10-13: "ちょっと碁盤の目すぎる"): some stretches only alleys, so streets
        // end at T junctions; every fourth line goes the whole way through
        test("town_streets_are_not_a_plain_grid", helper -> {
            long seed = 20261013L;
            int alleys = 0;
            int streets = 0;
            int throughLines = 0;
            for (int line = -12; line <= 12; line++) {
                boolean through = true;
                for (int along = -12; along <= 12; along++) {
                    if (dev.nookofthedead.world.Region.of(seed, line - 1, along) != dev.nookofthedead.world.Region.CITY || dev.nookofthedead.world.Region.of(seed, line, along) != dev.nookofthedead.world.Region.CITY) {
                        continue;
                    }
                    if (Town.open(seed, true, line, along)) {
                        streets++;
                    } else {
                        alleys++;
                        through = false;
                    }
                }
                throughLines += through ? 1 : 0;
            }
            check(helper, alleys > streets / 10 && alleys < streets, "some stretches alleys, most streets: " + alleys + " / " + streets);
            check(helper, throughLines >= 25 / Town.THROUGH, "every fourth line goes through: " + throughLines);
            check(helper, Town.half(seed, true, 0, 0) == (Town.through(seed, true, 0) ? Town.WIDE_HALF : 3), "the town's main streets are wide");
            check(helper, Town.ground(seed, 36, 36) == Town.Ground.LOT, "the middle of a block is lots");
            helper.succeed();
        });

        // every building faces a street or an alley, right at its edge (an M building's lot one in); most face a street
        // (r1: little ones faced the gap to a neighbour; the user: back to back, built up to the street)
        test("buildings_face_a_way_out", helper -> {
            long seed = 20261013L;
            int seen = 0;
            int onStreet = 0;
            for (int bx = -8; bx <= 8; bx++) {
                for (int bz = -8; bz <= 8; bz++) {
                    if (Town.rural(dev.nookofthedead.world.Region.of(seed, bx, bz))) {
                        continue;                                       // a farmhouse may stand with its back to a lane
                    }
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        if (lot.use() != Town.Use.BUILDING || lot.size() == Town.COURT) {
                            continue;
                        }
                        seen++;
                        Direction front = lot.front();
                        int cx = lot.corner().getX() + lot.size() / 2;
                        int cz = lot.corner().getZ() + lot.size() / 2;
                        // out from its front, past the paving where its stretch of street stands back, to the street
                        // - or the footpath where no street runs any more (Town.gone)
                        Town.Ground out = Town.Ground.LOT;
                        boolean footpath = false;
                        for (int reach = lot.size() / 2 + 1; reach <= lot.size() / 2 + Town.BLOCK / 2 && out == Town.Ground.LOT && !footpath; reach++) {
                            out = Town.ground(seed, cx + front.getStepX() * reach, cz + front.getStepZ() * reach);
                            footpath = Town.track(seed, cx + front.getStepX() * reach, cz + front.getStepZ() * reach);
                        }
                        check(helper, out != Town.Ground.LOT || footpath, "the lot at " + lot.corner() + " (" + lot.size() + ") faces " + front + " onto no way out");
                        onStreet += out == Town.Ground.STREET ? 1 : 0;
                    }
                }
            }
            check(helper, seen > 0 && onStreet > seen * 0.85, "most buildings face a street: " + onStreet + " of " + seen);
            helper.succeed();
        });

        // a variant is only what its changed copy differs in - a post taken away (air), a lantern and a marker added - and
        // set down over its building it makes the copy (the user, 2026-10-13: variants built separately)
        test("a_variant_is_what_differs_from_its_building", helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos at = helper.absolutePos(new BlockPos(0, 1, 1));
            BlockPos there = helper.absolutePos(new BlockPos(4, 1, 1));
            for (BlockPos p : BlockPos.betweenClosed(at, at.offset(2, 0, 2))) {
                level.setBlockAndUpdate(p, Blocks.STONE_BRICKS.defaultBlockState());
            }
            level.setBlockAndUpdate(at.offset(1, 1, 1), Blocks.OAK_LOG.defaultBlockState());
            level.setBlockAndUpdate(at.offset(0, 1, 0), Blocks.BARREL.defaultBlockState());
            var keep = List.of(Blocks.STRUCTURE_VOID);
            StructureTemplate building = new StructureTemplate();
            building.fillFromWorld(level, at, new net.minecraft.core.Vec3i(3, 3, 3), false, keep);
            level.setBlockAndUpdate(at.offset(1, 1, 1), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(at.offset(2, 1, 2), Blocks.LANTERN.defaultBlockState());
            level.setBlockAndUpdate(at.offset(1, 1, 0), Blocks.OAK_SIGN.defaultBlockState());
            if (level.getBlockEntity(at.offset(1, 1, 0)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                sign.updateText(text -> text.asMutable().setLine(0, net.minecraft.network.chat.Component.literal("[loot food]")).asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
            }
            StructureTemplate copy = new StructureTemplate();
            copy.fillFromWorld(level, at, new net.minecraft.core.Vec3i(3, 3, 3), false, keep);
            StructureTemplate patch = dev.nookofthedead.world.Variants.patch(building, copy,
                    level.holderLookup(net.minecraft.core.registries.Registries.BLOCK));
            check(helper, patch != null, "the copy differs");
            check(helper, dev.nookofthedead.world.Variants.changed(patch).equals(java.util.Set.of(new BlockPos(1, 1, 1),
                    new BlockPos(2, 1, 2), new BlockPos(1, 1, 0))), "only what differs: " + dev.nookofthedead.world.Variants.changed(patch));
            check(helper, Markers.of(patch).stream().anyMatch(m -> m.text().startsWith("[loot")), "the variant's marker is read");
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
            building.placeInWorld(level, there, there, settings, RandomSource.create(1), 2);
            patch.placeInWorld(level, there, there, settings, RandomSource.create(1), 2);
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(2, 2, 2))) {
                check(helper, level.getBlockState(there.offset(p)).equals(level.getBlockState(at.offset(p))),
                        "the building with its variant is the copy at " + p + ": " + level.getBlockState(there.offset(p)));
            }
            check(helper, dev.nookofthedead.world.Variants.patch(building, building, level.holderLookup(net.minecraft.core.registries.Registries.BLOCK)) == null,
                    "nothing differs from itself");
            helper.succeed();
        });

        // the town has its quarters: every kind comes up, each over a run of neighbouring blocks, the same for the same seed
        test("town_quarters_come_in_patches", helper -> {
            long seed = 20261013L;
            java.util.EnumSet<dev.nookofthedead.world.District> seen = java.util.EnumSet.noneOf(dev.nookofthedead.world.District.class);
            int same = 0;
            int pairs = 0;
            for (int bx = -20; bx <= 20; bx++) {
                for (int bz = -20; bz <= 20; bz++) {
                    dev.nookofthedead.world.District d = dev.nookofthedead.world.District.of(seed, bx, bz);
                    seen.add(d);
                    check(helper, d == dev.nookofthedead.world.District.of(seed, bx, bz), "a block's quarter is fixed by the seed");
                    pairs++;
                    if (d == dev.nookofthedead.world.District.of(seed, bx + 1, bz)) {
                        same++;
                    }
                }
            }
            check(helper, seen.size() == dev.nookofthedead.world.District.values().length, "every kind of quarter comes up: " + seen);
            check(helper, same > pairs * 0.6, "a quarter runs over neighbouring blocks (" + same + " of " + pairs + " alike)");
            helper.succeed();
        });

        // the places to find supplies in - tool shops, warehouses, what were shelters - come now and then, not often (the user,
        // 2026-10-13: "それなりにレアにしたい"): some in a town of 25 x 25 blocks, a few in a hundred S buildings
        test("supply_places_are_rare", helper -> {
            long seed = 20261013L;
            java.util.Set<Identifier> rareS = new java.util.HashSet<>();
            java.util.Set<Identifier> rareM = new java.util.HashSet<>();
            for (dev.nookofthedead.world.District d : dev.nookofthedead.world.District.values()) {
                d.rareS.forEach(w -> rareS.add(w.id()));
                d.rareM.forEach(w -> rareM.add(w.id()));
            }
            int s = 0;
            int sRare = 0;
            int m = 0;
            int mRare = 0;
            for (int bx = -12; bx <= 12; bx++) {
                for (int bz = -12; bz <= 12; bz++) {
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        if (lot.use() != Town.Use.BUILDING || lot.building().isEmpty() || lot.size() == Town.COURT) {
                            continue;
                        }
                        if (lot.size() == Town.S_LOT) {
                            s++;
                            sRare += rareS.contains(lot.building().get()) ? 1 : 0;
                        } else if (lot.size() == Town.M_LOT) {
                            m++;
                            mRare += rareM.contains(lot.building().get()) ? 1 : 0;
                        }
                    }
                }
            }
            check(helper, sRare > 0 && sRare < s * 0.07, "tool shops and storehouses now and then: " + sRare + " of " + s);
            check(helper, mRare > 0 && mRare < m * 0.4, "warehouses and old shelters now and then: " + mRare + " of " + m);
            helper.succeed();
        });

        // the AI's drafts from the build world are in the mod, the right size, their marker signs read
        test("town_templates_load_with_their_markers", helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            for (dev.nookofthedead.world.District district : dev.nookofthedead.world.District.values()) {
                for (var list : List.of(district.sBuildings, district.mBuildings, district.xsBuildings, district.courts, district.rareS,
                        district.rareM)) {
                    for (var w : list) {
                        Identifier id = w.id();
                        if (id.equals(dev.nookofthedead.world.Desert.PYRAMID) || id.equals(dev.nookofthedead.world.Market.ID)) {
                            continue;                                   // the game's own piece, or drawn by code: no template
                        }
                        Optional<StructureTemplate> t = server.getStructureTemplateManager().get(id);
                        if (dev.nookofthedead.world.Vanilla.is(id)) {   // the game's: there, and no bigger than its lot
                            int lot = list == district.xsBuildings ? 16 : list == district.courts ? Town.COURT : list == district.mBuildings
                                    || list == district.rareM ? 64 : 32;
                            check(helper, t.isPresent() && t.get().getSize().getX() <= lot && t.get().getSize().getZ() <= lot,
                                    id + " is there and fits a lot of " + lot);
                            continue;
                        }
                        String name = id.getPath().substring("town/".length());
                        int size = name.startsWith("xs") ? 16 : name.startsWith("s") ? 32 : 64;
                        check(helper, t.isPresent() && t.get().getSize().getX() == size && t.get().getSize().getZ() == size,
                                id + " is a " + size + " x " + size + " template");
                    }
                }
            }
            // the research institute: its building and its deep part the same footprint, the Vandal waiting in the deep
            StructureTemplate institute = server.getStructureTemplateManager().get(Town.INSTITUTE).orElseThrow();
            StructureTemplate deep = server.getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElseThrow();
            check(helper, institute.getSize().getX() == 64 && institute.getSize().getZ() == 64 && deep.getSize().getX() == 64
                    && deep.getSize().getZ() == 64, "the institute's two parts stack: " + institute.getSize() + " / " + deep.getSize());
            check(helper, Markers.of(deep).stream().filter(m -> m.text().equals("[vandal]")).count() == 1, "one [vandal] in the deep");
            check(helper, Markers.of(deep).stream().anyMatch(m -> m.text().equals("[brew]") && m.container() != null), "its brewing stands marked");
            StructureTemplate s3 = server.getStructureTemplateManager().get(dev.nookofthedead.NookOfTheDead.id("town/s3")).orElseThrow();
            List<Markers.Marker> markers = Markers.of(s3);
            check(helper, markers.stream().anyMatch(m -> m.text().startsWith("[zombie")), "S3's [zombie] sign is read");
            check(helper, markers.stream().anyMatch(m -> m.text().startsWith("[diary")), "S3's [diary] sign is read");
            helper.succeed();
        });

        // remains laid along the way with room, never across hay or a bed; sat up where there is no room to lie (the user,
        // 2026-10-18: bodies sunk into hay bales and beds)
        test("remains_lie_clear_of_what_stands_by", helper -> {
            ServerLevel level = helper.getLevel();
            for (int k = 0; k < 8; k++) {
                helper.setBlock(new BlockPos(0, 1, 2), Blocks.HAY_BLOCK);    // hay west and east, open north and south
                helper.setBlock(new BlockPos(2, 1, 2), Blocks.HAY_BLOCK);
                Markers.remains(level, helper.absolutePos(new BlockPos(1, 1, 2)), Direction.SOUTH, RandomSource.create(k));
            }
            List<dev.nookofthedead.entity.Remains> bodies = helper.getLevel().getEntitiesOfClass(dev.nookofthedead.entity.Remains.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(1, 1, 2))).inflate(0.5));
            check(helper, bodies.size() == 8, "eight bodies laid: " + bodies.size());
            for (dev.nookofthedead.entity.Remains body : bodies) {
                float yaw = net.minecraft.util.Mth.wrapDegrees(body.getYRot());
                check(helper, body.kind() == dev.nookofthedead.entity.Remains.LYING, "with room along it, the body lies");
                check(helper, Math.abs(Math.abs(yaw) - 90.0F) > 60.0F, "it lies along the open way, not across the hay (yaw " + yaw + ")");
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {                // chests all round: no wall, no room to lie
                helper.setBlock(new BlockPos(4, 1, 4).relative(side), Blocks.CHEST);
            }
            Markers.remains(level, helper.absolutePos(new BlockPos(4, 1, 4)), Direction.SOUTH, RandomSource.create(1));
            check(helper, helper.getLevel().getEntitiesOfClass(dev.nookofthedead.entity.Remains.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(4, 1, 4))).inflate(0.4))
                    .stream().allMatch(b -> b.kind() == dev.nookofthedead.entity.Remains.SITTING), "boxed in, the body sits");
            helper.succeed();
        });

        // the institute's refuge (Refuge): found for any institute's block, inside its deep part, the size of its rooms; none
        // where there is no institute
        test("refuge_lies_in_the_institutes_deep_part", helper -> {
            ServerLevel level = helper.getLevel();
            long seed = level.getSeed();
            int found = 0;
            for (int bx = -12; bx <= 12 && found < 3; bx++) {
                for (int bz = -12; bz <= 12 && found < 3; bz++) {
                    net.minecraft.world.level.levelgen.structure.BoundingBox box = dev.nookofthedead.world.Refuge.box(level, bx, bz);
                    if (!dev.nookofthedead.world.Institute.at(seed, bx, bz)) {
                        check(helper, box == null, "no refuge where there is no institute");
                        continue;
                    }
                    found++;
                    check(helper, box != null, "an institute has its refuge");
                    Town.Lot lot = Town.lots(seed, bx, bz).getFirst();
                    StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElseThrow();
                    BlockPos under = Town.deepOrigin(lot, deep);
                    net.minecraft.world.level.levelgen.structure.BoundingBox whole = deep.getBoundingBox(
                            new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(lot.rotation())
                                    .setMirror(lot.mirror()), under);
                    // its lower floor below the deep part's own blocks (RefugeRooms, 2026-10-23)
                    var deeper = new net.minecraft.world.level.levelgen.structure.BoundingBox(whole.minX(), whole.minY() + dev.nookofthedead.world.RefugeRooms.FLOOR,
                            whole.minZ(), whole.maxX(), whole.maxY(), whole.maxZ());
                    check(helper, deeper.isInside(new BlockPos(box.minX(), box.minY(), box.minZ()))
                            && deeper.isInside(new BlockPos(box.maxX(), box.maxY(), box.maxZ())), "the refuge lies in the deep part and under it");
                    int across = Math.min(box.getXSpan(), box.getZSpan());
                    int along = Math.max(box.getXSpan(), box.getZSpan());
                    check(helper, across == 26 && along == 49 && box.getYSpan() == 16, "the refuge is the size of its rooms: " + box);
                }
            }
            check(helper, found > 0, "an institute near the middle to look at");
            helper.succeed();
        });

        // Nookworks' Iron Pot on its Grill over a campfire, as one block, however the builder piled them (the user,
        // 2026-10-18: pots straight on the fire, the grill set beside)
        test("kitchen_sets_pots_on_their_grills", helper -> {
            net.minecraft.world.level.block.Block pot = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nookworks", "iron_pot"));
            net.minecraft.world.level.block.Block grill = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nookworks", "grill"));
            BlockPos a = new BlockPos(0, 64, 0);
            BlockPos b = new BlockPos(5, 64, 0);
            BlockPos c = new BlockPos(9, 64, 0);
            List<StructureTemplate.StructureBlockInfo> placed = List.of(
                    new StructureTemplate.StructureBlockInfo(a, Blocks.CAMPFIRE.defaultBlockState(), null),
                    new StructureTemplate.StructureBlockInfo(a.above(), pot.defaultBlockState(), null),             // pot on the fire
                    new StructureTemplate.StructureBlockInfo(b, Blocks.CAMPFIRE.defaultBlockState(), null),
                    new StructureTemplate.StructureBlockInfo(b.above(), grill.defaultBlockState(), null),           // grill, grill, pot
                    new StructureTemplate.StructureBlockInfo(b.above(2), grill.defaultBlockState(), null),
                    new StructureTemplate.StructureBlockInfo(b.above(3), pot.defaultBlockState(), null),
                    new StructureTemplate.StructureBlockInfo(c, Blocks.CAMPFIRE.defaultBlockState(), null),
                    new StructureTemplate.StructureBlockInfo(c.above(), grill.defaultBlockState(), null));          // a grill alone
            List<StructureTemplate.StructureBlockInfo> out = Town.KITCHEN.finalizeProcessing(helper.getLevel(), BlockPos.ZERO, BlockPos.ZERO,
                    placed, placed, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings());
            java.util.function.Function<BlockPos, BlockState> at = p -> out.stream().filter(i -> i.pos().equals(p)).findFirst().orElseThrow().state();
            java.util.function.BiPredicate<BlockState, String> flag = (s, name) -> s.getBlock().getStateDefinition().getProperty(name)
                    instanceof net.minecraft.world.level.block.state.properties.BooleanProperty p && s.getValue(p);
            check(helper, at.apply(a.above()).is(pot) && flag.test(at.apply(a.above()), "on_grill"), "a pot on the fire sits on its grill");
            check(helper, at.apply(b.above()).is(pot) && flag.test(at.apply(b.above()), "on_grill"), "a heap comes down to the pot on its grill");
            check(helper, at.apply(b.above(2)).isAir() && at.apply(b.above(3)).isAir(), "nothing left over it");
            check(helper, at.apply(c.above()).is(grill) && flag.test(at.apply(c.above()), "over_fire"), "a grill alone stands astride the fire");
            helper.succeed();
        });

        // each marker as BUILDING.md says: zombies kept, loot in the chest beside, a diary on a lectern, a memo gone
        test("markers_become_what_they_mark", helper -> {
            ServerLevel level = helper.getLevel();
            RandomSource random = RandomSource.create(7);
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
            Markers.make(level, helper.absolutePos(new BlockPos(2, 1, 1)), Direction.SOUTH, "[loot food]", random);
            Markers.make(level, helper.absolutePos(new BlockPos(5, 1, 5)), Direction.SOUTH, "[zombie 3]", random);
            Markers.make(level, helper.absolutePos(new BlockPos(1, 1, 5)), Direction.SOUTH, "[elite]", random);
            Markers.make(level, helper.absolutePos(new BlockPos(3, 1, 3)), Direction.NORTH, "[diary]", random);
            Markers.make(level, helper.absolutePos(new BlockPos(5, 1, 1)), Direction.EAST, "[loot medical]", random);
            Markers.make(level, helper.absolutePos(new BlockPos(3, 1, 5)), Direction.SOUTH, "[memo a well here]", random);
            check(helper, helper.getBlockState(new BlockPos(2, 1, 1)).isAir(), "the loot sign is gone");
            check(helper, helper.getBlockEntity(new BlockPos(1, 1, 1), RandomizableContainerBlockEntity.class).getLootTable() == Markers.table("food"),
                    "the chest beside it has the food loot");
            BlockState zombies = helper.getBlockState(new BlockPos(5, 1, 5));
            check(helper, zombies.is(ZwBlocks.ZOMBIE_MARKER.get()) && zombies.getValue(ZombieMarkerBlock.COUNT) == 3
                    && !zombies.getValue(ZombieMarkerBlock.ELITE), "[zombie 3] keeps three zombies");
            check(helper, helper.getBlockState(new BlockPos(1, 1, 5)).getValue(ZombieMarkerBlock.ELITE), "[elite] keeps a fallen player");
            check(helper, helper.getBlockEntity(new BlockPos(3, 1, 3), LecternBlockEntity.class).hasBook(), "[diary] is a lectern with a book");
            check(helper, helper.getBlockState(new BlockPos(5, 1, 1)).is(Blocks.CHEST)
                    && helper.getBlockEntity(new BlockPos(5, 1, 1), RandomizableContainerBlockEntity.class).getLootTable() == Markers.table("medical"),
                    "[loot medical] with nothing beside it is a chest of its own");
            check(helper, helper.getBlockState(new BlockPos(3, 1, 5)).isAir(), "a memo leaves nothing");
            helper.succeed();
        });

        // made by the generator, a building's markers are what they mark: a diary on its lectern, zombies kept (r1: the
        // sign's block entity stayed under them, and none of them worked)
        test("generated_markers_are_what_they_mark", 400, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            long seed = ifLevel.getSeed();
            for (int bx = 60; bx <= 90; bx++) {
                for (int bz = 60; bz <= 62; bz++) {
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        Optional<StructureTemplate> template = lot.building().flatMap(id -> server.getStructureTemplateManager().get(id));
                        dev.nookofthedead.world.District district = dev.nookofthedead.world.District.of(seed, bx, bz);
                        if (template.isEmpty() || dev.nookofthedead.world.Desert.on(seed, lot) || dev.nookofthedead.world.Traces.ruin(lot,
                                district) != dev.nookofthedead.world.Traces.Ruin.STANDING || dev.nookofthedead.world.Traces.fall(lot, district) != null
                                || lot.building().flatMap(id -> dev.nookofthedead.world.Variants.pick(server.getStructureTemplateManager(), id,
                                        lot.seed())).isPresent()) {
                            // worn or fallen in, or changed by a variant over it: its markers may be gone with it, as they
                            // should (one run in a few failed on such a lot, the marker rightly not set down where its floor fell)
                            continue;
                        }
                        var markers = Town.markersOn(lot, template.get());
                        var diary = markers.stream().filter(m -> m.getValue().startsWith("[diary")).findFirst();
                        // the dead's own marker, not a [zombie_villager] (set down as the villager itself, its sign gone: s18
                        // failed so, 2026-10-25)
                        var zombie = markers.stream().filter(m -> m.getValue().equals("[zombie]") || m.getValue().startsWith("[zombie ")).findFirst();
                        if (diary.isEmpty() || zombie.isEmpty()) {
                            continue;
                        }
                        var box = lot.footprint();
                        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                                ifLevel.getChunk(cx, cz);
                            }
                        }
                        BlockPos lectern = diary.get().getKey();
                        check(helper, ifLevel.getBlockEntity(lectern) instanceof LecternBlockEntity l && l.hasBook(),
                                "the diary's lectern at " + lectern + " has its book: " + ifLevel.getBlockState(lectern) + " " + ifLevel.getBlockEntity(lectern));
                        BlockPos kept = zombie.get().getKey();
                        check(helper, ifLevel.getBlockState(kept).is(ZwBlocks.ZOMBIE_MARKER.get())
                                && ifLevel.getBlockEntity(kept) instanceof dev.nookofthedead.world.ZombieMarkerBlockEntity,
                                "the zombies' marker at " + kept + " is one, with its block entity: " + ifLevel.getBlockState(kept) + " "
                                + ifLevel.getBlockEntity(kept) + " (seed " + seed + ", " + lot.building() + " at " + lot.corner() + ")");
                        helper.succeed();
                        return;
                    }
                }
            }
            throw helper.assertionException(net.minecraft.network.chat.Component.literal("no building with a diary and zombies found"));
        });

        // a [loot] marker and its chest in different chunks, the marker's made first: the chest is filled, no chest of the
        // marker's own appears (r1: one in eight was split so, and its chest stayed empty beside a new one)
        test("loot_marker_fills_its_chest_across_chunks", 400, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            long seed = ifLevel.getSeed();
            for (int bx = -90; bx >= -130; bx--) {
                for (int bz = 60; bz <= 64; bz++) {
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        Optional<StructureTemplate> template = lot.building().flatMap(id -> server.getStructureTemplateManager().get(id));
                        dev.nookofthedead.world.District district = dev.nookofthedead.world.District.of(seed, bx, bz);
                        if (template.isEmpty() || dev.nookofthedead.world.Desert.on(seed, lot) || dev.nookofthedead.world.Traces.ruin(lot,
                                district) != dev.nookofthedead.world.Traces.Ruin.STANDING || dev.nookofthedead.world.Traces.fall(lot, district) != null
                                || lot.building().flatMap(id -> dev.nookofthedead.world.Variants.pick(server.getStructureTemplateManager(), id,
                                        lot.seed())).isPresent()) {
                            continue;                                   // worn, fallen in or changed: its chest may be gone with it
                        }
                        for (BlockPos[] pair : Town.lootOn(lot, template.get())) {
                            BlockPos marker = pair[0];
                            BlockPos chest = pair[1];
                            if (marker.getX() >> 4 == chest.getX() >> 4 && marker.getZ() >> 4 == chest.getZ() >> 4) {
                                continue;
                            }
                            ifLevel.getChunk(marker.getX() >> 4, marker.getZ() >> 4); // the marker's chunk first
                            ifLevel.getChunk(chest.getX() >> 4, chest.getZ() >> 4);
                            check(helper, ifLevel.getBlockEntity(chest) instanceof RandomizableContainerBlockEntity c && (c.getLootTable() != null || !c.isEmpty()),   // rolled already if the builder left things in it
                                    "the chest beside the marker, at " + chest + ", has its loot: " + ifLevel.getBlockEntity(chest));
                            check(helper, !(ifLevel.getBlockEntity(marker) instanceof RandomizableContainerBlockEntity),
                                    "no chest of the marker's own at " + marker + ": " + ifLevel.getBlockState(marker));
                            helper.succeed();
                            return;
                        }
                    }
                }
            }
            throw helper.assertionException(net.minecraft.network.chat.Component.literal("no loot marker split from its chest found"));
        });

        // the user's S1 has rope stakes (Nookworks' rope knots): the town round one is made, and the knots stand on their
        // posts (they hung the world: a knot read its post from the world-making thread - fixed in Nookworks)
        test("town_with_rope_knots_is_made", 1200, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            long seed = ifLevel.getSeed();
            Identifier s1 = dev.nookofthedead.NookOfTheDead.id("town/s1");
            for (int bx = 40; bx <= 80; bx++) {
                for (int bz = -40; bz <= -36; bz++) {
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        if (!lot.building().map(s1::equals).orElse(false)
                                || dev.nookofthedead.world.Traces.ruin(lot, dev.nookofthedead.world.District.of(seed, bx, bz)) != dev.nookofthedead.world.Traces.Ruin.STANDING) {
                            continue;                                   // (one fallen in may have lost its posts with its corner)
                        }
                        var box = lot.footprint();
                        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                                ifLevel.setChunkForced(cx, cz, true);
                            }
                        }
                        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(),
                                box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
                        helper.succeedWhen(() -> {
                            var knots = ifLevel.getEntitiesOfClass(dev.nookworks.rope.RopeKnotEntity.class, area);
                            check(helper, !knots.isEmpty(), "S1's rope knots are in the town");
                            check(helper, knots.stream().allMatch(k -> k.survives()), "each on its post");
                            // its ropes run to posts here, not to where the house was built (saved before Nookworks kept
                            // a rope's far end by the knot, they ran back to the build world's plot)
                            check(helper, knots.stream().mapToInt(k -> k.spans().size()).sum() > 0, "the ropes are still tied");
                            check(helper, knots.stream().allMatch(k -> k.spans().stream()
                                    .allMatch(p -> dev.nookworks.rope.RopeSpans.isPost(ifLevel.getBlockState(p)))),
                                    "every rope runs to a post in the town");
                            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                                    ifLevel.setChunkForced(cx, cz, false);
                                }
                            }
                        });
                        return;
                    }
                }
            }
            throw helper.assertionException(net.minecraft.network.chat.Component.literal("no S1 found"));
        });

        // a [brew] sign marks the brewing stand beside it, which is left with bottles - a suppressant in about half of them;
        // an empty chest nobody marked is among the ones filled, one with things in it is not
        test("brewing_stands_and_empty_chests_are_filled", helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
            level.setBlockAndUpdate(at, Blocks.BREWING_STAND.defaultBlockState());
            level.setBlockAndUpdate(at.east(), Blocks.OAK_SIGN.defaultBlockState());
            if (level.getBlockEntity(at.east()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                sign.updateText(text -> text.asMutable().setLine(0, net.minecraft.network.chat.Component.literal("[brew]")).asImmutable(),
                        net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
            }
            level.setBlockAndUpdate(at.south(2), Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(at.south(2).east(2), Blocks.BARREL.defaultBlockState());
            if (level.getBlockEntity(at.south(2).east(2)) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity full) {
                full.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
            }
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(level, at, new net.minecraft.core.Vec3i(4, 1, 4), false, List.of(Blocks.STRUCTURE_VOID));
            var brew = Markers.of(template).stream().filter(m -> m.text().equals("[brew]")).toList();
            check(helper, brew.size() == 1 && BlockPos.ZERO.equals(brew.getFirst().container()), "the [brew] sign finds its stand: " + brew);
            // and filled from the stash table, which is there (the review, 2026-10-19: unknown to table(), it fell back to general)
            check(helper, Markers.table("stash").identifier().getPath().equals("chests/stash")
                    && helper.getLevel().getServer().reloadableRegistries().getLootTable(Markers.table("stash"))
                    != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "the empty chests' table is the stash");
            check(helper, Markers.autos(template).equals(List.of(new BlockPos(0, 0, 2))), "only the empty chest is filled at random: "
                    + Markers.autos(template));
            int with = 0;
            for (int i = 0; i < 400; i++) {
                if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand) {
                    stand.clearContent();
                    Markers.brew(stand, RandomSource.create(i));
                    boolean any = false;
                    for (int slot = 0; slot < 3; slot++) {
                        var contents = stand.getItem(slot).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                        any |= contents != null && contents.is(dev.nookofthedead.registry.ZwEffects.SUPPRESSANT_POTION);
                    }
                    with += any ? 1 : 0;
                }
            }
            check(helper, with > 120 && with < 300, "a suppressant left in about half of them: " + with + " of 400");
            helper.succeed();
        });

        // the If, once rolled, is the town: flat, streets at the block edges, its own biome; the ground down to bedrock at
        // y 0 and nothing under it (the user, 2026-10-13: "地下はy0くらいまででよくない？")
        test("if_is_the_fallen_town", 200, helper -> {
            MinecraftServer server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            check(helper, ifLevel != null && ifLevel.getChunkSource().getGenerator() instanceof FallenTownGenerator,
                    "the If's ground is the fallen town's");
            long seed = ifLevel.getSeed();
            // between two of the galleries' crossings: over one, the street may have fallen into a sinkhole (a run in a few
            // failed so, 2026-10-25: cave air where the paving was)
            int z = Town.BLOCK / 2 + dev.nookofthedead.world.Tunnels.GRID / 2;
            int x = 0;
            while (!Town.street(seed, x, z)) {                              // a street on a line between two blocks
                x += Town.BLOCK;
            }
            ifLevel.getChunk(x >> 4, z >> 4);
            BlockState street = ifLevel.getBlockState(new BlockPos(x, (int) Math.floor(Town.streetHeight(seed, x, z)), z));
            dev.nookofthedead.world.District quarter = dev.nookofthedead.world.District.of(seed, Math.floorDiv(x, Town.BLOCK), 0);
            // its quarter's paving - or, where the street has gone back to the ground, what grew over it and the track worn through
            check(helper, java.util.Arrays.stream(quarter.streets).anyMatch(street::is) || street.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                    || street.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK) || street.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
                    || street.is(net.minecraft.world.level.block.Blocks.DIRT_PATH), "the street is its quarter's paving, not " + street);
            check(helper, ifLevel.getBlockState(new BlockPos(x, FallenTownGenerator.BOTTOM, z)).is(Blocks.BEDROCK), "bedrock at y 0");
            check(helper, ifLevel.getBlockState(new BlockPos(x, FallenTownGenerator.BOTTOM + 1, z)).is(Blocks.STONE), "stone on it");
            check(helper, ifLevel.getBlockState(new BlockPos(x, FallenTownGenerator.BOTTOM - 1, z)).isAir()
                    && ifLevel.getBlockState(new BlockPos(x, -64, z)).isAir(), "nothing under it");
            check(helper, ifLevel.getBiome(new BlockPos(1, 70, 1)).is(FallenTownGenerator.BIOME), "the fallen town's biome");
            helper.succeed();
        });
    }

    private TownTests() {}

    /** Whether column ({@code a}, {@code b}) of {@code plan} is under a levelled lot (a building's or a court's). */
    private static boolean fixed(Town.Plan plan, int a, int b) {
        int x = plan.x0() + a;
        int z = plan.z0() + b;
        return plan.lots().stream().anyMatch(l -> !Town.soft(l) && l.covers(x, z));
    }
}
