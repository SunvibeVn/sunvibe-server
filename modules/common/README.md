# Common Module

Module chia sẻ cho toàn bộ dự án, chứa các thành phần dùng chung giữa các service modules.

## Cấu trúc

```
common/
├── exception/          # Custom exceptions
│   ├── BaseException.java
│   └── ResourceNotFoundException.java
├── dto/               # Data Transfer Objects chung
│   ├── BaseResponse.java
│   └── PageResponse.java
├── util/              # Utility classes
│   └── DateUtils.java
└── constant/          # Constants chung
    ├── Status.java
    └── ErrorCode.java
```

## Sử dụng

### Exceptions
```java
throw new ResourceNotFoundException("User", userId);
throw new BaseException("CUSTOM_ERROR", "Custom error message");
```

### DTOs
```java
// Success response
BaseResponse<User> response = BaseResponse.success(user);
BaseResponse<User> response = BaseResponse.success("User created", user);

// Error response
BaseResponse<Void> error = BaseResponse.error("Something went wrong");
BaseResponse<Void> error = BaseResponse.error("VALIDATION_ERROR", "Invalid input");

// Page response
PageResponse<User> page = PageResponse.of(users, 0, 10, totalElements);
```

### Constants
```java
String status = Status.ACTIVE;
String errorCode = ErrorCode.RESOURCE_NOT_FOUND;
```

### Utilities
```java
String formatted = DateUtils.format(LocalDateTime.now());
LocalDateTime parsed = DateUtils.parse("2024-01-01 12:00:00");
```

## Mở rộng

Khi cần thêm:
- New exception types → thêm vào `exception/`
- New DTOs → thêm vào `dto/`
- New utilities → thêm vào `util/`
- New constants → thêm vào `constant/`
