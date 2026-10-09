package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.usecase.auth.AccessTokenGenerateUseCase;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;

@Service
@AllArgsConstructor
@Log4j2
public class AccessTokenGenerateService implements AccessTokenGenerateUseCase {

    private final JWTProvider jwtProvider;

    @Override
    public String generateAccessToken(String username) {
        log.debug("AccessTokenGenerateService: Generate access token for user");
        return jwtProvider.generateAccessToken(username);
    }
}
