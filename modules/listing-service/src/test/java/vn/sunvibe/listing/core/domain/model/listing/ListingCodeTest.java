package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The public listing code (listing-detail BR-15, LD-16; ADR-012 decision 3; E06-S1 spec E06S1-D6): {@code TD-} and two
 * groups of four characters from 2 to 9 and A to Z without I and O, eight draws of one random source.
 */
class ListingCodeTest {

    @Test
    void draw_fixedSequence_formAndAlphabet() {
        SequenceRandom random = new SequenceRandom(0, 1, 2, 3, 4, 5, 6, 7);

        ListingCode code = ListingCode.draw(random);

        assertEquals("TD-2345-6789", code.value());
        assertEquals(Collections.nCopies(8, 32), random.bounds);
    }

    @Test
    void draw_highestIndex_lastLetterOfTheAlphabet() {
        SequenceRandom random = new SequenceRandom(31, 31, 31, 31, 31, 31, 31, 31);

        assertEquals("TD-ZZZZ-ZZZZ", ListingCode.draw(random).value());
    }

    @Test
    void of_offTheForm_refused() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> ListingCode.of("TD-2345-678")),
                () -> assertThrows(IllegalArgumentException.class, () -> ListingCode.of("TD-2345-6780")),
                () -> assertThrows(IllegalArgumentException.class, () -> ListingCode.of("TD-I345-6789")),
                () -> assertThrows(IllegalArgumentException.class, () -> ListingCode.of("td-2345-6789")),
                () -> assertThrows(IllegalArgumentException.class, () -> ListingCode.of("SV-2345-6789")),
                () -> assertThrows(IllegalArgumentException.class, () -> ListingCode.of(null)));
    }

    @Test
    void of_wellFormed_sameValue() {
        assertEquals("TD-7K3M-Q9TX", ListingCode.of("TD-7K3M-Q9TX").value());
        assertEquals(ListingCode.of("TD-7K3M-Q9TX"), new ListingCode("TD-7K3M-Q9TX"));
    }

    /** Answers the given indexes in order and records each bound it is asked for. */
    private static final class SequenceRandom implements RandomGenerator {

        private final int[] answers;
        private final List<Integer> bounds = new ArrayList<>();
        private int next;

        SequenceRandom(int... answers) {
            this.answers = answers;
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return answers[next++];
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("the code draws with nextInt(int) only");
        }
    }
}
