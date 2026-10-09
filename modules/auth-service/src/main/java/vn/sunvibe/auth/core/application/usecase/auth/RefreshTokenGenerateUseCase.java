package vn.sunvibe.auth.core.application.usecase.auth;

public interface RefreshTokenGenerateUseCase {

    String generateRefreshToken(String userId, String fingerprint);

}
