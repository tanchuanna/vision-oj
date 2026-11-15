package com.spingbootinit.service.impl;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.TeacherStudentService;
import com.spingbootinit.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeachingStudentAccessService {
    private final UserService userService;
    private final TeacherStudentService teacherStudentService;
    private final UserRolePolicy rolePolicy;

    public TeachingStudentAccessService(UserService userService,
                                        TeacherStudentService teacherStudentService,
                                        UserRolePolicy rolePolicy) {
        this.userService = userService;
        this.teacherStudentService = teacherStudentService;
        this.rolePolicy = rolePolicy;
    }

    public Scope resolve(long currentUserId) {
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        if (rolePolicy.isAdmin(currentUser.getUserRole())) {
            return new Scope(true, List.of());
        }
        if (rolePolicy.isTeacher(currentUser.getUserRole())) {
            return new Scope(false, teacherStudentService.activeStudentIds(currentUserId));
        }
        throw new BusinessException(ResultCode.NO_AUTH_ERROR);
    }

    public void requireStudentAccess(long currentUserId, long studentId) {
        Scope scope = resolve(currentUserId);
        if (!scope.allStudents() && !scope.studentIds().contains(studentId)) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
    }

    public record Scope(boolean allStudents, List<Long> studentIds) {
        public Scope {
            studentIds = studentIds == null ? List.of() : List.copyOf(studentIds);
        }
    }
}
