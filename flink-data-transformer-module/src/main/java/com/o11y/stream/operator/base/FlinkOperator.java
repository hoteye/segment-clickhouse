package com.o11y.stream.operator.base;

import com.o11y.domain.model.aggregation.ServiceAggResult;
import org.apache.flink.streaming.api.datastream.DataStream;

import java.util.List;
import java.util.Map;

/**
 * 所有自定义 Flink 算子需实现该接口，便于统一注册和自动组装。
 */
public interface FlinkOperator {
    /**
     * 注册算子处理逻辑，支持返回聚合流和告警流
     * 
     * @param input  原始数据流
     * @param params 算子参数（可选）
     * @return 聚合结果流
     */
    DataStream<ServiceAggResult> apply(DataStream<?> input, Map<String, List<String>> params);

    /**
     * 算子名称（唯一标识）
     */
    String getName();
}
