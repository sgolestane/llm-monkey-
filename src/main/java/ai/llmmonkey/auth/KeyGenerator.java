package ai.llmmonkey.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class KeyGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateKey() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(32);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return "sk-lm-" + hex;
    }
}
