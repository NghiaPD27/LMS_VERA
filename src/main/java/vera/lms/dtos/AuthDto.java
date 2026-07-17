package vera.lms.dtos;

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
}
