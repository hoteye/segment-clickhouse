package com.o11y.infrastructure.scheduler;

import com.o11y.domain.model.alarm.HourlyDynamicThresholdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 小时级规则数据汇聚器
 * 
 * <p>
 * 专门负责从 flink_operator_agg_result 表中汇聚前7天的数据，
 * 按小时分析生成动态阈值规则，并写入 hourly_alarm_rules 表。
 * 
 * <p>
 * <strong>职责分离：</strong>
 * <ul>
 * <li>本类：负责数据汇聚和规则生成（计算密集型操作）</li>
 * <li>HourlyRulePublishProcessFunction：负责定时下发（Flink算子实现）</li>
 * </ul>
 * 
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 * <li>系统初始化：首次生成所有24小时的规则</li>
 * <li>定期更新：每天或定期重新分析历史数据更新规则</li>
 * </ul>
 * 
 * <p>
 * <strong>设计思路：</strong>
 * <ol>
 * <li>固定分析前7天的 flink_operator_agg_result 数据</li>
 * <li>按小时汇总，体现不同时段的交易特征</li>
 * <li>生成所有24小时的动态阈值规则</li>
 * <li>批量写入 hourly_alarm_rules 表（24条记录）</li>
 * <li>规则下发由Flink算子自动处理</li>
 * </ol>
 */
public class HourlyRuleScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(HourlyRuleScheduler.class);

    private final HourlyDynamicThresholdGenerator generator = new HourlyDynamicThresholdGenerator();

    // 默认分析前7天的数据
    private static final int DEFAULT_ANALYSIS_DAYS = 7;

    public static void main(String[] args) {
        HourlyRuleScheduler scheduler = new HourlyRuleScheduler();

        LOG.info("小时级规则数据汇聚器启动，固定使用前{}天历史数据", DEFAULT_ANALYSIS_DAYS);
        scheduler.generateAllRules(DEFAULT_ANALYSIS_DAYS);
    }

    /**
     * 生成所有24小时的动态阈值规则
     * 
     * <p>
     * 核心功能：从 flink_operator_agg_result 表中汇聚前N天的数据，
     * 按小时分析生成动态阈值规则，并批量写入 hourly_alarm_rules 表。
     * 
     * @param analysisDays 分析的历史天数
     */
    public void generateAllRules(int analysisDays) {
        LOG.info("开始汇聚前{}天数据生成24小时动态阈值规则", analysisDays);

        try {
            long startTime = System.currentTimeMillis();

            // 先清空整个 hourly_alarm_rules 表，确保数据一致性
            generator.clearAllHourlyRules();

            // 调用核心生成逻辑
            generator.generateAllHourlyRulesOnce(analysisDays);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            LOG.info("规则生成完成，耗时: {}ms", duration);

        } catch (Exception e) {
            LOG.error("规则生成失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

}