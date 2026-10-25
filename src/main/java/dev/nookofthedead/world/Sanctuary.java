package dev.nookofthedead.world;

import dev.nookworks.darkmatter.DarkMatter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Where none of the dead come: round a block of Nookworks' Dark Matter, as far as its dark would reach - lit or not,
 * working or not (the user, 2026-10-16: "ダークマターの効果が届く範囲は、変異ゾンビが沸かない…起動していようがいまいが").
 * Dear for the purpose and dark if left alone, so it wants a light set beside it, and it covers little: a fair price for
 * a safe corner. What is already about still walks in.
 */
public final class Sanctuary {
    private static final Identifier DARK_MATTER = Identifier.fromNamespaceAndPath("nookworks", "dark_matter");
    private static Block darkMatter;

    /** Whether {@code pos} is in a Dark Matter's reach: the squares water would spread to from it, from the layer under it to three above. */
    public static boolean near(LevelReader level, BlockPos pos) {
        Block block = block();
        // a generating region cannot be asked for its full chunks (the review, 2026-10-19: a latent crash if a spawn check
        // ever came here during generation); nothing is placed by players there anyway
        if (block == null || !(level instanceof net.minecraft.world.level.Level)) {
            return false;
        }
        int cx0 = SectionPos.blockToSectionCoord(pos.getX() - DarkMatter.RADIUS);
        int cx1 = SectionPos.blockToSectionCoord(pos.getX() + DarkMatter.RADIUS);
        int cz0 = SectionPos.blockToSectionCoord(pos.getZ() - DarkMatter.RADIUS);
        int cz1 = SectionPos.blockToSectionCoord(pos.getZ() + DarkMatter.RADIUS);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos at : chunk.getBlockEntitiesPos()) {             // a chunk's few block entities, not its blocks
                    int dy = pos.getY() - at.getY();
                    if (dy >= -DarkMatter.BELOW && dy <= DarkMatter.ABOVE
                            && Math.abs(pos.getX() - at.getX()) + Math.abs(pos.getZ() - at.getZ()) <= DarkMatter.RADIUS
                            && chunk.getBlockState(at).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Block block() {
        if (darkMatter == null) {
            darkMatter = BuiltInRegistries.BLOCK.getOptional(DARK_MATTER).orElse(null);
        }
        return darkMatter;
    }

    private Sanctuary() {}
}
