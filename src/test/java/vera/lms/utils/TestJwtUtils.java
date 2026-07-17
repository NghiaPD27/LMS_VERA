package vera.lms.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vera.lms.configs.JwtProperties;
import vera.lms.enums.RoleName;
import vera.lms.models.User;
import vera.lms.repositories.UserRepository;

@Component
@Profile("test")
@Primary
public class TestJwtUtils extends JwtUtils {

    @Autowired
    public TestJwtUtils(JwtProperties jwtProperties) {
        super(jwtProperties);
    }

    @Override
    public boolean isMockToken(String token) {
        return "admin-token".equals(token)
                || "student-token".equals(token)
                || "teacher-token".equals(token)
                || "evaluator-token".equals(token)
                || "mock-student-token".equals(token)
                || "mock-access-token".equals(token);
    }

    @Override
    public User resolveMockToken(String token, UserRepository userRepository) {
        if ("admin-token".equals(token)) {
            return userRepository.findByUsername("admin")
                    .orElseGet(() -> userRepository.findAll().stream()
                            .filter(u -> u.getRole() != null && u.getRole().getName() == RoleName.ADMIN)
                            .findFirst().orElse(null));
        }
        if ("student-token".equals(token)) {
            return userRepository.findByUsername("student_user")
                    .orElseGet(() -> userRepository.findAll().stream()
                            .filter(u -> u.getRole() != null && u.getRole().getName() == RoleName.STUDENT)
                            .max((u1, u2) -> u1.getId().compareTo(u2.getId()))
                            .orElse(null));
        }
        if ("teacher-token".equals(token)) {
            return userRepository.findByUsername("teacher_user")
                    .orElseGet(() -> userRepository.findAll().stream()
                            .filter(u -> u.getRole() != null && u.getRole().getName() == RoleName.TEACHER)
                            .max((u1, u2) -> u1.getId().compareTo(u2.getId()))
                            .orElse(null));
        }
        if ("evaluator-token".equals(token)) {
            return userRepository.findByUsername("eval_user")
                    .orElseGet(() -> userRepository.findAll().stream()
                            .filter(u -> u.getRole() != null && u.getRole().getName() == RoleName.EVALUATOR)
                            .max((u1, u2) -> u1.getId().compareTo(u2.getId()))
                            .orElse(null));
        }
        if ("mock-student-token".equals(token)) {
            return userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null && u.getRole().getName() == RoleName.STUDENT)
                    .max((u1, u2) -> u1.getId().compareTo(u2.getId()))
                    .orElse(null);
        }
        if ("mock-access-token".equals(token)) {
            return userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null && u.getRole().getName() == RoleName.STUDENT)
                    .max((u1, u2) -> u1.getId().compareTo(u2.getId()))
                    .orElseGet(() -> userRepository.findByUsername("admin").orElse(null));
        }
        return null;
    }
}
