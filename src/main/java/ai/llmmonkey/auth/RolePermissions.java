package ai.llmmonkey.auth;

import ai.llmmonkey.model.UserRole;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class RolePermissions {

    private static final Map<UserRole, Set<Permission>> ROLE_PERMISSION_MAP = Map.of(
            UserRole.PROXY_ADMIN, EnumSet.allOf(Permission.class),

            UserRole.ADMIN_VIEWER, EnumSet.of(
                    Permission.VIEW_KEYS,
                    Permission.VIEW_TEAMS,
                    Permission.VIEW_USERS,
                    Permission.VIEW_ORGS,
                    Permission.VIEW_SPEND,
                    Permission.VIEW_AUDIT_LOGS
            ),

            UserRole.ORG_ADMIN, EnumSet.of(
                    Permission.MANAGE_KEYS,
                    Permission.VIEW_KEYS,
                    Permission.MANAGE_TEAMS,
                    Permission.VIEW_TEAMS,
                    Permission.MANAGE_USERS,
                    Permission.VIEW_USERS,
                    Permission.USE_PROXY,
                    Permission.VIEW_SPEND
            ),

            UserRole.TEAM_ADMIN, EnumSet.of(
                    Permission.MANAGE_KEYS,
                    Permission.VIEW_KEYS,
                    Permission.VIEW_TEAMS,
                    Permission.MANAGE_USERS,
                    Permission.VIEW_USERS,
                    Permission.USE_PROXY,
                    Permission.VIEW_SPEND
            ),

            UserRole.TEAM_MEMBER, EnumSet.of(
                    Permission.VIEW_KEYS,
                    Permission.USE_PROXY,
                    Permission.VIEW_SPEND
            )
    );

    public Set<Permission> getPermissions(UserRole role) {
        return ROLE_PERMISSION_MAP.getOrDefault(role, EnumSet.noneOf(Permission.class));
    }
}
