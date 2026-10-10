package vn.sunvibe.auth.core.application.usecase.user.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.auth.ContactVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.application.usecase.user.UserRegisterUseCase;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

import java.util.HashSet;

/**
 * Self-registration: creates a PENDING tenant account ("register first, verify
 * later"), sends the first verification codes and signs the new user in on the
 * registering device, all in one transaction.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class UserRegisterService implements UserRegisterUseCase {

    private final UserDomainRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdGenerator<String> ulidGenerator;
    private final AuditLogPort auditLog;
    private final ContactVerificationUseCase contactVerification;
    private final LoginUseCase loginUseCase;

    @Transactional
    @Override
    public AuthResult execute(UserRegisterCommand userRegisterCommand) {

        // Check username exists
        if (userRepository.existsByUsername(userRegisterCommand.username())) {
            throw new AuthValidationException(AuthFieldName.USER_NAME, "Phone number is already in use.");
        }

        // Optional fields: validated (format) and checked for duplicates only when provided
        Email emailVO = isProvided(userRegisterCommand.email()) ? Email.create(userRegisterCommand.email().trim()) : null;
        if (emailVO != null && userRepository.existsByEmail(emailVO.getValue())) {
            throw new AuthValidationException(AuthFieldName.EMAIL, "Email is already in use.");
        }
        IdCardNumber idCardNumberVO = isProvided(userRegisterCommand.idCardNumber()) ? IdCardNumber.create(userRegisterCommand.idCardNumber().trim()) : null;
        if (idCardNumberVO != null && userRepository.existsByIdCardNumber(idCardNumberVO)) {
            throw new AuthValidationException(AuthFieldName.ID_CARD_NUMBER, "ID card number is already in use.");
        }

        // Build Value-Object
        UserId userId = UserId.create(ulidGenerator.generateId());
        Username usernameVO = Username.create(userRegisterCommand.username());
        Password passwordVO = Password.create(passwordEncoder.encode(userRegisterCommand.password()));
        ProfileImage profileImageVO = ProfileImage.create(null);

        AppUser appUser = AppUser.create(userId, userRegisterCommand.fullName(), usernameVO, emailVO, passwordVO, profileImageVO, idCardNumberVO, new HashSet<>(), AppClock.currentUTC());

        // Self-registered accounts are always tenants; other roles are granted through separate flows
        appUser.assignRole(RoleName.TENANT.name());

        // Save in database
        appUser = userRepository.save(appUser);

        log.info("Register user successful: {}", appUser.getUsername().getValue());
        auditLog.record(AuthAuditEvent.of(AuditEventType.USER_REGISTERED, appUser.getId().getValue(),
                appUser.getUsername().getValue()));

        // The account stays PENDING until the phone is verified; send the first codes right away
        contactVerification.sendCodeAfterRegistration(appUser, VerificationChannel.PHONE);
        if (appUser.getEmail() != null) {
            contactVerification.sendCodeAfterRegistration(appUser, VerificationChannel.EMAIL);
        }

        // The password was just set: no need to check it again to start the first session
        ClientInfo clientInfo = userRegisterCommand.clientInfo() != null ? userRegisterCommand.clientInfo() : ClientInfo.unknown();
        return loginUseCase.loginInternal(appUser, clientInfo);
    }

    private static boolean isProvided(String value) {
        return value != null && !value.isBlank();
    }
}
