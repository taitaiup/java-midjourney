package com.mj.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mj.entity.*;
import com.mj.enums.TaskAction;
import com.mj.enums.TaskStatus;
import com.mj.exception.SensitivePromptException;
import com.mj.service.TaskService;
import com.mj.service.TaskStoreService;
import com.mj.service.UserCenterService;
import com.mj.utils.ConvertUtils;
import com.mj.utils.MimeTypeUtils;
import com.mj.utils.SensitivePromptUtils;
import eu.maxschuster.dataurl.DataUrl;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.*;

import static com.mj.enums.TaskAction.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 命令触发
 * 接收前端传来的命令，如：/imagine
 */
@RestController
@RequestMapping("/interactions")
@RequiredArgsConstructor
@Slf4j
public class ExecuteController {

    private final TaskStoreService taskStoreService;
    private final TaskService taskService;
    @Resource
    private UserCenterService userCenterService;


    /**
     * imagine命令
     * 文生图、垫图
     */
    @PostMapping("/imagine")
    public ResponseEntity<ExecuteResult> imagine(@RequestBody ExecuteImagineDTO imagineDTO) {
        String prompt = imagineDTO.getPrompt();
        if (CharSequenceUtil.isBlank(prompt)) {
            throw new ValidateException("参数错误！");
        }
        prompt = prompt.trim();
        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        limitCheck(fromUser.getId());
        Task task = new Task();
        task.initialize();
        task.setAction(TaskAction.IMAGINE);
        //翻译
        String promptEn = taskService.translateToEnglish(prompt);
        log.info("提示词翻译结果：" + promptEn);
        //处理参数+翻译
        String options = processingData(imagineDTO.getOptions());
        task.setOptions(JSONUtil.toJsonStr(imagineDTO.getOptions()));
        //参数原样返给前端
        task.setOptionsObj(JSONUtil.parseObj(imagineDTO.getOptions()));
        task.setPrompt(prompt);
        task.setPromptEn(promptEn + options);

        try {
            SensitivePromptUtils.checkSensitivePrompt(promptEn);
        } catch (SensitivePromptException e) {
            throw new ValidateException("可能包含敏感词");
        }
        List<String> base64Array = Optional.ofNullable(imagineDTO.getBase64Array()).orElse(new ArrayList<>());
        if (CharSequenceUtil.isNotBlank(imagineDTO.getBase64())) {
            base64Array.add(imagineDTO.getBase64());
        }
        List<DataUrl> dataUrls;
        try {
            dataUrls = ConvertUtils.convertBase64Array(base64Array);
        } catch (MalformedURLException e) {
            throw new ValidateException("base64格式错误");
        }
        task.setDescription("/imagine " + prompt);

        task.setUserId(fromUser.getId());
        ExecuteResult executeResult = taskService.submitImagine(task, dataUrls);
        return ResponseEntity.status(executeResult.getCode()).body(executeResult);
    }

    /**
     * 图生文
     */
    @PostMapping("/describe")
    public ResponseEntity<ExecuteResult> describe(@RequestBody ExecuteDescribeDTO describeDTO) {
        if (CharSequenceUtil.isBlank(describeDTO.getBase64())) {
            throw new ValidateException("base64不能为空");
        }
        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        limitCheck(fromUser.getId());
        //repeatClickCheck(fromUser, DESCRIBE);
        List<DataUrl> dataUrls;
        try {
            dataUrls = ConvertUtils.convertBase64Array(CollUtil.newArrayList(describeDTO.getBase64()));
        } catch (MalformedURLException e) {
            throw new ValidateException("base64格式错误");
        }
        Task task = new Task();
        task.initialize();
        task.setAction(TaskAction.DESCRIBE);
        task.setUserId(fromUser.getId());
        String taskFileName = task.getNonce() + "." + MimeTypeUtils.guessFileSuffix(dataUrls.get(0).getMimeType());
        task.setDescription("/describe " + taskFileName);
        ExecuteResult executeResult = taskService.submitDescribe(task, taskFileName, dataUrls.get(0));
        return ResponseEntity.status(executeResult.getCode()).body(executeResult);
    }


    /**
     * 通用指令接口，uv、之后的高级指令
     */
    @PostMapping("/commonInstructions")
    public ResponseEntity<Object> commonInstructions(@RequestBody BaseExecuteDTO baseExecuteDTO) {
        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        limitCheck(fromUser.getId());
        TaskAction action = baseExecuteDTO.getAction();
        switch (action) {
            //uv
            case UPSCALE:
            case VARIATION:
            case REROLL:
                return change(baseExecuteDTO);
            //高级选项
            case UPSCALE_SUBTLE:
            case UPSCALE_CREATIVE:
            case OUTPAINT15X:
            case OUTPAINT2X:
                return pixelChange(baseExecuteDTO);
            case VARY_SUBTLE:
            case VARY_STRONG:
                return lowAndHighChange(baseExecuteDTO);
            case DIRECTION_UP:
            case DIRECTION_DOWN:
            case DIRECTION_LEFT:
            case DIRECTION_RIGHT:
                return directionChange(baseExecuteDTO);
        }
        return ResponseEntity.status(NOT_FOUND).body("未找到对应的指令");
    }
    /**
     * UV指令
     */
    @PostMapping("/change")
    public ResponseEntity<Object> change(@RequestBody BaseExecuteDTO changeDTO) {
        if (!Set.of(TaskAction.UPSCALE, TaskAction.VARIATION, TaskAction.REROLL).contains(changeDTO.getAction())) {
            throw new ValidateException("上送的action参数错误");
        }

        if (ObjUtil.isNull(changeDTO.getTaskId())) {
            throw new ValidateException("taskId不能为空");
        }
        Task taskExist = taskStoreService.getById(changeDTO.getTaskId());
        if (ObjUtil.isNull(taskExist)) {
            //没有对应的task
            throw new ValidateException("非法请求操作");
        }
        if (!TaskStatus.SUCCESS.equals(taskExist.getStatus())) {
            throw new ValidateException("关联任务状态错误");
        }

        String description = "/change " + taskExist.getChildMessageId();
        if (TaskAction.REROLL.equals(changeDTO.getAction())) {
            // 格式：/change 当前消息的messageId REROLL 父taskId
            description += " REROLL " + changeDTO.getTaskId();
        } else {
            // 格式：/change 当前消息的messageId U/V索引 比如：/change 1210535784709365811 U1
            description += " " + changeDTO.getAction().name().toUpperCase().charAt(0) + changeDTO.getIndex();
        }

        Task task = new Task();
        task.setBaseTaskId(taskExist.getId());//基于哪个图做的操作
        task.initialize();
        task.setAction(changeDTO.getAction());
        task.setPrompt(taskExist.getPrompt());
        task.setPromptEn(taskExist.getPromptEn());
        task.setMessageId(taskExist.getChildMessageId());
        task.setDescription(description);
        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        task.setUserId(fromUser.getId());
        // 和U指令不同，V1-V4 监听不到用户V的是哪张图片，所以当监听到两次V操作，无法判断是哪次task的消息，映射不上，故只能在触发指令时限制v操作只能第一次完成后才能触发第二次
        repeatClickCheck(fromUser, changeDTO.getAction());
        if (TaskAction.UPSCALE.equals(changeDTO.getAction())) {
            ExecuteResult submitResult = taskService.submitUpscale(task, changeDTO.getIndex(), taskExist.getChildMessageId(), taskExist.getMessageHash());
            return ResponseEntity.status(submitResult.getCode()).body(submitResult);
        } else if (TaskAction.VARIATION.equals(changeDTO.getAction())) {
            ExecuteResult submitResult = taskService.submitVariation(task, changeDTO.getIndex(), taskExist.getChildMessageId(), taskExist.getMessageHash());
            return ResponseEntity.status(submitResult.getCode()).body(submitResult);
        } else {
            ExecuteResult submitResult = taskService.submitReroll(task, taskExist.getChildMessageId(), taskExist.getMessageHash());
            return ResponseEntity.status(submitResult.getCode()).body(submitResult);
        }
    }

    private void repeatClickCheck(FeiShuSysUser fromUser, TaskAction action) {
        Task existTask = taskStoreService.getOne(Wrappers.lambdaQuery(Task.class)
                .eq(Task::getAction, action)
                .eq(Task::getUserId, fromUser.getId())
                .in(Task::getStatus, Set.of(TaskStatus.SUBMITTED, TaskStatus.IN_PROGRESS)));
        if (existTask != null) {
            throw new ValidateException("有未完成的" + action + "任务");
        }
    }

    /**
     * 针对单个图片放大或缩小再生成四张图
     * upscale、zoom out
     */
    @PostMapping("/pixelChange")
    public ResponseEntity<Object> pixelChange(@RequestBody BaseExecuteDTO changeDTO) {
        if (ObjUtil.isEmpty(changeDTO.getTaskId())) {
            return ResponseEntity.badRequest().body("taskId不能为空");
        }
        if (!Set.of(UPSCALE_SUBTLE, UPSCALE_CREATIVE, OUTPAINT15X, OUTPAINT2X).contains(changeDTO.getAction())) {
            return ResponseEntity.badRequest().body("action参数错误");
        }
        // /up 206 VARY_SUBTLE
        String description = "/uz " + changeDTO.getTaskId() + " " + changeDTO.getAction().name();
        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        //如果用户相同操作已存在，不能重复点击
        repeatClickCheck(fromUser, changeDTO.getAction());
        Task targetTask = taskStoreService.getById(changeDTO.getTaskId());
        if (targetTask == null) {
            return ResponseEntity.badRequest().body("关联任务不存在或已失效");
        }
        if (!TaskStatus.SUCCESS.equals(targetTask.getStatus())) {
            return ResponseEntity.badRequest().body("关联任务状态错误");
        }
        Task task = new Task();
        task.setBaseTaskId(targetTask.getId());//基于哪个图做的操作
        task.initialize();
        task.setAction(changeDTO.getAction());
        task.setPrompt(targetTask.getPrompt());
        task.setPromptEn(targetTask.getPromptEn());
        task.setDescription(description);
        String messageId = targetTask.getChildMessageId();
        task.setMessageId(messageId);
        task.setUserId(fromUser.getId());
        String messageHash = targetTask.getMessageHash();
        ExecuteResult submitResult = null;
        if (UPSCALE_SUBTLE.equals(changeDTO.getAction())) {
            submitResult = taskService.submitUpscaleX(task, messageId, messageHash, 2);
        } else if (UPSCALE_CREATIVE.equals(changeDTO.getAction())) {
            submitResult = taskService.submitUpscaleX(task, messageId, messageHash, 4);
        } else if (OUTPAINT15X.equals(changeDTO.getAction())) {
            submitResult = taskService.submitOutpaintX(task, messageId, messageHash, 15);
        } else if (OUTPAINT2X.equals(changeDTO.getAction())) {
            submitResult = taskService.submitOutpaintX(task, messageId, messageHash, 2);
        }
        return ResponseEntity.status(submitResult.getCode()).body(submitResult);
    }
    /**
     * 轻微或强烈变化再生成四张图
     * vary
     */
    @PostMapping("/lowAndHighChange")
    public ResponseEntity<Object> lowAndHighChange(@RequestBody BaseExecuteDTO changeDTO) {
        if (ObjUtil.isEmpty(changeDTO.getTaskId())) {
            return ResponseEntity.badRequest().body("taskId不能为空");
        }
        if (!Set.of(TaskAction.VARY_SUBTLE, TaskAction.VARY_STRONG).contains(changeDTO.getAction())) {
            return ResponseEntity.badRequest().body("action参数错误");
        }
        String description = "/vary " + changeDTO.getTaskId() + " " + changeDTO.getAction().name();

        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        //重复点击
        repeatClickCheck(fromUser, changeDTO.getAction());
        Task targetTask = taskStoreService.getById(changeDTO.getTaskId());
        if (targetTask == null) {
            return ResponseEntity.badRequest().body("关联任务不存在或已失效");
        }
        if (!TaskStatus.SUCCESS.equals(targetTask.getStatus())) {
            return ResponseEntity.badRequest().body("关联任务状态错误");
        }
        Task task = new Task();
        task.setBaseTaskId(targetTask.getId());//基于哪个图做的操作
        task.initialize();
        task.setAction(changeDTO.getAction());
        task.setPrompt(targetTask.getPrompt());
        task.setPromptEn(targetTask.getPromptEn());
        task.setDescription(description);
        String messageId = targetTask.getChildMessageId();
        task.setMessageId(messageId);
        task.setUserId(fromUser.getId());
        String messageHash = targetTask.getMessageHash();
        ExecuteResult submitResult = null;
        if (TaskAction.VARY_SUBTLE.equals(changeDTO.getAction())) {
            submitResult = taskService.submitVariationLow(task, messageId, messageHash);
        } else {
            submitResult = taskService.submitVariationHigh(task, messageId, messageHash);
        }
        return ResponseEntity.status(submitResult.getCode()).body(submitResult);
    }
    /**
     * 上下左右延伸再生成四张图
     * @param changeDTO param
     * @return {@link ResponseEntity}<{@link Object}>
     */
    @PostMapping("/directionChange")
    public ResponseEntity<Object> directionChange(@RequestBody BaseExecuteDTO changeDTO) {
        if (ObjUtil.isEmpty(changeDTO.getTaskId())) {
            return ResponseEntity.badRequest().body("taskId不能为空");
        }
        if (!Set.of(TaskAction.DIRECTION_UP, TaskAction.DIRECTION_DOWN, TaskAction.DIRECTION_LEFT, TaskAction.DIRECTION_RIGHT).contains(changeDTO.getAction())) {
            return ResponseEntity.badRequest().body("action参数错误");
        }
        String description = "/direct " + changeDTO.getTaskId() + " " + changeDTO.getAction().name();

        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        //重复点击校验
        repeatClickCheck(fromUser, changeDTO.getAction());
        Task targetTask = taskStoreService.getById(changeDTO.getTaskId());
        if (targetTask == null) {
            return ResponseEntity.badRequest().body("关联任务不存在或已失效");
        }
        if (!TaskStatus.SUCCESS.equals(targetTask.getStatus())) {
            return ResponseEntity.badRequest().body("关联任务状态错误");
        }
        Task task = new Task();
        task.setBaseTaskId(targetTask.getId());//基于哪个图做的操作
        task.initialize();
        task.setAction(changeDTO.getAction());
        task.setPrompt(targetTask.getPrompt());
        task.setPromptEn(targetTask.getPromptEn());
        task.setDescription(description);
        String messageId = targetTask.getChildMessageId();
        task.setMessageId(messageId);
        task.setUserId(fromUser.getId());
        String messageHash = targetTask.getMessageHash();
        ExecuteResult submitResult = null;
        if (TaskAction.DIRECTION_UP.equals(changeDTO.getAction())) {
            submitResult = taskService.submitDirection("up", task, messageId, messageHash);
        } else if (TaskAction.DIRECTION_DOWN.equals(changeDTO.getAction())) {
            submitResult = taskService.submitDirection("down", task, messageId, messageHash);
        } else if (TaskAction.DIRECTION_LEFT.equals(changeDTO.getAction())) {
            submitResult = taskService.submitDirection("left", task, messageId, messageHash);
        } else {
            submitResult = taskService.submitDirection("right", task, messageId, messageHash);
        }
        return ResponseEntity.status(submitResult.getCode()).body(submitResult);
    }

    /**
     *
     * Vary region
     */
    @PostMapping("/region")
    public ResponseEntity<Object> region(@RequestBody ExecuteRegionDTO regionDTO) {
        if (ObjUtil.isEmpty(regionDTO.getTaskId())) {
            return ResponseEntity.badRequest().body("taskId不能为空");
        }

        Task targetTask = taskStoreService.getById(regionDTO.getTaskId());
        if (targetTask == null) {
            return ResponseEntity.badRequest().body("关联任务不存在或已失效");
        }
        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        limitCheck(fromUser.getId());

        Task task = new Task();
        task.setBaseTaskId(targetTask.getId());//基于哪个图做的操作
        task.initialize();
        task.setAction(REGION);
        task.setPrompt(regionDTO.getPrompt());
        task.setMask(regionDTO.getMask());
        //翻译
        String promptEn = taskService.translateToEnglish(regionDTO.getPrompt());
        try {
            SensitivePromptUtils.checkSensitivePrompt(promptEn);
        } catch (SensitivePromptException e) {
            throw new ValidateException("可能包含敏感词");
        }
        //处理参数+翻译
        String options = processingData(regionDTO.getOptions());
        task.setOptions(JSONUtil.toJsonStr(regionDTO.getOptions()));
        //参数原样返给前端
        task.setOptionsObj(JSONUtil.parseObj(regionDTO.getOptions()));
        task.setPromptEn(promptEn + options);

        String description = "/region " + regionDTO.getTaskId();
        task.setDescription(description);

        String messageId = targetTask.getChildMessageId();
        task.setMessageId(messageId);

        task.setUserId(fromUser.getId());
        String messageHash = targetTask.getMessageHash();
        ExecuteResult executeResult = taskService.submitRegion(task, messageId, messageHash, regionDTO.getMask(), regionDTO.getCustomId());
        return ResponseEntity.status(executeResult.getCode()).body(executeResult);
    }




    /**
     * 拼接创作参数
     */
    private String processingData(HashMap<String, String> options) {
        if (MapUtil.isEmpty(options)) {
            return "";
        }
        String paramStr = "";
        String noParam = options.get("--no");
        if (StrUtil.isNotBlank(noParam)) {
            String noParamEn = taskService.translateToEnglish(noParam);
            paramStr = " --no " + noParamEn;
        }
        for (String key : options.keySet()) {
            if (key.equals("--no")) {
                continue;
            }
            paramStr += " " + key + " " + options.get(key);
        }
        return paramStr;
    }

    private long limitCheck(Long userId) {
        LambdaQueryWrapper<Task> wrapper = Wrappers.lambdaQuery(Task.class).eq(Task::getUserId, userId)
                .in(Task::getStatus, Set.of(TaskStatus.SUBMITTED, TaskStatus.IN_PROGRESS, TaskStatus.INITIALIZE));
        long count = taskStoreService.count(wrapper);
        if (3 < count) {
            throw new ValidateException("无法提交过多任务");
        }
        return count;
    }

/*@PostMapping("/blend")
    public ResponseEntity<ExecuteResult> blend(@RequestBody ExecuteBlendDTO blendDTO) {
        List<String> base64Array = blendDTO.getBase64Array();
        if (base64Array == null || base64Array.size() < 2 || base64Array.size() > 5) {
            throw new ValidateException("base64List参数错误");
        }
        if (blendDTO.getDimensions() == null) {
            throw new ValidateException("dimensions参数错误");
        }
        List<DataUrl> dataUrlList = new ArrayList<>();
        try {
            dataUrlList = ConvertUtils.convertBase64Array(base64Array);
        } catch (MalformedURLException e) {
            throw new ValidateException("base64格式错误");
        }
        Task task = new Task();
        task.initialize();
        task.setAction(TaskAction.BLEND);
        AiSysUser fromUser = userCenterService.transferUCenterId(1L);
        limitCheck(fromUser.getId());
        task.setUserId(fromUser.getId());
        task.setDescription("/blend " + dataUrlList.size());
        ExecuteResult executeResult = taskService.submitBlend(task, dataUrlList, blendDTO.getDimensions());
        return ResponseEntity.status(executeResult.getCode()).body(executeResult);
    }*/
//@GetMapping
    /*String test(String name) throws IOException {
        File file = ResourceUtils.getFile("classpath:"+name+".jpg");
        byte[] redDotData = FileUtil.getBytesByFile(file);
        IDataUrlSerializer serializer = new DataUrlSerializer();
        DataUrl unserialized = new DataUrlBuilder()
                .setMimeType("image/png")
                .setEncoding(DataUrlEncoding.BASE64)
                .setData(redDotData)
                .build();
        String serialized = serializer.serialize(unserialized);
        System.out.println("======");
        System.out.println(serialized);
        System.out.println("======");
        return serialized;
    }*/


    public class FileUtil {
        //将文件转换成Byte数组
        public static byte[] getBytesByFile(File file) {
            try {
                FileInputStream fis = new FileInputStream(file);
                ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
                byte[] b = new byte[1024];
                int n;
                while ((n = fis.read(b)) != -1) {
                    bos.write(b, 0, n);
                }
                fis.close();
                byte[] data = bos.toByteArray();
                bos.close();
                return data;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
