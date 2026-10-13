package vn.sunvibe.auth.core.application.usecase.auth;

/**
 * Housekeeping of the pre-registration phone challenges, which pile up with every code sent.
 */
public interface RegistrationChallengePurgeUseCase {

    /**
     * Deletes challenges whose code and registration token expired long enough ago to be of no further use.
     *
     * @return the number of deleted challenges
     */
    int execute();

}
