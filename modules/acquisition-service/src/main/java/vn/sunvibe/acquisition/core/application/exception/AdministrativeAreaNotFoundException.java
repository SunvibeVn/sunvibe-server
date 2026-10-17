package vn.sunvibe.acquisition.core.application.exception;

import vn.sunvibe.shared.exception.ResourceNotFoundException;

import java.io.Serial;

/**
 * The province or ward of a territory request is not in the administrative-area catalog. The message names no code.
 */
public class AdministrativeAreaNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    private AdministrativeAreaNotFoundException(String message) {
        super(message);
    }

    public static AdministrativeAreaNotFoundException province() {
        return new AdministrativeAreaNotFoundException("Province is not in the catalog.");
    }

    public static AdministrativeAreaNotFoundException ward() {
        return new AdministrativeAreaNotFoundException("Ward is not in the catalog.");
    }
}
