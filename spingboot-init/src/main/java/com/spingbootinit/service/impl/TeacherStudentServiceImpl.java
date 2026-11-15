package com.spingbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.mapper.TeacherStudentRelationMapper;
import com.spingbootinit.model.entity.TeacherStudentRelation;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.teaching.TeacherStudentRelationVO;
import com.spingbootinit.security.UserRole;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.TeacherStudentService;
import com.spingbootinit.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class TeacherStudentServiceImpl implements TeacherStudentService {

    private static final int STATUS_INACTIVE = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_PENDING = 2;

    private final TeacherStudentRelationMapper relationMapper;
    private final UserService userService;
    private final UserRolePolicy rolePolicy;

    public TeacherStudentServiceImpl(TeacherStudentRelationMapper relationMapper,
                                     UserService userService,
                                     UserRolePolicy rolePolicy) {
        this.relationMapper = relationMapper;
        this.userService = userService;
        this.rolePolicy = rolePolicy;
    }

    @Override
    @Transactional
    public void bind(long operatorUserId, long teacherId, long studentId) {
        requireAdministrator(operatorUserId);
        if (teacherId == studentId) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "老师和学生不能是同一账号");
        }
        User teacher = requireActiveUser(teacherId);
        User student = requireActiveUser(studentId);
        if (rolePolicy.resolve(teacher.getUserRole()) != UserRole.TEACHER) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "目标账号不是老师");
        }
        if (rolePolicy.resolve(student.getUserRole()) != UserRole.STUDENT) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "目标账号不是学生");
        }
        TeacherStudentRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<TeacherStudentRelation>()
                .eq(TeacherStudentRelation::getTeacherId, teacherId)
                .eq(TeacherStudentRelation::getStudentId, studentId));
        Date now = new Date();
        if (relation == null) {
            relation = new TeacherStudentRelation();
            relation.setTeacherId(teacherId);
            relation.setStudentId(studentId);
            relation.setStatus(STATUS_ACTIVE);
            relation.setAssignedBy(operatorUserId);
            relation.setAssignedAt(now);
            relation.setUnassignedAt(null);
            relationMapper.insert(relation);
            return;
        }
        relation.setStatus(STATUS_ACTIVE);
        relation.setAssignedBy(operatorUserId);
        relation.setAssignedAt(now);
        relation.setUnassignedAt(null);
        relationMapper.updateById(relation);
    }

    @Override
    @Transactional
    public void unbind(long operatorUserId, long teacherId, long studentId) {
        requireAdministrator(operatorUserId);
        TeacherStudentRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<TeacherStudentRelation>()
                .eq(TeacherStudentRelation::getTeacherId, teacherId)
                .eq(TeacherStudentRelation::getStudentId, studentId));
        if (relation == null || !Objects.equals(relation.getStatus(), STATUS_ACTIVE)) {
            return;
        }
        relation.setStatus(STATUS_INACTIVE);
        relation.setUnassignedAt(new Date());
        relationMapper.updateById(relation);
    }

    @Override
    public List<Long> activeStudentIds(long teacherId) {
        return relationMapper.selectList(new LambdaQueryWrapper<TeacherStudentRelation>()
                        .eq(TeacherStudentRelation::getTeacherId, teacherId)
                        .eq(TeacherStudentRelation::getStatus, STATUS_ACTIVE))
                .stream()
                .filter(relation -> Objects.equals(relation.getStatus(), STATUS_ACTIVE))
                .map(TeacherStudentRelation::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public boolean canTeacherAccessStudent(long teacherId, long studentId) {
        return activeStudentIds(teacherId).contains(studentId);
    }

    @Override
    @Transactional
    public void deactivateRelationsForRoleChange(long userId) {
        List<TeacherStudentRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<TeacherStudentRelation>()
                        .and(q -> q.eq(TeacherStudentRelation::getTeacherId, userId)
                                .or().eq(TeacherStudentRelation::getStudentId, userId))
                        .eq(TeacherStudentRelation::getStatus, STATUS_ACTIVE));
        Date now = new Date();
        for (TeacherStudentRelation relation : relations) {
            relation.setStatus(STATUS_INACTIVE);
            relation.setUnassignedAt(now);
            relationMapper.updateById(relation);
        }
    }

    // ============ 双向申请/邀请流程 ============

    @Override
    @Transactional
    public long applyToTeacher(long studentId, long teacherId) {
        if (studentId == teacherId) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "不能申请自己");
        }
        User student = requireActiveUser(studentId);
        User teacher = requireActiveUser(teacherId);
        if (rolePolicy.resolve(student.getUserRole()) != UserRole.STUDENT) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "只有学生可以申请加入老师");
        }
        if (rolePolicy.resolve(teacher.getUserRole()) != UserRole.TEACHER) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "目标账号不是老师");
        }
        return createPending(teacherId, studentId, studentId);
    }

    @Override
    @Transactional
    public long inviteStudent(long teacherId, long studentId) {
        if (teacherId == studentId) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "不能邀请自己");
        }
        User teacher = requireActiveUser(teacherId);
        User student = requireActiveUser(studentId);
        UserRole inviterRole = rolePolicy.resolve(teacher.getUserRole());
        if (inviterRole != UserRole.TEACHER && inviterRole != UserRole.ADMIN) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "只有老师或管理员可以邀请学生");
        }
        if (rolePolicy.resolve(student.getUserRole()) != UserRole.STUDENT) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "目标账号不是学生");
        }
        return createPending(teacherId, studentId, teacherId);
    }

    private long createPending(long teacherId, long studentId, long initiatorId) {
        TeacherStudentRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<TeacherStudentRelation>()
                .eq(TeacherStudentRelation::getTeacherId, teacherId)
                .eq(TeacherStudentRelation::getStudentId, studentId));
        Date now = new Date();
        if (relation == null) {
            relation = new TeacherStudentRelation();
            relation.setTeacherId(teacherId);
            relation.setStudentId(studentId);
            relation.setStatus(STATUS_PENDING);
            relation.setAssignedBy(initiatorId);
            relation.setAssignedAt(null);
            relation.setUnassignedAt(null);
            relationMapper.insert(relation);
            return relation.getId();
        }
        if (Objects.equals(relation.getStatus(), STATUS_ACTIVE)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "已建立师生关系，无需重复操作");
        }
        if (Objects.equals(relation.getStatus(), STATUS_PENDING) && Objects.equals(relation.getAssignedBy(), initiatorId)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "已提交申请，等待对方处理");
        }
        relation.setStatus(STATUS_PENDING);
        relation.setAssignedBy(initiatorId);
        relation.setAssignedAt(null);
        relation.setUnassignedAt(null);
        relationMapper.updateById(relation);
        return relation.getId();
    }

    @Override
    @Transactional
    public void approveRelation(long relationId, long approverId) {
        TeacherStudentRelation relation = requireRelation(relationId);
        if (!Objects.equals(relation.getStatus(), STATUS_PENDING)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "该申请不在待审批状态");
        }
        long pendingUserId = pendingUserId(relation);
        if (approverId != pendingUserId) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        relation.setStatus(STATUS_ACTIVE);
        relation.setAssignedAt(new Date());
        relation.setUnassignedAt(null);
        relationMapper.updateById(relation);
    }

    @Override
    @Transactional
    public void rejectRelation(long relationId, long rejecterId) {
        TeacherStudentRelation relation = requireRelation(relationId);
        if (!Objects.equals(relation.getStatus(), STATUS_PENDING)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "该申请不在待审批状态");
        }
        long pendingUserId = pendingUserId(relation);
        if (rejecterId != pendingUserId) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        relation.setStatus(STATUS_INACTIVE);
        relation.setUnassignedAt(new Date());
        relationMapper.updateById(relation);
    }

    @Override
    @Transactional
    public void cancelRelation(long relationId, long cancellerId) {
        TeacherStudentRelation relation = requireRelation(relationId);
        if (!Objects.equals(relation.getStatus(), STATUS_PENDING)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "该申请不在待审批状态");
        }
        if (!Objects.equals(relation.getAssignedBy(), cancellerId)) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        relation.setStatus(STATUS_INACTIVE);
        relation.setUnassignedAt(new Date());
        relationMapper.updateById(relation);
    }

    @Override
    @Transactional
    public void leaveRelation(long relationId, long leaverId) {
        TeacherStudentRelation relation = requireRelation(relationId);
        if (!Objects.equals(relation.getStatus(), STATUS_ACTIVE)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "师生关系未建立");
        }
        if (leaverId != relation.getTeacherId() && leaverId != relation.getStudentId()) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        relation.setStatus(STATUS_INACTIVE);
        relation.setUnassignedAt(new Date());
        relationMapper.updateById(relation);
    }

    @Override
    public List<TeacherStudentRelationVO> listPending(long userId) {
        User user = userService.getById(userId);
        if (user == null) return List.of();
        UserRole role = rolePolicy.resolve(user.getUserRole());
        LambdaQueryWrapper<TeacherStudentRelation> w = new LambdaQueryWrapper<>();
        w.eq(TeacherStudentRelation::getStatus, STATUS_PENDING);
        // 待我审批 = 我是被申请/被邀请的一方
        if (role == UserRole.TEACHER) {
            w.eq(TeacherStudentRelation::getTeacherId, userId)
                    .ne(TeacherStudentRelation::getAssignedBy, userId);
        } else if (role == UserRole.STUDENT) {
            w.eq(TeacherStudentRelation::getStudentId, userId)
                    .ne(TeacherStudentRelation::getAssignedBy, userId);
        } else {
            return List.of();
        }
        List<TeacherStudentRelation> list = relationMapper.selectList(w);
        return list.stream().map(r -> toVO(r, userId)).toList();
    }

    @Override
    public List<TeacherStudentRelationVO> listMyRelations(long userId) {
        List<TeacherStudentRelation> list = relationMapper.selectList(
                new LambdaQueryWrapper<TeacherStudentRelation>()
                        .and(q -> q.eq(TeacherStudentRelation::getTeacherId, userId)
                                .or().eq(TeacherStudentRelation::getStudentId, userId))
                        .in(TeacherStudentRelation::getStatus, STATUS_ACTIVE, STATUS_PENDING));
        List<TeacherStudentRelationVO> result = new ArrayList<>();
        for (TeacherStudentRelation r : list) {
            result.add(toVO(r, userId));
        }
        return result;
    }

    @Override
    public TeacherStudentRelationVO getRelationWith(long currentUserId, long otherUserId) {
        TeacherStudentRelation relation = relationMapper.selectOne(
                new LambdaQueryWrapper<TeacherStudentRelation>()
                        .and(q -> q.eq(TeacherStudentRelation::getTeacherId, currentUserId)
                                        .eq(TeacherStudentRelation::getStudentId, otherUserId)
                                .or().eq(TeacherStudentRelation::getTeacherId, otherUserId)
                                        .eq(TeacherStudentRelation::getStudentId, currentUserId))
                        .in(TeacherStudentRelation::getStatus, STATUS_ACTIVE, STATUS_PENDING)
                        .last("LIMIT 1"));
        return relation == null ? null : toVO(relation, currentUserId);
    }

    // ============ 辅助方法 ============

    private TeacherStudentRelation requireRelation(long relationId) {
        TeacherStudentRelation relation = relationMapper.selectById(relationId);
        if (relation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "关系不存在");
        }
        return relation;
    }

    /** 等待谁审批：返回待审批方的 userId */
    private long pendingUserId(TeacherStudentRelation relation) {
        long initiator = relation.getAssignedBy() == null ? 0L : relation.getAssignedBy();
        if (initiator == relation.getStudentId()) {
            return relation.getTeacherId();
        }
        return relation.getStudentId();
    }

    private TeacherStudentRelationVO toVO(TeacherStudentRelation r, long currentUserId) {
        TeacherStudentRelationVO vo = new TeacherStudentRelationVO();
        vo.setId(r.getId());
        boolean currentIsTeacher = Objects.equals(r.getTeacherId(), currentUserId);
        long peerId = currentIsTeacher ? r.getStudentId() : r.getTeacherId();
        vo.setPeerId(peerId);
        User peer = userService.getById(peerId);
        if (peer != null) {
            vo.setPeerUsername(peer.getUsername());
            vo.setPeerNickname(peer.getNickname());
            vo.setPeerAvatar(peer.getAvatar());
        }
        vo.setStatus(r.getStatus());
        vo.setInitiatedBy(r.getAssignedBy());
        vo.setMine(r.getAssignedBy() != null && r.getAssignedBy() == currentUserId);
        if (Objects.equals(r.getStatus(), STATUS_PENDING)) {
            long pending = pendingUserId(r);
            vo.setPendingRole(Objects.equals(pending, r.getTeacherId()) ? "teacher" : "student");
        }
        vo.setCreateTime(r.getCreateTime());
        vo.setAssignedAt(r.getAssignedAt());
        return vo;
    }

    private void requireAdministrator(long operatorUserId) {
        User operator = userService.getById(operatorUserId);
        if (operator == null || !rolePolicy.isAdmin(operator.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
    }

    private User requireActiveUser(long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        if (!Objects.equals(user.getStatus(), 1)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "禁用账号不能建立师生关系");
        }
        return user;
    }
}
