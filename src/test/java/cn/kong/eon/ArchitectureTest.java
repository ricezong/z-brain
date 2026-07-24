package cn.kong.eon;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit 包依赖边界测试（方案 §2.3：ArchUnit 强制领域依赖方向）
 *
 * <p>铁律：rag/llm/event/config/knowledge 不反向依赖 agent；
 * persistence 不依赖任何业务域；common 不依赖任何域。</p>
 *
 * @author eon-team
 */
@AnalyzeClasses(packages = "cn.kong.eon")
public class ArchitectureTest {

    /**
     * persistence 层不依赖任何业务层
     */
    @ArchTest
    static final ArchRule persistence_should_not_depend_on_business =
            classes().that().resideInAPackage("..persistence..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..persistence..", "..common..", "java..", "javax..", "jakarta..",
                            "org.springframework..", "lombok..", "org.apache.ibatis..",
                            "com.fasterxml..", "io.swagger..");

    /**
     * common 层不依赖任何业务层
     */
    @ArchTest
    static final ArchRule common_should_not_depend_on_business =
            classes().that().resideInAPackage("..common..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..common..", "java..", "javax..", "jakarta..",
                            "org.springframework..", "lombok..", "com.fasterxml..",
                            "org.slf4j..", "io.swagger..");

    /**
     * event 层不依赖 agent/rag/llm/knowledge/controller（纯推送抽象，被 agent/controller 使用）
     */
    @ArchTest
    static final ArchRule event_is_leaf =
            classes().that().resideInAPackage("..event..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..event..", "..common..", "java..", "javax..", "jakarta..",
                            "org.springframework..", "lombok..", "com.fasterxml..",
                            "org.slf4j..");

    /**
     * llm 层不依赖 agent/rag/event/knowledge/controller（纯模型管理）
     */
    @ArchTest
    static final ArchRule llm_no_agent_rag =
            classes().that().resideInAPackage("..llm..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..llm..", "..config..", "..persistence..", "..common..",
                            "java..", "javax..", "jakarta..", "org.springframework..",
                            "lombok..", "com.alibaba..", "org.slf4j..");

    /**
     * rag 层不依赖 agent/controller/knowledge（纯检索能力域）
     */
    @ArchTest
    static final ArchRule rag_no_agent_controller =
            classes().that().resideInAPackage("..rag..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..rag..", "..config..", "..persistence..", "..common..",
                            "..llm..", "java..", "javax..", "jakarta..", "org.springframework..",
                            "lombok..", "com.alibaba..", "org.slf4j..", "io.swagger..");

    /**
     * config 层不依赖 agent/rag/llm/event/knowledge/controller（纯配置读写）
     */
    @ArchTest
    static final ArchRule config_no_business_domain =
            classes().that().resideInAPackage("..config..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..config..", "..persistence..", "..common..",
                            "java..", "javax..", "jakarta..", "org.springframework..",
                            "lombok..", "com.github.benmanes..", "org.slf4j..", "io.swagger..");

    /**
     * knowledge 层只依赖 rag + persistence + common（不依赖 agent/llm/event/controller）
     */
    @ArchTest
    static final ArchRule knowledge_no_agent_llm =
            classes().that().resideInAPackage("..knowledge..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..knowledge..", "..rag..", "..persistence..", "..common..",
                            "..config..", "java..", "javax..", "jakarta..", "org.springframework..",
                            "lombok..", "org.slf4j..", "io.swagger..");

    /**
     * 各领域切片之间不循环依赖
     */
    @ArchTest
    static final ArchRule no_cycles =
            slices().matching("cn.kong.eon.(*)..")
                    .should().beFreeOfCycles();
}
