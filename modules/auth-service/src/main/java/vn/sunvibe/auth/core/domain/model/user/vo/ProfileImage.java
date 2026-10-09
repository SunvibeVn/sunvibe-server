package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

@RequiredArgsConstructor
@Getter
public final class ProfileImage extends ValueObject<ProfileImage> {

    private final String url;

    // Build from new data
    public static ProfileImage create(String url) {
        if (null == url) {
            return new ProfileImage("https://www.vecteezy.com/free-vector/default-user");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new AuthValidationException(AuthFieldName.PROFILE_IMAGE, "Profile image URL must be valid.");
        }
        return new ProfileImage(url);
    }

    // Build from database
    public static ProfileImage from(String url) {
        return new ProfileImage(url);
    }

    @Override
    protected boolean equalsCore(ProfileImage other) {
        return this.url.equals(other.url);
    }

    @Override
    protected int hashCodeCore() {
        return 0;
    }

}
