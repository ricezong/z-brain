package cn.kong.eon.controller;

import cn.kong.eon.common.result.Result;
import cn.kong.eon.knowledge.DocumentService;
import cn.kong.eon.knowledge.EmbeddingTaskService;
import cn.kong.eon.persistence.entity.Document;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理 Controller
 *
 * @author eon-team
 */
@Tag(name = "文档管理")
@RestController
@RequestMapping("/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final EmbeddingTaskService embeddingTaskService;

    @Operation(summary = "上传文档")
    @PostMapping("/upload")
    public Result<Document> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("kbId") Long kbId,
                                    @RequestParam(value = "parseType", required = false) String parseType) throws Exception {
        return Result.success(documentService.upload(file, kbId, parseType));
    }

    @Operation(summary = "文档列表")
    @GetMapping("/list")
    public Result<List<Document>> list(@RequestParam("kbId") Long kbId) {
        return Result.success(documentService.listByKbId(kbId));
    }

    @Operation(summary = "文档详情")
    @GetMapping("/{id}")
    public Result<Document> getById(@PathVariable Long id) {
        return Result.success(documentService.getById(id));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success();
    }

    @Operation(summary = "审核通过并触发 embedding")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        embeddingTaskService.embedDocument(id);
        return Result.success();
    }
}
