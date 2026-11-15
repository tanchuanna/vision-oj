package com.spingbootinit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.Result;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.dto.contest.ContestAdminAddRequest;
import com.spingbootinit.model.dto.contest.ContestAdminBatchDeleteRequest;
import com.spingbootinit.model.dto.contest.ContestAdminPageRequest;
import com.spingbootinit.model.dto.contest.ContestAdminUpdateRequest;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.contest.ContestAdminListItemVO;
import com.spingbootinit.security.UserPermission;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.ContestService;
import com.spingbootinit.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 赛事管理（老师和管理员）
 */
@RestController
@RequestMapping("/contest/admin")
public class ContestAdminController {

    @Resource
    private ContestService contestService;

    @Resource
    private UserService userService;

    @Resource
    private UserRolePolicy userRolePolicy;

    private void assertCanManageContest() {
        Long uid = UserContext.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        User user = userService.getById(uid);
        if (user == null) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        userRolePolicy.requirePermission(user.getUserRole(), UserPermission.CONTEST_MANAGE);
    }

    @PostMapping("/page")
    public Result<Page<ContestAdminListItemVO>> page(@RequestBody ContestAdminPageRequest request) {
        assertCanManageContest();
        return Result.success(contestService.pageAdminContests(request));
    }


    @PostMapping("/add")
    public Result<String> add(@Valid @RequestBody ContestAdminAddRequest request) {
        assertCanManageContest();
        return Result.success(String.valueOf(contestService.adminAddContest(request)));
    }

    @PostMapping("/update")
    public Result<Boolean> update(@Valid @RequestBody ContestAdminUpdateRequest request) {
        assertCanManageContest();
        contestService.adminUpdateContest(request);
        return Result.success(true);
    }

    @PostMapping("/delete/batch")
    public Result<Boolean> deleteBatch(@Valid @RequestBody ContestAdminBatchDeleteRequest request) {
        assertCanManageContest();
        contestService.adminDeleteContests(request);
        return Result.success(true);
    }
}
