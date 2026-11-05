package vn.sunvibe.auth.core.infrastructure.security.filter;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import io.jsonwebtoken.JwtBuilder;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.infrastructure.security.provider.CustomUserDetailsService;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;
import vn.sunvibe.shared.security.AuthenticatedUser;

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
 * A signed token is only as good as the account behind it right now: the filter reloads the user by {@code uid} on
 * every request and refuses tokens for a locked, deleted or different account, or for an older credential version.
 * Tokens are minted by the real {@link JWTProvider} while the user is ACTIVE; the fake user store is then changed
 * before the request. Tokens of the previous release ({@code sub} = phone, no {@code cv}) are minted by hand.
 */
class JWTRequestFilterTest {

    private static final String SECRET = "bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=";
    private static final String USERNAME = "0912345678";
    private static final String OTHER_PHONE = "0987654321";
    private static final String USER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
    private static final String OTHER_USER_ID = "01BX5ZZKBKACTAV9WEVGEMMVRZ";
    /** The legacy access-token cookie; the filter no longer reads it (W10-60). */
    private static final String ACCESS_COOKIE = "cookie-jwt";

    /** Current state of the user store by user id, as the filter will see it. */
    private final Map<String, CustomUserDetails> users = new HashMap<>();

    private final CustomUserDetailsService userDetailsService = new CustomUserDetailsService(null, null) {
        @Override
        public CustomUserDetails loadUserById(String userId) {
            CustomUserDetails user = users.get(userId);
            if (user == null) {
                throw new UsernameNotFoundException("Account not found.");
            }
            return user;
        }
    };

    private JWTProvider jwtProvider;
    private JWTRequestFilter filter;

    @BeforeEach
    void setUp() {
        jwtProvider = new JWTProvider(SECRET, 60);
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
        details.setPhone(USERNAME);
        details.setPassword("hashed");
        details.setEmail("a@example.com");
        details.setAuthorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
        details.setUserStatus(status);
        return details;
    }

    /** Mint a real access token while the account is ACTIVE with ROLE_USER. */
    private String issueTokenForActiveUser() {
        users.put(USER_ID, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));
        AppUser active = AuthTestUsers.withPhone(UserId.create(USER_ID), "Nguyen Van A", USERNAME,
                Email.create("a@example.com"), Password.create("hashed"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("USER")), Instant.now());
        active.setUserStatus(UserStatus.ACTIVE);
        return jwtProvider.issue(active);
    }

    /** A token as the previous release issued it: {@code sub} is the phone, and there is no {@code cv}. */
    private static String previousReleaseToken(String subject, String uid) {
        return tokenBuilder().subject(subject).claim(JWTProvider.USER_ID_CLAIM, uid).compact();
    }

    private static JwtBuilder tokenBuilder() {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("rol", List.of("ROLE_USER"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(60, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)));
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
        users.put(USER_ID, user(USER_ID, UserStatus.ACTIVE, "ROLE_HOST"));

        Authentication authentication = filterWith(token);

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals(Set.of("ROLE_HOST"), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }

    /** auth-email-access A-5: the principal and its name are the user id, whatever identifiers the account has. */
    @Test
    void principal_nameIsUserId() throws Exception {
        String token = issueTokenForActiveUser();

        Authentication authentication = filterWith(token);

        assertNotNull(authentication);
        assertEquals(USER_ID, authentication.getName());
        AuthenticatedUser principal = assertInstanceOf(AuthenticatedUser.class, authentication.getPrincipal());
        assertEquals(USER_ID, principal.getUserId());
        assertFalse(authentication.getPrincipal() instanceof CustomUserDetails,
                "the principal must not carry the password hash or e-mail");
    }

    /** auth-email-access S-18, A-4: a token of the previous release works until it expires. */
    @Test
    void filter_oldToken_subPhone_uidValid_noCv_accepted() throws Exception {
        users.put(USER_ID, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));

        Authentication authentication = filterWith(previousReleaseToken(USERNAME, USER_ID));

        assertNotNull(authentication);
        assertEquals(USER_ID, authentication.getName());
    }

    /** auth-email-access I-5: a change of credentials ends the access tokens issued before it. */
    @Test
    void filter_lowerCv_refused() throws Exception {
        String token = issueTokenForActiveUser();
        CustomUserDetails changed = user(USER_ID, UserStatus.ACTIVE, "ROLE_USER");
        changed.setCredentialVersion(1);
        users.put(USER_ID, changed);

        assertNull(filterWith(token));
    }

    @Test
    void filter_equalCv_accepted() throws Exception {
        CustomUserDetails account = user(USER_ID, UserStatus.ACTIVE, "ROLE_USER");
        account.setCredentialVersion(3);
        users.put(USER_ID, account);
        String token = tokenBuilder().subject(USER_ID).claim(JWTProvider.USER_ID_CLAIM, USER_ID)
                .claim(JWTProvider.CREDENTIAL_VERSION_CLAIM, 3L).compact();

        assertNotNull(filterWith(token));
    }

    /** auth-email-access S-18: the phone in {@code sub} must be the phone of the account {@code uid} names. */
    @Test
    void filter_subIsPhoneOfAnotherUser_refused() throws Exception {
        users.put(USER_ID, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));

        assertNull(filterWith(previousReleaseToken(OTHER_PHONE, USER_ID)));
    }

    @Test
    void filter_uidOfDeletedUser_refused() throws Exception {
        // A soft-deleted row is invisible to lookups, so the user is simply not found
        String token = issueTokenForActiveUser();
        users.remove(USER_ID);

        assertNull(filterWith(token));
    }

    /** The account was deleted and its number registered again: the old token cannot reach the new account. */
    @Test
    void filter_uidReusedPhone_refused() throws Exception {
        users.put(OTHER_USER_ID, user(OTHER_USER_ID, UserStatus.ACTIVE, "ROLE_USER"));

        assertNull(filterWith(previousReleaseToken(USERNAME, USER_ID)));
    }

    @Test
    void filter_missingSub_refused() throws Exception {
        users.put(USER_ID, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));

        assertNull(filterWith(tokenBuilder().claim(JWTProvider.USER_ID_CLAIM, USER_ID).compact()));
    }

    @Test
    void userLockedAfterLogin_isNotAuthenticated() throws Exception {
        String token = issueTokenForActiveUser();
        users.put(USER_ID, user(USER_ID, UserStatus.LOCK, "ROLE_USER"));

        assertNull(filterWith(token));
    }

    @Test
    void userTerminatedAfterLogin_isNotAuthenticated() throws Exception {
        String token = issueTokenForActiveUser();
        users.put(USER_ID, user(USER_ID, UserStatus.TERMINATED, "ROLE_USER"));

        assertNull(filterWith(token));
    }

    @Test
    void tokenWithoutUid_isNotAuthenticated() throws Exception {
        users.put(USER_ID, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));

        assertNull(filterWith(tokenBuilder().subject(USERNAME).compact()));
    }

    /** W10-60: a valid token sent only in the legacy access-token cookie leaves the request anonymous. */
    @Test
    void tokenInAccessCookie_isNotAuthenticated() throws Exception {
        String token = issueTokenForActiveUser();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/query/me");
        request.setServletPath("/api/users/query/me");
        request.setCookies(new Cookie(ACCESS_COOKIE, token));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "filter chain must always continue");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void getJwtFromRequest_readsOnlyTheAuthorizationHeader() {
        MockHttpServletRequest cookieOnly = new MockHttpServletRequest("GET", "/api/users/query/me");
        cookieOnly.setCookies(new Cookie(ACCESS_COOKIE, "cookie-token"));
        MockHttpServletRequest both = new MockHttpServletRequest("GET", "/api/users/query/me");
        both.addHeader(HttpHeaders.AUTHORIZATION, JWTProvider.TOKEN_PREFIX + "header-token");
        both.setCookies(new Cookie(ACCESS_COOKIE, "cookie-token"));

        assertTrue(filter.getJwtFromRequest(cookieOnly).isEmpty());
        assertEquals("header-token", filter.getJwtFromRequest(both).orElseThrow());
    }

    @Test
    void expiredToken_isNotAuthenticated() throws Exception {
        users.put(USER_ID, user(USER_ID, UserStatus.ACTIVE, "ROLE_USER"));
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
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/session/refresh");
        request.setServletPath("/api/v1/auth/session/refresh");

        assertTrue(filter.shouldNotFilter(request));
    }

    /** W10-60: the removed legacy paths are no longer on the skip list. */
    @Test
    void legacyAuthPaths_areFiltered() {
        for (String path : List.of("/api/auth/login", "/api/auth/logout", "/api/auth/refresh-token",
                "/api/users/command/register")) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setServletPath(path);

            assertFalse(filter.shouldNotFilter(request), path);
        }
    }
}
