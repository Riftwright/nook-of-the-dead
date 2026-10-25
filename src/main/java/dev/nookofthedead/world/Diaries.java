package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Pages of the diaries left in the ruins (PLAN.md: the town falling, a family turned, no help coming, the medicine running
 * out and whether to go out for more), and, since 0.3, everyday ones as well (the user, 2026-10-19: "日記は当たり障りの
 * ないことも書いておいてほしいな"). Each is a written book whose pages are translated where it is read
 * ({@code diary.nookofthedead.<n>.<page>} in the language files), titled as a diary in the reader's language - traces,
 * never the story told.
 * <p>
 * Which diary a book is, is decided by the one who first opens it: of those they have not read yet, one ({@link #SEEN},
 * kept with the player; the user: "同じ内容の日記を何度も見ることになるのは微妙だから、種類も増やしてほしい"). Until then
 * a book in a lectern or a chest is only "a diary" ({@link #UNREAD}). Once all are read, any.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Diaries {
    /** How many diaries there are, and the pages of each (1-12 the first ones, 13 on since 0.3). */
    public static final int[] PAGES = {2, 2, 2, 1, 2, 2, 1, 2, 1, 2, 1, 1,
            1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 1, 1, 1, 2, 2, 1, 2, 1, 2, 2, 1, 2, 2, 2,
            2, 1, 1,                                                   // 37-39: the mirror, the night's sounds, the medicine handed out
            2, 2, 1,                                                   // 40-42: the refuge - shut in, the work, the one in isolation
            10, 8, 11,                                                 // 43-45: the long ones - the clerk's record, the doctor's notes, the boy's book
            6, 3, 3, 2, 3, 2, 3, 2, 2, 10,                             // 46-55: the capital's (CapitalStory) - the king, the chamberlain, the
                                                                       // gate's guard, the petition, the priest, the printer, the river
                                                                       // quarter, the fire, the camp, the council's minutes
            2, 2, 2};                                                  // 56-58: the archer over the castle gate, the bridge's baker, a mother

    /**
     * Where a diary was found, and so which it is likely to be (the story's review, 2026-10-23, STORY.md): a building's
     * own first - the inn has the man with the box, the station the last train - then the place's, then any of those
     * free to be anywhere. The sky base's and the desert's are found only there (one tells of the top of the tower,
     * and must not be read in a town before the tower is known of; the other is of the sand).
     */
    public enum Place {
        TOWN(), INN(8), CLINIC(11, 14, 44), STATION(12), CHECKPOINT(17), LAB(23), BELL(3), WORKS(7), SQUARE(5, 39, 43),
        FARM(6, 16, 19, 27, 33, 34, 38), UNDER(10), DESERT(20), SKY(29), REFUGE(40, 41, 42), SHELTER(45),
        /** The capital's (2026-10-24): its houses, its castle, the camp outside its wall, its cathedral, its council. */
        CAPITAL(49, 51, 58), CASTLE(46, 47, 56), CAMP(54), CATHEDRAL(50), COUNCIL(55),
        /** The city wall's gate towers; the burnt bank's quays; the near end of the bridges, where the fence was. */
        GATEHOUSE(48), RIVERSIDE(52), BRIDGE(57, 53);

        final List<Integer> own;

        Place(Integer... own) {
            this.own = List.of(own);
        }

        /**
         * Once its own are read, what else may turn up here: anywhere's, but in the capital only the everyday ones (the
         * story's review, 2026-10-24: the last train, the checkpoint letting people through, the tents in the square for
         * those from outside - none of them could have been written in a city that shut its gates and had no station).
         */
        boolean capital() {
            return this == CAPITAL || this == CASTLE || this == CAMP || this == CATHEDRAL || this == COUNCIL || this == GATEHOUSE
                    || this == RIVERSIDE || this == BRIDGE;
        }
    }

    /** Diaries found nowhere but their own place. */
    public static final java.util.Map<Integer, Place> ONLY = java.util.Map.ofEntries(java.util.Map.entry(20, Place.DESERT),
            java.util.Map.entry(29, Place.SKY), java.util.Map.entry(40, Place.REFUGE), java.util.Map.entry(41, Place.REFUGE),
            java.util.Map.entry(42, Place.REFUGE), java.util.Map.entry(43, Place.SQUARE), java.util.Map.entry(44, Place.CLINIC),
            java.util.Map.entry(45, Place.SHELTER), java.util.Map.entry(46, Place.CASTLE), java.util.Map.entry(47, Place.CASTLE),
            java.util.Map.entry(48, Place.GATEHOUSE), java.util.Map.entry(49, Place.CAPITAL), java.util.Map.entry(50, Place.CATHEDRAL),
            java.util.Map.entry(51, Place.CAPITAL), java.util.Map.entry(52, Place.RIVERSIDE), java.util.Map.entry(53, Place.BRIDGE),
            java.util.Map.entry(54, Place.CAMP), java.util.Map.entry(55, Place.COUNCIL), java.util.Map.entry(56, Place.CASTLE),
            java.util.Map.entry(57, Place.BRIDGE), java.util.Map.entry(58, Place.CAPITAL));

    /** The everyday diaries, free to be found in the capital once its own are read (Place#capital). */
    static final List<Integer> EVERYDAY = List.of(13, 15, 18, 21, 22, 24, 25, 28, 30, 31, 35);

    /** The place a diary set down now is in (the generating thread's own; the town sets it for each building). */
    private static final ThreadLocal<Place> HERE = ThreadLocal.withInitial(() -> Place.TOWN);

    /** Diaries set down from now on this thread are at {@code place} (TOWN to go back). */
    public static void at(Place place) {
        HERE.set(place == null ? Place.TOWN : place);
    }

    /** The mark on an unread diary of where it was found. */
    public static final String PLACE = "nookofthedead_place";

    /** The mark on a diary nobody has opened yet. */
    public static final String UNREAD = "nookofthedead_unread";

    /** The diaries a player has read, by number: kept through death. */
    public static final Supplier<AttachmentType<List<Integer>>> SEEN = dev.nookofthedead.infection.Infection.ATTACHMENT_TYPES.register("diaries_seen",
            () -> AttachmentType.<List<Integer>>builder(() -> List.of())
                    .serialize(Codec.INT.listOf().fieldOf("seen"), seen -> !seen.isEmpty())
                    .copyOnDeath()
                    .build());

    /** A diary as found: which one it is, its first reader decides. */
    public static ItemStack book(RandomSource random) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Diary"), "?", 3,
                List.of(Filterable.passThrough(Component.translatable("diary.nookofthedead.unread"))), true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("item.nookofthedead.diary"));
        Place place = HERE.get();
        CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> {
            tag.putBoolean(UNREAD, true);
            tag.putString(PLACE, place.name());
        });
        return book;
    }

    /** A diary as found at {@code place}. */
    public static ItemStack book(RandomSource random, Place place) {
        Place was = HERE.get();
        at(place);
        try {
            return book(random);
        } finally {
            at(was);
        }
    }

    /** Diary {@code n} (from 1), its pages. */
    public static ItemStack book(int n) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content(n));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("item.nookofthedead.diary"));
        return book;
    }

    private static WrittenBookContent content(int n) {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int page = 1; page <= PAGES[n - 1]; page++) {
            pages.add(Filterable.passThrough(Component.translatable("diary.nookofthedead." + n + "." + page)));
        }
        return new WrittenBookContent(Filterable.passThrough("Diary"), "?", 3, pages, true);
    }

    /** Whether {@code stack} is a diary nobody has opened yet. */
    public static boolean unread(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return stack.is(Items.WRITTEN_BOOK) && data != null && data.copyTag().getBooleanOr(UNREAD, false);
    }

    /** The mark on an opened diary of which one it is (so whoever else reads it has it counted as read too). */
    public static final String NUMBER = "nookofthedead_diary";

    /** {@code stack}, an unread diary, made the one {@code reader} has not read yet (any, once they have read them all). */
    public static void open(Player reader, ItemStack stack) {
        if (!unread(stack)) {
            return;
        }
        int n = choose(reader.getData(SEEN), placeOf(stack), reader.getRandom());
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, content(n));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(UNREAD);
            tag.putInt(NUMBER, n);
        });
        read(reader, stack);
    }

    /** Where an unread diary was found (TOWN for one from before places were kept). */
    static Place placeOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String name = data == null ? "" : data.copyTag().getStringOr(PLACE, "");
        try {
            return name.isEmpty() ? Place.TOWN : Place.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Place.TOWN;
        }
    }

    /**
     * Which diary one found at {@code place} is, for a reader who has read {@code seen}: of the unread, the place's own
     * first; else any free to be anywhere; all read, any that may be here.
     */
    public static int choose(List<Integer> seen, Place place, RandomSource random) {
        List<Integer> own = place.own.stream().filter(n -> !seen.contains(n)).toList();
        if (!own.isEmpty()) {
            return own.get(random.nextInt(own.size()));
        }
        List<Integer> here = new ArrayList<>();
        for (int n = 1; n <= PAGES.length; n++) {
            Place only = ONLY.get(n);
            if (place.capital() ? only == place || only == null && EVERYDAY.contains(n) : only == null || only == place) {
                here.add(n);
            }
        }
        List<Integer> left = here.stream().filter(n -> !seen.contains(n)).toList();
        List<Integer> from = left.isEmpty() ? here : left;
        return from.get(random.nextInt(from.size()));
    }

    /** {@code reader} reads {@code stack}: if it is a diary, counted as read. */
    static void read(Player reader, ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        int n = data == null ? 0 : data.copyTag().getIntOr(NUMBER, 0);
        List<Integer> seen = reader.getData(SEEN);
        if (n > 0 && !seen.contains(n)) {
            List<Integer> more = new ArrayList<>(seen);
            more.add(n);
            reader.setData(SEEN, List.copyOf(more));
        }
    }

    /** One opened in hand: decided before it opens. */
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer reader) || reader.isSpectator()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (unread(held)) {
            ItemStack rest = held.copyWithCount(held.getCount() - 1);   // a pile of them, each a diary of its own: only this one opened
            held.setCount(1);
            open(reader, held);
            if (!rest.isEmpty()) {
                reader.getInventory().placeItemBackInInventory(rest, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            reader.containerMenu.broadcastChanges();   // the hand's new pages reach the client before the book opens there
        } else if (held.is(Items.WRITTEN_BOOK)) {
            read(reader, held);
        }
    }

    /**
     * One opened on its lectern: decided before its page shows, and set on the lectern again so it knows its pages (it
     * counts them only as a book is set: left at the unread one's one page, the second could not be turned to - the
     * review, 2026-10-19). Not by a spectator, nor by one sneaking with something in hand (the lectern does not open).
     */
    @SubscribeEvent
    static void onLectern(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer reader) || reader.isSpectator()
                || reader.isSecondaryUseActive() && !event.getItemStack().isEmpty()
                || !(reader.level().getBlockEntity(event.getPos()) instanceof LecternBlockEntity lectern) || !lectern.hasBook()) {
            return;
        }
        if (unread(lectern.getBook())) {
            ItemStack book = lectern.getBook();
            open(reader, book);
            lectern.setBook(book);
            lectern.setChanged();
        } else {
            read(reader, lectern.getBook());
        }
    }

    private Diaries() {}
}
