package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.List;
import java.util.Optional;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Markers;
import dev.nookofthedead.world.Stories;
import dev.nookofthedead.world.Stories.Story;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * What happened in a building (world/Stories, 1.2): each story set down in a building of the town, and what it leaves
 * there seen - the ransacked containers emptied and the doors left open, the held house's doors piled shut and one broken
 * in, the nest's dead and webs, the last stand's body.
 */
final class StoryTests {
    /** The buildings tried, the first with doors, floors and containers of its own taken. */
    static final List<String> BUILDINGS = List.of("s22", "xs10", "s9", "s15", "xs13");

    static void register() {
        int i = 0;
        for (Story story : Story.values()) {
            int slot = i++;
            test("story_" + story.name().toLowerCase(java.util.Locale.ROOT), 300, helper -> told(helper, story, slot));
        }
        // no story where there is no building of the town to tell it, nor in a held one, the institute or a park
        test("story_only_in_the_towns_own_buildings", helper -> {
            for (long seed = 0; seed < 400; seed++) {
                check(helper, Stories.of(Optional.of(NookOfTheDead.id("town/m7")), seed).isEmpty(), "a shelter has its own story");
                check(helper, Stories.of(Optional.of(NookOfTheDead.id("town/institute")), seed).isEmpty(), "the institute has none");
                check(helper, Stories.of(Optional.of(NookOfTheDead.id("town/s7")), seed).isEmpty(), "a fountain has none");
                check(helper, Stories.of(Optional.of(net.minecraft.resources.Identifier.withDefaultNamespace("village/plains/houses/plains_small_house_1")), seed)
                        .isEmpty(), "a village house has none");
            }
            int told = 0;
            for (long seed = 0; seed < 1000; seed++) {
                told += Stories.of(Optional.of(NookOfTheDead.id("town/s22")), seed).isPresent() ? 1 : 0;
            }
            check(helper, told > 300 && told < 600, "about " + Stories.CHANCE + " in a hundred have a story: " + told + " of 1000");
            helper.succeed();
        });
    }

    private static void told(GameTestHelper helper, Story story, int slot) {
        ServerLevel level = helper.getLevel();
        var manager = level.getServer().getStructureTemplateManager();
        StructureTemplate template = null;
        String name = null;
        for (String b : BUILDINGS) {
            StructureTemplate t = manager.get(NookOfTheDead.id("town/" + b)).orElse(null);
            if (t != null && !Stories.rooms(t).doors().isEmpty() && !Stories.rooms(t).floors().isEmpty() && !Markers.autos(t).isEmpty()) {
                template = t;
                name = b;
                break;
            }
        }
        check(helper, template != null, "no building with doors, floors and containers among " + BUILDINGS);
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        // well apart from each other (the tests' arenas lie a few dozen blocks apart) and from the lab's rooms
        BlockPos origin = new BlockPos(corner.getX() - 200 - slot * 200, corner.getY() + 1, corner.getZ() + 600 + slot * 200);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        BoundingBox box = template.getBoundingBox(settings, origin);
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        StructureTemplate building = template;
        String called = name;
        // the forced chunks loaded through to their entities first: an entity added before is not seen there
        helper.runAfterDelay(20, () -> tell(helper, level, story, building, called, origin, settings, box, slot));
    }

    private static void tell(GameTestHelper helper, ServerLevel level, Story story, StructureTemplate template, String name, BlockPos origin,
            StructurePlaceSettings settings, BoundingBox box, int slot) {
        template.placeInWorld(level, origin, origin, settings, RandomSource.create(1), 2);
        for (BlockPos local : Markers.autos(template)) {                     // filled as the town fills them
            if (level.getBlockEntity(StructureTemplate.calculateRelativePosition(settings, local).offset(origin)) instanceof RandomizableContainer c) {
                c.setLootTable(Markers.table("stash_home"), 1);
            }
        }
        int doorsBefore = doors(level, box);
        Stories.draw(level, story, template, origin, settings, box, 0x5EEDL + slot);
        String what = name + " " + story;
        switch (story) {
            case RANSACKED -> {
                int emptied = 0, opened = 0;
                for (BlockPos local : Markers.autos(template)) {
                    BlockPos at = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
                    if (level.getBlockEntity(at) instanceof RandomizableContainer c && c.getLootTable() == null && c.isEmpty()) {
                        emptied++;
                    }
                    if (level.getBlockState(at).hasProperty(BarrelBlock.OPEN) && level.getBlockState(at).getValue(BarrelBlock.OPEN)) {
                        opened++;
                    }
                }
                int open = 0;
                for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    var state = level.getBlockState(at);
                    if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && state.getValue(DoorBlock.OPEN)) {
                        open++;
                    }
                }
                check(helper, emptied > 0, what + ": no container gone through (" + Markers.autos(template).size() + " of its own, " + opened + " barrels opened)");
                check(helper, open > 0, what + ": no door left open");
            }
            case BARRICADED -> {
                check(helper, doors(level, box) < doorsBefore, what + ": no door broken in (" + doorsBefore + " doors)");
                check(helper, count(level, box, Blocks.BARREL) + count(level, box, Blocks.SPRUCE_PLANKS) + count(level, box,
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("nookworks", "crate"))) > 0,
                        what + ": nothing piled at a door or kept");
            }
            case NEST -> {
                int dead = count(level, box, dev.nookofthedead.registry.ZwBlocks.ZOMBIE_MARKER.get());
                int webs = count(level, box, Blocks.COBWEB), bones = count(level, box, Blocks.BONE_BLOCK);
                check(helper, dead > 0, what + ": none of the dead left in it (webs " + webs + ", bones " + bones + ")");
                check(helper, webs + bones > 0, what + ": no webs or bones");
            }
            case LAST_STAND -> {
                // the body is an entity, seen unevenly in a forced chunk far off: its kit is not - a barrel of the guard's, by it
                int kits = 0;
                for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    if (level.getBlockEntity(at) instanceof RandomizableContainer c && Markers.table("stash_guard").equals(c.getLootTable())) {
                        kits++;
                    }
                }
                check(helper, kits == 1, what + ": " + kits + " guard's kits laid by a body, not one");
                check(helper, doors(level, box) < doorsBefore, what + ": no door broken in below");
            }
        }
        helper.succeed();
    }

    private static int doors(ServerLevel level, BoundingBox box) {
        int n = 0;
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var state = level.getBlockState(at);
            n += state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? 1 : 0;
        }
        return n;
    }

    private static int count(ServerLevel level, BoundingBox box, net.minecraft.world.level.block.Block block) {
        int n = 0;
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            n += level.getBlockState(at).is(block) ? 1 : 0;
        }
        return n;
    }

    private StoryTests() {}
}
