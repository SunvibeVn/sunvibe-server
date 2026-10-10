package vn.sunvibe.auth.core.application.usecase.auth;

/**
 * Housekeeping of the refresh-token table, which grows by one row per refresh.
 */
public interface ExpiredRefreshTokenPurgeUseCase {

    /**
     * Deletes refresh tokens that expired long enough ago to be of no further use.
     *
     * @return the number of deleted tokens
     */
    int execute();

}
