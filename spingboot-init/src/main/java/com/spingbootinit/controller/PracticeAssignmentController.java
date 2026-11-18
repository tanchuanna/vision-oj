package com.spingbootinit.controller;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.Result;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.dto.teaching.PracticeAssignRequest;
import com.spingbootinit.model.vo.teaching.PracticeAssignResultVO;
import com.spingbootinit.model.vo.teaching.PracticeAssignmentVO;
import com.spingbootinit.service.PracticeAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 老师一键布置练习；学生侧读取自己的练习进度。
 */
@RestController
@RequestMapping("/teaching/practice")
@RequiredArgsConstructor
public class PracticeAssignmentController {
    private final PracticeAssignmentService practiceAssignmentService;

    @PostMapping("/assign")
    public Result<PracticeAssignResultVO> assign(@RequestBody PracticeAssignRequest request) {
        long userId = requireLogin();
        return Result.success(practiceAssignmentService.assign(userId, UserContext.getCurrentRole(), request));
    }

    @GetMapping("/assignments")
    public Result<List<PracticeAssignmentVO>> assignments(@RequestParam(required = false) Long studentId) {
        long userId = requireLogin();
        return Result.success(
                practiceAssignmentService.listForTeacher(userId, UserContext.getCurrentRole(), studentId));
    }

    @DeleteMapping("/assignments/{id}")
    public Result<Void> delete(@PathVariable long id) {
        long userId = requireLogin();
        practiceAssignmentService.delete(userId, UserContext.getCurrentRole(), id);
        return Result.success("已删除", null);
    }

    @GetMapping("/my")
    public Result<List<PracticeAssignmentVO>> mine() {
        return Result.success(practiceAssignmentService.listForStudent(requireLogin()));
    }

    private long requireLogin() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}
