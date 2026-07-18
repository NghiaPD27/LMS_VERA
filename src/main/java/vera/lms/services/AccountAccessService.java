package vera.lms.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.enums.AccountStatus;
import vera.lms.enums.RoleName;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.models.AccountAccess;
import vera.lms.models.User;
import vera.lms.repositories.AccountAccessRepository;

import java.time.Instant;

@Service
public class AccountAccessService {

    private final AccountAccessRepository accountAccessRepository;

    public AccountAccessService(AccountAccessRepository accountAccessRepository) {
        this.accountAccessRepository = accountAccessRepository;
    }

    public AccountAccess createDefaultAccess(User user) {
        return AccountAccess.builder()
                .user(user)
                .status(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .build();
    }

    public AccountAccess createSelfRegisteredStudentAccess(User user) {
        return AccountAccess.builder()
                .user(user)
                .status(AccountStatus.ACTIVE)
                .mustChangePassword(false)
                .firstLoginAt(Instant.now())
                .build();
    }

    @Transactional
    public void initializeStudentFirstLoginIfNeeded(User user, AccountAccess accountAccess) {
        if (accountAccess == null
                || accountAccess.getFirstLoginAt() != null
                || !isStudent(user)) {
            return;
        }

        Instant firstLoginAt = Instant.now();
        accountAccess.setFirstLoginAt(firstLoginAt);
        accountAccessRepository.save(accountAccess);
    }

    @Transactional(noRollbackFor = ForbiddenException.class)
    public void ensureAccountCanAccess(User user) {
        if (!user.isEnabled()) {
            throw new ForbiddenException("Account is disabled");
        }

        AccountAccess accountAccess = user.getAccountAccess();
        if (accountAccess == null) {
            return;
        }

        if (accountAccess.getStatus() == AccountStatus.EXPIRED) {
            throw new ForbiddenException("Account access is expired");
        }
        if (accountAccess.getStatus() == AccountStatus.SUSPENDED) {
            throw new ForbiddenException("Account access is suspended");
        }
    }

    public boolean mustChangePassword(User user) {
        return user.getAccountAccess() != null && user.getAccountAccess().isMustChangePassword();
    }

    @Transactional
    public void clearMustChangePassword(AccountAccess accountAccess) {
        if (accountAccess == null) {
            return;
        }
        accountAccess.setMustChangePassword(false);
        accountAccessRepository.save(accountAccess);
    }

    private boolean isStudent(User user) {
        return user.getRole() != null && user.getRole().getName() == RoleName.STUDENT;
    }
}
