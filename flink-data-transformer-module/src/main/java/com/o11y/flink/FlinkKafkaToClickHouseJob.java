package com.o11y.flink;

import com.o11y.application.launcher.FlinkServiceLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flink Kafka 到 ClickHouse 的数据转换作业主入口类。
 * 
 * <p>
 * 该类作为 Flink 作业的统一入口点，负责启动完整的数据流转换处理流程。
 * 包括从 Kafka 消费数据、流式处理、聚合分析、告警检测和数据写入 ClickHouse 等功能。
 * 
 * <p>
 * <strong>主要功能：</strong>
 * <ul>
 * <li>从 Kafka 消费 Segment 数据流</li>
 * <li>实时数据转换和聚合处理</li>
 * <li>告警规则匹配和异常检测</li>
 * <li>结果数据写入 ClickHouse 存储</li>
 * <li>支持动态配置和实时参数调整</li>
 * </ul>
 * 
 * <p>
 * <strong>部署和运行：</strong>
 * 
 * <pre>
 * # 本地开发环境运行
 * java -cp target/flink-data-transformer-module-1.0.5-shaded.jar com.o11y.flink.FlinkKafkaToClickHouseJob
 * 
 * # 生产环境 Flink 集群提交
 * flink run target/flink-data-transformer-module-1.0.5-shaded.jar
 * 
 * # 使用自定义配置文件
 * java -Dconfig.file=custom-application.yaml -cp target/flink-data-transformer-module-1.0.5-shaded.jar com.o11y.flink.FlinkKafkaToClickHouseJob
 * </pre>
 * 
 * @author O11y Team
 * @version 1.0.5
 * @since 2024-01-01
 */
public class FlinkKafkaToClickHouseJob {

    private static final Logger LOG = LoggerFactory.getLogger(FlinkKafkaToClickHouseJob.class);

    /**
     * Flink 作业主函数入口。
     * 
     * <p>
     * 该方法作为整个数据转换作业的启动点，会初始化所有必要的组件和配置，
     * 然后启动完整的 Flink 流处理作业。
     * 
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     * <li>记录作业启动日志</li>
     * <li>委托给 FlinkServiceLauncher 进行实际的启动处理</li>
     * <li>捕获和处理启动过程中的异常</li>
     * <li>确保失败时正确退出进程</li>
     * </ol>
     * 
     * @param args 命令行参数（当前未使用，预留扩展用于配置文件路径等）
     */
    public static void main(String[] args) {
        LOG.info("正在启动 Flink Kafka 到 ClickHouse 数据转换作业...");

        try {
            // 委托给现有的 FlinkServiceLauncher 处理具体的启动逻辑
            FlinkServiceLauncher.main(args);

            LOG.info("Flink Kafka 到 ClickHouse 数据转换作业启动成功");

        } catch (Exception e) {
            LOG.error("Flink Kafka 到 ClickHouse 数据转换作业启动失败", e);
            System.exit(1);
        }
    }
}
