package vn.sunvibe.auth.core.infrastructure.security.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;
import vn.sunvibe.common.security.AuthenticatedUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final String ACCESS_COOKIE = "cookie-jwt";

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
        jwtProvider = new JWTProvider(SECRET, 60, ACCESS_COOKIE);
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
        AppUser active = AppUser.create(UserId.create(USER_ID), "Nguyen Van A", Username.create(USERNAME),
                Email.create("a@example.com"), Password.create("hashed"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("USER")), Instant.now());
        active.setUserStatus(UserStatus.ACTIVE);
        return jwtProvider.issue(active);
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
        assertEquals(USERNAME, authentication.getName());
        assertEquals(Set.of("ROLE_HOST"), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }

    @Test
    void activeUser_principalExposesUserIdAndNameStaysTheUsername() throws Exception {
        String token = issueTokenForActiveUser();

        Authentication authentication = filterWith(token);

        assertNotNull(authentication);
        assertEquals(USERNAME, authentication.getName(), "getName() is still the username (created_by, CurrentUser)");
        AuthenticatedUser principal = assertInstanceOf(AuthenticatedUser.class, authentication.getPrincipal());
        assertEquals(USER_ID, principal.getUserId());
        assertFalse(authentication.getPrincipal() instanceof CustomUserDetails,
                "the principal must not carry the password hash or e-mail");
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

    /** Browser clients send the access token only as its HttpOnly cookie. */
    @Test
    void tokenInAccessCookie_isAuthenticatedWithoutAuthorizationHeader() throws Exception {
        String token = issueTokenForActiveUser();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/query/me");
        request.setServletPath("/api/users/query/me");
        request.setCookies(new Cookie(ACCESS_COOKIE, token));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(USERNAME, authentication.getName());
    }

    @Test
    void authorizationHeaderWinsOverAccessCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/query/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, JWTProvider.TOKEN_PREFIX + "header-token");
        request.setCookies(new Cookie(ACCESS_COOKIE, "cookie-token"));

        assertEquals("header-token", filter.getJwtFromRequest(request).orElseThrow());
    }

    @Test
    void expiredToken_isNotAuthenticated() throws Exception {
        users.put(USERNAME, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String expired = Jwts.builder()
                .subject(USERNAME)
                .claim(JWTProvider.USER_ID_CLAIM, USER_ID)
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plus(15, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        assertNull(filterWith(expired));
    }

    @Test
    void refreshEndpoint_isNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh-token");
        request.setServletPath("/api/auth/refresh-token");

        assertTrue(filter.shouldNotFilter(request));
    }
}
