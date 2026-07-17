package vera.lms.dtos;

import jakarta.validation.constraints.*;
import java.time.Instant;

public class UserDto {

    public record CreateStudentRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password,

        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        @Pattern(regexp = "^\\d*$", message = "Phone number must contain only digits")
        String phoneNumber
    ) {}

    public record StudentProfileResponse(
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String status,
        boolean mustChangePassword
    ) {}

    public record CreateTeacherRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password,

        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        @Pattern(regexp = "^\\d*$", message = "Phone number must contain only digits")
        String phoneNumber,

        String bio
    ) {}

    public record TeacherProfileResponse(
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String bio
    ) {}

    public record CreateEvaluatorRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password,

        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        @Pattern(regexp = "^\\d*$", message = "Phone number must contain only digits")
        String phoneNumber
    ) {}

    public record EvaluatorProfileResponse(
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber
    ) {}

    public record UpdateUserRequest(
        @Email(message = "Email must be valid")
        @Size(min = 1, max = 100, message = "Email must be between 1 and 100 characters")
        String email,

        Boolean enabled,

        String status
    ) {}

    public record UserResponse(
        Long id,
        String username,
        String email,
        boolean enabled,
        String role,
        StudentProfileResponse studentProfile,
        TeacherProfileResponse teacherProfile,
        EvaluatorProfileResponse evaluatorProfile,
        AccountAccessResponse accountAccess
    ) {}

    public record ExtendAccountRequest(
        @NotNull(message = "Months count is required")
        @Min(value = 1, message = "Months count must be at least 1")
        Integer months
    ) {}

    public record AccountAccessResponse(
        Long userId,
        String status,
        boolean mustChangePassword,
        Instant firstLoginAt,
        Instant expiredAt
    ) {}
}
