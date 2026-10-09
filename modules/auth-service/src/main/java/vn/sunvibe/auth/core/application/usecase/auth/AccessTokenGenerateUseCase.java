package vn.sunvibe.auth.core.application.usecase.auth;

public interface AccessTokenGenerateUseCase {

    String generateAccessToken(String username);

}
