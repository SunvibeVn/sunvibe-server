package vn.sunvibe.property.core.shared.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ApplicationMessage {

    public static final String ENTITY_NOT_FOUND = "Entity not found.";
    public static final String PRODUCT_NOT_FOUND = "Product not found.";
    public static final String PRODUCT_IMAGE_NOT_FOUND = "Product Image not found.";
    public static final String CATEGORY_NOT_FOUND = "Product not found.";
    public static final String PRODUCT_TYPE_NOT_FOUND = "Product type not found.";

    public static final String INVALID_EMAIL = "Email %s is invalid.";
    public static final String EMPLOYEE_NOT_FOUND = "Employee not found.";
    public static final String CUSTOMER_NOT_FOUND = "Customer not found.";
    public static final String SENDING_MAIL_FAILED = "Sending mail failed.";


    /*────────── EXCEPTION MESSAGES ──────────*/

    public static final String SERVER_ERROR = "Server error, please try again.";


    /*────────── VALIDATION MESSAGES ──────────*/

    public static final String VALIDATION_FAILED = "Validation failed.";


}
