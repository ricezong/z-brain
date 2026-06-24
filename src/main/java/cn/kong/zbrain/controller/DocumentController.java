package cn.kong.zbrain.controller;

import cn.kong.zbrain.common.PageResult;
import cn.kong.zbrain.common.Result;
import cn.kong.zbrain.dto.request.ReviewSubmitRequest;
import cn.kong.zbrain.dto.response.DocumentProgressResponse;
import cn.kong.zbrain.entity.Document;
import cn.kong.zbrain.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理 Controller
 *
 * @author zbrain-team
 */
@Tag(name = "文档管理")
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "上传文档（异步解析）")
    @PostMapping("/upload")
    public Result<Long> upload(
            @RequestParam("kbId") Long kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) String userId) {
        Long docId = documentService.upload(kbId, file, userId);
        documentService.parseAsync(docId);
        return Result.success(docId);
    }

    @Operation(summary = "获取文档处理进度")
    @GetMapping("/{id}/progress")
    public Result<DocumentProgressResponse> getProgress(@PathVariable Long id) {
        return Result.success(documentService.getProgress(id));
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/{id}")
    public Result<Document> getById(@PathVariable Long id) {
        return Result.success(documentService.getById(id));
    }

    @Operation(summary = "分页查询文档")
    @GetMapping
    public Result<PageResult<Document>> list(
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(documentService.list(kbId, fileName, status, pageNum, pageSize));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success();
    }

    @Operation(summary = "触发向量化")
    @PostMapping("/{id}/embed")
    public Result<Void> triggerEmbedding(@PathVariable Long id) {
        documentService.triggerEmbedding(id);
        return Result.success();
    }

    @Operation(summary = "提交审核（批量 Diff）")
    @PostMapping("/{id}/review")
    public Result<Void> submitReview(@PathVariable Long id, @RequestBody ReviewSubmitRequest request) {
        documentService.submitReview(id, request);
        return Result.success();
    }
}
