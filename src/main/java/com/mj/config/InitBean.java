package com.mj.config;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.tmt.v20180321.TmtClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 初始化Bean配置类
 */
@Configuration
public class InitBean {

    @Value("${tencent.api.secretId}")
    private String secretId;
    @Value("${tencent.api.secretKey}")
    private String secretKey;

    @Bean
    public TmtClient tmtClient() {
        Credential credential = new Credential(secretId, secretKey);
        return new TmtClient(credential, "ap-beijing");
    }


}
