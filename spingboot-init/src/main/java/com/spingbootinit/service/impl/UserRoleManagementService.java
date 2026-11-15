package com.spingbootinit.service.impl;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserRole;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.TeacherStudentService;
import com.spingbootinit.service.UserService;
import com.spingbootinit.service.UserStatusNotifyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRoleManagementService {
    private final UserService userService;
    private final TeacherStudentService teacherStudentService;
    private final UserStatusNotifyService statusNotifyService;
    private final UserRolePolicy rolePolicy;

    public UserRoleManagementService(UserService userService,
                                     TeacherStudentService teacherStudentService,
                                     UserStatusNotifyService statusNotifyService,
                                     UserRolePolicy rolePolicy) {
        this.userService = userService;
        this.teacherStudentService = teacherStudentService;
        this.statusNotifyService = statusNotifyService;
        this.rolePolicy = rolePolicy;
    }

    @Transactional
    public void changeRole(long operatorUserId, long targetUserId, String requestedRole) {
        User operator = userService.getById(operatorUserId);
        if (operator == null || !rolePolicy.isAdmin(operator.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        if (operatorUserId == targetUserId) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "不能修改自己的管理员角色");
        }
        User target = userService.getById(targetUserId);
        if (target == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        if (rolePolicy.isAdmin(target.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR.getCode(), "管理员账号不能在此处变更角色");
        }
        UserRole next = rolePolicy.resolve(requestedRole);
        if (next != UserRole.STUDENT && next != UserRole.TEACHER) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "角色仅支持 user 或 teacher");
        }
        if (rolePolicy.resolve(target.getUserRole()) == next) {
            return;
        }
        teacherStudentService.deactivateRelationsForRoleChange(targetUserId);
        target.setUserRole(next.storageValue());
        if (!userService.updateById(target)) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "修改角色失败");
        }
        statusNotifyService.bumpStatusRevision(targetUserId);
    }
}
