package cn.kong.eon.controller;

import cn.kong.eon.common.result.Result;
import cn.kong.eon.knowledge.KnowledgeBaseService;
import cn.kong.eon.persistence.entity.KnowledgeBase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理 Controller
 *
 * @author eon-team
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "知识库列表")
    @GetMapping("/list")
    public Result<List<KnowledgeBase>> list() {
        return Result.success(knowledgeBaseService.list());
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBase> getById(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getById(id));
    }

    @Operation(summary = "创建知识库")
    @PostMapping
    public Result<Void> create(@RequestBody KnowledgeBase kb) {
        knowledgeBaseService.create(kb);
        return Result.success();
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody KnowledgeBase kb) {
        kb.setId(id);
        knowledgeBaseService.update(kb);
        return Result.success();
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }
}
