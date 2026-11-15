package com.spingbootinit.service.impl;

import com.spingbootinit.common.constant.RedisKeyConstants;
import com.spingbootinit.utils.RedisUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户在线状态服务。
 * <p>
 * 在线判定规则：前端在页面活跃期间定时调用 {@link #markOnline(Long)}，
 * 后端在 Redis 中写入带 TTL 的心跳键。只要键未过期即视为在线；
 * 关闭页面 / 切走标签页后心跳停止，TTL 到期自动变为离线。
 * </p>
 */
@Service
public class UserOnlineService {

    /** 心跳过期时间：超过该时长未收到心跳即视为离线 */
    private static final long ONLINE_TTL_SECONDS = 75L;

    private final RedisUtil redisUtil;

    public UserOnlineService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    /**
     * 记录用户心跳（刷新在线 TTL）。
     */
    public void markOnline(Long userId) {
        if (userId == null) {
            return;
        }
        redisUtil.set(RedisKeyConstants.onlineUserKey(userId), "1", ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 立即将用户置为离线（登出时调用）。
     */
    public void markOffline(Long userId) {
        if (userId == null) {
            return;
        }
        redisUtil.delete(RedisKeyConstants.onlineUserKey(userId));
    }

    /**
     * 判断单个用户是否在线。
     */
    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisUtil.hasKey(RedisKeyConstants.onlineUserKey(userId)));
    }

    /**
     * 批量过滤出在线用户 ID 集合。
     *
     * @param userIds 待查询的用户 ID 集合
     * @return 其中在线的用户 ID 集合；入参为空时返回空集合
     */
    public Set<Long> filterOnline(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> online = new HashSet<>();
        for (Long uid : userIds) {
            if (uid != null && isOnline(uid)) {
                online.add(uid);
            }
        }
        return online;
    }
}
