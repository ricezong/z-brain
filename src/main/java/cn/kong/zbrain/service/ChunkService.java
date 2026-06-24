package cn.kong.zbrain.service;

import cn.kong.zbrain.dto.request.ChunkMergeRequest;
import cn.kong.zbrain.dto.request.ChunkSplitRequest;
import cn.kong.zbrain.dto.request.ChunkUpdateRequest;
import cn.kong.zbrain.entity.Chunk;

import java.util.List;

/**
 * 分块服务接口
 *
 * <p>提供人工审核工作台的分块管理能力。</p>
 *
 * @author zbrain-team
 */
public interface ChunkService {

    /**
     * 查询文档下所有分块（父子树形结构）
     */
    List<Chunk> listByDocId(Long docId);

    /**
     * 获取分块详情
     */
    Chunk getById(Long id);

    /**
     * 更新分块内容（触发器自动更新 tsv）
     */
    void update(ChunkUpdateRequest request);

    /**
     * 删除分块
     */
    void delete(Long id);

    /**
     * 合并多个相邻子块
     */
    Chunk merge(ChunkMergeRequest request);

    /**
     * 拆分单个分块
     */
    List<Chunk> split(ChunkSplitRequest request);

    /**
     * 调整父子关系
     */
    void adjustParent(Long chunkId, Long parentId);
}
