package com.mj.handler;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.mj.config.MjProperties;
import com.mj.entity.Task;
import com.mj.enums.TaskStatus;
import com.mj.service.DiscordService;
import com.mj.service.TaskService;
import com.mj.support.DiscordHelper;
import com.mj.utils.ParamUtil;
import com.neovisionaries.ws.client.*;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.data.DataType;
import net.dv8tion.jda.internal.requests.WebSocketCode;
import net.dv8tion.jda.internal.utils.compress.Decompressor;
import net.dv8tion.jda.internal.utils.compress.ZlibDecompressor;
import org.apache.logging.log4j.util.Strings;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RegionWebSocketListener extends WebSocketAdapter {

    private final ScheduledExecutorService heartExecutor;

    private Decompressor decompressor;

    private WebSocket socket = null;

    private long interval = 41250;

    private boolean heartbeatAck = false;

    private MjProperties mjProperties;
    private final DataObject authData;
    private String sessionId;

    private Object sequence = null;
    private Future<?> heartbeatTimeout;
    private Future<?> heartbeatInterval;
    private String resumeGatewayUrl;

    private TaskService taskService;
    private DiscordService discordService;
    protected DiscordHelper discordHelper;
    private boolean trying = false;

    public RegionWebSocketListener(MjProperties mjProperties, TaskService taskService, DiscordService discordService, DiscordHelper discordHelper) {
        this.heartExecutor = Executors.newSingleThreadScheduledExecutor();
        this.mjProperties = mjProperties;
        this.authData = createAuthData();
        this.taskService = taskService;
        this.discordService = discordService;
        this.discordHelper = discordHelper;
    }

    public void startWss() throws Exception {
        this.decompressor = new ZlibDecompressor(2048);
        WebSocketFactory webSocketFactory = new WebSocketFactory().setConnectionTimeout(10000);
        MjProperties.ProxyConfig proxy = mjProperties.getProxy();
        if (Strings.isNotBlank(proxy.getHost())) {
            ProxySettings proxySettings = webSocketFactory.getProxySettings();
            proxySettings.setHost(proxy.getHost());
            proxySettings.setPort(proxy.getPort());
        }
        String gatewayUrl = CharSequenceUtil.isNotBlank(this.resumeGatewayUrl) ? this.resumeGatewayUrl : discordHelper.getWss();
        this.socket = webSocketFactory.createSocket(gatewayUrl + "/?encoding=json&v=9&compress=zlib-stream");
        this.socket.addListener(this);
		/*webSocket.addHeader("Accept-Encoding", "gzip, deflate, br")
				.addHeader("Accept-Language", "zh-CN,zh;q=0.9")
				.addHeader("Cache-Control", "no-cache")
				.addHeader("Pragma", "no-cache")
				.addHeader("Sec-Websocket-Extensions", "permessage-deflate; client_max_window_bits")
				.addHeader("User-Agent", mjProperties.getDiscord().getUserAgent());*/
        this.socket.connect();
    }

    /**
     * 收到消息后
     */


    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
        if (this.decompressor == null) {
            return;
        }
        byte[] decompressBinary = this.decompressor.decompress(binary);
        if (decompressBinary == null) {
            return;
        }
        String json = new String(decompressBinary, StandardCharsets.UTF_8);
        //DataObject 是 JDA 内部用于处理和表示 JSON 数据的类
        DataObject data = DataObject.fromJson(json);
        //data为discord发送来的数据，他的数据结构为：https://discord.com/developers/docs/topics/gateway-events#payload-structure
        int opCode = data.getInt("op");//op为操作码
        switch (opCode) {
            case WebSocketCode.HEARTBEAT://心跳
                //System.out.println("1-收到心跳消息: " + Thread.currentThread().getName());
                handleHeartbeat();
                break;
            case WebSocketCode.HEARTBEAT_ACK://心跳确认
                //System.out.println("11-收到心跳确认: " + Thread.currentThread().getName());
                this.heartbeatAck = true;
                clearHeartbeatTimeout();
                break;
            case WebSocketCode.HELLO:
                //System.out.println("10-收到hello消息: " + Thread.currentThread().getName());
                handleHello(data);
                //识别
                doResumeOrIdentify();
                break;
            case WebSocketCode.DISPATCH:
                handleDispatch(data);
                break;
            case WebSocketCode.RESUME://恢复先前断开的会话
                log.info("接收到RESUME事件");
                break;
            //您应该尝试重新连接并立即恢复
            case WebSocketCode.RECONNECT:
                log.warn("收到重新连接事件，需进行重新连接");
                sendReconnect("收到重新连接事件，需进行重新连接");
                break;
            //会话已失效
            case WebSocketCode.INVALIDATE_SESSION:
                log.warn("接收到INVALIDATE_SESSION事件");
                break;
            default:
                break;
        }
    }
    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {

        int code;
        String closeReason;
        if (closedByServer) {
            code = serverCloseFrame.getCloseCode();
            closeReason = serverCloseFrame.getCloseReason();
        } else {
            code = clientCloseFrame.getCloseCode();
            closeReason = clientCloseFrame.getCloseReason();
        }
        if (this.trying) {
            return;
        }
        if (code == 2001) {
            log.warn("关闭原因：" + closeReason + "，开始重试连接ws请求");
            tryReconnect();
        }
    }
    private void handleHello(DataObject data) {
        clearHeartbeatInterval();
        this.interval = data.getObject("d").getLong("heartbeat_interval");
        this.heartbeatAck = true;
        this.heartbeatInterval = this.heartExecutor.scheduleAtFixedRate(() -> {
            if (this.heartbeatAck) {
                this.heartbeatAck = false;
                send(WebSocketCode.HEARTBEAT, this.sequence);
            } else {
                sendReconnect("心跳请求没响应，开始重新连接");
            }
        }, (long) Math.floor(RandomUtil.randomDouble(0, 1) * this.interval), this.interval, TimeUnit.MILLISECONDS);
    }

    private void handleHeartbeat() {
        send(WebSocketCode.HEARTBEAT, this.sequence);
        this.heartbeatTimeout = ThreadUtil.execAsync(() -> {
            ThreadUtil.sleep(this.interval);
            sendReconnect("心跳请求响应超时，开始重新连接");
        });
    }
    private void doResumeOrIdentify() {
        if (CharSequenceUtil.isBlank(this.sessionId)) {
            send(WebSocketCode.IDENTIFY, this.authData);
        } else {
            send(WebSocketCode.RESUME, DataObject.empty().put("token", mjProperties.getDiscord().getUserToken())
                    .put("session_id", this.sessionId).put("seq", this.sequence));
        }
    }
    private void handleDispatch(DataObject raw) {
        //s为用于恢复会话和心跳的事件序列号
        this.sequence = raw.opt("s").orElse(null);
        //d为Event data，isType 方法用于检查指定字段的值是否为特定的 JSON 数据类型
        if (!raw.isType("d", DataType.OBJECT)) {
            //如果不是object类型就返回
            return;
        }
        DataObject dContent = raw.getObject("d");
        String tContent = raw.getString("t");

        if ("READY".equals(tContent)) {
            log.info("收到READY事件，证明identify事件握手成功，wss连接成功" + dContent.getString("session_id"));
            //ParamUtil.setDefaultSessionId("c1a0c92d039c2a5828d622d7237d0430");
            //this.sessionId = "c1a0c92d039c2a5828d622d7237d0430";
            ParamUtil.setDefaultSessionId(dContent.getString("session_id"));
            this.sessionId = dContent.getString("session_id");
            this.resumeGatewayUrl = dContent.getString("resume_gateway_url");
            return;
        } else if ("RESUMED".equals(tContent)) {
            return;
        }
        if ("MESSAGE_DELETE".equals(tContent)) {
            return;
        }
        if (ignoreAndLogMessage(dContent, raw)) {
            return;
        }
        //vary_region
        if ("INTERACTION_IFRAME_MODAL_CREATE".equals(tContent)) {
            System.out.println("vary_region内容为：" + dContent);
            String nonce = dContent.getString("nonce");
            Task task = taskService.getRunningTaskByNonce(nonce);
            if (ObjUtil.isNull(task)) {
                log.info("【VaryRegion消息监听初始化】nonce为：{}，此时运行的task集合为：{}", nonce, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            String customId = dContent.getString("custom_id");
            String messageId = dContent.getString("id");
            customId = CharSequenceUtil.subAfter(customId, "::", true);
            //log.info("INTERACTION_IFRAME_MODAL_CREATE：{}", raw);
            //log.warn("INTERACTION_IFRAME_MODAL_CREATE指定的customId：{}", customId);
            task.setCustomId(customId);
            task.setStatus(TaskStatus.IN_PROGRESS);
            //task.setMessageId(messageId);
            task.awake();
            String mask = task.getMask();
            //String mask = "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AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
            discordService.submitJobRegion(task, mask, customId);
            return;
        }
        //监听通过JDA监听
        /*if ("MESSAGE_CREATE".equals(tContent)) {
            String content = getMessageContent(dContent);
            if (StrUtil.isBlank(content)) {
                log.info("【VARY_REGION指令消息拒绝】，content内容为空");
                return;
            }
            ContentParseData parseData = ConvertUtils.parseContentAction(content);
            if (Objects.isNull(parseData)) {
                return;
            }
            if (parseData.getAction() != REGION) {
                return;
            }
            DataObject referencedMessage = dContent.optObject("referenced_message").orElse(DataObject.empty());
            String parentMessageId = referencedMessage.getString("id");
            String messageId = dContent.getString("id");
            String imageUrl = discordHelper.getImageUrl(dContent);
            String messageHash = discordHelper.getMessageHash(imageUrl);
            if (parseData.getAction() == REGION) {
                //往右延伸生成四张图
                TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(REGION)).setMessageId(parentMessageId);
                Task task = taskService.findRunningTaskByCondition(taskCondition);
                if (ObjUtil.isNull(task)) {
                    log.info("【VARY_REGION指令消息拒绝】此时运行的task集合为：{}", JSONUtil.toJsonStr(taskService.getRunningTasks()));
                    return;
                }
                log.info("【VARY_REGION指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + parentMessageId + "，内容为：" + content);
                task.setChildMessageId(messageId);
                task.setMessageHash(messageHash);
                task.setImageUrl(discordHelper.replaceCdnUrl(imageUrl));
                task.setContentMj(content);
                task.success();
                task.awake();
            }
        }*/

        //System.out.println("=======锋巢频道-分发start======");
        //System.out.println(raw);
        //System.out.println("=======锋巢频道-分发end======\n");
    }

    private void tryReconnect() {
        clearSocketStates();
        try {
            startWss();
        } catch (Exception e) {
            ThreadUtil.sleep(1000);
            tryNewConnect();
        }
    }
    private void tryNewConnect() {
        clearAllStates();
        try {
            startWss();
        } catch (Exception e) {
            log.error("wss重新连接也失败！！！");
            throw new RuntimeException(e);
        }
    }
    private void send(int op, Object d) {
        if (this.socket != null) {
            String param = DataObject.empty().put("op", op).put("d", d).toString();
            this.socket.sendText(param);
        }
    }
    private void sendReconnect(String reason) {
        sendClose(2001, reason);
    }

    private void sendClose(int code, String reason) {
        if (this.socket != null) {
            this.socket.sendClose(code, reason);
        }
    }

    private void clearAllStates() {
        clearSocketStates();
        clearResumeStates();
    }

    private void clearSocketStates() {
        clearHeartbeatTimeout();
        clearHeartbeatInterval();
        this.socket = null;
        this.decompressor = null;
    }

    private void clearResumeStates() {
        this.sessionId = null;
        this.sequence = null;
        this.resumeGatewayUrl = null;
    }

    private void clearHeartbeatInterval() {
        if (this.heartbeatInterval != null) {
            this.heartbeatInterval.cancel(true);
            this.heartbeatInterval = null;
        }
    }

    private void clearHeartbeatTimeout() {
        if (this.heartbeatTimeout != null) {
            this.heartbeatTimeout.cancel(true);
            this.heartbeatTimeout = null;
        }
    }

    private DataObject createAuthData() {
        //UserAgent agent = UserAgent.parseUserAgentString(mjProperties.getDiscord().getUserAgent());
        //以下是完全模拟浏览器的上送参数
        DataObject connectionProperties = DataObject.empty()
                .put("browser", "Chrome")
                .put("browser_user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .put("browser_version", "122.0.0.0")
                .put("client_build_number", 277196)
                .put("client_event_source", null)
                .put("device", "")
                .put("os", "Windows")
                .put("os_version", "10")
                .put("referrer", "")
                .put("referrer_current", "https://docs.midjourney.com/")
                .put("referring_domain", "")
                .put("referring_domain_current", "docs.midjourney.com")
                .put("release_channel", "stable")
                .put("system_locale", "zh-CN");

        DataObject presence = DataObject.empty()
                .put("activities", DataArray.empty())
                .put("afk", false)
                .put("since", 0)
                .put("status", "online");

        DataObject clientState = DataObject.empty()
                .put("api_code_version", 0)
                .put("guild_versions", DataObject.empty())
                .put("highest_last_message_id", "0")
                .put("private_channels_version", "0")
                .put("read_state_version", 0)
                .put("user_guild_settings_version", -1)
                .put("user_settings_version", -1);

        return DataObject.empty()
                .put("capabilities", 16381)
                .put("client_state", clientState)
                .put("token", mjProperties.getDiscord().getUserToken())
                .put("presence", presence)
                .put("properties", connectionProperties)
                //.put("intents", 34048)
                .put("compress", false);
    }

    private boolean ignoreAndLogMessage(DataObject data, DataObject raw) {
        if (!data.hasKey("channel_id")) {
            return true;
        }
        String channelId = data.getString("channel_id");
        return !CharSequenceUtil.equals(channelId, mjProperties.getDiscord().getChannelId());
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        log.info("收到消息后");
    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws Exception {
        log.info("消息无法创建");
    }
    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
        log.info("【wss连接】成功");
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
        log.info("【wss连接】异常：" + cause);
    }


}
