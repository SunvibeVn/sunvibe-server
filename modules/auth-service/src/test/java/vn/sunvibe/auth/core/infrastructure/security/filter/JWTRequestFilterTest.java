package vn.sunvibe.auth.core.infrastructure.security.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A signed token is only as good as the account behind it right now: the
 * filter must reload the user on every request and refuse tokens for a
 * locked, deleted or different (same username, new id) account.
 * Tokens are minted by the real {@link JWTProvider} while the user is
 * ACTIVE; the fake user store is then changed before the request.
 */
class JWTRequestFilterTest {

    private static final String SECRET = "bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=";
    private static final String USERNAME = "0912345678";
    private static final String USER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    /** Current state of the user store, as the filter will see it. */
    private final Map<String, CustomUserDetails> users = new HashMap<>();

    private final UserDetailsService userDetailsService = username -> {
        CustomUserDetails user = users.get(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
        return user;
    };

    private JWTProvider jwtProvider;
    private JWTRequestFilter filter;

    @BeforeEach
    void setUp() {
        jwtProvider = new JWTProvider(userDetailsService);
        ReflectionTestUtils.setField(jwtProvider, "jwtSecretKey", SECRET);
        ReflectionTestUtils.setField(jwtProvider, "jwtExpirationMinutes", 60L);
        filter = new JWTRequestFilter(jwtProvider, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static CustomUserDetails user(String id, UserStatus status, String... authorities) {
        CustomUserDetails details = new CustomUserDetails();
        details.setUserId(id);
        details.setUsername(USERNAME);
        details.setPassword("hashed");
        details.setEmail("a@example.com");
        details.setAuthorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
        details.setUserStatus(status);
        return details;
    }

    /** Mint a real access token while the account is ACTIVE with ROLE_USER. */
    private String issueTokenForActiveUser() {
        users.put(USERNAME, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));
        return jwtProvider.generateAccessToken(USERNAME);
    }

    /** Runs the filter and returns the resulting authentication (or null); asserts the chain continued. */
    private Authentication filterWith(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/query/me");
        request.setServletPath("/api/users/query/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, JWTProvider.TOKEN_PREFIX + token);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "filter chain must always continue");
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void activeUserWithMatchingUid_isAuthenticatedWithReloadedAuthorities() throws Exception {
        String token = issueTokenForActiveUser();
        // Roles changed after the token was issued: the filter must use the fresh ones, not the "rol" claim
        users.put(USERNAME, user(USER_ID, UserStatus.ACTIVE, "ROLE_HOST"));

        Authentication authentication = filterWith(token);

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals(USERNAME, authentication.getPrincipal());
        assertEquals(Set.of("ROLE_HOST"), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }

    @Test
    void userLockedAfterLogin_isNotAuthenticated() throws Exception {
        String token = issueTokenForActiveUser();
        users.put(USERNAME, user(USER_ID, UserStatus.LOCK, "ROLE_USER"));

        assertNull(filterWith(token));
    }

    @Test
    void userTerminatedAfterLogin_isNotAuthenticated() throws Exception {
        String token = issueTokenForActiveUser();
        users.put(USERNAME, user(USER_ID, UserStatus.TERMINATED, "ROLE_USER"));

        assertNull(filterWith(token));
    }

    @Test
    void userDeletedAfterLogin_isNotAuthenticated() throws Exception {
        String token = issueTokenForActiveUser();
        // A soft-deleted row is invisible to lookups, so the user is simply not found
        users.remove(USERNAME);

        assertNull(filterWith(token));
    }

    @Test
    void usernameReRegisteredByDifferentAccount_isNotAuthenticated() throws Exception {
        String token = issueTokenForActiveUser();
        // Old account deleted, same username registered again with a new id
        users.put(USERNAME, user("01BX5ZZKBKACTAV9WEVGEMMVRZ", UserStatus.ACTIVE, "ROLE_USER"));

        assertNull(filterWith(token));
    }

    @Test
    void tokenWithoutUid_isNotAuthenticated() throws Exception {
        users.put(USERNAME, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));
        Instant now = Instant.now();
        String tokenWithoutUid = Jwts.builder()
                .subject(USERNAME)
                .claim("rol", List.of("ROLE_USER"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(60, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        assertNull(filterWith(tokenWithoutUid));
    }
}
