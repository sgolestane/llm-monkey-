package ai.llmmonkey.auth;

import ai.llmmonkey.api.exception.AuthorizationException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RbacService {

    private final RolePermissions rolePermissions;

    public RbacService(RolePermissions rolePermissions) {
        this.rolePermissions = rolePermissions;
    }

    public void checkPermission(AuthContext ctx, Permission required) {
        if (ctx.role() == null) {
            throw new AuthorizationException("No role assigned to user");
        }

        Set<Permission> permissions = rolePermissions.getPermissions(ctx.role());
        if (!permissions.contains(required)) {
            throw new AuthorizationException(
                    "Permission denied: " + required + " is not granted to role " + ctx.role());
        }
    }
}
