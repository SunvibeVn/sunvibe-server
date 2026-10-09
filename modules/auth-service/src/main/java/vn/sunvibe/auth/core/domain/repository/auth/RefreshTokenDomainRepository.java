package vn.sunvibe.auth.core.domain.repository.auth;


import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;

public interface RefreshTokenDomainRepository {

    void saveRefreshToken(RefreshToken refreshToken);

    RefreshToken findRefreshTokenByTokenHash(byte[] hash);

}
