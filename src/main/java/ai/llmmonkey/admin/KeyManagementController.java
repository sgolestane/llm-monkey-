package ai.llmmonkey.admin;

import ai.llmmonkey.admin.dto.GenerateKeyRequest;
import ai.llmmonkey.admin.dto.GenerateKeyResponse;
import ai.llmmonkey.admin.dto.UpdateKeyRequest;
import ai.llmmonkey.auth.VirtualKeyService;
import ai.llmmonkey.model.VerificationTokenEntity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/key")
public class KeyManagementController {

    private final VirtualKeyService virtualKeyService;

    public KeyManagementController(VirtualKeyService virtualKeyService) {
        this.virtualKeyService = virtualKeyService;
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateKeyResponse> generateKey(@RequestBody GenerateKeyRequest request,
                                                           @RequestAttribute(value = "authContext", required = false) Object authContext) {
        String createdBy = authContext != null ? authContext.toString() : "system";
        GenerateKeyResponse response = virtualKeyService.generateKey(request, createdBy);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info/{keyHash}")
    public ResponseEntity<VerificationTokenEntity> getKeyInfo(@PathVariable String keyHash) {
        return virtualKeyService.listKeys(null).stream()
                .filter(k -> k.getToken().equals(keyHash))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/update/{keyHash}")
    public ResponseEntity<VerificationTokenEntity> updateKey(@PathVariable String keyHash,
                                                              @RequestBody UpdateKeyRequest request) {
        VerificationTokenEntity updated = virtualKeyService.updateKey(keyHash, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/delete/{keyHash}")
    public ResponseEntity<Void> deleteKey(@PathVariable String keyHash) {
        virtualKeyService.deleteKey(keyHash);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/list")
    public ResponseEntity<List<VerificationTokenEntity>> listKeys(
            @RequestParam(required = false) String userId) {
        List<VerificationTokenEntity> keys = virtualKeyService.listKeys(userId);
        return ResponseEntity.ok(keys);
    }
}
