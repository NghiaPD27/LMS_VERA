package vera.lms.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sepay")
public record SepayProperties(
        String webhookSecret,
        String bankAccount,
        String bankCode,
        String paymentCodePrefix,
        String qrBaseUrl
) {
    public String paymentCodePrefixOrDefault() {
        return isBlank(paymentCodePrefix) ? "LMSP" : paymentCodePrefix.trim();
    }

    public String qrBaseUrlOrDefault() {
        return isBlank(qrBaseUrl) ? "https://vietqr.app/img" : qrBaseUrl.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
