package cn.kong.zbrain.controller;

import cn.kong.zbrain.common.Result;
import cn.kong.zbrain.dto.request.PromptTemplateRequest;
import cn.kong.zbrain.entity.PromptTemplate;
import cn.kong.zbrain.service.PromptTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词模板 Controller
 *
 * @author zbrain-team
 */
@Tag(name = "提示词模板")
@RestController
@RequestMapping("/prompt-templates")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @Operation(summary = "创建提示词模板")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PromptTemplateRequest request) {
        return Result.success(promptTemplateService.create(request));
    }

    @Operation(summary = "更新提示词模板")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PromptTemplateRequest request) {
        promptTemplateService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除提示词模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptTemplateService.delete(id);
        return Result.success();
    }

    @Operation(summary = "获取提示词模板详情")
    @GetMapping("/{id}")
    public Result<PromptTemplate> getById(@PathVariable Long id) {
        return Result.success(promptTemplateService.getById(id));
    }

    @Operation(summary = "根据知识库 ID 获取提示词模板")
    @GetMapping("/kb/{kbId}")
    public Result<PromptTemplate> getByKbId(@PathVariable Long kbId) {
        return Result.success(promptTemplateService.getByKbId(kbId));
    }

    @Operation(summary = "获取默认提示词模板")
    @GetMapping("/default")
    public Result<PromptTemplate> getDefault() {
        return Result.success(promptTemplateService.getDefault());
    }

    @Operation(summary = "查询提示词模板列表")
    @GetMapping
    public Result<List<PromptTemplate>> list(@RequestParam(required = false) Long kbId) {
        return Result.success(promptTemplateService.list(kbId));
    }
}
