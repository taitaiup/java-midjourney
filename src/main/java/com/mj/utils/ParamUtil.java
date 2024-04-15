package com.mj.utils;

import com.mj.config.MjProperties;
import lombok.Setter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ParamUtil {
    @Setter
    private String defaultSessionId = "9c5711b797f4b1ef22aaf9f2312629d835a";

    public static String replaceCommonParams(String paramsStr, MjProperties mjProperties, String nonce) {
        return paramsStr.replace("$guild_id", mjProperties.getDiscord().getGuildId())
                .replace("$channel_id", mjProperties.getDiscord().getChannelId())
                .replace("$session_id", defaultSessionId)
                .replace("$nonce", nonce);
    }
}
