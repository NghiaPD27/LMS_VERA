package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.AccountStatus;
import java.time.Instant;

@Entity
@Table(name = "account_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountAccess {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = true;

    @Column(name = "first_login_at")
    private Instant firstLoginAt;

    @Column(name = "expired_at")
    private Instant expiredAt;
}
