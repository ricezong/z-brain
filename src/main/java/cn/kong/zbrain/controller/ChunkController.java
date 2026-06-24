package cn.kong.zbrain.controller;

import cn.kong.zbrain.common.Result;
import cn.kong.zbrain.dto.request.ChunkMergeRequest;
import cn.kong.zbrain.dto.request.ChunkSplitRequest;
import cn.kong.zbrain.dto.request.ChunkUpdateRequest;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.service.ChunkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分块管理 Controller（人工审核工作台）
 *
 * @author zbrain-team
 */
@Tag(name = "分块管理（人工审核工作台）")
@RestController
@RequestMapping("/chunks")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkService chunkService;

    @Operation(summary = "查询文档下所有分块")
    @GetMapping("/document/{docId}")
    public Result<List<Chunk>> listByDocId(@PathVariable Long docId) {
        return Result.success(chunkService.listByDocId(docId));
    }

    @Operation(summary = "获取分块详情")
    @GetMapping("/{id}")
    public Result<Chunk> getById(@PathVariable Long id) {
        return Result.success(chunkService.getById(id));
    }

    @Operation(summary = "更新分块内容（触发器自动更新 tsv）")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ChunkUpdateRequest request) {
        chunkService.update(request);
        return Result.success();
    }

    @Operation(summary = "删除分块")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chunkService.delete(id);
        return Result.success();
    }

    @Operation(summary = "合并多个相邻子块")
    @PostMapping("/merge")
    public Result<Chunk> merge(@Valid @RequestBody ChunkMergeRequest request) {
        return Result.success(chunkService.merge(request));
    }

    @Operation(summary = "拆分单个分块")
    @PostMapping("/split")
    public Result<List<Chunk>> split(@Valid @RequestBody ChunkSplitRequest request) {
        return Result.success(chunkService.split(request));
    }

    @Operation(summary = "调整父子关系")
    @PutMapping("/{chunkId}/parent/{parentId}")
    public Result<Void> adjustParent(@PathVariable Long chunkId, @PathVariable Long parentId) {
        chunkService.adjustParent(chunkId, parentId);
        return Result.success();
    }
}
