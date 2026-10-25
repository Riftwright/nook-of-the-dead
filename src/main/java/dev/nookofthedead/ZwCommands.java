package dev.nookofthedead;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfMirrorBlock;
import dev.nookworks.ifworld.IfMirrors;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.ifworld.IfWorld;
import dev.nookofthedead.infection.Infection;
import dev.nookofthedead.registry.ZwEffects;
import dev.nookofthedead.registry.ZwItems;
import dev.nookofthedead.world.Diaries;
import dev.nookofthedead.world.Town;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /nzw} (operators): for trying the prototype and for bringing the user's buildings in.
 * <ul>
 * <li>{@code start} - for trying the prototype in one step: the If rolled if it is not, survival, a starter kit (a sword,
 * a shield, bread, torches, planks, one suppressant), and in, the If's mirror (the way home) set down beside one if it has
 * none standing, as a trip through one would; {@code start strong} - the same, armed to the teeth instead (the user,
 * 2026-10-18, to play it through to the Vandal: Nookworks' Velarium armour and sword heavily enchanted - the sword with
 * Smite and Sweeping Edge III - suppressants, ender pearls, stone, grenades, food);</li>
 * <li>{@code roll} - the If rolled at once (six random faces) if it is not, and opened;</li>
 * <li>{@code enter} - into it, at the same x and z, on the ground;</li>
 * <li>{@code infection} / {@code infection <seconds>} / {@code infection clear} - the time left told, set, or the infection gone;</li>
 * <li>{@code find <name>} - to the nearest of a building in the town (s1, m3...; village, portal for the game's own; pyramid; refuge, into the institute's), on the street
 * before it;</li>
 * <li>{@code variant <building> <name> <from> <to>} - a variant of a building out of its changed copy: only what differs
 * (Variants);</li>
 * <li>{@code diary <n>} - one of the ruins' diaries, to read;</li>
 * <li>{@code region <kind>} - to the middle of the nearest block of that region (city, wilds, settlement, farmland);</li>
 * <li>{@code quarter <kind>} - to the middle of the nearest block of that quarter (centre, old_town, residential, outskirts,
 * waste), high up, looking down on it;</li>
 * <li>{@code export <name> <from> <to>} - the blocks between two corners (a build world plot, its ground layer at the
 * bottom) saved as a template, its empty top cut off, to {@code <world>/generated/nookofthedead/structure/town/<name>.nbt}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class ZwCommands {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nzw").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start").executes(c -> start(c.getSource(), false))
                        .then(Commands.literal("strong").executes(c -> start(c.getSource(), true))))
                .then(Commands.literal("roll").executes(c -> roll(c.getSource())))
                .then(Commands.literal("enter").executes(c -> enter(c.getSource().getPlayerOrException())))
                .then(Commands.literal("infection")
                        .executes(c -> left(c.getSource().getPlayerOrException()))
                        .then(Commands.literal("clear").executes(c -> clear(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, Infection.FULL / 20))
                                .executes(c -> infection(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "seconds")))))
                .then(Commands.literal("variant").then(Commands.argument("building", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.word()).then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(c -> variant(c.getSource(),
                                        StringArgumentType.getString(c, "building"), StringArgumentType.getString(c, "name"),
                                        BlockPosArgument.getLoadedBlockPos(c, "from"), BlockPosArgument.getLoadedBlockPos(c, "to"))))))))
                .then(Commands.literal("ground").executes(c -> groundCheck(c.getSource().getPlayerOrException())))
                .then(Commands.literal("groundmap").executes(c -> groundMap(c.getSource().getPlayerOrException())))
                .then(Commands.literal("capgo").then(Commands.argument("dx", IntegerArgumentType.integer(-400, 400))
                        .then(Commands.argument("dz", IntegerArgumentType.integer(-400, 400)).then(Commands.argument("yaw", IntegerArgumentType.integer(-360, 360))
                                .executes(c -> capgo(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "dx"),
                                        IntegerArgumentType.getInteger(c, "dz"), IntegerArgumentType.getInteger(c, "yaw")))))))
                .then(Commands.literal("capin").then(Commands.argument("dx", IntegerArgumentType.integer(-400, 400))
                        .then(Commands.argument("dz", IntegerArgumentType.integer(-400, 400)).then(Commands.argument("storey", IntegerArgumentType.integer(0, 9))
                                .executes(c -> {
                                    String said = dev.nookofthedead.world.CapitalMap.into(c.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(c, "dx"), IntegerArgumentType.getInteger(c, "dz"),
                                            IntegerArgumentType.getInteger(c, "storey"));
                                    c.getSource().sendSuccess(() -> Component.literal(said), false);
                                    return 1;
                                })))))
                .then(Commands.literal("capspots").executes(c -> {
                    String said = dev.nookofthedead.world.CapitalMap.spots(c.getSource().getLevel().getSeed());
                    c.getSource().sendSuccess(() -> Component.literal(said), false);
                    return 1;
                }))
                .then(Commands.literal("capmap").executes(c -> {
                    try {
                        String said = dev.nookofthedead.world.CapitalMap.write(c.getSource().getLevel().getSeed(), new java.io.File("nzw_capital.png"));
                        c.getSource().sendSuccess(() -> Component.literal(said), false);
                        return 1;
                    } catch (java.io.IOException e) {
                        c.getSource().sendFailure(Component.literal(e.toString()));
                        return 0;
                    }
                }))
                .then(Commands.literal("seams").executes(c -> seams(c.getSource().getPlayerOrException(), 12))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(c -> seams(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("what").executes(c -> what(c.getSource().getPlayerOrException())))
                .then(Commands.literal("stock").executes(c -> stock(c.getSource().getPlayerOrException())))
                .then(Commands.literal("film").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> film(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("find").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> find(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("inside").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("k", IntegerArgumentType.integer(0, 999))
                                .executes(c -> find(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name"),
                                        IntegerArgumentType.getInteger(c, "k"))))))
                .then(Commands.literal("region").then(Commands.argument("kind", StringArgumentType.word())
                        .executes(c -> region(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "kind")))))
                .then(Commands.literal("quarter").then(Commands.argument("kind", StringArgumentType.word())
                        .executes(c -> quarter(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "kind")))))
                .then(Commands.literal("diary").then(Commands.argument("n", IntegerArgumentType.integer(1, Diaries.PAGES.length))
                        .executes(c -> diary(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("tidy").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> tidy(c.getSource(), StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("export").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(c -> export(c.getSource(), StringArgumentType.getString(c, "name"),
                                        BlockPosArgument.getLoadedBlockPos(c, "from"), BlockPosArgument.getLoadedBlockPos(c, "to"))))))));
    }

    private static int roll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        IfWorld world = IfWorld.get(server);
        while (!world.rolled()) {
            world.addThrow(1 + server.overworld().getRandom().nextInt(6));
        }
        world.setDirty();
        ServerLevel level = IfLevel.open(server);
        source.sendSuccess(() -> Component.literal("The If No. " + world.number() + (level != null ? " is open" : " waits for the next start")), true);
        return 1;
    }

    private static int start(CommandSourceStack source, boolean strong) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        roll(source);
        player.setGameMode(GameType.SURVIVAL);
        if (strong) {
            strongKit(player);
        } else {
            for (ItemStack kit : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD, 16),
                    new ItemStack(Items.TORCH, 32), new ItemStack(Items.OAK_PLANKS, 32), ZwEffects.bottle(Items.POTION))) {
                player.getInventory().placeItemBackInInventory(kit, Prediction.SERVER_ONLY);
            }
        }
        int entered = enter(player);
        wayHome(player);
        return entered;
    }

    /**
     * The strong start: Nookworks' Velarium armour on (netherite if Nookworks has none), Protection IV and the rest, and
     * in hand its sword with Smite V, Sweeping Edge III, Fire Aspect II, Looting III; a shield; suppressants to drink and
     * to throw, ender pearls, a stack of stone, grenades, food and torches.
     */
    /** {@code stack} put on in {@code slot}, what was there given back rather than lost (the review, 2026-10-19). */
    private static void wear(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        ItemStack was = player.getItemBySlot(slot);
        player.setItemSlot(slot, stack);
        if (!was.isEmpty()) {
            player.getInventory().placeItemBackInInventory(was, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    private static void strongKit(ServerPlayer player) {
        HolderLookup.RegistryLookup<Enchantment> book = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        wear(player, EquipmentSlot.HEAD, enchanted(book, velarium("helmet", Items.NETHERITE_HELMET),
                Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1, Enchantments.RESPIRATION, 3, Enchantments.AQUA_AFFINITY, 1));
        wear(player, EquipmentSlot.CHEST, enchanted(book, velarium("chestplate", Items.NETHERITE_CHESTPLATE),
                Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1, Enchantments.THORNS, 3));
        wear(player, EquipmentSlot.LEGS, enchanted(book, velarium("leggings", Items.NETHERITE_LEGGINGS),
                Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1, Enchantments.SWIFT_SNEAK, 3));
        wear(player, EquipmentSlot.FEET, enchanted(book, velarium("boots", Items.NETHERITE_BOOTS),
                Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1, Enchantments.FEATHER_FALLING, 4, Enchantments.DEPTH_STRIDER, 3));
        wear(player, EquipmentSlot.OFFHAND, enchanted(book, new ItemStack(Items.SHIELD), Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
        List<ItemStack> kit = new java.util.ArrayList<>(List.of(
                enchanted(book, velarium("sword", Items.NETHERITE_SWORD), Enchantments.SMITE, 5, Enchantments.SWEEPING_EDGE, 3,
                        Enchantments.FIRE_ASPECT, 2, Enchantments.LOOTING, 3, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1),
                new ItemStack(ZwItems.GRENADE.get(), 16), new ItemStack(ZwItems.GRENADE.get(), 16),
                new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.STONE, 64),
                new ItemStack(Items.COOKED_BEEF, 64), new ItemStack(Items.GOLDEN_CARROT, 64), new ItemStack(Items.GOLDEN_APPLE, 16),
                new ItemStack(Items.TORCH, 64)));
        for (int i = 0; i < 6; i++) {
            kit.add(ZwEffects.bottle(Items.POTION));                   // to drink: the infection held back
        }
        for (int i = 0; i < 4; i++) {
            kit.add(ZwEffects.bottle(Items.SPLASH_POTION));            // to throw: the mutated made ordinary
        }
        for (ItemStack stack : kit) {
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
    }

    /** Nookworks' Velarium {@code part} (velarium_sword...), or {@code otherwise} if it has none. */
    private static ItemStack velarium(String part, Item otherwise) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("nookworks", "velarium_" + part)).orElse(otherwise));
    }

    /** {@code stack} with the enchantments given as pairs, each a key and its level - together as no table would allow. */
    private static ItemStack enchanted(HolderLookup.RegistryLookup<Enchantment> book, ItemStack stack, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pairs[i];
            stack.enchant(book.getOrThrow(key), (Integer) pairs[i + 1]);
        }
        return stack;
    }

    /**
     * The If's mirror, as a trip through one sets it down, beside {@code player} (just arrived) if the If has none standing:
     * the way home, for the zombies to find.
     */
    private static void wayHome(ServerPlayer player) {
        ServerLevel level = player.level();
        if (!NookOfTheDead.fallen(level) || IfMirrors.standing(level) != null) {
            return;
        }
        Direction facing = player.getDirection();
        for (Direction side : new Direction[] {facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()}) {
            BlockPos at = player.blockPosition().relative(side, 2);
            at = at.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ())); // on top of a tree's leaves, not in them
            if (level.getBlockState(at).canBeReplaced() && level.getBlockState(at.above()).canBeReplaced()
                    && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) { // not on a slab or fence, to drop
                BlockState mirror = ModTravel.IF_MIRROR.get().defaultBlockState().setValue(IfMirrorBlock.FACING, side.getOpposite());
                level.setBlock(at, mirror, 3);
                level.setBlock(at.above(), mirror.setValue(IfMirrorBlock.HALF, DoubleBlockHalf.UPPER), 3);
                IfMirrors.placed(level, at);
                return;
            }
        }
    }

    private static int enter(ServerPlayer player) throws CommandSyntaxException {
        ServerLevel level = IfLevel.open(player.level().getServer());
        if (level == null) {
            player.sendSystemMessage(Component.literal("The If is not rolled: /nzw roll"));
            return 0;
        }
        int x = player.getBlockX();
        int z = player.getBlockZ();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z); // on the leaves, not inside a tree (r1)
        player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
        return 1;
    }

    /**
     * A film played now (0.3): {@code opening} as on first coming into the If (seen or not, any game mode), or
     * {@code ending} - the vaccine let go from where the player stands: the If is cured; {@code vandal} - the refuge's
     * door opened, in the institute the player is in; {@code ascent}, {@code base} - the arrivals, from where they stand.
     */
    private static int film(ServerPlayer player, String name) {
        if (!NookOfTheDead.fallen(player.level())) {
            player.sendSystemMessage(Component.literal("Not in the If: /nzw enter"));
            return 0;
        }
        if (name.equals("opening")) {
            dev.nookofthedead.ending.Opening.play(player);
            return 1;
        }
        if (name.equals("ending")) {
            dev.nookofthedead.ending.Ending.start(player.level(), player, player.blockPosition());
            return 1;
        }
        if (name.equals("vandal")) {                                     // the refuge's door opened, from in its institute
            return dev.nookofthedead.world.RefugeRooms.open(player.level(), player.blockPosition()) ? 1 : 0;
        }
        if (name.equals("ascent")) {
            dev.nookofthedead.ending.Cuts.ascent(player);
            return 1;
        }
        if (name.equals("base")) {
            dev.nookofthedead.ending.Cuts.base(player);
            return 1;
        }
        player.sendSystemMessage(Component.literal("Films: opening, ending, vandal, ascent, base"));
        return 0;
    }

    /**
     * To the nearest lot of the town with building {@code name} on it (s1, m3, xs2...), searched outward a block of the
     * grid at a time: on the street before its front, looking at it.
     */
    private static int find(ServerPlayer player, String name) {
        return find(player, name, -1);
    }

    private static int find(ServerPlayer player, String name, int inside) {
        ServerLevel level = player.level();
        if (!NookOfTheDead.fallen(level)) {
            player.sendSystemMessage(Component.literal("Not in the If: /nzw enter"));
            return 0;
        }
        net.minecraft.resources.Identifier id = NookOfTheDead.id("town/" + name.toLowerCase(java.util.Locale.ROOT));
        long seed = level.getSeed();
        int bx0 = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz0 = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        if (name.equals("trap")) {                                       // an arrow trap in a gallery: just short of its wire
            int g = dev.nookofthedead.world.Tunnels.GRID;
            int gx0 = Math.floorDiv(player.getBlockX(), g);
            int gz0 = Math.floorDiv(player.getBlockZ(), g);
            for (int r = 0; r <= 60; r++) {
                for (int gx = gx0 - r; gx <= gx0 + r; gx++) {
                    for (int gz = gz0 - r; gz <= gz0 + r; gz++) {
                        if (Math.max(Math.abs(gx - gx0), Math.abs(gz - gz0)) != r) {
                            continue;
                        }
                        for (boolean east : new boolean[] {true, false}) {
                            if (dev.nookofthedead.world.Tunnels.arrowsAt(seed, gx, gz, east)) {
                                double x = gx * g + 0.5 + (east ? g / 2 - 3 : 0);
                                double z = gz * g + 0.5 + (east ? 0 : g / 2 - 3);
                                level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x), net.minecraft.core.SectionPos.blockToSectionCoord(z));
                                player.teleportTo(level, x, dev.nookofthedead.world.Tunnels.FLOOR, z, java.util.Set.<Relative>of(), east ? -90 : 0, 0, true);
                                player.sendSystemMessage(Component.literal("trap at " + (int) x + " " + (int) z));
                                return 1;
                            }
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No trap near"));
            return 0;
        }
        if (name.equals("capital")) {                                    // the capital (2026-10-24): high over its castle
            int[] c = dev.nookofthedead.world.Capital.centre(seed);
            level.getChunk(c[0] >> 4, c[1] >> 4);
            player.teleportTo(level, c[0] + 0.5, 190, c[1] + 0.5, java.util.Set.<Relative>of(), player.getYRot(), 60, true);
            player.sendSystemMessage(Component.literal("capital at " + c[0] + " " + c[1]));
            return 1;
        }
        if (name.equals("ascent")) {                                     // the second institute: onto its lodestone
            dev.nookofthedead.world.Ascent.Site site = dev.nookofthedead.world.Ascent.site(seed);
            level.getChunk(site.arrival());
            player.teleportTo(level, site.arrival().getX() + 0.5, site.arrival().getY(), site.arrival().getZ() + 0.5,
                    java.util.Set.<Relative>of(), player.getYRot(), 0, true);
            player.sendSystemMessage(Component.literal("second institute at " + site.x() + " " + site.ground() + " " + site.z()));
            return 1;
        }
        if (name.equals("shelter") || name.equals("salvation")) {        // a shelter's well, from its rim; a church of "salvation", in its nave
            for (int r = 0; r <= 12; r++) {
                for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                    for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                        if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) != r) {
                            continue;
                        }
                        net.minecraft.core.BlockPos at = null;
                        if (name.equals("shelter")) {
                            var shelter = dev.nookofthedead.world.Shelters.at(seed, bx, bz);
                            if (shelter.isPresent()) {
                                at = shelter.get().well().atY(shelter.get().top() + 1).south(2);
                            }
                        } else {
                            for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                                at = at != null ? at : dev.nookofthedead.world.Salvation.where(level, lot);
                            }
                        }
                        if (at != null) {
                            level.getChunk(at);
                            player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.<Relative>of(), 180, 20, true);
                            player.sendSystemMessage(Component.literal(name + " at " + at.toShortString()));
                            return 1;
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No " + name + " near"));
            return 0;
        }
        if (name.equals("market")) {                                     // the covered market: at its front door, looking in
            for (int r = 0; r <= 20; r++) {
                for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                    for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                        if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) != r) {
                            continue;
                        }
                        for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                            if (lot.building().map(dev.nookofthedead.world.Market.ID::equals).orElse(false)) {
                                net.minecraft.core.BlockPos c = lot.corner().offset(lot.size() / 2, 0, lot.size() / 2);
                                level.getChunk(c);
                                player.teleportTo(level, c.getX() + 0.5, c.getY() + 6, c.getZ() + 0.5, java.util.Set.<Relative>of(), player.getYRot(), 30, true);
                                player.sendSystemMessage(Component.literal("market at " + c.toShortString() + " " + lot.rotation() + " "
                                        + dev.nookofthedead.world.Market.trades(lot)));
                                return 1;
                            }
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No market near"));
            return 0;
        }
        if (name.equals("roofcamp")) {                                   // a last holdout's camp on a roof: in its middle
            for (int r = 0; r <= 12; r++) {
                for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                    for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                        if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) != r) {
                            continue;
                        }
                        for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                            net.minecraft.core.BlockPos at = dev.nookofthedead.world.Rooftops.where(level, lot);
                            if (at != null) {
                                level.getChunk(at);
                                player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.<Relative>of(), player.getYRot(), 30, true);
                                player.sendSystemMessage(Component.literal("roof camp on " + lot.building().get() + " at " + at.toShortString()));
                                return 1;
                            }
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No roof camp near"));
            return 0;
        }
        if (name.equals("sinkhole")) {                                   // the ground fallen into the galleries: at its edge, looking in
            int g = dev.nookofthedead.world.Tunnels.GRID;
            int gx0 = Math.floorDiv(player.getBlockX(), g);
            int gz0 = Math.floorDiv(player.getBlockZ(), g);
            for (int r = 0; r <= 80; r++) {
                for (int gx = gx0 - r; gx <= gx0 + r; gx++) {
                    for (int gz = gz0 - r; gz <= gz0 + r; gz++) {
                        if (Math.max(Math.abs(gx - gx0), Math.abs(gz - gz0)) == r && dev.nookofthedead.world.Tunnels.sinkholeAt(seed, gx, gz)) {
                            int top = Town.plan(seed, gx * g - 8, gz * g - 8).top()[8][8];
                            level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(gx * g), net.minecraft.core.SectionPos.blockToSectionCoord(gz * g));
                            player.teleportTo(level, gx * g + 0.5, top + 1, gz * g + 6.5, java.util.Set.<Relative>of(), 180, 45, true);
                            player.sendSystemMessage(Component.literal("sinkhole at " + gx * g + " " + top + " " + gz * g));
                            return 1;
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No sinkhole near"));
            return 0;
        }
        if (name.equals("bloom")) {                                      // a sewer bloom's vault: at the mouth of a way in, facing it
            int g = dev.nookofthedead.world.Tunnels.GRID;
            int gx0 = Math.floorDiv(player.getBlockX(), g);
            int gz0 = Math.floorDiv(player.getBlockZ(), g);
            for (int r = 0; r <= 80; r++) {
                for (int gx = gx0 - r; gx <= gx0 + r; gx++) {
                    for (int gz = gz0 - r; gz <= gz0 + r; gz++) {
                        if (Math.max(Math.abs(gx - gx0), Math.abs(gz - gz0)) == r && dev.nookofthedead.world.Tunnels.bloomAt(seed, gx, gz)) {
                            level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(gx * g), net.minecraft.core.SectionPos.blockToSectionCoord(gz * g));
                            // in the gallery that comes in, eight out, facing it
                            int[][] ways = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
                            boolean[] dug = {dev.nookofthedead.world.Tunnels.dug(seed, gx, gz - 1, false), dev.nookofthedead.world.Tunnels.dug(seed, gx, gz, false),
                                    dev.nookofthedead.world.Tunnels.dug(seed, gx - 1, gz, true), dev.nookofthedead.world.Tunnels.dug(seed, gx, gz, true)};
                            int w = dug[0] ? 0 : dug[1] ? 1 : dug[2] ? 2 : 3;
                            float yaw = new float[] {0, 180, -90, 90}[w];
                            player.teleportTo(level, gx * g + 0.5 + ways[w][0] * 8, dev.nookofthedead.world.Tunnels.FLOOR, gz * g + 0.5 + ways[w][1] * 8,
                                    java.util.Set.<Relative>of(), yaw, 10, true);
                            player.sendSystemMessage(Component.literal("bloom at " + gx * g + " " + gz * g));
                            return 1;
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No bloom near"));
            return 0;
        }
        if (name.equals("hideout")) {                                    // a gallery's hideout: before its door, facing it
            int g = dev.nookofthedead.world.Tunnels.GRID;
            int gx0 = Math.floorDiv(player.getBlockX(), g);
            int gz0 = Math.floorDiv(player.getBlockZ(), g);
            for (int r = 0; r <= 60; r++) {
                for (int gx = gx0 - r; gx <= gx0 + r; gx++) {
                    for (int gz = gz0 - r; gz <= gz0 + r; gz++) {
                        if (Math.max(Math.abs(gx - gx0), Math.abs(gz - gz0)) != r) {
                            continue;
                        }
                        for (boolean east : new boolean[] {true, false}) {
                            if (dev.nookofthedead.world.Tunnels.hideoutAt(seed, gx, gz, east)) {
                                double x = gx * g + 0.5 + (east ? 13 : -1);
                                double z = gz * g + 0.5 + (east ? -1 : 7);
                                level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x), net.minecraft.core.SectionPos.blockToSectionCoord(z));
                                player.teleportTo(level, x, dev.nookofthedead.world.Tunnels.FLOOR, z, java.util.Set.<Relative>of(), east ? 0 : -90, 10, true);
                                player.sendSystemMessage(Component.literal("hideout door at " + (int) (x + (east ? 0 : 3)) + " " + (int) (z + (east ? 3 : 0))));
                                return 1;
                            }
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No hideout near"));
            return 0;
        }
        if (name.equals("refuge")) {                                     // into the nearest institute's refuge (Refuge)
            for (int r = 0; r <= 40; r++) {
                for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                    for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                        net.minecraft.world.level.levelgen.structure.BoundingBox box = Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) == r
                                ? dev.nookofthedead.world.Refuge.box(level, bx, bz) : null;
                        if (box != null) {
                            net.minecraft.core.BlockPos mid = box.getCenter();
                            level.getChunk(mid.getX() >> 4, mid.getZ() >> 4);
                            player.teleportTo(level, mid.getX() + 0.5, box.minY() + 1, mid.getZ() + 0.5, java.util.Set.<Relative>of(), player.getYRot(), 20, true);
                            player.sendSystemMessage(Component.literal("refuge " + box));
                            return 1;
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No refuge near"));
            return 0;
        }
        if (name.equals("bunker") || name.equals("nest")) {              // a room of that kind, from its middle
            int g = dev.nookofthedead.world.Tunnels.GRID;
            int want = name.equals("bunker") ? 1 : 2;
            for (int r = 0; r <= 60; r++) {
                for (int gx = Math.floorDiv(player.getBlockX(), g) - r; gx <= Math.floorDiv(player.getBlockX(), g) + r; gx++) {
                    for (int gz = Math.floorDiv(player.getBlockZ(), g) - r; gz <= Math.floorDiv(player.getBlockZ(), g) + r; gz++) {
                        if (dev.nookofthedead.world.Tunnels.reachedAt(seed, gx, gz) && dev.nookofthedead.world.Tunnels.crossingAt(seed, gx, gz) == 1
                                && dev.nookofthedead.world.Tunnels.roomKind(seed, gx, gz) == want) {
                            level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(gx * g), net.minecraft.core.SectionPos.blockToSectionCoord(gz * g));
                            player.teleportTo(level, gx * g + 0.5, dev.nookofthedead.world.Tunnels.FLOOR, gz * g + 2.5, java.util.Set.<Relative>of(), 180, 20, true);
                            player.sendSystemMessage(Component.literal(name + " at " + gx * g + " " + gz * g));
                            return 1;
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No " + name + " near"));
            return 0;
        }
        if (name.equals("room") || name.equals("shaft")) {                // the galleries': into a room, onto the rim of a way down
            int g = dev.nookofthedead.world.Tunnels.GRID;
            int gx0 = Math.floorDiv(player.getBlockX(), g);
            int gz0 = Math.floorDiv(player.getBlockZ(), g);
            int want = name.equals("room") ? 1 : 2;
            for (int r = 0; r <= 60; r++) {
                for (int gx = gx0 - r; gx <= gx0 + r; gx++) {
                    for (int gz = gz0 - r; gz <= gz0 + r; gz++) {
                        if (Math.max(Math.abs(gx - gx0), Math.abs(gz - gz0)) != r || !dev.nookofthedead.world.Tunnels.reachedAt(seed, gx, gz)
                                || dev.nookofthedead.world.Tunnels.crossingAt(seed, gx, gz) != want) {
                            continue;
                        }
                        if (want == 2 && !dev.nookofthedead.world.Tunnels.open(seed, gx * g, gz * g)) {
                            continue;                                   // not open ground there: no way down was made
                        }
                        double x = gx * g + 0.5;
                        double z = gz * g + 0.5 + (want == 2 ? 3 : 2);              // beside the room's fire, not in it
                        double y = want == 1 ? dev.nookofthedead.world.Tunnels.FLOOR : Town.height(seed, gx * g, gz * g) + 2;
                        level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x), net.minecraft.core.SectionPos.blockToSectionCoord(z));
                        player.teleportTo(level, x, y, z, java.util.Set.<Relative>of(), 180, 20, true);
                        player.sendSystemMessage(Component.literal(name + " at " + gx * g + " " + gz * g));
                        return 1;
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No " + name + " near"));
            return 0;
        }
        if (name.equals("cart")) {                                       // a cart left standing on a main street, from its side
            int[] cart = dev.nookofthedead.world.Traces.nearestCart(seed, player.getBlockX(), player.getBlockZ(), 600);
            if (cart == null) {
                player.sendSystemMessage(Component.literal("No cart near"));
                return 0;
            }
            boolean ns = cart[2] == 1;
            double x = cart[0] + 0.5 + (ns ? 5 : 0);
            double z = cart[1] + 0.5 + (ns ? 0 : 5);
            level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x), net.minecraft.core.SectionPos.blockToSectionCoord(z));
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            player.teleportTo(level, x, y, z, java.util.Set.<Relative>of(), ns ? 90 : 180, 15, true);
            player.sendSystemMessage(Component.literal("cart at " + cart[0] + " " + cart[1]));
            return 1;
        }
        if (name.equals("sea") || name.equals("peak")) {                 // on a sea's beach, at a mountain's foot: looking at it
            int side = dev.nookofthedead.world.Terrain.FEATURE_CELL;
            int q0x = Math.floorDiv(bx0, side);
            int q0z = Math.floorDiv(bz0, side);
            for (int r = 0; r <= 16; r++) {                                 // they are rare: further out than the rest
                for (int qx = q0x - r; qx <= q0x + r; qx++) {
                    for (int qz = q0z - r; qz <= q0z + r; qz++) {
                        if (Math.max(Math.abs(qx - q0x), Math.abs(qz - q0z)) != r) {
                            continue;
                        }
                        Object found = dev.nookofthedead.world.Terrain.square(seed, qx, qz);
                        int cx;
                        int cz;
                        int out;
                        if (name.equals("sea") && found instanceof dev.nookofthedead.world.Terrain.Sea sea) {
                            cx = sea.x();
                            cz = sea.z();
                            out = (int) (sea.radius() * 1.35);
                        } else if (name.equals("peak") && found instanceof dev.nookofthedead.world.Terrain.Peak peak) {
                            cx = peak.x();
                            cz = peak.z();
                            out = (int) (peak.radius() * 1.3);
                        } else {
                            continue;
                        }
                        double x = cx + 0.5;
                        double z = cz + out + 0.5;
                        level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x), net.minecraft.core.SectionPos.blockToSectionCoord(z));
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                        player.teleportTo(level, x, y, z, java.util.Set.<Relative>of(), 180, 0, true);
                        player.sendSystemMessage(Component.literal(name + " at " + cx + " " + cz));
                        return 1;
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No " + name + " within " + FIND_REACH + " blocks of the grid"));
            return 0;
        }
        if (name.equals("cave") || name.equals("lake")) {                // the country's: before a cave's mouth, by a lake
            for (int r = 0; r <= FIND_REACH; r++) {
                for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                    for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                        if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) != r) {
                            continue;
                        }
                        dev.nookofthedead.world.Terrain.Cave cave = name.equals("cave") ? dev.nookofthedead.world.Terrain.cave(seed, bx, bz) : null;
                        dev.nookofthedead.world.Terrain.Lake lake = name.equals("lake") ? dev.nookofthedead.world.Terrain.lake(seed, bx, bz) : null;
                        if (cave == null && lake == null) {
                            continue;
                        }
                        int out = cave != null ? dev.nookofthedead.world.Terrain.Cave.HILL_RADIUS + 8 : lake.radius() + 10;
                        Direction way = cave != null ? cave.mouth() : Direction.NORTH;
                        int cx = cave != null ? cave.x() : lake.x();
                        int cz = cave != null ? cave.z() : lake.z();
                        double x = cx + way.getStepX() * out + 0.5;
                        double z = cz + way.getStepZ() * out + 0.5;
                        level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x), net.minecraft.core.SectionPos.blockToSectionCoord(z));
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                        player.teleportTo(level, x, y, z, java.util.Set.<Relative>of(), way.getOpposite().toYRot(), 0, true);
                        player.sendSystemMessage(Component.literal(name + " at " + cx + " " + cz));
                        return 1;
                    }
                }
            }
            player.sendSystemMessage(Component.literal("No " + name + " within " + FIND_REACH + " blocks of the grid"));
            return 0;
        }
        if (java.util.Set.of("canal", "walled", "burnt", "graves", "fallen", "grown", "square", "barricade").contains(name)) {
            return trace(player, name);
        }
        // "village" and "portal": the game's own, set down now and then
        java.util.function.Function<net.minecraft.resources.Identifier, Boolean> matches = name.equals("pyramid") ? dev.nookofthedead.world.Desert.PYRAMID::equals
                : name.equals("village") ? dev.nookofthedead.world.Vanilla::village
                : name.equals("portal") ? b -> dev.nookofthedead.world.Vanilla.is(b) && b.getPath().startsWith("ruined_portal/") : id::equals;
        for (int r = 0; r <= FIND_REACH; r++) {
            Town.Lot best = null;
            double nearest = Double.MAX_VALUE;
            for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                    if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) != r) {
                        continue;
                    }
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        double d = player.distanceToSqr(lot.corner().getX() + lot.size() / 2.0, Town.SURFACE, lot.corner().getZ() + lot.size() / 2.0);
                        if (lot.use() == Town.Use.BUILDING && lot.building().map(matches).orElse(false) && d < nearest) {
                            best = lot;
                            nearest = d;
                        }
                    }
                }
            }
            if (best != null && inside >= 0) {
                BlockPos floor = Town.floorIn(level, best, inside);
                if (floor != null) {
                    level.getChunk(floor);
                    player.teleportTo(level, floor.getX() + 0.5, floor.getY() + 0.2, floor.getZ() + 0.5, java.util.Set.<Relative>of(),
                            player.getYRot(), 20, true);
                    player.sendSystemMessage(Component.literal(name + " inside at " + floor.toShortString()));
                    return 1;
                }
            }
            if (best != null) {
                Direction front = best.front();
                double x = best.corner().getX() + best.size() / 2.0 + front.getStepX() * (best.size() / 2.0 + 3);
                double z = best.corner().getZ() + best.size() / 2.0 + front.getStepZ() * (best.size() / 2.0 + 3);
                level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x), net.minecraft.core.SectionPos.blockToSectionCoord(z));
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)); // the town rises and falls
                player.teleportTo(level, x, y, z, java.util.Set.<Relative>of(), front.getOpposite().toYRot(), 0, true);
                player.sendSystemMessage(Component.literal(name + " at " + best.corner().getX() + " " + best.corner().getZ() + ", facing " + front));
                return 1;
            }
        }
        player.sendSystemMessage(Component.literal("No " + name + " within " + FIND_REACH + " blocks of the grid"));
        return 0;
    }

    /**
     * To the nearest of what the town shows of what happened (dev.nookofthedead.world.Traces): a canal, a block walled in,
     * where fire went through, a burial ground, a building fallen in or grown through, a square; above it, looking down.
     */
    private static int trace(ServerPlayer player, String name) {
        ServerLevel level = player.level();
        long seed = level.getSeed();
        int bx0 = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz0 = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        for (int r = 0; r <= FIND_REACH; r++) {
            for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                    if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) != r) {
                        continue;
                    }
                    int[] at = traceIn(seed, name, bx, bz);
                    if (at != null) {
                        level.getChunk(at[0] >> 4, at[1] >> 4);
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at[0], at[1]);
                        player.teleportTo(level, at[0] + 0.5, y + 24, at[1] + 0.5 - 18, java.util.Set.<Relative>of(), 0, 50, true);
                        player.sendSystemMessage(Component.literal(name + " at " + at[0] + " " + at[1]));
                        return 1;
                    }
                }
            }
        }
        player.sendSystemMessage(Component.literal("No " + name + " within " + FIND_REACH + " blocks of the grid"));
        return 0;
    }

    private static int @org.jspecify.annotations.Nullable [] traceIn(long seed, String name, int bx, int bz) {
        int cx = bx * Town.BLOCK + Town.BLOCK / 2;
        int cz = bz * Town.BLOCK + Town.BLOCK / 2;
        switch (name) {
            case "walled" -> {
                return dev.nookofthedead.world.Traces.quarantined(seed, bx, bz) ? new int[] {cx, cz} : null;
            }
            case "burnt" -> {
                return dev.nookofthedead.world.Traces.burning(seed, cx, cz) ? new int[] {cx, cz} : null;
            }
            case "barricade" -> {
                int along = dev.nookofthedead.world.Traces.barricadeAt(seed, true, bx, bz);
                if (along >= 0) {
                    return new int[] {bx * Town.BLOCK, along};
                }
                along = dev.nookofthedead.world.Traces.barricadeAt(seed, false, bz, bx);
                return along >= 0 ? new int[] {along, bz * Town.BLOCK} : null;
            }
            case "square" -> {
                return Town.square(seed, bx, bz) != null ? new int[] {bx * Town.BLOCK, bz * Town.BLOCK} : null;
            }
            case "canal" -> {
                for (int d = -6; d <= 6; d++) {
                    if (dev.nookofthedead.world.Traces.canal(seed, bx * Town.BLOCK + d, cz) > 0) {
                        return new int[] {bx * Town.BLOCK + d, cz};
                    }
                    if (dev.nookofthedead.world.Traces.canal(seed, cx, bz * Town.BLOCK + d) > 0) {
                        return new int[] {cx, bz * Town.BLOCK + d};
                    }
                }
                return null;
            }
            default -> {
                for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                    int[] mid = {lot.corner().getX() + lot.size() / 2, lot.corner().getZ() + lot.size() / 2};
                    boolean hit = switch (name) {
                        case "graves" -> lot.use() == Town.Use.YARD && dev.nookofthedead.world.Traces.graveyard(lot)
                                && dev.nookofthedead.world.Region.at(seed, mid[0], mid[1]) != dev.nookofthedead.world.Region.WILDS
                                && dev.nookofthedead.world.Region.at(seed, mid[0], mid[1]) != dev.nookofthedead.world.Region.FARMLAND;
                        case "fallen" -> lot.use() == Town.Use.BUILDING
                                && dev.nookofthedead.world.Traces.ruin(lot, dev.nookofthedead.world.District.of(seed, bx, bz)) == dev.nookofthedead.world.Traces.Ruin.FALLEN;
                        case "grown" -> lot.use() == Town.Use.BUILDING
                                && dev.nookofthedead.world.Traces.ruin(lot, dev.nookofthedead.world.District.of(seed, bx, bz)) == dev.nookofthedead.world.Traces.Ruin.GROWN;
                        default -> false;
                    };
                    if (hit) {
                        return mid;
                    }
                }
                return null;
            }
        }
    }

    /** For looking the town over: the lot under the player - what it is, which building, which way, in which quarter. */
    /** What the containers of the refuge the player stands in hold, all told: to see its line has run. */
    private static int stock(ServerPlayer player) {
        ServerLevel level = player.level();
        if (!NookOfTheDead.fallen(level)) {
            player.sendSystemMessage(Component.literal("Not in the If: /nzw enter"));
            return 0;
        }
        net.minecraft.world.level.levelgen.structure.BoundingBox box = dev.nookofthedead.world.Refuge.box(level,
                Math.floorDiv(player.getBlockX(), Town.BLOCK), Math.floorDiv(player.getBlockZ(), Town.BLOCK));
        if (box == null) {
            player.sendSystemMessage(Component.literal("Not at an institute"));
            return 0;
        }
        java.util.Map<String, Integer> count = new java.util.TreeMap<>();
        for (net.minecraft.core.BlockPos at : net.minecraft.core.BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(at) instanceof net.minecraft.world.Container container
                    && level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.CHEST)) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty()) {
                        count.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
                    }
                }
            }
        }
        int burning = 0;
        java.util.List<String> levers = new java.util.ArrayList<>();
        for (net.minecraft.core.BlockPos at : net.minecraft.core.BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof net.minecraft.world.level.block.LeverBlock) {
                levers.add(at.getX() + " " + at.getY() + " " + at.getZ() + " " + state);
            } else if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals("generator")
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                burning++;
            }
        }
        player.sendSystemMessage(Component.literal("refuge chests: " + count + "; generators burning: " + burning + "; levers: " + levers));
        return 1;
    }

    private static int what(ServerPlayer player) {
        long seed = player.level().getSeed();
        int x = player.getBlockX();
        int z = player.getBlockZ();
        Town.Plan plan = Town.plan(seed, x - 8, z - 8);
        StringBuilder out = new StringBuilder("at " + x + " " + z + " " + dev.nookofthedead.world.Region.at(seed, x, z) + " "
                + dev.nookofthedead.world.District.of(seed, Math.floorDiv(x, Town.BLOCK), Math.floorDiv(z, Town.BLOCK)) + ":");
        for (Town.Lot lot : plan.lots()) {
            if (lot.covers(x, z)) {
                out.append(" ").append(lot.use()).append(" ").append(lot.building().map(Object::toString).orElse("-")).append(" ")
                        .append(lot.rotation()).append(" ").append(lot.mirror()).append(" corner ").append(lot.corner().toShortString())
                        .append(" size ").append(lot.size());
            }
        }
        player.sendSystemMessage(Component.literal(out.toString()));
        return 1;
    }

    /**
     * For checking the town's ground: along the way the player looks, the next eleven columns - what each was planned to be
     * (street, alley, lot; built on, paved) and how high, and what stands there now.
     */
    /** The ground as made round the player, checked for seams at the chunks' edges and streets off their height ({@link dev.nookofthedead.world.SeamScan}). */
    private static int seams(ServerPlayer player, int radius) {
        if (!NookOfTheDead.fallen(player.level())) {
            player.sendSystemMessage(Component.literal("Not in the If: /nzw enter"));
            return 0;
        }
        var report = dev.nookofthedead.world.SeamScan.scan(player.level(), player.getBlockX(), player.getBlockZ(), radius);
        List<String> lines = dev.nookofthedead.world.SeamScan.write(player.level(), player.getBlockX(), player.getBlockZ(), radius, report);
        for (String line : lines.subList(0, Math.min(8, lines.size()))) {
            player.sendSystemMessage(Component.literal(line));
        }
        return report.seams().size();
    }

    /** For looking at the capital: to the column {@code dx}, {@code dz} from its middle, on its ground, facing {@code yaw}. */
    private static int capgo(ServerPlayer player, int dx, int dz, int yaw) {
        ServerLevel level = player.level();
        int[] c = dev.nookofthedead.world.Capital.centre(level.getSeed());
        var col = dev.nookofthedead.world.Capital.column(level.getSeed(), c[0] + dx, c[1] + dz);
        int y = col == null ? 90 : col.ground() + 1;
        level.getChunk((c[0] + dx) >> 4, (c[1] + dz) >> 4);
        player.teleportTo(level, c[0] + dx + 0.5, y, c[1] + dz + 0.5, java.util.Set.<Relative>of(), yaw, 0, true);
        player.sendSystemMessage(Component.literal("capital " + dx + " " + dz + ": " + (col == null ? "outside" : col.kind() + " ground " + col.ground())));
        return 1;
    }

    private static int groundCheck(ServerPlayer player) {
        ServerLevel level = player.level();
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        Direction look = player.getDirection();
        Town.Plan plan = Town.plan(level.getSeed(), px - 8, pz - 8);
        StringBuilder out = new StringBuilder("ground " + look + ":");
        for (int d = 0; d <= 8; d++) {
            int x = px + look.getStepX() * d;
            int z = pz + look.getStepZ() * d;
            int a = x - plan.x0();
            int b = z - plan.z0();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            out.append(String.format(" [%d,%d %s%s%s plan %d now %d %s]", x, z, plan.kind()[a][b].name().charAt(0),
                    plan.built()[a][b] ? "b" : "", plan.paved()[a][b] ? "p" : "", plan.top()[a][b], y,
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).getPath()));
        }
        player.sendSystemMessage(Component.literal(out.toString()));
        return 1;
    }

    /**
     * For checking the town's ground from outside the game: the 64 x 64 columns round the player as planned, written to
     * {@code nzw_groundmap.txt} in the game's folder - what each is (S street, A alley, p paved before a lot, and under a lot
     * B building, C court, F field, Y yard, R rubble, . open ground), then each one's height above or below the surface.
     */
    private static int groundMap(ServerPlayer player) {
        long seed = player.level().getSeed();
        int x0 = player.getBlockX() - 32;
        int z0 = player.getBlockZ() - 32;
        char[][] kind = new char[64][64];
        int[][] height = new int[64][64];
        for (int tx = 0; tx < 4; tx++) {                                  // a plan for each 16 x 16
            for (int tz = 0; tz < 4; tz++) {
                Town.Plan plan = Town.plan(seed, x0 + 16 * tx - 1, z0 + 16 * tz - 1);
                for (int a = 1; a <= 16; a++) {
                    for (int b = 1; b <= 16; b++) {
                        int x = plan.x0() + a;
                        int z = plan.z0() + b;
                        char c;
                        if (plan.kind()[a][b] != Town.Ground.LOT) {
                            c = plan.kind()[a][b] == Town.Ground.STREET ? 'S' : 'A';
                        } else if (plan.paved()[a][b]) {
                            c = 'p';
                        } else {
                            c = plan.lots().stream().filter(l -> l.covers(x, z)).findFirst()
                                    .map(l -> l.size() == Town.COURT ? 'C' : switch (l.use()) {
                                        case BUILDING -> 'B';
                                        case FIELD -> 'F';
                                        case YARD -> 'Y';
                                        case RUBBLE -> 'R';
                                    }).orElse('.');
                        }
                        kind[x - x0][z - z0] = c;
                        height[x - x0][z - z0] = plan.top()[a][b] - Town.SURFACE;
                    }
                }
            }
        }
        StringBuilder kinds = new StringBuilder("x " + x0 + ".." + (x0 + 63) + ", z " + z0 + ".." + (z0 + 63) + " (rows are z, north at the top)\n");
        StringBuilder heights = new StringBuilder("\nheights above " + Town.SURFACE + "\n");
        for (int dz = 0; dz < 64; dz++) {
            for (int dx = 0; dx < 64; dx++) {
                kinds.append(kind[dx][dz]);
                heights.append(String.format("%3d", height[dx][dz]));
            }
            kinds.append('\n');
            heights.append('\n');
        }
        java.nio.file.Path out = player.level().getServer().getServerDirectory().resolve("nzw_groundmap.txt");
        try {
            java.nio.file.Files.writeString(out, kinds.toString() + heights);
        } catch (java.io.IOException e) {
            player.sendSystemMessage(Component.literal("Could not write " + out + ": " + e.getMessage()));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Ground map: " + out));
        return 1;
    }

    /** To the middle of the nearest block of a region (city, wilds, settlement, farmland), high over it. */
    private static int region(ServerPlayer player, String kind) {
        ServerLevel level = player.level();
        if (!NookOfTheDead.fallen(level)) {
            player.sendSystemMessage(Component.literal("Not in the If: /nzw enter"));
            return 0;
        }
        dev.nookofthedead.world.Region want;
        try {
            want = dev.nookofthedead.world.Region.valueOf(kind.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("No region " + kind + ": " + java.util.Arrays.toString(dev.nookofthedead.world.Region.values())));
            return 0;
        }
        int bx0 = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz0 = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        for (int r = 0; r <= FIND_REACH; r++) {
            for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                    if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) == r && dev.nookofthedead.world.Region.of(level.getSeed(), bx, bz) == want) {
                        double x = bx * Town.BLOCK + Town.BLOCK / 2.0;
                        double z = bz * Town.BLOCK + Town.BLOCK / 2.0;
                        player.teleportTo(level, x, Town.SURFACE + 60, z, java.util.Set.<Relative>of(), 0, 90, true);
                        player.sendSystemMessage(Component.literal(kind + " at " + (int) x + " " + (int) z));
                        return 1;
                    }
                }
            }
        }
        player.sendSystemMessage(Component.literal("No " + kind + " within " + FIND_REACH + " blocks of the grid"));
        return 0;
    }

    private static int quarter(ServerPlayer player, String kind) {
        ServerLevel level = player.level();
        if (!NookOfTheDead.fallen(level)) {
            player.sendSystemMessage(Component.literal("Not in the If: /nzw enter"));
            return 0;
        }
        dev.nookofthedead.world.District want;
        try {
            want = dev.nookofthedead.world.District.valueOf(kind.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("No quarter " + kind + ": " + java.util.Arrays.toString(dev.nookofthedead.world.District.values())));
            return 0;
        }
        int bx0 = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz0 = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        for (int r = 0; r <= FIND_REACH; r++) {
            for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                    if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) == r && dev.nookofthedead.world.District.of(level.getSeed(), bx, bz) == want) {
                        double x = bx * Town.BLOCK + Town.BLOCK / 2.0;
                        double z = bz * Town.BLOCK + Town.BLOCK / 2.0;
                        player.teleportTo(level, x, Town.SURFACE + 60, z, java.util.Set.<Relative>of(), 0, 90, true);
                        player.sendSystemMessage(Component.literal(kind + " at " + (int) x + " " + (int) z));
                        return 1;
                    }
                }
            }
        }
        player.sendSystemMessage(Component.literal("No " + kind + " within " + FIND_REACH + " blocks of the grid"));
        return 0;
    }

    /** How far {@code find} looks, in blocks of the town's grid. */
    private static final int FIND_REACH = 20;

    private static int infection(ServerPlayer player, int seconds) {
        player.setData(Infection.LEFT, seconds * 20);
        player.sendSystemMessage(Component.literal("Infection: " + seconds + " s left"));
        return 1;
    }

    private static int left(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(Infection.infected(player) ? "Infection: " + Infection.left(player) / 20 + " s left" : "Not infected"));
        return 1;
    }

    private static int clear(ServerPlayer player) {
        Infection.clear(player);
        player.sendSystemMessage(Component.literal("Infection cleared"));
        return 1;
    }

    private static int diary(ServerPlayer player, int n) {
        net.minecraft.world.item.ItemStack book = Diaries.book(n);
        player.getInventory().placeItemBackInInventory(book, net.minecraft.util.Prediction.SERVER_ONLY);   // at the feet if it is full
        return 1;
    }

    /**
     * Whether {@code name} will do for a template's: an id's path (lower case, digits, _ - . /), and no step out of the
     * folder it is written to - no {@code .} or {@code ..} part, no empty one (the review, 2026-10-18: {@code ../} wrote
     * beside it).
     */
    static boolean templateName(String name) {
        if (name.isEmpty() || !net.minecraft.resources.Identifier.isValidPath(name)) {
            return false;
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Template {@code town/<name>} saved again with each of its things once (the review, 2026-10-19: rebuilt and exported
     * over and over, the deep part held one armour stand forty times - placing a template puts its things down again over
     * the ones still standing), to {@code <world>/generated/nookofthedead/structure/town/<name>.nbt}.
     */
    private static int tidy(CommandSourceStack source, String name) {
        if (!templateName(name)) {
            source.sendFailure(Component.literal("A template's name is lower case: " + name.toLowerCase(java.util.Locale.ROOT)));
            return 0;
        }
        StructureTemplate template = source.getServer().getStructureTemplateManager().get(NookOfTheDead.id("town/" + name)).orElse(null);
        if (template == null) {
            source.sendFailure(Component.literal("No template town/" + name));
            return 0;
        }
        List<StructureTemplate.StructureEntityInfo> things = ((dev.nookofthedead.mixin.StructureTemplateAccessor) template).nookofthedead$entities();
        int before = things.size();
        java.util.Set<String> seen = new java.util.HashSet<>();
        // one thing of a kind on one spot: the copies differ only in their ids and in a turn put down again (180, -180,
        // -540: the review, 2026-10-19)
        things.removeIf(info -> !seen.add(info.blockPos + "|" + info.pos + "|" + info.nbt.getStringOr("id", "")));
        Path file = source.getServer().getWorldPath(LevelResource.GENERATED_DIR)
                .resolve(NookOfTheDead.MODID).resolve("structure").resolve("town").resolve(name + ".nbt");
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(template.save(new CompoundTag()), file);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write " + file + ": " + e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Tidied " + name + ": " + before + " things to " + things.size() + " -> " + file), true);
        return 1;
    }

    private static int export(CommandSourceStack source, String name, BlockPos a, BlockPos b) {
        if (!templateName(name)) {
            // a template's name is an id: lower case, digits, _ - . / (r1: "S9" made a file nothing could read)
            source.sendFailure(Component.literal("A template's name is lower case: " + name.toLowerCase(java.util.Locale.ROOT)));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPos.min(a, b);
        BlockPos to = BlockPos.max(a, b);
        int top = from.getY();
        for (int y = to.getY(); y >= from.getY() && top == from.getY(); y--) {
            for (BlockPos p : BlockPos.betweenClosed(from.getX(), y, from.getZ(), to.getX(), y, to.getZ())) {
                if (!level.getBlockState(p).isAir()) {
                    top = y;
                    break;
                }
            }
        }
        Vec3i size = new Vec3i(to.getX() - from.getX() + 1, top - from.getY() + 1, to.getZ() - from.getZ() + 1);
        StructureTemplate template = new StructureTemplate();
        // with the things in it - frames, paintings, armour stands, posters (BUILDING.md 7) - but not what only passes
        // through: mobs, dropped items and the like (r1: everything was left out)
        template.fillFromWorld(level, from, size, true, List.of(Blocks.STRUCTURE_VOID));
        ((dev.nookofthedead.mixin.StructureTemplateAccessor) template).nookofthedead$entities().removeIf(info -> !kept(info.nbt));
        template.setAuthor("nookofthedead");
        Path file = source.getServer().getWorldPath(LevelResource.GENERATED_DIR)
                .resolve(NookOfTheDead.MODID).resolve("structure").resolve("town").resolve(name + ".nbt");
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(template.save(new CompoundTag()), file);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write " + file + ": " + e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Saved " + name + " (" + size.getX() + " x " + size.getY() + " x " + size.getZ() + ") to " + file), true);
        return 1;
    }

    /**
     * A variant of {@code building} taken out of its changed copy between two corners (the copy's plot, from the same
     * height as the building was, y -63): only what differs from the building as the mod has it, saved as
     * {@code town/variant/<building>/<name>} beside the exported buildings (Variants).
     */
    private static int variant(CommandSourceStack source, String building, String name, BlockPos a, BlockPos b) {
        if (!templateName(building) || !templateName(name)) {
            source.sendFailure(Component.literal("Names are lower case: " + building.toLowerCase(java.util.Locale.ROOT) + " "
                    + name.toLowerCase(java.util.Locale.ROOT)));
            return 0;
        }
        ServerLevel level = source.getLevel();
        var manager = source.getServer().getStructureTemplateManager();
        var base = manager.get(NookOfTheDead.id("town/" + building));
        if (base.isEmpty()) {
            source.sendFailure(Component.literal("No building town/" + building + " in the mod"));
            return 0;
        }
        Vec3i size = base.get().getSize();
        BlockPos from = BlockPos.min(a, b);
        BlockPos to = BlockPos.max(a, b);
        if (to.getX() - from.getX() + 1 != size.getX() || to.getZ() - from.getZ() + 1 != size.getZ()) {
            source.sendFailure(Component.literal("The copy's plot must be the building's size, " + size.getX() + " x " + size.getZ()));
            return 0;
        }
        int top = from.getY() + size.getY() - 1;                     // the building's height at least, more if the copy is taller
        for (int y = to.getY(); y > top; y--) {
            boolean any = false;
            for (BlockPos p : BlockPos.betweenClosed(from.getX(), y, from.getZ(), to.getX(), y, to.getZ())) {
                if (!level.getBlockState(p).isAir()) {
                    any = true;
                    break;
                }
            }
            if (any) {
                top = y;
                break;
            }
        }
        StructureTemplate copy = new StructureTemplate();
        copy.fillFromWorld(level, from, new Vec3i(size.getX(), top - from.getY() + 1, size.getZ()), true, List.of(Blocks.STRUCTURE_VOID));
        ((dev.nookofthedead.mixin.StructureTemplateAccessor) copy).nookofthedead$entities().removeIf(info -> !kept(info.nbt));
        StructureTemplate patch = dev.nookofthedead.world.Variants.patch(base.get(), copy,
                level.holderLookup(net.minecraft.core.registries.Registries.BLOCK));
        if (patch == null) {
            source.sendFailure(Component.literal("Nothing differs from town/" + building));
            return 0;
        }
        patch.setAuthor("nookofthedead");
        Path file = source.getServer().getWorldPath(LevelResource.GENERATED_DIR).resolve(NookOfTheDead.MODID).resolve("structure")
                .resolve("town").resolve("variant").resolve(building).resolve(name + ".nbt");
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(patch.save(new CompoundTag()), file);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write " + file + ": " + e.getMessage()));
            return 0;
        }
        int blocks = dev.nookofthedead.world.Variants.changed(patch).size();
        source.sendSuccess(() -> Component.literal("Saved variant " + building + "/" + name + " (" + blocks + " blocks differ) to " + file), true);
        return 1;
    }

    /**
     * Whether an exported thing stays with the building: a fixture (MISC: frames, stands, posters), not a mob, drop or
     * shot. Public for tests.
     */
    public static boolean kept(CompoundTag entity) {
        return java.util.Optional.ofNullable(net.minecraft.resources.Identifier.tryParse(entity.getStringOr("id", "")))
                .flatMap(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE::getOptional)
                .filter(type -> type.getCategory() == net.minecraft.world.entity.MobCategory.MISC)
                .filter(type -> !PASSING.contains(net.minecraft.world.entity.EntityType.getKey(type).toString()))
                .isPresent();
    }

    private static final java.util.Set<String> PASSING = java.util.Set.of("minecraft:item", "minecraft:experience_orb",
            "minecraft:falling_block", "minecraft:tnt", "minecraft:lightning_bolt", "minecraft:arrow", "minecraft:spectral_arrow",
            "minecraft:trident", "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl", "minecraft:fishing_bobber",
            "minecraft:area_effect_cloud", "minecraft:marker", "nookworks:lode_pearl", "nookofthedead:remains");

    private ZwCommands() {}
}
