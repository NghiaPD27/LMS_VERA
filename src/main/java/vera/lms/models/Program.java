package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.ProgramSalesStatus;

import java.math.BigDecimal;

@Entity
@Table(name = "programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "sales_status", nullable = false, length = 20)
    private ProgramSalesStatus salesStatus = ProgramSalesStatus.DRAFT;
}
