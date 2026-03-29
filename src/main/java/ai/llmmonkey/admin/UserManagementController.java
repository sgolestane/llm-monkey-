package ai.llmmonkey.admin;

import ai.llmmonkey.admin.dto.CreateUserRequest;
import ai.llmmonkey.model.UserEntity;
import ai.llmmonkey.model.UserRole;
import ai.llmmonkey.repository.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserManagementController {

    private final UserRepository userRepository;

    public UserManagementController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/new")
    public ResponseEntity<UserEntity> createUser(@RequestBody CreateUserRequest request) {
        UserEntity user = new UserEntity();
        user.setId(request.userId());
        user.setUserEmail(request.userEmail());
        if (request.userRole() != null) {
            user.setUserRole(UserRole.valueOf(request.userRole()));
        }
        user.setOrganizationId(request.organizationId());
        user.setMaxBudget(request.maxBudget());
        user.setTpmLimit(request.tpmLimit());
        user.setRpmLimit(request.rpmLimit());
        UserEntity saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/list")
    public ResponseEntity<List<UserEntity>> listUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/info/{userId}")
    public ResponseEntity<UserEntity> getUserInfo(@PathVariable String userId) {
        return userRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/update/{userId}")
    public ResponseEntity<UserEntity> updateUser(@PathVariable String userId,
                                                  @RequestBody CreateUserRequest request) {
        return userRepository.findById(userId)
                .map(user -> {
                    if (request.userEmail() != null) user.setUserEmail(request.userEmail());
                    if (request.userRole() != null) user.setUserRole(UserRole.valueOf(request.userRole()));
                    if (request.organizationId() != null) user.setOrganizationId(request.organizationId());
                    if (request.maxBudget() != null) user.setMaxBudget(request.maxBudget());
                    if (request.tpmLimit() != null) user.setTpmLimit(request.tpmLimit());
                    if (request.rpmLimit() != null) user.setRpmLimit(request.rpmLimit());
                    return ResponseEntity.ok(userRepository.save(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
