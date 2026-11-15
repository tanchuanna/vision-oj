package com.spingbootinit.service.impl;

import com.spingbootinit.mapper.QuestionMapper;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserPermission;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiQuestionAccessService {
    private final UserService userService;
    private final QuestionMapper questionMapper;
    private final UserRolePolicy rolePolicy;

    public boolean canReceiveFullAnswer(long userId, String rawQuestionId) {
        User user = userService.getById(userId);
        if (user == null) return false;
        if (rolePolicy.hasPermission(user.getUserRole(), UserPermission.AI_FULL_ANSWER_ALL)) return true;
        if (!rolePolicy.isTeacher(user.getUserRole()) || rawQuestionId == null || rawQuestionId.isBlank()) return false;
        try {
            var question = questionMapper.selectById(Long.parseLong(rawQuestionId.trim()));
            return question != null && Long.valueOf(userId).equals(question.getUserId());
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
