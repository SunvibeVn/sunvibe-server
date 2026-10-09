# 02. ĐẶC TẢ NGHIỆP VỤ ĐĂNG NHẬP (LOGIN)
## Module `auth-service` · Endpoint `POST /api/auth/login`

---

## 1. MỤC TIÊU & PHẠM VI

Xác thực người dùng bằng **số điện thoại + mật khẩu** và mở một **phiên đăng nhập** cho thiết bị đang dùng.

Kết quả đăng nhập gồm hai token:
- **Access token** (JWT, sống 15 phút): dùng để gọi API.
- **Refresh token** (sống tối đa 30 ngày): dùng để xin access token mới mà không phải đăng nhập lại. Xem [03_AUTH_REFRESH_TOKEN_FLOW.md](03_AUTH_REFRESH_TOKEN_FLOW.md).

Mỗi lần đăng nhập tạo một **phiên độc lập**. Người dùng có thể đăng nhập đồng thời trên nhiều thiết bị; đăng xuất ở thiết bị này không ảnh hưởng thiết bị khác.

Cùng một logic cấp phiên (`LoginService.loginInternal`) còn được dùng cho:
- **Đăng nhập tự động sau đăng ký** ([01_AUTH_REGISTER_FLOW.md](01_AUTH_REGISTER_FLOW.md)).
- **Đăng nhập lại sau khi đổi mật khẩu** (§6).

---

## 2. API

### 2.1. Request

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{ "username": "0912345678", "password": "Str0ng@Pass" }
```

| Trường | Bắt buộc | Ràng buộc |
|---|---|---|
| `username` | Có | Số điện thoại, `^[0-9]{10,12}$` |
| `password` | Có | Không rỗng (không kiểm tra độ mạnh khi đăng nhập) |

Endpoint **public**, không cần access token.

### 2.2. Response thành công

HTTP `200 OK`:

```http
Set-Cookie: cookie-jwt=<access token>; Path=/; Max-Age=900; Secure; HttpOnly; SameSite=Strict
Set-Cookie: cookie-jwt-rf=<refresh token>; Path=/api/auth; Max-Age=604800; Secure; HttpOnly; SameSite=Strict
```

```json
{
  "code": 200,
  "message": "User logged in successfully.",
  "result": {
    "tokenType": "JWT",
    "accessToken": "eyJ0eXAiOiJKV1Qi...",
    "expiresIn": 900,
    "username": "0912345678",
    "userEmail": null,
    "roles": ["TENANT"],
    "message": "User logged in successfully."
  }
}
```

| Thành phần | Ý nghĩa |
|---|---|
| `result.accessToken` | JWT để gửi trong header `Authorization: Bearer <token>`. Cũng được set vào cookie `cookie-jwt` |
| `result.expiresIn` | Số giây còn lại trước khi access token hết hạn |
| Cookie `cookie-jwt` | Access token cho client dạng trình duyệt; được gửi kèm mọi request (`Path=/`) |
| Cookie `cookie-jwt-rf` | Refresh token. **Chỉ nằm trong cookie, không bao giờ có trong body**. JavaScript không đọc được (`HttpOnly`); trình duyệt chỉ gửi nó tới `/api/auth/*` |

---

## 3. LUỒNG XỬ LÝ

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant AC as AuthController
    participant LS as LoginService
    participant AM as AuthenticationManager
    participant UDS as CustomUserDetailsService
    participant JWT as JWTProvider
    participant RT as RefreshTokenGenerateService
    participant DB as PostgreSQL

    C->>AC: POST /api/auth/login
    AC->>AC: Bean Validation + ClientInfo (User-Agent, IP)
    AC->>LS: execute(LoginCommand, ClientInfo)
    LS->>AM: authenticate(username, password)
    AM->>UDS: loadUserByUsername
    UDS->>DB: SELECT users (deleted_at IS NULL) + roles
    AM->>AM: Kiểm tra trạng thái tài khoản, so khớp BCrypt
    alt Sai thông tin / tài khoản bị khóa
        LS-->>C: Lỗi xác thực (xem §5)
    end
    LS->>JWT: generateAccessToken(username)
    LS->>RT: generateRefreshToken(userId, ClientInfo)
    RT->>DB: INSERT refresh_tokens (phiên mới, chỉ lưu hash)
    LS->>DB: UPDATE users.last_login
    AC-->>C: 200 + Set-Cookie x2 + LoginResponse
```

### 3.1. Xác thực (Spring Security)

1. `CustomUserDetailsService` tìm người dùng theo số điện thoại, **bỏ qua tài khoản đã xóa mềm**. Không tìm thấy thì ném `UsernameNotFoundException`, và Spring Security chuyển nó thành lỗi sai thông tin đăng nhập chung.
2. Kiểm tra trạng thái **trước** khi so mật khẩu:

   | `user_status` | Đăng nhập |
   |---|---|
   | `ACTIVE` | Được |
   | `PENDING` | Được (hiện chưa chặn) |
   | `LOCK` | Bị từ chối (`LockedException`) |
   | `TERMINATED`, `DELETED` | Bị từ chối (`DisabledException`) |

3. So khớp mật khẩu với hash BCrypt.

### 3.2. Cấp access token (`JWTProvider.generateAccessToken`)

- `JWTProvider` là **điểm chặn duy nhất**: kiểm tra lại trạng thái tài khoản và không bao giờ cấp token cho tài khoản bị khóa/vô hiệu, bất kể đi từ luồng nào.
- Ký HS256 bằng `app.jwt.secret`, thời hạn `app.jwt.expiration.minutes` (15 phút).

| Claim | Nội dung |
|---|---|
| `sub` | Số điện thoại (username) |
| `uid` | ID tài khoản (ULID). Dùng để phát hiện token của tài khoản cũ khi số điện thoại được đăng ký lại |
| `email` | Email hoặc `null` |
| `rol` | Danh sách role (chỉ để client hiển thị; server **không** tin claim này) |
| `iss` | `Nestify` |
| `iat`, `exp` | Thời điểm phát hành / hết hạn |

### 3.3. Mở phiên đăng nhập (`RefreshTokenGenerateService`)

- Sinh refresh token ngẫu nhiên 256-bit (base64url) và **chỉ lưu SHA-256** vào `refresh_tokens`.
- Mỗi lần đăng nhập là một phiên mới, có `family_id` riêng:
  - hạn idle 7 ngày;
  - hạn tuyệt đối 30 ngày.
- Lưu User-Agent và IP để truy vết. Hai giá trị này **không** dùng để chặn request.

Chi tiết: [03_AUTH_REFRESH_TOKEN_FLOW.md](03_AUTH_REFRESH_TOKEN_FLOW.md).

### 3.4. Cập nhật `last_login`

Ghi thời điểm đăng nhập (UTC) vào `users.last_login`.

---

## 4. DÙNG ACCESS TOKEN KHI GỌI API

Mọi endpoint không thuộc danh sách public đều đi qua `JWTRequestFilter`.

1. **Lấy token**: ưu tiên header `Authorization: Bearer <token>` (SPA/mobile); nếu không có thì lấy từ cookie `cookie-jwt` (trình duyệt).
2. **Kiểm tra chữ ký và hạn**. Token hết hạn hoặc không hợp lệ thì request được coi là chưa đăng nhập, và endpoint cần đăng nhập trả **401**.
3. **Nạp lại tài khoản từ DB** ở mỗi request. Request chỉ được xác thực khi:
   - tài khoản còn tồn tại (chưa xóa mềm);
   - `uid` trong token trùng ID tài khoản hiện tại;
   - tài khoản không bị `LOCK` / `TERMINATED` / `DELETED`.
4. **Quyền (authorities) lấy từ DB**, không lấy từ claim `rol`. Thay đổi role có hiệu lực ngay ở request kế tiếp.

Nhờ bước 3, khóa tài khoản có hiệu lực **ngay lập tức** với cả access token đang còn hạn.

Endpoint public (không cần access token):

| Method | Endpoint |
|---|---|
| POST | `/api/auth/login` |
| POST | `/api/users/command/register` |
| POST | `/api/auth/refresh-token` |
| POST | `/api/auth/logout` |
| POST | `/api/auth/forgot-password` |
| POST | `/api/auth/reset-password` |
| GET | `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` |

---

## 5. BẢNG LỖI

| Tình huống | HTTP hiện tại | Message |
|---|---|---|
| Thiếu / sai định dạng `username`, thiếu `password` | 406 | `Validation failed.` + lỗi theo field |
| Số điện thoại không tồn tại **hoặc** sai mật khẩu | 500 ⚠️ | `Incorrect username or password.` |
| Tài khoản `LOCK` / `TERMINATED` / `DELETED` | 500 ⚠️ | `Account is locked or disabled.` |

Hai trường hợp "không tồn tại" và "sai mật khẩu" cố ý trả **cùng một message** để không lộ số điện thoại nào đã đăng ký.

⚠️ **Lỗi đã biết**: các lỗi xác thực đáng lẽ phải trả **401**. `GlobalServerExceptionHandler` (module `common`) đang bắt nhầm `jakarta.mail.AuthenticationFailedException`, nên `AuthenticationFailedException` của auth-service rơi vào handler chung và trả 500. Client tạm thời phân biệt bằng `message`.

---

## 6. LUỒNG LIÊN QUAN

### 6.1. Đổi mật khẩu — `POST /api/auth/change-password` (cần đăng nhập)

1. Kiểm tra mật khẩu hiện tại đúng, và mật khẩu mới khác mật khẩu hiện tại.
2. Lưu mật khẩu mới (BCrypt).
3. **Thu hồi mọi phiên** của tài khoản trên mọi thiết bị (`PASSWORD_CHANGED`).
4. Đăng nhập lại thiết bị hiện tại bằng mật khẩu mới: phiên mới, response giống §2.2 với message `Change password successfully.`

Kết quả: thiết bị đang đổi mật khẩu vẫn dùng tiếp, mọi thiết bị khác bị đăng xuất khi access token của chúng hết hạn (≤ 15 phút).

### 6.2. Quên / đặt lại mật khẩu

`POST /api/auth/forgot-password` gửi OTP qua email. `POST /api/auth/reset-password` xác minh OTP rồi đặt mật khẩu mới, và:
- **Thu hồi mọi phiên** (`PASSWORD_RESET`).
- **Không** cấp token mới; response xóa cả hai cookie.
- Người dùng đăng nhập lại bằng mật khẩu mới.
- Tài khoản bị khóa/vô hiệu không đặt lại mật khẩu được.

### 6.3. Đăng xuất

`POST /api/auth/logout` (thiết bị hiện tại) và `POST /api/auth/logout-all` (mọi thiết bị). Xem [03_AUTH_REFRESH_TOKEN_FLOW.md](03_AUTH_REFRESH_TOKEN_FLOW.md) §5.

---

## 7. CẤU HÌNH

| Thuộc tính (`application.yml`) | Mặc định | Ý nghĩa |
|---|---|---|
| `app.jwt.secret` | — | Khóa ký JWT (base64). **Production phải đặt qua biến môi trường** |
| `app.jwt.expiration.minutes` | `15` | Thời hạn access token |
| `app.jwt.jwtCookieName` | `cookie-jwt` | Tên cookie access token |
| `app.jwt.jwtRefreshCookieName` | `cookie-jwt-rf` | Tên cookie refresh token |
| `app.cors.allowed-origins` | `http://localhost:3000` | Origin frontend được gọi API kèm cookie |

---

## 8. BẢO MẬT

| Biện pháp | Mô tả |
|---|---|
| Mật khẩu | BCrypt cost 10; không log, không trả về |
| Chống dò tài khoản | Cùng message cho "không tồn tại" và "sai mật khẩu" |
| Cookie | `HttpOnly` (chống đọc bằng XSS), `Secure` (chỉ HTTPS), `SameSite=Strict` (chống CSRF) |
| Trạng thái tài khoản | Kiểm tra lúc đăng nhập, lúc cấp token và **ở mỗi request** |
| Quyền | Luôn lấy từ DB, không tin claim trong token |
| Log | Không ghi access/refresh token ra log |

**CSRF**: CSRF của Spring Security đang tắt, nên việc chống CSRF dựa hoàn toàn vào `SameSite=Strict`. Nếu sau này cần `SameSite=None` (frontend khác site), phải bật lại CSRF.

---

## 9. THÀNH PHẦN MÃ NGUỒN

| Tầng | Lớp |
|---|---|
| Presentation | `AuthController`, `LoginRequest`, `LoginResponse`, `AuthConverter` |
| Application | `LoginUseCase` / `LoginService`, `AccessTokenGenerateService`, `RefreshTokenGenerateService`, `AuthResultAssembler`, `ClientInfo` |
| Infrastructure | `JWTProvider`, `JWTRequestFilter`, `CustomUserDetailsService`, `CustomUserDetails` |
| Cấu hình | `SecurityConfig` (module `app`), `CookieService` |

Test: `LoginServiceTest`, `JWTProviderTest`, `JWTRequestFilterTest`, `CustomUserDetailsServiceTest`, `CustomUserDetailsTest`, `PasswordChangeServiceTest`, `PasswordResetServiceTest`.

---

## 10. HẠN CHẾ ĐÃ BIẾT

1. Lỗi xác thực trả **500** thay vì 401 (§5).
2. **Chưa giới hạn số lần đăng nhập sai** (rate limit / khóa tạm thời), nên có thể bị dò mật khẩu.
3. Tài khoản bị khóa nhận message khác với "sai thông tin", nên vẫn lộ được sự tồn tại của số điện thoại.
4. Sau khi bị thu hồi phiên (đổi mật khẩu, đăng xuất), access token đã phát vẫn dùng được tới khi hết hạn (≤ 15 phút). Riêng khóa tài khoản thì có hiệu lực ngay (§4).
5. Trạng thái `PENDING` hiện vẫn đăng nhập được.
