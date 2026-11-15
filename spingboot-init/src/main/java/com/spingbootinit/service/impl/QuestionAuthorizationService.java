package com.spingbootinit.service.impl;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.mapper.QuestionMapper;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserPermission;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionAuthorizationService {
    private final UserService userService;
    private final UserRolePolicy rolePolicy;
    private final QuestionMapper questionMapper;

    public User requireAuthor(long actorId) {
        User actor = userService.getById(actorId);
        if (actor == null || !Integer.valueOf(1).equals(actor.getStatus())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        rolePolicy.requirePermission(actor.getUserRole(), UserPermission.QUESTION_AUTHOR);
        return actor;
    }

    public User requireCanManage(long actorId, long questionId) {
        User actor = requireAuthor(actorId);
        if (rolePolicy.hasPermission(actor.getUserRole(), UserPermission.QUESTION_MANAGE_ALL)) {
            return actor;
        }
        Question question = questionMapper.selectById(questionId);
        if (question == null) throw new BusinessException(ResultCode.NOT_FOUND_ERROR);
        if (!rolePolicy.hasPermission(actor.getUserRole(), UserPermission.QUESTION_MANAGE_OWN)
                || !Long.valueOf(actorId).equals(question.getUserId())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        return actor;
    }

    public boolean canManageAll(User actor) {
        return actor != null && rolePolicy.hasPermission(actor.getUserRole(), UserPermission.QUESTION_MANAGE_ALL);
    }
}
