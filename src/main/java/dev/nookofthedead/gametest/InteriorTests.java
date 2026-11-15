package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.List;
import java.util.Optional;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Furnish;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * The interiors' Nookworks 1.7 things (1.2): a house's pictures framed now and then, never a notice or a stain; a house's
 * tables laid with what was on the plates.
 */
final class InteriorTests {
    /** Houses with desks or tables, the first that has some taken. */
    static final List<String> HOUSES = List.of("xs10", "xs11", "xs12", "xs9", "s3", "s5", "s6", "xs15", "m1", "m2");

    static void register() {
        test("interior_pictures_framed_now_and_then", helper -> {
            int framed = 0, stains = 0;
            for (int i = 0; i < 400; i++) {
                for (String variant : new String[] {"nookworks:fox", "nookworks:bloodstain"}) {
                    CompoundTag nbt = new CompoundTag();
                    nbt.putString("id", "nookworks:poster");
                    nbt.putString("variant", variant);
                    BlockPos at = new BlockPos(i, 3, i * 7);
                    var info = new StructureTemplate.StructureEntityInfo(Vec3.atCenterOf(at), at, nbt);
                    var out = Furnish.frames(0xF00DL, Optional.of("home")).processEntity(helper.getLevel(), BlockPos.ZERO, info, info,
                            new StructurePlaceSettings(), null);
                    boolean has = out.nbt.contains("frame");
                    if (variant.endsWith("fox")) {
                        framed += has ? 1 : 0;
                    } else {
                        stains += has ? 1 : 0;
                    }
                }
            }
            check(helper, stains == 0, "a bloodstain framed " + stains + " times");
            check(helper, framed > 120 && framed < 240, "a house's pictures framed about 45 in 100: " + framed + " of 400");
            helper.succeed();
        });
        test("interior_tables_laid", 120, helper -> {
            ServerLevel level = helper.getLevel();
            var manager = level.getServer().getStructureTemplateManager();
            BlockPos corner = helper.absolutePos(BlockPos.ZERO);
            int plates = 0, fed = 0, tried = 0;
            for (String name : HOUSES) {
                StructureTemplate t = manager.get(NookOfTheDead.id("town/" + name)).orElse(null);
                if (t == null) {
                    continue;
                }
                for (long seed = 1; seed <= 4; seed++) {
                    BlockPos origin = new BlockPos(corner.getX() + 300 + tried * 70, corner.getY() + 1, corner.getZ() - 900);
                    tried++;
                    StructurePlaceSettings settings = new StructurePlaceSettings();
                    BoundingBox box = t.getBoundingBox(settings, origin);
                    t.placeInWorld(level, origin, origin, settings, RandomSource.create(1), 2);
                    Furnish.draw(level, t, origin, settings, box, seed, Optional.of("home"));
                    for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                        if (level.getBlockEntity(at) instanceof dev.nookworks.cooking.PlateBlockEntity plate) {
                            plates++;
                            fed += plate.isEmpty() ? 0 : 1;
                        }
                    }
                }
                if (plates > 3) {
                    break;
                }
            }
            check(helper, plates > 0, "no table laid in " + tried + " houses");
            check(helper, fed > 0, "no plate with anything on it (" + plates + " plates)");
            helper.succeed();
        });
    }

    private InteriorTests() {}
}
