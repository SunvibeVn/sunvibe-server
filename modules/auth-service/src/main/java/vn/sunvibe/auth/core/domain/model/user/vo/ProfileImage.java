package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

/**
 * URL of the user's avatar; a default picture is used until one is uploaded.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class ProfileImage extends ValueObject<ProfileImage> {

    private static final String DEFAULT_URL = "https://www.vecteezy.com/free-vector/default-user";

    private final String url;

    /**
     * Builds from user input; {@code null} means "use the default picture".
     *
     * @throws AuthValidationException if the URL is not http(s)
     */
    public static ProfileImage create(String url) {
        if (url == null) {
            return new ProfileImage(DEFAULT_URL);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new AuthValidationException(AuthFieldName.PROFILE_IMAGE, "Profile image URL must be valid.");
        }
        return new ProfileImage(url);
    }

    /** Rebuilds from persisted data. */
    public static ProfileImage from(String url) {
        return new ProfileImage(url);
    }

    @Override
    protected boolean equalsCore(ProfileImage other) {
        return this.url.equals(other.url);
    }

    @Override
    protected int hashCodeCore() {
        return url.hashCode();
    }

}
