package vn.sunvibe.auth.core.application.usecase.user;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a person typed to sign in, once it passed its policy. The typed name it gives Spring Security is the only way a
 * sign-in reaches the account lookup, so an email can never be read as a phone or the other way round
 * (auth-email-access PD-1).
 */
public sealed interface LoginIdentifier {

    String EMAIL_PREFIX = "email:";
    String PHONE_PREFIX = "phone:";
    String LEGACY_PREFIX = "legacy:";

    /** The name handed to the authentication manager. */
    String authenticationName();

    /** Reads such a name back; anything else is no login name. */
    static Optional<LoginIdentifier> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        if (name.startsWith(EMAIL_PREFIX)) {
            return Optional.of(new EmailLogin(name.substring(EMAIL_PREFIX.length())));
        }
        if (name.startsWith(PHONE_PREFIX)) {
            return Optional.of(new PhoneLogin(name.substring(PHONE_PREFIX.length()), null));
        }
        if (name.startsWith(LEGACY_PREFIX)) {
            return Optional.of(new LegacyLogin(name.substring(LEGACY_PREFIX.length())));
        }
        return Optional.empty();
    }

    /** @param key the canonical email */
    record EmailLogin(String key) implements LoginIdentifier {
        @Override
        public String authenticationName() {
            return EMAIL_PREFIX + key;
        }

        /** Without the address: it is personal data. */
        @Override
        public String toString() {
            return "EmailLogin[hidden]";
        }
    }

    /**
     * @param key the E.164 phone
     * @param raw the value as typed
     */
    record PhoneLogin(String key, String raw) implements LoginIdentifier {
        @Override
        public String authenticationName() {
            return PHONE_PREFIX + key;
        }

        /** Without the number: it is personal data. */
        @Override
        public String toString() {
            return "PhoneLogin[hidden]";
        }
    }

    /** @param raw a value no phone key answers for, looked up as the exact stored string */
    record LegacyLogin(String raw) implements LoginIdentifier {

        private static final Pattern OLD_SHAPE = Pattern.compile("^[0-9]{10,12}$");

        /** The shape the old login took, which also keeps NUL away from the database. */
        public boolean hasOldShape() {
            return raw != null && OLD_SHAPE.matcher(raw).matches();
        }

        @Override
        public String authenticationName() {
            return LEGACY_PREFIX + raw;
        }

        /** Without the value: it is personal data. */
        @Override
        public String toString() {
            return "LegacyLogin[hidden]";
        }
    }
}
