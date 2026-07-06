package cn.kong.zbrain.controller;

import cn.kong.zbrain.common.PageResult;
import cn.kong.zbrain.common.Result;
import cn.kong.zbrain.dto.request.KnowledgeBaseCreateRequest;
import cn.kong.zbrain.dto.request.KnowledgeBaseUpdateRequest;
import cn.kong.zbrain.entity.KnowledgeBase;
import cn.kong.zbrain.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理 Controller
 *
 * @author zbrain-team
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "创建知识库")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return Result.success(knowledgeBaseService.create(request));
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        knowledgeBaseService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除知识库（软删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }

    @Operation(summary = "获取知识库详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBase> getById(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getById(id));
    }

    @Operation(summary = "分页查询知识库")
    @GetMapping
    public Result<PageResult<KnowledgeBase>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(knowledgeBaseService.list(name, category, status, pageNum, pageSize));
    }

    @Operation(summary = "获取知识库分类列表")
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(knowledgeBaseService.listCategories());
    }
}
