package com.o11y.flink.hotupdate;

import com.o11y.flink.operator.model.ServiceAggResult;
import com.o11y.flink.rule.AlarmRule;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 用于 ServiceAggResult 与批量 AlarmRule 的 connect 处理，输出 ServiceAggResult（可自定义告警对象）。
 */
public class AggAlertBroadcastFunction
        extends KeyedBroadcastProcessFunction<String, ServiceAggResult, Map<String, AlarmRule>, ServiceAggResult> {
    private final MapStateDescriptor<String, Map<String, AlarmRule>> ruleStateDescriptor;
    private static final Logger LOG = LoggerFactory.getLogger(AggAlertBroadcastFunction.class);

    public AggAlertBroadcastFunction(
            MapStateDescriptor<String, Map<String, AlarmRule>> ruleStateDescriptor) {
        this.ruleStateDescriptor = ruleStateDescriptor;
    }

    @Override
    public void processElement(ServiceAggResult value, ReadOnlyContext ctx, Collector<ServiceAggResult> out)
            throws Exception {
        String key = value.getKey();
        Map<String, AlarmRule> ruleMap = ctx.getBroadcastState(ruleStateDescriptor).get("all_rules");
        if (ruleMap != null) {
            int idx = 1;
            for (String ruleKey : ruleMap.keySet()) {
                LOG.info("[BroadcastState] 规则{}: {}", idx++, ruleKey);
            }
            AlarmRule rule = ruleMap.get(key);
            LOG.info("Processing element: key={}, value={}", key, value);
            if (rule != null && value.avgDuration != null) {
                if (rule.avgDurationHigh != null && value.avgDuration > rule.avgDurationHigh) {
                    String msg = String.format("[ALERT-HIGH] 服务[%s]算子[%s]平均耗时 %.2f 超过高阈值 %.2f，窗口[%d]", value.service,
                            value.operatorName, value.avgDuration, rule.avgDurationHigh, value.windowStart);
                    LOG.warn(msg);
                } else if (rule.avgDurationMid != null && value.avgDuration > rule.avgDurationMid) {
                    String msg = String.format("[ALERT-MID] 服务[%s]算子[%s]平均耗时 %.2f 超过中阈值 %.2f，窗口[%d]", value.service,
                            value.operatorName, value.avgDuration, rule.avgDurationMid, value.windowStart);
                    LOG.warn(msg);
                } else if (rule.avgDurationLow != null && value.avgDuration > rule.avgDurationLow) {
                    String msg = String.format("[ALERT-LOW] 服务[%s]算子[%s]平均耗时 %.2f 超过低阈值 %.2f，窗口[%d]", value.service,
                            value.operatorName, value.avgDuration, rule.avgDurationLow, value.windowStart);
                    LOG.warn(msg);
                }
                out.collect(value);
            }
        }
    }

    @Override
    public void processBroadcastElement(Map<String, AlarmRule> ruleMap, Context ctx, Collector<ServiceAggResult> out)
            throws Exception {
        // 直接用固定 key "all_rules" 存储整张规则表
        ctx.getBroadcastState(ruleStateDescriptor).put("all_rules", ruleMap);
    }
}
