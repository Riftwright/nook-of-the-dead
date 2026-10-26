package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.Optional;

import dev.nookofthedead.ending.Cure;
import dev.nookofthedead.registry.ZwEntities;
import dev.nookofthedead.world.Shelters;
import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** What the reviews found once (the third, 2026-10-25), kept from coming back: each as its reviewer reproduced it. */
final class RegressionTests {
    static void register() {
        // the capital's crows are of the plague, though ambient: a cured If takes none in, and keeps no flocks; and none is
        // carried off in a bottle
        test("if_cured_world_takes_no_crows", helper -> {
            var server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel level = IfLevel.open(server);
            level.getChunk(new BlockPos(0, 180, 0));
            var crow = ZwEntities.CAPITAL_CROW.get().create(level, EntitySpawnReason.NATURAL);
            check(helper, crow != null, "a crow is made");
            check(helper, crow.getType().builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.EntityTypes.CAPTURING_NOT_SUPPORTED),
                    "a crow cannot be bottled and let go outside the capital");
            boolean accepted;
            Cure.cure(server);
            try {
                check(helper, Cure.plague(crow), "a crow is one of the plague's");
                crow.snapTo(0.5, 180, 0.5);
                accepted = level.addFreshEntity(crow);
            } finally {
                crow.discard();
                Cure.forget(server);
            }
            check(helper, !accepted, "a cured If took a crow in");
            helper.succeed();
        });

        // a shelter worked out for one If is never another's: the reviewer's two Ifs, whose packed keys were one
        test("shelters_of_two_ifs_kept_apart", helper -> {
            long seed = IfWorld.seedOf("555315");
            long other = IfWorld.seedOf("233431");
            Shelters.forget();
            Optional<Shelters.Shelter> alone = Shelters.at(other, 36345, 25);
            Shelters.forget();
            Shelters.at(seed, -36351, 166);                                  // asked first, as the reviewer did
            Optional<Shelters.Shelter> after = Shelters.at(other, 36345, 25);
            Shelters.forget();
            check(helper, alone.isPresent(), "the second If has its shelter there");
            check(helper, after.equals(alone), "the first If's answer stood for the second's: " + after + " for " + alone);
            helper.succeed();
        });

        // a shelf's books face out as the shelf does, in a building mirrored and turned a quarter
        test("mirrored_shelf_books_face_out", helper -> {
            try {
                var top = Class.forName("dev.nookofthedead.world.Furnish").getDeclaredMethod("shelfTop", WorldGenLevel.class,
                        BoundingBox.class, BlockPos.class, StructurePlaceSettings.class, BlockPos.class, Direction.class, long.class,
                        RandomSource.class);
                top.setAccessible(true);
                ServerLevel level = helper.getLevel();
                BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
                BoundingBox box = new BoundingBox(origin.getX() - 4, origin.getY() - 2, origin.getZ() - 4, origin.getX() + 4, origin.getY() + 4,
                        origin.getZ() + 4);
                StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.LEFT_RIGHT).setRotation(Rotation.CLOCKWISE_90);
                Direction out = Direction.NORTH;
                Direction shelf = settings.getRotation().rotate(settings.getMirror().mirror(out));   // as the shelf itself is set
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 2);
                top.invoke(null, level, box, origin, settings, BlockPos.ZERO, out, 0L, RandomSource.create(7));
                var state = level.getBlockState(origin);
                check(helper, state.hasProperty(BlockStateProperties.HORIZONTAL_FACING), "books on the shelf: " + state);
                check(helper, state.getValue(BlockStateProperties.HORIZONTAL_FACING) == shelf,
                        "the books face " + state.getValue(BlockStateProperties.HORIZONTAL_FACING) + ", the shelf " + shelf);
                helper.succeed();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });

        // the covered market, drawn by code and unmirrored, still faces the street on a lot turned for a mirrored building
        test("market_faces_its_lot_front", helper -> {
            try {
                var make = Class.forName("dev.nookofthedead.world.Market").getDeclaredConstructor(WorldGenLevel.class,
                        dev.nookofthedead.world.Town.Lot.class, BoundingBox.class);
                var at = make.getDeclaringClass().getDeclaredMethod("at", int.class, int.class, int.class);
                make.setAccessible(true);
                at.setAccessible(true);
                for (Mirror mirror : new Mirror[] {Mirror.NONE, Mirror.LEFT_RIGHT}) {
                    for (Rotation rotation : Rotation.values()) {
                        var lot = new dev.nookofthedead.world.Town.Lot(new BlockPos(0, 64, 0), 32, dev.nookofthedead.world.Town.Use.BUILDING,
                                Optional.of(dev.nookofthedead.world.Market.ID), rotation, mirror, 1L);
                        Object market = make.newInstance(helper.getLevel(), lot, new BoundingBox(0, 0, 0, 31, 100, 31));
                        BlockPos front = (BlockPos) at.invoke(market, 16, 0, 0);
                        BlockPos back = (BlockPos) at.invoke(market, 16, 0, 31);
                        Direction d = lot.front();
                        check(helper, (front.getX() - back.getX()) * d.getStepX() + (front.getZ() - back.getZ()) * d.getStepZ() > 0,
                                "the market's front faces " + d + " (" + rotation + ", " + mirror + ")");
                    }
                }
                helper.succeed();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private RegressionTests() {}
}
