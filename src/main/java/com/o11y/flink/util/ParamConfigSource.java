package com.o11y.flink.util;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import com.o11y.DatabaseService;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 定时从 ClickHouse param_config 表拉取参数，适用于 Broadcast State 热刷新场景。
 * 注意：不要持有不可序列化的 DatabaseService，只持有连接参数，在 open/close 生命周期管理连接。
 */
public class ParamConfigSource extends RichSourceFunction<Map<String, List<String>>> {
    private final String url;
    private final String schema;
    private final String table;
    private final String username;
    private final String password;
    private final String operatorClass;
    private final long intervalMs;
    private volatile boolean running = true;
    private transient DatabaseService dbService;
    private Map<String, List<String>> lastParams = null;

    public ParamConfigSource(String url, String schema, String table, String username, String password,
            String operatorClass, long intervalMs) {
        this.url = url;
        this.schema = schema;
        this.table = table;
        this.username = username;
        this.password = password;
        this.operatorClass = operatorClass;
        this.intervalMs = intervalMs;
    }

    /**
     * 初始化数据库服务连接。
     * 
     * @param parameters Flink 配置参数
     * @throws Exception 初始化异常
     */
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        dbService = new DatabaseService(url, schema, table, username, password).initConnection();
    }

    /**
     * 定时从数据库加载参数并推送到下游。
     * 
     * @param ctx Flink SourceContext
     * @throws Exception 加载或推送参数异常
     */
    @Override
    public void run(SourceContext<Map<String, List<String>>> ctx) throws Exception {
        while (running) {
            Map<String, List<String>> params = OperatorParamLoader.loadParamList(dbService, operatorClass);
            if (lastParams == null || !Objects.equals(params, lastParams)) {
                ctx.collect(params);
                lastParams = params;
            }
            Thread.sleep(intervalMs);
        }
    }

    /**
     * 关闭资源。
     * 
     * @throws Exception 关闭异常
     */
    @Override
    public void close() throws Exception {
        // DatabaseService 无需显式关闭
        super.close();
    }

    /**
     * 取消数据源运行。
     */
    @Override
    public void cancel() {
        running = false;
    }
}
