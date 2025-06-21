package com.o11y.application.launcher;

import com.o11y.shared.util.ConfigurationUtils;
import com.o11y.infrastructure.flink.FlinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Flink 作业启动入口类。
 * 负责加载配置文件，初始化 FlinkService 并启动主流程。
 * 支持异常捕获与日志输出，便于生产环境部署和排障。
 */
public class FlinkServiceLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkServiceLauncher.class);

    /**
     * 主函数，作为 Flink 作业的启动入口。
     * 
     * <p>
     * 执行流程：
     * <ol>
     * <li>加载 application.yaml 配置文件</li>
     * <li>构造 FlinkService 实例</li>
     * <li>调用 run() 启动完整 Flink 流式作业</li>
     * <li>捕获所有异常并输出日志，启动失败时退出进程</li>
     * </ol>
     *
     * @param args 启动参数（当前未使用，预留扩展用于传递配置文件路径等）
     */
    public static void main(String[] args) {
        try {
            Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
            FlinkService flinkService = new FlinkService(config);
            flinkService.run();
        } catch (Exception e) {
            LOG.error("FlinkService 启动失败", e);
            System.exit(1);
        }
    }
}
