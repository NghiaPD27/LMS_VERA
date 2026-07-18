package vera.lms.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDto {

    public record LoginRequest(
        String username,
        String password
    ) {}

    public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean mustChangePassword
    ) {}

    public record RefreshRequest(
        String refreshToken
    ) {}

    public record ChangePasswordRequest(
        String oldPassword,
        String newPassword
    ) {}

    public record RegisterStudentRequest(
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
}
