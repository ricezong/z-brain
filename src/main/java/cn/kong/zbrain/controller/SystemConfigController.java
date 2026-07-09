package cn.kong.zbrain.controller;

import cn.kong.zbrain.common.Result;
import cn.kong.zbrain.entity.SysLlmModel;
import cn.kong.zbrain.entity.SysPrompt;
import cn.kong.zbrain.llm.LLMModelRegistry;
import cn.kong.zbrain.service.EmbeddingService;
import cn.kong.zbrain.service.RerankService;
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
 * <p>统一管理系统提示词和 LLM 模型配置。
 * 模型配置变更后自动热更新对应服务缓存：chat 走细粒度 register/reload/evict，
 * embedding / rerank 按类型清缓存（其缓存仅为单条默认配置）。</p>
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
    private final LLMModelRegistry llmModelRegistry;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;

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
        // 热更新：注册新建的 chat 模型；若设为默认则刷新默认模型指针
        refreshAfterCreate(model, id);
        return Result.success(id);
    }

    @Operation(summary = "更新模型配置")
    @PutMapping("/llm-models/{id}")
    public Result<Void> updateLlmModel(@PathVariable Long id, @RequestBody SysLlmModel model) {
        model.setId(id);
        sysLlmModelService.update(model);
        // 热更新：重载该 chat 模型实例；默认/激活状态可能变化，刷新默认模型指针
        refreshAfterUpdate(model);
        return Result.success();
    }

    @Operation(summary = "删除模型配置")
    @DeleteMapping("/llm-models/{id}")
    public Result<Void> deleteLlmModel(@PathVariable Long id) {
        SysLlmModel existing = sysLlmModelService.getById(id);
        sysLlmModelService.delete(id);
        // 热更新：移除该 chat 模型缓存；若删除的是默认模型则刷新默认模型指针
        refreshAfterDelete(existing);
        return Result.success();
    }

    @Operation(summary = "设置默认模型")
    @PutMapping("/llm-models/{id}/default")
    public Result<Void> setDefaultLlmModel(@PathVariable Long id) {
        SysLlmModel model = sysLlmModelService.getById(id);
        sysLlmModelService.setDefault(id);
        // 热更新：默认模型变更，刷新默认模型指针
        refreshAfterDefaultChange(model.getModelType());
        return Result.success();
    }

    /**
     * 新建模型后的热更新
     *
     * <p>chat 类型走细粒度注册；embedding/rerank 仍按类型清缓存（其缓存仅为单条默认配置）。</p>
     */
    private void refreshAfterCreate(SysLlmModel model, Long id) {
        String type = model.getModelType();
        if ("chat".equals(type)) {
            llmModelRegistry.reload(id); // 从数据库读取并注册新模型
            if (Boolean.TRUE.equals(model.getIsDefault())) {
                llmModelRegistry.reloadDefault();
            }
        } else {
            clearCacheByType(type);
        }
    }

    /**
     * 更新模型后的热更新
     *
     * <p>chat 类型重载该模型实例并刷新默认指针；embedding/rerank 清缓存。</p>
     */
    private void refreshAfterUpdate(SysLlmModel model) {
        String type = model.getModelType();
        if ("chat".equals(type)) {
            llmModelRegistry.reload(model.getId());
            llmModelRegistry.reloadDefault();
        } else {
            clearCacheByType(type);
        }
    }

    /**
     * 删除模型后的热更新
     *
     * <p>chat 类型移除该模型缓存；若删除的是默认模型则刷新默认指针；embedding/rerank 清缓存。</p>
     */
    private void refreshAfterDelete(SysLlmModel existing) {
        String type = existing.getModelType();
        if ("chat".equals(type)) {
            llmModelRegistry.evict(existing.getId());
            if (Boolean.TRUE.equals(existing.getIsDefault())) {
                llmModelRegistry.reloadDefault();
            }
        } else {
            clearCacheByType(type);
        }
    }

    /**
     * 默认模型变更后的热更新
     */
    private void refreshAfterDefaultChange(String modelType) {
        if ("chat".equals(modelType)) {
            llmModelRegistry.reloadDefault();
        } else {
            clearCacheByType(modelType);
        }
    }

    /**
     * 根据模型类型清除对应服务的缓存（仅用于 embedding / rerank）
     *
     * @param modelType embedding / rerank
     */
    private void clearCacheByType(String modelType) {
        if (modelType == null) {
            return;
        }
        switch (modelType) {
            case "embedding" -> embeddingService.clearCache();
            case "rerank" -> rerankService.clearCache();
            default -> { /* 未知类型，忽略 */ }
        }
    }
}
