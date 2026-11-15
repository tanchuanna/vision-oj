package com.spingbootinit.service.impl;

import com.spingbootinit.context.UserContext.IsAdminRoleString;
import com.spingbootinit.mapper.QuestionSubmitMapper;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.service.QuestionSolutionAccessService;
import com.spingbootinit.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class QuestionSolutionAccessServiceImpl implements QuestionSolutionAccessService {
    @Resource
    private QuestionSubmitMapper questionSubmitMapper;
    @Resource
    private UserService userService;
    @Resource
    private IsAdminRoleString isAdminRole;

    @Override
    public boolean hasAccepted(long userId, long questionId) {
        return userId > 0 && questionId > 0 && questionSubmitMapper.existsAccepted(userId, questionId);
    }

    @Override
    public AccessDecision decide(Long userId, long questionId) {
        if (userId == null || userId <= 0 || questionId <= 0) {
            return AccessDecision.hintOnly();
        }
        User user = userService.getById(userId);
        if (user == null) {
            return AccessDecision.hintOnly();
        }
        boolean admin = isAdminRole.isAdminRoleString(user.getUserRole());
        if (admin) {
            return new AccessDecision(false, true, true, true);
        }
        boolean accepted = hasAccepted(userId, questionId);
        return new AccessDecision(accepted, accepted, accepted, false);
    }
}
