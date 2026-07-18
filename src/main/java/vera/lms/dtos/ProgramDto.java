package vera.lms.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public class ProgramDto {
    public record CreateProgramRequest(
        @NotBlank(message = "Program name is required")
        String name,
        String description,

        @DecimalMin(value = "0.00", message = "Program price must be non-negative")
        BigDecimal price,

        String currency,
        String salesStatus
    ) {}
    public record ProgramResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        String salesStatus
    ) {}
}
