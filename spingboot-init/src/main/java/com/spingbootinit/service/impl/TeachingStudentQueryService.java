package com.spingbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.mapper.QuestionSubmitMapper;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.teaching.TeachingStudentVO;
import com.spingbootinit.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeachingStudentQueryService {
    private final TeachingStudentAccessService accessService;
    private final UserService userService;
    private final QuestionSubmitMapper questionSubmitMapper;
    private final UserOnlineService userOnlineService;

    public Page<TeachingStudentVO> page(long actorId, long current, long size, String keyword) {
        var scope = accessService.resolve(actorId);
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.min(50, Math.max(1, size));
        if (!scope.allStudents() && scope.studentIds().isEmpty()) return new Page<>(safeCurrent, safeSize, 0);

        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .eq(User::getUserRole, "user")
                .in(!scope.allStudents(), User::getId, scope.studentIds())
                .and(StringUtils.isNotBlank(keyword), q -> q.like(User::getUsername, keyword.trim())
                        .or().like(User::getNickname, keyword.trim()));
        Page<User> users = userService.page(new Page<>(safeCurrent, safeSize), query);
        List<TeachingStudentVO> records = users.getRecords().stream()
                .map(u -> questionSubmitMapper.selectTeachingStudent(u.getId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        // 批量填充在线状态
        Set<Long> ids = records.stream().map(TeachingStudentVO::getId).collect(Collectors.toSet());
        Set<Long> onlineIds = userOnlineService.filterOnline(ids);
        for (TeachingStudentVO vo : records) {
            vo.setOnline(onlineIds.contains(vo.getId()));
        }
        Page<TeachingStudentVO> result = new Page<>(users.getCurrent(), users.getSize(), users.getTotal());
        result.setRecords(records);
        return result;
    }

    public TeachingStudentVO overview(long actorId, long studentId) {
        accessService.requireStudentAccess(actorId, studentId);
        TeachingStudentVO result = questionSubmitMapper.selectTeachingStudent(studentId);
        if (result == null) throw new BusinessException(ResultCode.NOT_FOUND_ERROR);
        result.setOnline(userOnlineService.isOnline(studentId));
        return result;
    }
}
