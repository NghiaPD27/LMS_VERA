package vera.lms.dtos;

import jakarta.validation.constraints.NotBlank;

public class ProgramDto {
    public record CreateProgramRequest(
        @NotBlank(message = "Program name is required")
        String name,
        String description
    ) {}
    public record ProgramResponse(Long id, String name, String description) {}
}
