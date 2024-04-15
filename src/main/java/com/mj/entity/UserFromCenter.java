package com.mj.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 从用户中心usercenter请求来的数据，封装成model
 */
@Data
public class UserFromCenter {
    private Long id;
    private String username;
    private String mobile;
    private String avatar;
    private String nickname;
    private Boolean enabled;
    private LocalDateTime createTime;
    private List<Role> roleList;
    private List<Oauth> oauthList;
    /**
     * 部门树
     */
    private List<String> departments;

    @Data
    public class Role {
        private Long id;
        private String name;
    }

    @Data
    public class Oauth {
        private Long id;
        private Long userId;
        private String oauthType;
        private String oauthId;
        private String unionid;
    }
}
