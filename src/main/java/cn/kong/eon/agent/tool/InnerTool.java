package cn.kong.eon.agent.tool;

/**
 * 内置工具接口（Agent 可调用的"原子能力"）
 *
 * <p>每个 InnerTool 代表 Agent 的一项原子能力（如 RAG 检索、时间查询、记忆存储等 Skill）。
 * 实现类注册为 {@link ToolRegistry} 管理的 Spring Bean，
 * 由 {@link InnerToolCallback} 适配为 Spring AI ToolCallback 暴露给模型。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>实现类标注 {@code @Component}，由 Spring 自动扫描注册</li>
 *   <li>{@link #jsonSchema()} 返回输入参数的 JSON Schema（运行时由 {@code InnerToolCallback} 注入必填 {@code reason} 参数，
 *       模型发起工具调用时必须说明调用动机）</li>
 *   <li>执行结果由 {@link ToolResultPostProcessor} 统一包装为语义标注格式，
 *       超长结果自动外置 artifact，保持上下文精简</li>
 * </ul>
 *
 * @author eon-team
 */
public interface InnerTool {

    /**
     * 工具名称（英文小写连字符，唯一标识，用于模型调用与审计日志）
     */
    String name();

    /**
     * 工具描述（供模型理解工具用途，决定是否调用以及如何传参）
     */
    String description();

    /**
     * 输入参数的 JSON Schema 字符串
     *
     * <p>示例：{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}</p>
     */
    String jsonSchema();

    /**
     * 执行工具
     *
     * @param argumentsJson 模型传入的参数 JSON（含自动注入的 reason 字段）
     * @return 工具执行结果（JSON 或 Markdown，经后处理器包装后回灌上下文）
     */
    String execute(String argumentsJson);
}
