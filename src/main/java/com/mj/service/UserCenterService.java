package com.mj.service;


import com.mj.entity.FeiShuSysUser;
import com.mj.entity.UserFromCenter;

import java.util.List;

public interface UserCenterService {

    /**
     * 通过用户id获取用户中心数据
     */
    UserFromCenter getUser(Long id);

    /**
     * 获取用户中心用户列表
     */
    List<UserFromCenter> getUserList();
    /**
     * 通过openId获取用户中心用户
     */
    UserFromCenter getUserByOpenId(String openId);

    /**
     * 将用户中心的id转换成本地的user对象
     */
    FeiShuSysUser transferUCenterId(Long id);

    /**
     * 登录用户中心-api账号
     * 获取token
     */
    String login();

}
