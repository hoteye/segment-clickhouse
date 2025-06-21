package com.o11y.shared.util;

import org.apache.flink.streaming.api.functions.source.SourceFunction;

/**
 * 无限流 SourceFunction，用于保证定时任务算子不会因流结束而终止。
 */
public class InfiniteSource implements SourceFunction<String> {
    private volatile boolean running = true;

    @Override
    public void run(SourceContext<String> ctx) throws Exception {
        ctx.collect("singleton");
        while (running) {
            Thread.sleep(60000); // 保持作业不退出
        }
    }

    @Override
    public void cancel() {
        running = false;
    }
}
