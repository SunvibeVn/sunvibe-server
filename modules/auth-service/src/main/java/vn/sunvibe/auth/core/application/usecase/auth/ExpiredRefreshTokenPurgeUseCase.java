package vn.sunvibe.auth.core.application.usecase.auth;

public interface ExpiredRefreshTokenPurgeUseCase {

    /**
     * Deletes refresh tokens that expired long enough ago to be of no further use.
     *
     * @return the number of deleted tokens
     */
    int execute();

}
