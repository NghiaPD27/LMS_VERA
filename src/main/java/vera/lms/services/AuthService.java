package vera.lms.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.configs.JwtProperties;
import vera.lms.dtos.AuthDto.*;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.exceptions.UnauthorizedException;
import vera.lms.models.AccountAccess;
import vera.lms.models.RefreshToken;
import vera.lms.models.User;
import vera.lms.repositories.RefreshTokenRepository;
import vera.lms.repositories.UserRepository;
import vera.lms.utils.JwtUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(noRollbackFor = ForbiddenException.class)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final AccountAccessService accountAccessService;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            JwtProperties jwtProperties,
            AccountAccessService accountAccessService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.accountAccessService = accountAccessService;
    }

    public LoginResponse login(LoginRequest request) {
        if (request.username() == null || request.username().trim().isEmpty()) {
            throw new BadRequestException("Username is required");
        }
        if (request.password() == null || request.password().isEmpty()) {
            throw new BadRequestException("Password is required");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPassword());

        if (!passwordMatches) {
            throw new UnauthorizedException("Invalid username or password");
        }

        AccountAccess accountAccess = user.getAccountAccess();
        accountAccessService.ensureAccountCanAccess(user);
        accountAccessService.initializeStudentFirstLoginIfNeeded(user, accountAccess);

        String accessToken = jwtUtils.generateAccessToken(user.getUsername());
        String refreshTokenStr = UUID.randomUUID().toString();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .expiryDate(Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        boolean mustChangePassword = accountAccessService.mustChangePassword(user);

        return new LoginResponse(accessToken, refreshTokenStr, mustChangePassword);
    }

    public LoginResponse refresh(RefreshRequest request) {
        if (request.refreshToken() == null || request.refreshToken().trim().isEmpty()) {
            throw new BadRequestException("Refresh token is required");
        }

        String tokenStr = request.refreshToken();

        // If it looks like a JWT, parse and check validity first
        if (tokenStr.startsWith("eyJ")) {
            if (!jwtUtils.validateToken(tokenStr)) {
                throw new UnauthorizedException("Invalid or expired refresh token");
            }
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired");
        }

        User user = refreshToken.getUser();
        accountAccessService.ensureAccountCanAccess(user);

        // Revoke the old refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Generate new tokens
        String newAccessToken = jwtUtils.generateAccessToken(user.getUsername());
        String newRefreshTokenStr = UUID.randomUUID().toString();

        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshTokenStr)
                .expiryDate(Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshToken);

        boolean mustChangePassword = accountAccessService.mustChangePassword(user);

        return new LoginResponse(newAccessToken, newRefreshTokenStr, mustChangePassword);
    }

    public void changePassword(ChangePasswordRequest request, User currentUser) {
        if (request.oldPassword() == null || request.oldPassword().isEmpty()) {
            throw new BadRequestException("Old password is required");
        }
        if (request.newPassword() == null || request.newPassword().isEmpty()) {
            throw new BadRequestException("New password is required");
        }
        if (request.newPassword().length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters");
        }
        if (request.newPassword().length() > 100) {
            throw new BadRequestException("New password must not exceed 100 characters");
        }
        if (request.newPassword().equals(request.oldPassword())) {
            throw new BadRequestException("New password cannot be the same as the old password");
        }
        if (!passwordEncoder.matches(request.oldPassword(), currentUser.getPassword())) {
            throw new BadRequestException("Incorrect old password");
        }

        currentUser.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(currentUser);

        accountAccessService.clearMustChangePassword(currentUser.getAccountAccess());

        // Revoke all active refresh tokens for the user
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndRevokedFalse(currentUser);
        for (RefreshToken t : activeTokens) {
            t.setRevoked(true);
        }
        refreshTokenRepository.saveAll(activeTokens);
    }

    public void logout(RefreshRequest request, String currentUsername) {
        if (request.refreshToken() == null || request.refreshToken().trim().isEmpty()) {
            return;
        }

        RefreshToken token = refreshTokenRepository.findByToken(request.refreshToken())
                .orElse(null);
        if (token != null) {
            if (currentUsername == null || !currentUsername.equals(token.getUser().getUsername())) {
                throw new ForbiddenException("Cannot logout other users' sessions");
            }
            token.setRevoked(true);
            refreshTokenRepository.save(token);

            // Revoke all active tokens for the user
            List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndRevokedFalse(token.getUser());
            for (RefreshToken t : activeTokens) {
                t.setRevoked(true);
            }
            refreshTokenRepository.saveAll(activeTokens);

            // Update user's updatedAt field and save
            User user = token.getUser();
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
        }
    }
}
