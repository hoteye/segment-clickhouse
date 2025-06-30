package com.o11y.infrastructure.flink;

import com.o11y.domain.model.alarm.AlertMessage;
import com.o11y.domain.model.aggregation.ServiceAggResult;
import com.o11y.domain.model.alarm.AlarmRule;
import org.apache.flink.api.common.state.MapStateDescriptor;

import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * 用于 ServiceAggResult 与批量 AlarmRule 的 connect 处理，输出 ServiceAggResult（可自定义告警对象）。
 */
public class AggAlertBroadcastFunction
        extends KeyedBroadcastProcessFunction<String, ServiceAggResult, Map<String, AlarmRule>, AlertMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(AggAlertBroadcastFunction.class);
    private final MapStateDescriptor<String, Map<String, AlarmRule>> ruleStateDescriptor;

    public AggAlertBroadcastFunction(
            MapStateDescriptor<String, Map<String, AlarmRule>> ruleStateDescriptor) {
        this.ruleStateDescriptor = ruleStateDescriptor;
    }

    @Override
    public void processElement(ServiceAggResult value, ReadOnlyContext ctx, Collector<AlertMessage> out)
            throws Exception {
        String key = value.getKey();
        Map<String, AlarmRule> ruleMap = ctx.getBroadcastState(ruleStateDescriptor).get("all_rules");
        if (ruleMap != null) {
            AlarmRule rule = ruleMap.get(key);
            if (rule != null) {
                AlertMessage alert = buildAlertReport(rule, value);
                out.collect(alert);
            }
        }
    }

    @Override
    public void processBroadcastElement(Map<String, AlarmRule> ruleMap, Context ctx, Collector<AlertMessage> out)
            throws Exception {

        if (ruleMap == null || ruleMap.isEmpty()) {
            LOG.warn("[热更新] 收到空规则消息，跳过处理");
            return;
        }

        // 直接用固定 key "all_rules" 存储整张规则表
        ctx.getBroadcastState(ruleStateDescriptor).put("all_rules", ruleMap);

        LOG.info("[热更新] 规则更新成功，规则总数：{}，包含服务：{}",
                ruleMap.size(),
                ruleMap.keySet().stream()
                        .map(key -> key.split("\\|")[0])
                        .distinct()
                        .sorted()
                        .limit(5) // 只显示前5个服务
                        .toArray());
    }

    /**
     * 构建多指标多级阈值的告警分析报告，返回 Pair<alertLevel, content>。
     */
    public static AlertMessage buildAlertReport(AlarmRule rule, ServiceAggResult value) {
        StringBuilder sb = new StringBuilder();
        String alertLevel = "NORMAL";
        boolean triggered = false;
        // 分析结论内容
        StringBuilder conclusion = new StringBuilder();
        // 1. 平均延迟
        if (rule.avgDurationHigh != null && value.avgDuration != null && value.avgDuration > rule.avgDurationHigh) {
            conclusion.append("[HIGH] 平均延迟超过高阈值，建议立即排查服务性能瓶颈。\n");
            alertLevel = "HIGH";
            triggered = true;
        } else if (rule.avgDurationMid != null && value.avgDuration != null
                && value.avgDuration > rule.avgDurationMid) {
            conclusion.append("[MID] 平均延迟超过中阈值，建议关注服务近期变更。\n");
            alertLevel = "MID";
            triggered = true;
        } else if (rule.avgDurationLow != null && value.avgDuration != null
                && value.avgDuration > rule.avgDurationLow) {
            conclusion.append("[LOW] 平均延迟超过低阈值，建议持续观察。\n");
            alertLevel = "LOW";
            triggered = true;
        }
        // 2. 最大延迟
        if (rule.maxDurationHigh != null && value.maxDuration != null && value.maxDuration > rule.maxDurationHigh) {
            conclusion.append("[HIGH] 最大延迟超过高阈值，建议排查慢请求。\n");
            alertLevel = "HIGH";
            triggered = true;
        } else if (rule.maxDurationMid != null && value.maxDuration != null
                && value.maxDuration > rule.maxDurationMid) {
            conclusion.append("[MID] 最大延迟超过中阈值，建议关注慢请求趋势。\n");
            alertLevel = "MID";
            triggered = true;
        } else if (rule.maxDurationLow != null && value.maxDuration != null
                && value.maxDuration > rule.maxDurationLow) {
            conclusion.append("[LOW] 最大延迟超过低阈值，建议持续观察。\n");
            alertLevel = "LOW";
            triggered = true;
        }
        // 3. 成功率（低于阈值告警）
        if (rule.successRateHigh != null && value.errorRate != null && (1 - value.errorRate) < rule.successRateHigh) {
            conclusion.append("[HIGH] 成功率低于高阈值，建议立即排查异常原因。\n");
            alertLevel = "HIGH";
            triggered = true;
        } else if (rule.successRateMid != null && value.errorRate != null
                && (1 - value.errorRate) < rule.successRateMid) {
            conclusion.append("[MID] 成功率低于中阈值，建议关注异常波动。\n");
            alertLevel = "MID";
            triggered = true;
        } else if (rule.successRateLow != null && value.errorRate != null
                && (1 - value.errorRate) < rule.successRateLow) {
            conclusion.append("[LOW] 成功率低于低阈值，建议持续观察。\n");
            alertLevel = "LOW";
            triggered = true;
        }
        // 4. 交易量（高于阈值告警）
        if (rule.trafficVolumeHigh != null && value.totalCount != null && value.totalCount > rule.trafficVolumeHigh) {
            conclusion.append("[HIGH] 交易量高于高阈值，建议关注流量激增原因。\n");
            alertLevel = "HIGH";
            triggered = true;
        } else if (rule.trafficVolumeMid != null && value.totalCount != null
                && value.totalCount > rule.trafficVolumeMid) {
            conclusion.append("[MID] 交易量高于中阈值，建议关注业务波动。\n");
            alertLevel = "MID";
            triggered = true;
        } else if (rule.trafficVolumeLow != null && value.totalCount != null
                && value.totalCount > rule.trafficVolumeLow) {
            conclusion.append("[LOW] 交易量高于低阈值，建议持续观察。\n");
            alertLevel = "LOW";
            triggered = true;
        }
        if (!triggered) {
            conclusion.append("未触发任何告警阈值，服务运行正常。\n");
        }
        // 组装完整分析报告
        sb.append("**告警对象**\n");
        sb.append(String.format("- 服务名：%s\n- 算子名：%s\n- 方法名：%s\n\n", value.service, value.operatorClass,
                value.operatorName));
        sb.append("**窗口信息**\n");
        sb.append(String.format("- 窗口起始时间：%s\n- 窗口长度：%d 秒\n\n",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(value.windowStart)),
                value.windowSize));
        sb.append("**聚合数据**\n");
        sb.append(String.format("- 平均耗时：%s ms\n- 最大耗时：%s ms\n- 成功率：%s%%\n- 总调用次数：%s\n- 成功次数：%s\n\n",
                value.avgDuration == null ? "N/A" : String.format("%.2f", value.avgDuration),
                value.maxDuration == null ? "N/A"
                        : String.format("%.0f", value.maxDuration == null ? 0.0 : value.maxDuration.doubleValue()),
                value.errorRate == null ? "N/A" : String.format("%.2f", (1 - value.errorRate) * 100),
                value.totalCount == null ? "N/A" : value.totalCount,
                value.successCount == null ? "N/A" : value.successCount));
        sb.append("**告警规则阈值**\n");
        sb.append("- 平均延迟阈值：高=" + (rule.avgDurationHigh == null ? "N/A" : rule.avgDurationHigh) + "，中="
                + (rule.avgDurationMid == null ? "N/A" : rule.avgDurationMid) + "，低="
                + (rule.avgDurationLow == null ? "N/A" : rule.avgDurationLow) + "\n");
        sb.append("- 最大延迟阈值：高=" + (rule.maxDurationHigh == null ? "N/A" : rule.maxDurationHigh) + "，中="
                + (rule.maxDurationMid == null ? "N/A" : rule.maxDurationMid) + "，低="
                + (rule.maxDurationLow == null ? "N/A" : rule.maxDurationLow) + "\n");
        sb.append("- 成功率阈值：高=" + (rule.successRateHigh == null ? "N/A" : rule.successRateHigh) + "，中="
                + (rule.successRateMid == null ? "N/A" : rule.successRateMid) + "，低="
                + (rule.successRateLow == null ? "N/A" : rule.successRateLow) + "\n");
        sb.append("- 交易量阈值：高=" + (rule.trafficVolumeHigh == null ? "N/A" : rule.trafficVolumeHigh) + "，中="
                + (rule.trafficVolumeMid == null ? "N/A" : rule.trafficVolumeMid) + "，低="
                + (rule.trafficVolumeLow == null ? "N/A" : rule.trafficVolumeLow) + "\n\n");
        sb.append("**分析结论**\n");
        sb.append(conclusion);

        AlertMessage alert = new AlertMessage(
                value.service,
                value.operatorName,
                alertLevel, // alertLevel
                value.windowStart, // 告警时间
                sb.toString(),
                triggered);
        return alert;
    }
}
