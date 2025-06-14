package com.o11y.flink.registry;

import java.util.ArrayList;
import java.util.List;

import com.o11y.flink.operator.base.FlinkOperator;

/**
 * 算子注册表，支持自动注册和遍历所有算子。
 */
public class OperatorRegistry {
    private static final List<FlinkOperator> operators = new ArrayList<>();

    /**
     * 注册 FlinkOperator 到全局算子列表。
     * 
     * @param operator 算子实例
     */
    public static void register(FlinkOperator operator) {
        operators.add(operator);
    }

    /**
     * 获取所有已注册的 FlinkOperator。
     * 
     * @return 算子列表
     */
    public static List<FlinkOperator> getOperators() {
        return operators;
    }
}
