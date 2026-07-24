package cn.kong.eon.controller;

import cn.kong.eon.common.result.Result;
import cn.kong.eon.config.ConfigService;
import cn.kong.eon.persistence.entity.SysLlmModel;
import cn.kong.eon.persistence.entity.SysPrompt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置 Controller（模型配置 + 提示词配置）
 *
 * <p>所有读写统一经 {@link ConfigService}，不直接操作 Mapper（设计文档 §4.4 统一配置入口）。</p>
 *
 * @author eon-team
 */
@Tag(name = "系统配置")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final ConfigService configService;

    // ==================== 模型配置 ====================

    @Operation(summary = "模型列表")
    @GetMapping("/models")
    public Result<List<SysLlmModel>> listModels(@RequestParam(value = "modelType", required = false) String modelType) {
        return Result.success(configService.listAllModels(modelType));
    }

    @Operation(summary = "新增模型")
    @PostMapping("/models")
    public Result<Void> createModel(@RequestBody SysLlmModel model) {
        configService.createModelConfig(model);
        return Result.success();
    }

    @Operation(summary = "更新模型")
    @PutMapping("/models/{id}")
    public Result<Void> updateModel(@PathVariable Long id, @RequestBody SysLlmModel model) {
        configService.updateModelConfig(id, model);
        return Result.success();
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        configService.deleteModelConfig(id);
        return Result.success();
    }

    // ==================== 提示词配置 ====================

    @Operation(summary = "提示词列表")
    @GetMapping("/prompts")
    public Result<List<SysPrompt>> listPrompts() {
        return Result.success(configService.listAllPrompts());
    }

    @Operation(summary = "更新提示词")
    @PutMapping("/prompts/{id}")
    public Result<Void> updatePrompt(@PathVariable Long id, @RequestBody SysPrompt prompt) {
        prompt.setId(id);
        configService.updatePrompt(id, prompt.getPromptKey(), prompt.getContent());
        return Result.success();
    }
}
