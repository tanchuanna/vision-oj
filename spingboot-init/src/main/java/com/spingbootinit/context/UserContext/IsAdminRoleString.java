package com.spingbootinit.context.UserContext;

import com.spingbootinit.security.UserRolePolicy;
import org.springframework.stereotype.Component;

/**
 * 判断是否是管理员
 * @author: 98050
 * @create: 2021-11-05 21:05
 **/
@Component
public class IsAdminRoleString {
    private final UserRolePolicy rolePolicy = new UserRolePolicy();

    public  boolean isAdminRoleString(String role) {
        return rolePolicy.isAdmin(role);
    }
}
