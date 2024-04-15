package com.mj.support;

import cn.hutool.core.text.CharSequenceUtil;
import com.mj.config.MjProperties;
import jakarta.annotation.Resource;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.springframework.stereotype.Component;

@Component
public class DiscordHelper {

	@Resource
	private MjProperties properties;
	public static final String DISCORD_SERVER_URL = "https://discord.com";
	/**
	 * 比如：
	 * https://cdn.discordapp.com/attachments/1197143389662085173/1198951610999640145/an0nymous2383_Programmer_fixing_bug_in_darkroom_screen_emits_fa_c7f053a0-b376-4230-9d0d-71e6a499848d.png?ex=65c0c5b3&is=65ae50b3&hm=6a2c4345300f3b5189900d70eb88bf463805d8a31d2f5a0ab5149b58020c1d9b&
	 * https://rp-discordapp-cdn.zxagi.cn/ephemeral-attachments/1092492867185950852/1214537535288639538/1709638707288245.png?ex=65f97939&is=65e70439&hm=6ef5931fa7ca490bf7d363e1d9d9b6baf9dc8fa671f855dc2e67f664110a4231&
	 */
	public static final String DISCORD_CDN_URL = "https://cdn.discordapp.com";
	public static final String DISCORD_UPLOAD_URL = "https://discord-attachments-uploads-prd.storage.googleapis.com";
	public static final String DISCORD_WSS_URL = "wss://gateway.discord.gg";

	/**
	 * 获取server反代
	 */
	public String getServer() {
		if (CharSequenceUtil.isBlank(this.properties.getNgDiscord().getServer())) {
			return DISCORD_SERVER_URL;
		}
		String serverUrl = this.properties.getNgDiscord().getServer();
		if (serverUrl.endsWith("/")) {
			serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
		}
		return serverUrl;
	}

	/**
	 * 获取cdn反代
	 */
	public String getCdn() {
		if (CharSequenceUtil.isBlank(this.properties.getNgDiscord().getCdn())) {
			return DISCORD_CDN_URL;
		}
		String cdnUrl = this.properties.getNgDiscord().getCdn();
		if (cdnUrl.endsWith("/")) {
			cdnUrl = cdnUrl.substring(0, cdnUrl.length() - 1);
		}
		return cdnUrl;
	}

	/**
	 * 获取上传地址反代
	 */
	public String getDiscordUploadUrl(String uploadUrl) {
		if (CharSequenceUtil.isBlank(this.properties.getNgDiscord().getUploadServer()) || CharSequenceUtil.isBlank(uploadUrl)) {
			return uploadUrl;
		}
		String uploadServer = this.properties.getNgDiscord().getUploadServer();
		if (uploadServer.endsWith("/")) {
			uploadServer = uploadServer.substring(0, uploadServer.length() - 1);
		}
		return uploadUrl.replaceFirst(DISCORD_UPLOAD_URL, uploadServer);
	}

	/**
	 * 获取网关反代
	 */
	public String getWss() {
		if (CharSequenceUtil.isBlank(this.properties.getNgDiscord().getWss())) {
			return DISCORD_WSS_URL;
		}
		String wssUrl = this.properties.getNgDiscord().getWss();
		if (wssUrl.endsWith("/")) {
			wssUrl = wssUrl.substring(0, wssUrl.length() - 1);
		}
		return wssUrl;
	}

	/**
	 * 替换cdn
	 */
	public String replaceCdnUrl(String imageUrl) {
		if (CharSequenceUtil.isBlank(imageUrl)) {
			return imageUrl;
		}
		String cdn = this.getCdn();
		if (CharSequenceUtil.startWith(imageUrl, cdn)) {
			return imageUrl;
		}
		return CharSequenceUtil.replaceFirst(imageUrl, DiscordHelper.DISCORD_CDN_URL, cdn);
	}
	/**
	 * 获取并替换imageUrl
	 */
	public String getImageUrl(DataObject message) {
		DataArray attachments = message.getArray("attachments");
		if (!attachments.isEmpty()) {
			String imageUrl = attachments.getObject(0).getString("url");
			return replaceCdnUrl(imageUrl);
		}
		return null;
	}
	/**
	 * 获取并替换messageHash
	 */
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
