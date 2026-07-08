package cn.kong.zbrain.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 对话页配置响应
 *
 * <p>包含可用的工作模式与模型列表，供前端初始化工具栏使用。</p>
 *
 * @author zbrain-team
 */
@Data
public class ChatConfigResponse {

    /** 可用工作模式列表 */
    private List<WorkMode> modes;

    /** 可用模型列表 */
    private List<ModelOption> models;

    /** 默认模型 ID */
    private Long defaultModelId;

    /**
     * 工作模式选项
     */
    @Data
    public static class WorkMode {
        /** 模式标识：ask / agent */
        private String value;
        /** 显示名称 */
        private String label;
        /** 描述说明 */
        private String description;

        public WorkMode(String value, String label, String description) {
            this.value = value;
            this.label = label;
            this.description = description;
        }
    }

    /**
     * 模型选项
     */
    @Data
    public static class ModelOption {
        private Long id;
        private String name;
        /** 模型标识，如 deepseek-v4-pro */
        private String modelName;
        private Boolean isDefault;

        public ModelOption(Long id, String name, String modelName, Boolean isDefault) {
            this.id = id;
            this.name = name;
            this.modelName = modelName;
            this.isDefault = isDefault;
        }
    }
}
