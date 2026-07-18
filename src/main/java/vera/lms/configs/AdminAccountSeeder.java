package vera.lms.configs;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.enums.AccountStatus;
import vera.lms.enums.RoleName;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.AccountAccess;
import vera.lms.models.Role;
import vera.lms.models.User;
import vera.lms.repositories.AccountAccessRepository;
import vera.lms.repositories.RoleRepository;
import vera.lms.repositories.UserRepository;

@Component
@Profile("!test")
public class AdminAccountSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountAccessRepository accountAccessRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AccountAccessRepository accountAccessRepository,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.accountAccessRepository = accountAccessRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = environment.getRequiredProperty("ADMIN_USERNAME");
        this.adminEmail = environment.getRequiredProperty("ADMIN_GMAIL");
        this.adminPassword = environment.getRequiredProperty("ADMIN_PASSWORD");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminEmail) && userRepository.findByUsername(adminUsername).isEmpty()) {
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Role ADMIN not found"));

        User admin = userRepository.findByUsername(adminUsername)
                .orElseGet(() -> User.builder()
                        .username(adminUsername)
                        .role(adminRole)
                        .build());

        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setEnabled(true);
        admin.setRole(adminRole);
        admin = userRepository.save(admin);

        AccountAccess accountAccess = admin.getAccountAccess();
        if (accountAccess == null) {
            accountAccess = AccountAccess.builder()
                    .user(admin)
                    .build();
        }
        accountAccess.setStatus(AccountStatus.ACTIVE);
        accountAccess.setMustChangePassword(false);
        accountAccessRepository.save(accountAccess);
    }
}
