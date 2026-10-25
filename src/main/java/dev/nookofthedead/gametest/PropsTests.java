package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookofthedead.world.Notes;
import dev.nookofthedead.world.Props;
import dev.nookworks.decor.BookendBlockEntity;
import dev.nookworks.decor.PlacedBooksBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Nookworks 1.1's things in the If (0.3): books set down by the world, the notes in them, scrap, the refuge's workshop. */
final class PropsTests {
    static void register() {
        // a pile, books leant on a wall, a row against a Bookend - each holding its books, and standing a while after
        test("books_lie_lean_and_stand", 40, helper -> {
            for (int x = 0; x < 6; x++) {
                for (int z = 0; z < 6; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            helper.setBlock(new BlockPos(4, 1, 0), Blocks.STONE);
            var level = helper.getLevel();
            var box = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(5, 4, 5)));
            var random = level.getRandom();
            check(helper, Props.pile(level, box, helper.absolutePos(new BlockPos(1, 1, 1)), 3, 30, random, () -> Notes.book(Notes.INSTITUTE.get(0))),
                    "a pile lies on the floor");
            check(helper, Props.leant(level, box, helper.absolutePos(new BlockPos(4, 1, 1)), Direction.SOUTH, 2, random, () -> new ItemStack(Items.BOOK)),
                    "books lean on the wall");
            check(helper, !Props.leant(level, box, helper.absolutePos(new BlockPos(2, 1, 4)), Direction.SOUTH, 2, random, () -> new ItemStack(Items.BOOK)),
                    "but not where there is no wall");
            int stood = Props.row(level, box, helper.absolutePos(new BlockPos(0, 1, 3)), Direction.SOUTH, DoorHingeSide.LEFT, 12, () -> new ItemStack(Items.BOOK));
            check(helper, stood == 12, "a row of twelve stands against its bookend: " + stood);
            helper.runAfterDelay(20, () -> {
                check(helper, helper.getBlockEntity(new BlockPos(1, 1, 1), PlacedBooksBlockEntity.class).count() == 3, "the pile keeps its three");
                ItemStack top = helper.getBlockEntity(new BlockPos(1, 1, 1), PlacedBooksBlockEntity.class).book(0);
                check(helper, top.is(Items.WRITTEN_BOOK) && top.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().size() == Notes.INSTITUTE.get(0).pages(),
                        "the note in it is the note, every page");
                check(helper, helper.getBlockEntity(new BlockPos(4, 1, 1), PlacedBooksBlockEntity.class).count() == 2, "the leant books stay");
                int books = 0;
                for (int k = 0; k < 4; k++) {
                    BlockPos at = helper.absolutePos(new BlockPos(0, 1, 3)).relative(dev.nookworks.decor.BookendBlock.along(
                            level.getBlockState(helper.absolutePos(new BlockPos(0, 1, 3)))), k);
                    if (level.getBlockEntity(at) instanceof BookendBlockEntity row) {
                        books += row.count();
                    }
                }
                check(helper, books == 12, "the row still stands, every book in it: " + books);
                helper.succeed();
            });
        });

        // the windows of a building people held out in, sheeted over: a glass pane a Corrugated Sheet, across the same way
        test("windows_are_sheeted_over", helper -> {
            var pane = Blocks.GLASS_PANE.defaultBlockState().setValue(net.minecraft.world.level.block.IronBarsBlock.EAST, true)
                    .setValue(net.minecraft.world.level.block.IronBarsBlock.WEST, true);
            var info = new StructureTemplate.StructureBlockInfo(helper.absolutePos(new BlockPos(1, 1, 1)), pane, null);
            var out = Props.boarded(100).process(helper.getLevel(), info.pos(), info.pos(), info, info, new StructurePlaceSettings(), null);
            check(helper, out != null && out.state().getBlock() instanceof dev.nookworks.recycling.CorrugatedSheetBlock, "a pane sheeted over");
            check(helper, out.state().getValue(dev.nookworks.recycling.CorrugatedSheetBlock.CENTERED)
                    && out.state().getValue(dev.nookworks.recycling.CorrugatedSheetBlock.FACING).getAxis() == Direction.Axis.Z,
                    "in the pane's place, across the same way");
            var bars = new StructureTemplate.StructureBlockInfo(info.pos(), Blocks.IRON_BARS.defaultBlockState(), null);
            check(helper, Props.boarded(100).process(helper.getLevel(), info.pos(), info.pos(), bars, bars, new StructurePlaceSettings(), null)
                    .state().is(Blocks.IRON_BARS), "iron bars left as they are");
            helper.succeed();
        });

        // the refuge's workshop: its machines in a row, the cable before them joined to each and to itself
        test("the_refuge_workshop_is_wired", helper -> {
            for (int x = 0; x < 15; x++) {
                for (int z = 0; z < 3; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            BlockPos under = helper.absolutePos(new BlockPos(0, 1, 0)).subtract(new BlockPos(34, 2, 48));
            var box = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(14, 3, 2)));
            dev.nookofthedead.world.Refuge.workshop(helper.getLevel(), under, new StructurePlaceSettings(), box);
            check(helper, helper.getBlockState(new BlockPos(0, 1, 0)).is(dev.nookworks.registry.ModBlocks.GENERATOR.get()), "a Generator at the head");
            check(helper, helper.getBlockState(new BlockPos(6, 1, 0)).is(dev.nookworks.registry.ModRecycling.COMPRESSOR.get()), "a Compressor");
            check(helper, helper.getBlockState(new BlockPos(10, 2, 0)).is(dev.nookworks.registry.ModRecycling.RECYCLER.get()), "a Recycler, two high");
            var cable = helper.getBlockState(new BlockPos(5, 1, 1));
            check(helper, cable.is(dev.nookworks.registry.ModBlocks.ENERGY_CABLE.get())
                    && cable.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.NORTH))
                    && cable.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST))
                    && cable.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST))
                    && !cable.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.SOUTH)),
                    "the cable joined to its machine and along the line, nothing else");
            helper.succeed();
        });
    }

    private PropsTests() {}
}
