package com.o11y.flink.registry;

import java.util.ArrayList;
import java.util.List;

import com.o11y.flink.operator.base.FlinkOperator;

/**
 * 算子注册表，支持自动注册和遍历所有算子。
 */
public class OperatorRegistry {
    private static final List<FlinkOperator> operators = new ArrayList<>();

    public static void register(FlinkOperator operator) {
        operators.add(operator);
    }

    public static List<FlinkOperator> getOperators() {
        return operators;
    }
}
