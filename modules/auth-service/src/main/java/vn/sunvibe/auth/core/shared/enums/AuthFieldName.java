package vn.sunvibe.auth.core.shared.enums;

import lombok.Getter;

@Getter
public enum AuthFieldName {

    /*────────── USER ──────────*/

    USER_ID("id", "User ID"),
    USER_NAME("username", "User Name"),
    PHONE_NUMBER("phoneNumber", "Phone Number"),
    EMAIL("email", "Email"),
    PROFILE_IMAGE("profileImage", "Email"),
    PASSWORD("password", "Password"),
    NEW_PASSWORD("newPassword", "New Password"),
    CURRENT_PASSWORD("currentPassword", "Current Password"),
    CONFIRM_PASSWORD("confirmPassword", "Confirm Password"),
    FULL_NAME("fullName", "Full Name"),
    COUNTRY_CODE("countryCode", "Country Code");


    // Getters
    private final String fieldName;      // Database/JSON field name
    private final String displayName;    // User-friendly name

    AuthFieldName(String fieldName, String displayName) {
        this.fieldName = fieldName;
        this.displayName = displayName;
    }

}
