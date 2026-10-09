package vn.sunvibe.auth.core.shared.enums;

import lombok.Getter;

/**
 * Request fields an {@code AuthValidationException} can point at.
 */
@Getter
public enum AuthFieldName {

    /*────────── USER ──────────*/

    USER_ID("id", "User ID"),
    USER_NAME("username", "User Name"),
    EMAIL("email", "Email"),
    PROFILE_IMAGE("profileImage", "Profile Image"),
    PASSWORD("password", "Password"),
    NEW_PASSWORD("newPassword", "New Password"),
    CURRENT_PASSWORD("currentPassword", "Current Password"),
    ID_CARD_NUMBER("idCardNumber", "ID Card Number");

    /** Field name as it appears in the JSON request. */
    private final String fieldName;
    /** User-friendly name. */
    private final String displayName;

    AuthFieldName(String fieldName, String displayName) {
        this.fieldName = fieldName;
        this.displayName = displayName;
    }

}
