package cn.kong.zbrain.controller;

import cn.kong.zbrain.common.Result;
import cn.kong.zbrain.entity.SysLlmModel;
import cn.kong.zbrain.entity.SysPrompt;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.service.SysLlmModelService;
import cn.kong.zbrain.service.SysPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置 Controller
 *
 * <p>统一管理系统提示词和 LLM 模型配置。</p>
 *
 * @author zbrain-team
 */
@Tag(name = "系统配置")
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SysPromptService sysPromptService;
    private final SysLlmModelService sysLlmModelService;
    private final LLMService llmService;

    // ==================== 系统提示词 ====================

    @Operation(summary = "查询所有系统提示词")
    @GetMapping("/prompts")
    public Result<List<SysPrompt>> listPrompts() {
        return Result.success(sysPromptService.list());
    }

    @Operation(summary = "获取提示词详情")
    @GetMapping("/prompts/{id}")
    public Result<SysPrompt> getPrompt(@PathVariable Long id) {
        return Result.success(sysPromptService.getById(id));
    }

    @Operation(summary = "更新系统提示词")
    @PutMapping("/prompts/{id}")
    public Result<Void> updatePrompt(@PathVariable Long id, @RequestBody SysPrompt body) {
        sysPromptService.update(
                id,
                body.getName(),
                body.getDescription(),
                body.getContent(),
                body.getIsActive()
        );
        return Result.success();
    }

    // ==================== LLM 模型配置 ====================

    @Operation(summary = "查询所有 LLM 模型配置")
    @GetMapping("/llm-models")
    public Result<List<SysLlmModel>> listLlmModels() {
        return Result.success(sysLlmModelService.listAll());
    }

    @Operation(summary = "按类型查询模型列表")
    @GetMapping("/llm-models/type/{modelType}")
    public Result<List<SysLlmModel>> listLlmModelsByType(@PathVariable String modelType) {
        return Result.success(sysLlmModelService.listByType(modelType));
    }

    @Operation(summary = "获取模型配置详情")
    @GetMapping("/llm-models/{id}")
    public Result<SysLlmModel> getLlmModel(@PathVariable Long id) {
        return Result.success(sysLlmModelService.getById(id));
    }

    @Operation(summary = "创建模型配置")
    @PostMapping("/llm-models")
    public Result<Long> createLlmModel(@RequestBody SysLlmModel model) {
        Long id = sysLlmModelService.create(model);
        // 如果新建模型设为默认，清除缓存使新配置生效
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            llmService.clearCache();
        }
        return Result.success(id);
    }

    @Operation(summary = "更新模型配置")
    @PutMapping("/llm-models/{id}")
    public Result<Void> updateLlmModel(@PathVariable Long id, @RequestBody SysLlmModel model) {
        model.setId(id);
        sysLlmModelService.update(model);
        // 清除 LLM 服务缓存的 ChatModel
        llmService.clearCache();
        return Result.success();
    }

    @Operation(summary = "删除模型配置")
    @DeleteMapping("/llm-models/{id}")
    public Result<Void> deleteLlmModel(@PathVariable Long id) {
        sysLlmModelService.delete(id);
        llmService.clearCache();
        return Result.success();
    }

    @Operation(summary = "设置默认模型")
    @PutMapping("/llm-models/{id}/default")
    public Result<Void> setDefaultLlmModel(@PathVariable Long id) {
        sysLlmModelService.setDefault(id);
        llmService.clearCache();
        return Result.success();
    }
}
