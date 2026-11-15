package com.spingbootinit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.Result;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.teaching.TeacherStudentRelationVO;
import com.spingbootinit.model.vo.teaching.TeachingStudentVO;
import com.spingbootinit.model.vo.teaching.TeachingTeacherVO;
import com.spingbootinit.security.UserPermission;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.TeacherStudentService;
import com.spingbootinit.service.UserService;
import com.spingbootinit.service.impl.TeachingStudentQueryService;
import com.spingbootinit.service.impl.UserOnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/teaching")
public class TeacherStudentController {
    private final TeacherStudentService teacherStudentService;
    private final UserRolePolicy rolePolicy;
    private final TeachingStudentQueryService teachingStudentQueryService;
    private final UserService userService;
    private final UserOnlineService userOnlineService;

    /** Kept for focused unit tests and binary compatibility with the first controller version. */
    public TeacherStudentController(TeacherStudentService teacherStudentService, UserRolePolicy rolePolicy) {
        this(teacherStudentService, rolePolicy, null, null, null);
    }

    @Autowired
    public TeacherStudentController(TeacherStudentService teacherStudentService, UserRolePolicy rolePolicy,
                                    TeachingStudentQueryService teachingStudentQueryService,
                                    UserService userService, UserOnlineService userOnlineService) {
        this.teacherStudentService = teacherStudentService;
        this.rolePolicy = rolePolicy;
        this.teachingStudentQueryService = teachingStudentQueryService;
        this.userService = userService;
        this.userOnlineService = userOnlineService;
    }

    @GetMapping("/students/page")
    public Result<Page<TeachingStudentVO>> students(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "15") long size,
                                                     @RequestParam(required = false) String keyword) {
        return Result.success(teachingStudentQueryService.page(requireLogin(), current, size, keyword));
    }

    @GetMapping("/students/{studentId}/overview")
    public Result<TeachingStudentVO> overview(@PathVariable long studentId) {
        return Result.success(teachingStudentQueryService.overview(requireLogin(), studentId));
    }

    @GetMapping("/admin/teachers/{teacherId}/students")
    public Result<List<Long>> assignedStudentIds(@PathVariable long teacherId) {
        requireLogin();
        rolePolicy.requirePermission(UserContext.getCurrentRole(), UserPermission.TEACHER_STUDENT_ASSIGN);
        return Result.success(teacherStudentService.activeStudentIds(teacherId));
    }

    @PostMapping("/admin/teachers/{teacherId}/students/{studentId}")
    public Result<Void> bind(@PathVariable long teacherId, @PathVariable long studentId) {
        teacherStudentService.bind(requireLogin(), teacherId, studentId);
        return Result.success("学生已分配", null);
    }

    @DeleteMapping("/admin/teachers/{teacherId}/students/{studentId}")
    public Result<Void> unbind(@PathVariable long teacherId, @PathVariable long studentId) {
        teacherStudentService.unbind(requireLogin(), teacherId, studentId);
        return Result.success("已解除分配", null);
    }

    /**
     * 学生查看老师列表（含在线状态）。任何已登录用户均可调用，仅返回老师角色。
     */
    @GetMapping("/teachers/page")
    public Result<Page<TeachingTeacherVO>> teachers(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "50") long size,
                                                    @RequestParam(required = false) String keyword) {
        requireLogin();
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .eq(User::getUserRole, "teacher")
                .eq(User::getStatus, 1)
                .and(keyword != null && !keyword.isBlank(),
                        q -> q.like(User::getUsername, keyword.trim()).or().like(User::getNickname, keyword.trim()))
                .orderByDesc(User::getId);
        Page<User> page = userService.page(new Page<>(safeCurrent, safeSize), query);
        List<TeachingTeacherVO> records = page.getRecords().stream().map(u -> {
            TeachingTeacherVO vo = new TeachingTeacherVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            return vo;
        }).collect(Collectors.toList());
        Set<Long> ids = records.stream().map(TeachingTeacherVO::getId).collect(Collectors.toSet());
        Set<Long> onlineIds = userOnlineService.filterOnline(ids);
        for (TeachingTeacherVO vo : records) {
            vo.setOnline(onlineIds.contains(vo.getId()));
        }
        Page<TeachingTeacherVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records);
        return Result.success(result);
    }

    // ============ 双向申请/邀请/审批 ============

    /** 学生申请加入老师 */
    @PostMapping("/relations/apply")
    public Result<Long> apply(@RequestBody Map<String, Long> body) {
        Long teacherId = body.get("teacherId");
        if (teacherId == null) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "缺少 teacherId");
        }
        long id = teacherStudentService.applyToTeacher(requireLogin(), teacherId);
        return Result.success("申请已提交，等待老师同意", id);
    }

    /** 老师邀请学生 */
    @PostMapping("/relations/invite")
    public Result<Long> invite(@RequestBody Map<String, Long> body) {
        Long studentId = body.get("studentId");
        if (studentId == null) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "缺少 studentId");
        }
        long id = teacherStudentService.inviteStudent(requireLogin(), studentId);
        return Result.success("邀请已发送，等待学生同意", id);
    }

    /** 同意申请/邀请 */
    @PostMapping("/relations/{id}/approve")
    public Result<Void> approve(@PathVariable long id) {
        teacherStudentService.approveRelation(id, requireLogin());
        return Result.success("已同意", null);
    }

    /** 拒绝申请/邀请 */
    @PostMapping("/relations/{id}/reject")
    public Result<Void> reject(@PathVariable long id) {
        teacherStudentService.rejectRelation(id, requireLogin());
        return Result.success("已拒绝", null);
    }

    /** 发起人撤回申请/邀请 */
    @PostMapping("/relations/{id}/cancel")
    public Result<Void> cancel(@PathVariable long id) {
        teacherStudentService.cancelRelation(id, requireLogin());
        return Result.success("已撤回", null);
    }

    /** 已建立关系后：学生退出 / 老师移除 */
    @PostMapping("/relations/{id}/leave")
    public Result<Void> leave(@PathVariable long id) {
        teacherStudentService.leaveRelation(id, requireLogin());
        return Result.success("已解除师生关系", null);
    }

    /** 待我审批的列表 */
    @GetMapping("/relations/pending")
    public Result<List<TeacherStudentRelationVO>> pending() {
        return Result.success(teacherStudentService.listPending(requireLogin()));
    }

    /** 我的师生关系（活跃 + 待审批） */
    @GetMapping("/relations/my")
    public Result<List<TeacherStudentRelationVO>> myRelations() {
        return Result.success(teacherStudentService.listMyRelations(requireLogin()));
    }

    /** 查询与某用户的关系状态 */
    @GetMapping("/relations/with/{userId}")
    public Result<TeacherStudentRelationVO> relationWith(@PathVariable long userId) {
        return Result.success(teacherStudentService.getRelationWith(requireLogin(), userId));
    }

    /** 老师搜索全站学生（用于邀请） */
    @GetMapping("/students/search")
    public Result<Page<TeachingStudentVO>> searchStudents(@RequestParam(defaultValue = "1") long current,
                                                          @RequestParam(defaultValue = "20") long size,
                                                          @RequestParam(required = false) String keyword) {
        long me = requireLogin();
        if (!rolePolicy.isTeacher(UserContext.getCurrentRole()) && !rolePolicy.isAdmin(UserContext.getCurrentRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .eq(User::getUserRole, "user")
                .eq(User::getStatus, 1)
                .and(keyword != null && !keyword.isBlank(),
                        q -> q.like(User::getUsername, keyword.trim()).or().like(User::getNickname, keyword.trim()))
                .orderByDesc(User::getId);
        Page<User> page = userService.page(new Page<>(safeCurrent, safeSize), query);
        List<TeachingStudentVO> records = page.getRecords().stream().map(u -> {
            TeachingStudentVO vo = new TeachingStudentVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setAccountStatus(u.getStatus());
            return vo;
        }).collect(Collectors.toList());
        Set<Long> ids = records.stream().map(TeachingStudentVO::getId).collect(Collectors.toSet());
        Set<Long> onlineIds = userOnlineService.filterOnline(ids);
        for (TeachingStudentVO vo : records) {
            vo.setOnline(onlineIds.contains(vo.getId()));
        }
        Page<TeachingStudentVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records);
        return Result.success(result);
    }

    private long requireLogin() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}
