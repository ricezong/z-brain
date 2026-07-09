package cn.kong.zbrain.service;

import cn.kong.zbrain.dto.request.ReviewSubmitRequest;
import cn.kong.zbrain.common.PageResult;
import cn.kong.zbrain.dto.response.DocumentProgressResponse;
import cn.kong.zbrain.entity.Document;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档服务接口
 *
 * @author zbrain-team
 */
public interface DocumentService {

    /**
     * 上传文档（异步解析）
     *
     * @param kbId      知识库 ID
     * @param file      上传的文件
     * @param userId    用户 ID
     * @param chunkSize 分块大小（Token 数），为 null 时使用知识库配置
     * @param parseType 解析方式：tika / llama_index，为 null 时默认 tika
     * @return 文档 ID
     */
    Long upload(Long kbId, MultipartFile file, String userId, Integer chunkSize, String parseType);

    /**
     * 触发异步解析与分块
     */
    void parseAsync(Long documentId);

    /**
     * 获取文档处理进度
     */
    DocumentProgressResponse getProgress(Long documentId);

    /**
     * 获取文档详情
     */
    Document getById(Long id);

    /**
     * 分页查询文档
     */
    PageResult<Document> list(Long kbId, String fileName, String status, int pageNum, int pageSize);

    /**
     * 删除文档（连同分块）
     */
    void delete(Long id);

    /**
     * 触发向量化
     */
    void triggerEmbedding(Long documentId);

    /**
     * 审核通过，提交 Diff 数据
     */
    void submitReview(Long documentId, ReviewSubmitRequest request);
}
