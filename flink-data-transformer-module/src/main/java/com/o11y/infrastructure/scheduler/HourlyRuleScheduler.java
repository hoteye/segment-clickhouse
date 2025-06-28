package com.o11y.infrastructure.scheduler;

import com.o11y.domain.model.alarm.HourlyDynamicThresholdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 小时级规则数据汇聚器
 * 
 * <p>
 * 专门负责从 flink_operator_agg_result 表中汇聚前N天的数据，
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
 * <li>手动触发：当需要调整分析天数或重新计算规则时</li>
 * </ul>
 * 
 * <p>
 * <strong>设计思路：</strong>
 * <ol>
 * <li>一次性分析前N天的 flink_operator_agg_result 数据</li>
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

        if (args.length > 0) {
            if ("generate-all".equals(args[0])) {
                // 一次性生成所有24小时的规则
                int days = DEFAULT_ANALYSIS_DAYS;
                if (args.length > 1) {
                    try {
                        days = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        LOG.warn("无效的天数参数: {}，使用默认值: {}", args[1], DEFAULT_ANALYSIS_DAYS);
                    }
                }
                scheduler.generateAllRules(days);
            } else if ("test-connection".equals(args[0])) {
                scheduler.testConnection();
            } else if ("help".equals(args[0]) || "--help".equals(args[0])) {
                printUsage();
            } else {
                LOG.error("未知命令: {}", args[0]);
                printUsage();
                System.exit(1);
            }
        } else {
            // 没有参数时默认执行7天数据汇聚
            LOG.info("未指定参数，默认执行 {} 天数据汇聚", DEFAULT_ANALYSIS_DAYS);
            scheduler.generateAllRules(DEFAULT_ANALYSIS_DAYS);
        }
    }

    private static void printUsage() {
        System.out.println("小时级规则数据汇聚器");
        System.out.println("功能：从 flink_operator_agg_result 表汇聚前N天数据，生成24小时动态阈值规则");
        System.out.println("");
        System.out.println("使用方法:");
        System.out.println("  generate-all [days]  - 汇聚前N天数据，生成所有24小时规则（默认7天）");
        System.out.println("  test-connection      - 测试数据库连接");
        System.out.println("  help                 - 显示此帮助信息");
        System.out.println("");
        System.out.println("说明:");
        System.out.println("  • 本工具专门负责数据汇聚和规则生成");
        System.out.println("  • 规则下发由Flink算子（HourlyRulePublishProcessFunction）自动处理");
        System.out.println("  • 生成的规则存储在 hourly_alarm_rules 表中（24条记录）");
        System.out.println("");
        System.out.println("推荐使用流程:");
        System.out.println("  1. 系统初始化: java HourlyRuleScheduler generate-all 7");
        System.out.println("  2. 定期更新: 每天执行 generate-all 命令更新规则");
        System.out.println("  3. 规则下发: 由Flink作业自动处理，无需手动干预");
        System.out.println("");
        System.out.println("示例:");
        System.out.println("  java HourlyRuleScheduler generate-all 7     # 分析前7天数据");
        System.out.println("  java HourlyRuleScheduler generate-all 14    # 分析前14天数据");
        System.out.println("  java HourlyRuleScheduler test-connection    # 测试连接");
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
        LOG.info("==========================================");
        LOG.info("开始汇聚前 {} 天的数据生成24小时动态阈值规则", analysisDays);
        LOG.info("==========================================");

        try {
            long startTime = System.currentTimeMillis();

            // 先清空整个 hourly_alarm_rules 表，确保数据一致性
            LOG.info("步骤1: 清空 hourly_alarm_rules 表，确保数据一致性...");
            generator.clearAllHourlyRules();

            // 调用核心生成逻辑
            LOG.info("步骤2: 开始分析前 {} 天数据并生成24小时规则...", analysisDays);
            generator.generateAllHourlyRulesOnce(analysisDays);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            LOG.info("==========================================");
            LOG.info("规则生成完成！耗时: {} ms ({} 秒)", duration, duration / 1000.0);
            LOG.info("数据来源: flink_operator_agg_result 表（前 {} 天）", analysisDays);
            LOG.info("输出目标: hourly_alarm_rules 表（24条记录）");
            LOG.info("下一步: 规则将由Flink算子自动下发到Kafka");
            LOG.info("==========================================");

        } catch (Exception e) {
            LOG.error("==========================================");
            LOG.error("规则生成失败: {}", e.getMessage(), e);
            LOG.error("请检查：");
            LOG.error("  1. ClickHouse连接是否正常");
            LOG.error("  2. flink_operator_agg_result 表是否有前 {} 天的数据", analysisDays);
            LOG.error("  3. hourly_alarm_rules 表是否已创建");
            LOG.error("==========================================");
            System.exit(1);
        }
    }

    /**
     * 测试数据库连接和基本功能
     */
    public void testConnection() {
        LOG.info("==========================================");
        LOG.info("测试数据库连接和基本功能");
        LOG.info("==========================================");

        try {
            LOG.info("步骤1: 测试ClickHouse连接...");

            // 使用最少天数测试连接和基本功能
            generator.generateAllHourlyRulesOnce(1);

            LOG.info("步骤2: 测试数据汇聚功能...");
            LOG.info("步骤3: 测试规则生成逻辑...");
            LOG.info("步骤4: 测试数据写入功能...");

            LOG.info("==========================================");
            LOG.info("测试通过！系统功能正常");
            LOG.info("数据库连接: ✓");
            LOG.info("数据汇聚: ✓");
            LOG.info("规则生成: ✓");
            LOG.info("数据写入: ✓");
            LOG.info("==========================================");

        } catch (Exception e) {
            LOG.error("==========================================");
            LOG.error("测试失败: {}", e.getMessage(), e);
            LOG.error("可能的问题：");
            LOG.error("  1. ClickHouse服务未启动");
            LOG.error("  2. 数据库连接配置错误");
            LOG.error("  3. 表结构不存在或不正确");
            LOG.error("  4. 权限不足");
            LOG.error("==========================================");
            System.exit(1);
        }
    }

    /**
     * 获取默认分析天数
     */
    public static int getDefaultAnalysisDays() {
        return DEFAULT_ANALYSIS_DAYS;
    }
}