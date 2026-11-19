package vn.sunvibe.listing.core.domain.model.listing;

import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * The public code of a listing (listing-detail BR-15, LD-16; ADR-012 decision 3, D12-3): {@code TD-} and two groups of
 * four characters from 2 to 9 and A to Z without I and O, drawn at random at creation, unique among every code ever
 * issued, never reissued and never changed. It tells neither the creation time nor a count, and the internal id never
 * becomes public.
 */
public record ListingCode(String value) {

    /** The 32 characters of a code: 2 to 9 and A to Z without I and O. */
    public static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final Pattern FORM = Pattern.compile("^TD-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$");

    public ListingCode {
        if (value == null || !FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("a listing code is TD- and two groups of four code characters");
        }
    }

    /** A code of eight draws of {@code random} over the alphabet: {@code TD-}, four, a dash, four. */
    public static ListingCode draw(RandomGenerator random) {
        StringBuilder code = new StringBuilder("TD-");
        for (int index = 0; index < 8; index++) {
            if (index == 4) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new ListingCode(code.toString());
    }

    public static ListingCode of(String value) {
        return new ListingCode(value);
    }
}
