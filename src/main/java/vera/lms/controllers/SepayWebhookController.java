package vera.lms.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import vera.lms.dtos.PurchaseDto.SepayWebhookResponse;
import vera.lms.services.SepayWebhookService;

@RestController
public class SepayWebhookController {

    private final SepayWebhookService sepayWebhookService;

    public SepayWebhookController(SepayWebhookService sepayWebhookService) {
        this.sepayWebhookService = sepayWebhookService;
    }

    @PostMapping("/api/webhooks/sepay")
    public ResponseEntity<SepayWebhookResponse> handleSepayWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp) {
        return ResponseEntity.ok(sepayWebhookService.handleWebhook(rawBody, signature, timestamp));
    }
}
