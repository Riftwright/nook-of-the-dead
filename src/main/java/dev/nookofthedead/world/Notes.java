package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Notes left where they were written (0.3, set down as books with Nookworks 1.1): unlike the diaries ({@link Diaries}),
 * each belongs to a place - the institute's labs, the second institute, the base above the cloud, the camps, the
 * library - and is always the same one there. Traces only: what they tell is never told outright. Their words are in
 * the language files ({@code note.nookofthedead.<id>.<page>}, the title {@code .title}); the user reads them all before
 * a release (2026-10-20: "完了したら手記の内容を一覧にして、見せてください").
 */
public final class Notes {
    /** A note: its id, and how many pages. */
    public record Note(String id, int pages) {}

    public static final List<Note> INSTITUTE = List.of(new Note("lab_1", 2), new Note("lab_2", 1), new Note("lab_3", 2), new Note("lab_4", 1));
    public static final List<Note> ASCENT = List.of(new Note("ascent_1", 1), new Note("ascent_2", 1));
    public static final List<Note> BASE = List.of(new Note("base_1", 2), new Note("base_2", 1), new Note("base_3", 2));
    public static final List<Note> CAMP = List.of(new Note("camp_1", 1), new Note("camp_2", 1), new Note("camp_3", 1));
    public static final List<Note> LIBRARY = List.of(new Note("library_1", 1), new Note("library_2", 1));
    /** The capital's proclamations, posted in its squares as the fever came (CapitalStory, 2026-10-24), first to last. */
    public static final List<Note> CAPITAL = List.of(new Note("capital_1", 1), new Note("capital_2", 1), new Note("capital_3", 1),
            new Note("capital_4", 1), new Note("capital_5", 1));

    /** Every note, for the tests and the list the user reads. */
    /**
     * The notes that are found in one place only, set there by hand (the story's review, 2026-10-23, STORY.md): the
     * refuge's preparation of the suppressant from Seven's blood; at the second institute, the two who stayed with two
     * of the launch keys; in the sky base, where the keys were.
     */
    public static final Note PREPARATION = new Note("lab_5", 1);
    public static final Note STAYING = new Note("ascent_3", 1);
    public static final Note KEYS = new Note("base_4", 1);
    /** What the ones who called it salvation sang, on the lectern in their church ({@link Salvation}, 2026-10-23). */
    public static final Note SALVATION = new Note("salvation_1", 1);
    public static final List<Note> PLACED = List.of(PREPARATION, STAYING, KEYS, SALVATION);

    public static final List<List<Note>> ALL = List.of(INSTITUTE, ASCENT, BASE, CAMP, LIBRARY, CAPITAL, PLACED);

    /** {@code note} as a written book: its own title, its pages. */
    public static ItemStack book(Note note) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int page = 1; page <= note.pages(); page++) {
            pages.add(Filterable.passThrough(Component.translatable("note.nookofthedead." + note.id() + "." + page)));
        }
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Note"), "?", 3, pages, true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("note.nookofthedead." + note.id() + ".title"));
        return book;
    }

    /** One of {@code notes}, picked by {@code random}. */
    public static ItemStack any(List<Note> notes, RandomSource random) {
        return book(notes.get(random.nextInt(notes.size())));
    }

    private Notes() {}
}
