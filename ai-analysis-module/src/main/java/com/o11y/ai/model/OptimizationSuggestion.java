package com.o11y.ai.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * 优化建议模型
 * 
 * 包含由 LLM 分析生成的性能优化建议信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationSuggestion {

    /**
     * 建议 ID
     */
    private String id;

    /**
     * 建议分类 (JVM, 数据库, 应用, 系统等)
     */
    @NotBlank(message = "建议分类不能为空")
    private String category;

    /**
     * 建议标题
     */
    @NotBlank(message = "建议标题不能为空")
    private String title;

    /**
     * 建议详细描述
     */
    @NotBlank(message = "建议描述不能为空")
    private String description;

    /**
     * 优先级 (高, 中, 低)
     */
    @NotBlank(message = "优先级不能为空")
    private String priority;

    /**
     * 影响级别 (高, 中, 低)
     */
    @NotBlank(message = "影响级别不能为空")
    private String impactLevel;

    /**
     * 实施复杂度 (简单, 中等, 复杂)
     */
    @NotBlank(message = "实施复杂度不能为空")
    private String implementationComplexity;

    /**
     * 具体实施步骤或行动计划
     */
    private String actionPlan;

    /**
     * 预期效果或收益
     */
    private String expectedBenefit;

    /**
     * 相关的性能指标
     */
    private String relatedMetrics;

    /**
     * 建议来源 (LLM模型名称或分析器)
     */
    private String source;

    /**
     * 建议生成时间
     */
    private LocalDateTime createdTime;

    /**
     * 建议状态 (待处理, 进行中, 已完成, 已忽略)
     */
    private String status = "待处理";

    /**
     * 置信度分数 (0.0 - 1.0)
     */
    private Double confidenceScore;

    /**
     * 相关标签
     */
    private String tags;

    /**
     * 备注信息
     */
    private String notes;

    /**
     * 优先级枚举
     */
    public enum Priority {
        HIGH("高"),
        MEDIUM("中"),
        LOW("低");

        private final String label;

        Priority(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 影响级别枚举
     */
    public enum ImpactLevel {
        HIGH("高"),
        MEDIUM("中"),
        LOW("低");

        private final String label;

        ImpactLevel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 实施复杂度枚举
     */
    public enum Complexity {
        SIMPLE("简单"),
        MEDIUM("中等"),
        COMPLEX("复杂");

        private final String label;

        Complexity(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
