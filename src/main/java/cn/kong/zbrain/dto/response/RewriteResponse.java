package cn.kong.zbrain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query 改写响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RewriteResponse {
    /** 原始查询 */
    private String originalQuery;

    /** 改写后的查询 */
    private String rewrittenQuery;
}
