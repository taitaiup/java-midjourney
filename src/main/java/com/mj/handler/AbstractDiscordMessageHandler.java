package com.mj.handler;

import cn.hutool.core.text.CharSequenceUtil;
import com.mj.support.DiscordHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;


/**
 */
@Slf4j
public abstract class AbstractDiscordMessageHandler {

    @Resource
    protected DiscordHelper discordHelper;

    /**
     * 接收到新消息
     *
     * @param message 消息
     */
    abstract void onMessageReceived(Message message);

    /**
     * 已发送的消息更新
     *
     * @param message 消息
     */
    abstract void onMessageUpdate(Message message);

    /**
     * 反代，替换cdn
     */
    protected String replaceCdnUrl(String imageUrl) {
        if (CharSequenceUtil.isBlank(imageUrl)) {
            return imageUrl;
        }
        String cdn = this.discordHelper.getCdn();
        if (CharSequenceUtil.startWith(imageUrl, cdn)) {
            return imageUrl;
        }
        return CharSequenceUtil.replaceFirst(imageUrl, DiscordHelper.DISCORD_CDN_URL, cdn);
    }

    public String getMessageHash(String imageUrl) {
        if (CharSequenceUtil.isBlank(imageUrl)) {
            return null;
        }
        if (CharSequenceUtil.contains(imageUrl, "_grid_0.webp")) {
            int hashStartIndex = imageUrl.lastIndexOf("/");
            int hashEndIndex = imageUrl.lastIndexOf("_grid_0.webp");
            if (hashStartIndex < 0) {
                return null;
            }
            //https://cdn.discordapp.com/attachments/1197143389662085173/1202947665701376050/2bd33e73-a73d-4d3a-bd3b-1ddc866ce1e0_grid_0.webp
            // ?ex=65cf4f51&is=65bcda51&hm=5897b2e1a7610b8be2f443f621b2fcdc324f2e5c49dd1566c6ef3dac262eb3e6&
            //截取出hash即为message_hash
            return CharSequenceUtil.sub(imageUrl, hashStartIndex + 1, hashEndIndex);
        }
        if (CharSequenceUtil.endWith(imageUrl, "_grid_0.webp")) {
            int hashStartIndex = imageUrl.lastIndexOf("/");
            if (hashStartIndex < 0) {
                return null;
            }
            return CharSequenceUtil.sub(imageUrl, hashStartIndex + 1, imageUrl.length() - "_grid_0.webp".length());
        }
        int hashStartIndex = imageUrl.lastIndexOf("_");
        if (hashStartIndex < 0) {
            return null;
        }
        return CharSequenceUtil.subBefore(imageUrl.substring(hashStartIndex + 1), ".", true);
    }

}
