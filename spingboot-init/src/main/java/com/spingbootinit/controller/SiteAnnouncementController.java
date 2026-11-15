package com.spingbootinit.controller;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.Result;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.dto.notification.AnnouncementPublishRequest;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.notification.SiteAnnouncementItemVO;
import com.spingbootinit.security.UserPermission;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.SiteAnnouncementService;
import com.spingbootinit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/announcement")
@Tag(name = "全站公告", description = "公开列表与老师、管理员发布")
public class SiteAnnouncementController {

    @Resource
    private SiteAnnouncementService siteAnnouncementService;

    @Resource
    private UserService userService;

    @Resource
    private UserRolePolicy userRolePolicy;

    @GetMapping("/public/recent")
    @Operation(summary = "最近公告（无需登录）")
    public Result<List<SiteAnnouncementItemVO>> recent(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return Result.success(siteAnnouncementService.listRecentPublic(limit));
    }

    @PostMapping("/admin/publish")
    @Operation(summary = "发布公告（老师或管理员）")
    public Result<String> adminPublish(@Valid @RequestBody AnnouncementPublishRequest request) {
        Long uid = UserContext.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        User u = userService.getById(uid);
        if (u == null) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        userRolePolicy.requirePermission(u.getUserRole(), UserPermission.ANNOUNCEMENT_MANAGE);
        long id = siteAnnouncementService.publish(uid, request);
        return Result.success(String.valueOf(id));
    }
}
