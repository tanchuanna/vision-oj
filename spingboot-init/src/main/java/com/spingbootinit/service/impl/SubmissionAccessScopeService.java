package com.spingbootinit.service.impl;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.IsAdminRoleString;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubmissionAccessScopeService {
    private final UserService userService;
    private final IsAdminRoleString adminRole;
    private final TeachingStudentAccessService teachingStudentAccessService;
    private final UserRolePolicy rolePolicy;

    public SubmissionAccessScopeService(UserService userService, IsAdminRoleString adminRole) {
        this(userService, adminRole, null, new UserRolePolicy());
    }

    @Autowired
    public SubmissionAccessScopeService(UserService userService,
                                        IsAdminRoleString adminRole,
                                        TeachingStudentAccessService teachingStudentAccessService,
                                        UserRolePolicy rolePolicy) {
        this.userService = userService;
        this.adminRole = adminRole;
        this.teachingStudentAccessService = teachingStudentAccessService;
        this.rolePolicy = rolePolicy;
    }

    public Access resolve(long currentUserId, String requestedScope) {
        String scope = requestedScope == null ? "SELF" : requestedScope.trim().toUpperCase();
        if ("SELF".equals(scope)) {
            return Access.self(currentUserId);
        }
        if (!"MANAGED".equals(scope)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "数据范围仅支持 SELF 或 MANAGED");
        }
        User user = userService.getById(currentUserId);
        if (user == null) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        if (adminRole.isAdminRoleString(user.getUserRole())) {
            return Access.allManaged();
        }
        if (rolePolicy.isTeacher(user.getUserRole()) && teachingStudentAccessService != null) {
            var teachingScope = teachingStudentAccessService.resolve(currentUserId);
            return Access.managedStudents(teachingScope.studentIds());
        }
        throw new BusinessException(ResultCode.NO_AUTH_ERROR);
    }

    public boolean isAdmin(long currentUserId) {
        User user = userService.getById(currentUserId);
        return user != null && adminRole.isAdminRoleString(user.getUserRole());
    }

    public Access resolveForDetail(long currentUserId) {
        User user = userService.getById(currentUserId);
        if (user == null) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        if (adminRole.isAdminRoleString(user.getUserRole())) {
            return Access.allManaged();
        }
        if (rolePolicy.isTeacher(user.getUserRole()) && teachingStudentAccessService != null) {
            return Access.managedStudents(teachingStudentAccessService.resolve(currentUserId).studentIds());
        }
        return Access.self(currentUserId);
    }

    public record Access(Long ownerUserId, boolean managed, boolean allManagedUsers, List<Long> managedUserIds) {
        public Access {
            managedUserIds = managedUserIds == null ? List.of() : List.copyOf(managedUserIds);
        }
        public static Access self(long userId) { return new Access(userId, false, false, List.of()); }
        public static Access allManaged() { return new Access(null, true, true, List.of()); }
        public static Access managedStudents(List<Long> ids) { return new Access(null, true, false, ids); }
    }
}
