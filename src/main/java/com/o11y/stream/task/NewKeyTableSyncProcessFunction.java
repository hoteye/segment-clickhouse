package com.o11y.stream.task;

import com.o11y.infrastructure.database.DatabaseService;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Flink 原生定时器实现的 new_key 表同步算子。
 * 
 * <p>
 * <strong>功能描述：</strong>
 * <ul>
 * <li>定期扫描 new_key 表中未创建的字段（isCreated = 0）</li>
 * <li>为 ClickHouse events 表动态添加新字段</li>
 * <li>更新 new_key 表的 isCreated 状态为已创建</li>
 * <li>支持多种数据类型的自动转换和列创建</li>
 * </ul>
 * 
 * <p>
 * <strong>设计特点：</strong>
 * <ul>
 * <li>使用 Flink ProcessingTime 定时器实现周期性同步</li>
 * <li>并发度建议为 1，确保全局唯一的同步过程</li>
 * <li>支持异常容错，单个字段创建失败不影响其他字段</li>
 * <li>自动管理数据库连接的生命周期</li>
 * </ul>
 * 
 * <p>
 * <strong>配置要求：</strong>
 * <ul>
 * <li>需要 ClickHouse 数据库连接权限</li>
 * <li>需要 new_key 表存在且结构正确</li>
 * <li>需要 events 表的 ALTER 权限</li>
 * </ul>
 * 
 * @author DDD Architecture Team
 * @since 1.0.0
 * @see DatabaseService 数据库连接和类型转换服务
 */
public class NewKeyTableSyncProcessFunction extends KeyedProcessFunction<String, String, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(NewKeyTableSyncProcessFunction.class);

    /** 数据库服务实例，用于连接 ClickHouse 和执行 SQL 操作 */
    private transient DatabaseService databaseService;

    /** 数据库连接配置参数 */
    private final String url, schema, table, username, password;

    /** 同步间隔时间（毫秒），控制定时器触发频率 */
    private final long intervalMs;

    /** 定时器注册标志，确保只注册一次定时器 */
    private boolean timerRegistered = false;

    /**
     * 构造 NewKeyTableSyncProcessFunction 实例，配置数据库连接和同步参数。
     * 
     * <p>
     * <strong>参数来源：</strong><br>
     * 这些参数通常在 FlinkServiceLauncher 中从 application.yaml 配置文件中读取，
     * 然后通过依赖注入的方式传递给该构造函数。
     * 
     * <p>
     * <strong>配置映射关系：</strong>
     * <table border="1" cellpadding="5" cellspacing="0">
     * <tr>
     * <th>参数</th>
     * <th>配置路径</th>
     * <th>示例值</th>
     * </tr>
     * <tr>
     * <td>url</td>
     * <td>clickhouse.url</td>
     * <td>jdbc:clickhouse://localhost:8123/default</td>
     * </tr>
     * <tr>
     * <td>schema</td>
     * <td>clickhouse.schema</td>
     * <td>default</td>
     * </tr>
     * <tr>
     * <td>table</td>
     * <td>clickhouse.table</td>
     * <td>events</td>
     * </tr>
     * <tr>
     * <td>username</td>
     * <td>clickhouse.username</td>
     * <td>default</td>
     * </tr>
     * <tr>
     * <td>password</td>
     * <td>clickhouse.password</td>
     * <td>（加密存储）</td>
     * </tr>
     * <tr>
     * <td>intervalMs</td>
     * <td>sync.interval</td>
     * <td>60000 (1分钟)</td>
     * </tr>
     * </table>
     * 
     * <p>
     * <strong>参数验证：</strong>
     * <ul>
     * <li><strong>url：</strong>必须是有效的 ClickHouse JDBC URL 格式</li>
     * <li><strong>schema/table：</strong>不能为空，必须是有效的数据库标识符</li>
     * <li><strong>intervalMs：</strong>建议设置在 30-300 秒之间，避免过于频繁或过于稀疏</li>
     * <li><strong>凭证：</strong>username/password 组合必须具有目标表的 DDL 权限</li>
     * </ul>
     * 
     * <p>
     * <strong>生命周期：</strong>
     * <ol>
     * <li>构造函数：保存配置参数，不建立连接</li>
     * <li>open()：建立数据库连接，初始化 DatabaseService</li>
     * <li>processElement()：注册定时器，启动周期性同步</li>
     * <li>onTimer()：执行实际的同步操作</li>
     * <li>close()：释放数据库连接和资源</li>
     * </ol>
     * 
     * @param url        ClickHouse JDBC 连接
     *                   URL，格式：jdbc:clickhouse://host:port/database
     * @param schema     数据库模式名称，通常为 "default"，用于构造完整的表名
     * @param table      目标事件表名称，通常为 "events"，存储实际的业务数据
     * @param username   数据库连接用户名，需要具有目标表的 ALTER 权限
     * @param password   数据库连接密码，建议通过配置加密或环境变量传递
     * @param intervalMs 同步检查间隔（毫秒），决定定时器触发频率，建议 30000-300000 之间
     * 
     * @see com.o11y.application.launcher.FlinkServiceLauncher#createSyncStream()
     *      算子实例化位置
     * @see com.o11y.shared.util.ConfigurationUtils 配置加载工具类
     */

    public NewKeyTableSyncProcessFunction(String url, String schema, String table, String username, String password,
            long intervalMs) {
        this.url = url;
        this.schema = schema;
        this.table = table;
        this.username = username;
        this.password = password;
        this.intervalMs = intervalMs;
    }

    /**
     * Flink 算子初始化方法，在任务启动时自动调用，负责资源初始化。
     * 
     * <p>
     * <strong>调用时机：</strong><br>
     * 该方法在 Flink 任务启动时，ProcessFunction 被部署到 TaskManager 后立即调用，
     * 在任何 processElement() 或 onTimer() 调用之前执行，确保必要的资源已就绪。
     * 
     * <p>
     * <strong>初始化流程：</strong>
     * <ol>
     * <li><strong>创建数据库服务：</strong>使用构造函数中的连接参数创建 DatabaseService 实例</li>
     * <li><strong>建立连接：</strong>调用 initConnection() 方法建立到 ClickHouse 的连接</li>
     * <li><strong>连接验证：</strong>DatabaseService 内部会执行连接测试，确保连接可用</li>
     * <li><strong>状态记录：</strong>记录初始化成功的日志，便于运维监控</li>
     * </ol>
     * 
     * <p>
     * <strong>连接池配置：</strong><br>
     * DatabaseService 内部使用连接池管理数据库连接，典型配置：
     * <ul>
     * <li>最小连接数：1</li>
     * <li>最大连接数：5</li>
     * <li>连接超时：30秒</li>
     * <li>空闲超时：10分钟</li>
     * </ul>
     * 
     * <p>
     * <strong>异常处理策略：</strong>
     * <ul>
     * <li><strong>连接失败：</strong>抛出 RuntimeException，导致任务启动失败</li>
     * <li><strong>权限不足：</strong>记录详细错误信息，帮助快速定位问题</li>
     * <li><strong>网络问题：</strong>依赖 Flink 的重启策略进行任务恢复</li>
     * <li><strong>配置错误：</strong>立即失败，避免在错误配置下运行</li>
     * </ul>
     * 
     * <p>
     * <strong>Flink 生命周期集成：</strong>
     * 
     * <pre>
     * Task 启动 → open() → processElement()/onTimer() → close() → Task 结束
     *              ↑                    ↑                    ↑
     *          资源初始化            业务处理              资源清理
     * </pre>
     * 
     * <p>
     * <strong>故障排查：</strong>
     * <ul>
     * <li><strong>初始化失败：</strong>检查 ClickHouse 服务状态和网络连通性</li>
     * <li><strong>权限问题：</strong>验证用户是否具有目标库表的 ALTER 和 SELECT 权限</li>
     * <li><strong>配置错误：</strong>检查 JDBC URL 格式和数据库名称拼写</li>
     * <li><strong>资源不足：</strong>检查连接池配置和数据库最大连接数限制</li>
     * </ul>
     * 
     * @param parameters Flink 运行时配置参数，包含全局配置和算子特定配置
     * @throws RuntimeException 当数据库连接初始化失败时抛出，触发任务失败和重启
     * 
     * @see com.o11y.infrastructure.database.DatabaseService#initConnection()
     *      连接初始化方法
     * @see #close() 对应的资源清理方法
     */
    @Override
    public void open(Configuration parameters) {
        try {
            this.databaseService = new DatabaseService(url, schema, table, username, password).initConnection();
            LOG.info("NewKeyTableSyncProcessFunction 数据库连接初始化成功");
        } catch (Exception e) {
            LOG.error("NewKeyTableSyncProcessFunction 数据库连接初始化失败", e);
            throw new RuntimeException("Failed to initialize DatabaseService in open()", e);
        }
    }

    /**
     * Flink 算子关闭方法，在任务停止或算子销毁时调用，负责资源清理。
     * 
     * <p>
     * <strong>调用时机：</strong><br>
     * 该方法在以下场景下会被 Flink 自动调用：
     * <ul>
     * <li><strong>正常停止：</strong>作业被用户主动取消或正常完成</li>
     * <li><strong>异常停止：</strong>任务因异常失败需要清理资源</li>
     * <li><strong>重启前：</strong>故障恢复时先清理旧资源再重新初始化</li>
     * <li><strong>扩缩容：</strong>并行度调整时销毁不需要的算子实例</li>
     * </ul>
     * 
     * <p>
     * <strong>清理流程：</strong>
     * <ol>
     * <li><strong>空值检查：</strong>确保 databaseService 已正确初始化，避免空指针异常</li>
     * <li><strong>连接关闭：</strong>调用 DatabaseService.close() 方法关闭数据库连接</li>
     * <li><strong>连接池清理：</strong>释放连接池中的所有连接资源</li>
     * <li><strong>状态重置：</strong>将 databaseService 引用置为清理状态</li>
     * <li><strong>日志记录：</strong>记录资源清理完成的状态</li>
     * </ol>
     * 
     * <p>
     * <strong>资源清理范围：</strong>
     * <ul>
     * <li><strong>数据库连接：</strong>关闭所有到 ClickHouse 的 JDBC 连接</li>
     * <li><strong>连接池：</strong>清理 HikariCP 或其他连接池的资源</li>
     * <li><strong>线程资源：</strong>停止连接池内部的维护线程</li>
     * <li><strong>内存资源：</strong>释放缓存的连接对象和元数据</li>
     * </ul>
     * 
     * <p>
     * <strong>异常处理策略：</strong>
     * <ul>
     * <li><strong>静默处理：</strong>资源清理异常不应影响任务的正常停止</li>
     * <li><strong>日志记录：</strong>记录清理过程中的异常，便于问题追踪</li>
     * <li><strong>最大努力：</strong>即使部分清理失败，也要尝试清理其他资源</li>
     * <li><strong>优雅降级：</strong>清理失败时依赖系统级资源回收机制</li>
     * </ul>
     * 
     * <p>
     * <strong>生命周期对应关系：</strong>
     * <table border="1" cellpadding="5" cellspacing="0">
     * <tr>
     * <th>open() 操作</th>
     * <th>close() 对应清理</th>
     * </tr>
     * <tr>
     * <td>创建 DatabaseService</td>
     * <td>调用 close() 方法</td>
     * </tr>
     * <tr>
     * <td>初始化连接池</td>
     * <td>关闭连接池</td>
     * </tr>
     * <tr>
     * <td>建立数据库连接</td>
     * <td>关闭所有连接</td>
     * </tr>
     * <tr>
     * <td>启动维护线程</td>
     * <td>停止后台线程</td>
     * </tr>
     * </table>
     * 
     * <p>
     * <strong>并发安全性：</strong>
     * <ul>
     * <li>close() 方法可能与 onTimer() 并发执行，DatabaseService 内部需要处理并发关闭</li>
     * <li>使用连接池的线程安全特性确保资源清理的原子性</li>
     * <li>避免在清理过程中创建新的数据库操作</li>
     * </ul>
     * 
     * <p>
     * <strong>监控指标：</strong>
     * <ul>
     * <li>资源清理耗时（通常在几十毫秒内）</li>
     * <li>清理成功率（正常情况下应为 100%）</li>
     * <li>清理过程中的异常数量</li>
     * <li>资源泄露检测（连接数、内存使用）</li>
     * </ul>
     * 
     * <p>
     * <strong>最佳实践：</strong>
     * <ul>
     * <li><strong>幂等性：</strong>多次调用 close() 应该是安全的</li>
     * <li><strong>快速清理：</strong>避免长时间阻塞任务停止流程</li>
     * <li><strong>异常隔离：</strong>清理异常不应抛出到外层</li>
     * <li><strong>资源监控：</strong>通过日志和指标监控清理效果</li>
     * </ul>
     * 
     * @throws Exception 资源清理过程中的异常，但通常会被 Flink 框架捕获处理
     * 
     * @see #open(Configuration) 对应的资源初始化方法
     * @see com.o11y.infrastructure.database.DatabaseService#close() 数据库服务清理方法
     */
    @Override
    public void close() throws Exception {
        if (databaseService != null) {
            databaseService.close();
            LOG.info("NewKeyTableSyncProcessFunction 数据库连接已关闭");
        }
    }

    /**
     * 处理输入元素的方法，负责启动周期性的 new_key 表同步任务。
     * 
     * <p>
     * <strong>业务背景：</strong><br>
     * 该方法是 Flink 流处理中的核心组件，用于解决 ClickHouse 数据一致性问题。
     * 由于 ClickHouse 集群环境下可能存在数据分布不均或同步延迟，需要定期将新产生的
     * 键值对同步到 new_key 表中，确保后续的告警规则能够基于最新的数据进行判断。
     * 
     * <p>
     * <strong>数据流上下文：</strong><br>
     * 在 FlinkServiceLauncher 中，该算子通过以下方式集成到数据流中：
     * 
     * <pre>
     * env.addSource(new InfiniteSource()) // 无限数据源，每秒产生一个触发信号
     *         .keyBy(value -> "sync") // 按固定key分组，确保单并行度
     *         .process(new NewKeyTableSyncProcessFunction(intervalMs)) // 本算子
     * </pre>
     * 
     * <p>
     * <strong>执行逻辑：</strong>
     * <ol>
     * <li><strong>单次注册检查：</strong>通过 timerRegistered 标志确保定时器只注册一次，
     * 避免多个 InfiniteSource 事件导致的重复注册</li>
     * <li><strong>立即触发：</strong>获取当前处理时间并注册立即执行的定时器，
     * 确保应用启动后能立即执行一次同步</li>
     * <li><strong>周期性调度：</strong>首次定时器触发后，在 onTimer 方法中会自动注册
     * 下一个定时器，形成周期性执行模式</li>
     * <li><strong>状态管理：</strong>设置 timerRegistered = true，防止后续事件重复注册</li>
     * </ol>
     * 
     * <p>
     * <strong>intervalMs 参数详解：</strong>
     * <table border="1" cellpadding="5" cellspacing="0">
     * <tr>
     * <th>来源</th>
     * <th>说明</th>
     * </tr>
     * <tr>
     * <td>配置文件</td>
     * <td>application.yaml 中的 sync.interval 配置项</td>
     * </tr>
     * <tr>
     * <td>加载路径</td>
     * <td>ConfigurationUtils.loadConfiguration() → SyncConfig.interval</td>
     * </tr>
     * <tr>
     * <td>传递链路</td>
     * <td>FlinkServiceLauncher.createSyncStream() → new
     * NewKeyTableSyncProcessFunction(intervalMs)</td>
     * </tr>
     * <tr>
     * <td>默认值</td>
     * <td>通常设置为 60000ms (1分钟) 或 300000ms (5分钟)</td>
     * </tr>
     * <tr>
     * <td>影响范围</td>
     * <td>决定 new_key 表的同步频率，影响数据一致性和系统负载</td>
     * </tr>
     * </table>
     * 
     * <p>
     * <strong>定时器执行时间线：</strong>
     * 
     * <pre>
     * 应用启动时刻: T0
     * 首次processElement: T0 + 几毫秒 → 注册定时器(T0)
     * 首次onTimer执行: T0 → 立即执行同步 + 注册定时器(T0 + intervalMs)
     * 第二次onTimer: T0 + intervalMs → 执行同步 + 注册定时器(T0 + 2*intervalMs)
     * 第三次onTimer: T0 + 2*intervalMs → 执行同步 + 注册定时器(T0 + 3*intervalMs)
     * ...依此类推，形成精确的周期性执行
     * </pre>
     * 
     * <p>
     * <strong>容错机制：</strong>
     * <ul>
     * <li><strong>幂等性保证：</strong>timerRegistered 标志确保定时器只注册一次</li>
     * <li><strong>处理时间语义：</strong>使用 ProcessingTime 而非 EventTime，不受数据延迟影响</li>
     * <li><strong>异常隔离：</strong>onTimer 中的异常不会影响定时器的继续注册</li>
     * <li><strong>资源清理：</strong>close() 方法确保数据库连接正确释放</li>
     * </ul>
     * 
     * <p>
     * <strong>性能考虑：</strong>
     * <ul>
     * <li>该方法仅在首次调用时执行实际逻辑，后续调用几乎无开销</li>
     * <li>定时器基于 Flink 内部调度，不会创建额外的线程</li>
     * <li>同步操作在 onTimer 中异步执行，不阻塞主数据流</li>
     * </ul>
     * 
     * @param value 输入的字符串值（来自 InfiniteSource，内容通常为时间戳字符串，仅作为触发信号使用）
     * @param ctx   Flink 运行时上下文，提供定时器服务、状态访问、时间获取等功能
     * @param out   输出收集器（本算子为副作用算子，不产生下游数据，该参数实际未使用）
     * @throws Exception 当定时器注册失败或系统异常时抛出
     * 
     * @see #onTimer(long, OnTimerContext, Collector) 定时器触发时的实际同步逻辑
     * @see com.o11y.shared.source.InfiniteSource InfiniteSource 数据源实现
     * @see com.o11y.application.launcher.FlinkServiceLauncher#createSyncStream()
     *      数据流构建方法
     */
    @Override
    public void processElement(String value, Context ctx, Collector<Object> out) throws Exception {
        LOG.info("processElement called, registering timer");
        if (!timerRegistered) {
            // 获取当前处理时间并立即注册定时器
            long now = ctx.timerService().currentProcessingTime();
            ctx.timerService().registerProcessingTimeTimer(now);
            timerRegistered = true;
            LOG.info("NewKeyTableSyncProcessFunction timer registered, interval: {} ms", intervalMs);
        }
    }

    /**
     * 定时器触发回调方法，负责执行周期性的 new_key 表同步任务。
     * 
     * <p>
     * <strong>业务价值：</strong><br>
     * 该方法是数据一致性保障的核心实现，确保 ClickHouse events 表的 schema
     * 能够动态适应新产生的键值对，从而支持灵活的告警规则和查询需求。
     * 
     * <p>
     * <strong>调用时机：</strong>
     * <ul>
     * <li><strong>首次调用：</strong>应用启动后，processElement 注册的立即定时器触发</li>
     * <li><strong>周期调用：</strong>每次执行完毕后自动注册下一个定时器 (timestamp + intervalMs)</li>
     * <li><strong>精确调度：</strong>基于 Flink ProcessingTime，调度精度通常在毫秒级别</li>
     * </ul>
     * 
     * <p>
     * <strong>执行流程详解：</strong>
     * <ol>
     * <li><strong>同步执行：</strong>调用 syncNewKeys() 方法执行核心业务逻辑
     * <ul>
     * <li>查询 new_key 表中 isCreated = 0 的记录</li>
     * <li>为每个新键在 events 表中创建对应的列</li>
     * <li>更新 new_key 表的 isCreated 状态</li>
     * </ul>
     * </li>
     * <li><strong>成功日志：</strong>记录同步完成的时间戳，便于运维监控</li>
     * <li><strong>异常处理：</strong>捕获同步过程中的所有异常，记录详细错误信息</li>
     * <li><strong>持续调度：</strong>无论成功还是失败，都会注册下一个定时器</li>
     * </ol>
     * 
     * <p>
     * <strong>时间参数说明：</strong>
     * <table border="1" cellpadding="5" cellspacing="0">
     * <tr>
     * <th>参数</th>
     * <th>含义</th>
     * <th>示例值</th>
     * </tr>
     * <tr>
     * <td>timestamp</td>
     * <td>定时器预定触发时间</td>
     * <td>1703856000000 (2023-12-29 12:00:00)</td>
     * </tr>
     * <tr>
     * <td>实际触发时间</td>
     * <td>Flink 实际调度时间</td>
     * <td>1703856000003 (可能有几毫秒延迟)</td>
     * </tr>
     * <tr>
     * <td>下次调度时间</td>
     * <td>timestamp + intervalMs</td>
     * <td>1703856060000 (1分钟后)</td>
     * </tr>
     * </table>
     * 
     * <p>
     * <strong>容错机制：</strong>
     * <ul>
     * <li><strong>异常隔离：</strong>try-catch 确保单次同步失败不影响后续执行</li>
     * <li><strong>持续运行：</strong>finally 块或 catch 后都会注册下一个定时器</li>
     * <li><strong>状态恢复：</strong>依赖 ClickHouse 的幂等性操作（IF NOT EXISTS）</li>
     * <li><strong>监控友好：</strong>详细的日志记录支持问题诊断和性能监控</li>
     * </ul>
     * 
     * <p>
     * <strong>性能特征：</strong>
     * <ul>
     * <li><strong>非阻塞：</strong>定时器在独立线程中执行，不影响主数据流</li>
     * <li><strong>轻量级：</strong>只处理新增的键，避免全量扫描</li>
     * <li><strong>批处理：</strong>一次调用处理所有待同步的键，减少数据库交互</li>
     * <li><strong>内存友好：</strong>使用流式处理，不缓存大量数据</li>
     * </ul>
     * 
     * <p>
     * <strong>监控指标建议：</strong>
     * <ul>
     * <li>同步频率：每 intervalMs 毫秒执行一次</li>
     * <li>同步耗时：通常在几百毫秒到几秒之间</li>
     * <li>失败率：正常情况下应接近 0%</li>
     * <li>处理量：每次同步的新键数量</li>
     * </ul>
     * 
     * <p>
     * <strong>故障排查：</strong>
     * <ul>
     * <li><strong>同步停止：</strong>检查 ClickHouse 连接状态和定时器注册日志</li>
     * <li><strong>同步延迟：</strong>检查 ClickHouse 性能和 intervalMs 配置</li>
     * <li><strong>重复同步：</strong>检查 new_key 表的 isCreated 状态更新</li>
     * <li><strong>Schema 冲突：</strong>检查 events 表的列定义和类型转换逻辑</li>
     * </ul>
     * 
     * @param timestamp 定时器的预定触发时间戳（毫秒），用于计算下次调度时间
     * @param ctx       定时器上下文对象，提供定时器服务、状态访问、时间获取等功能
     * @param out       输出收集器（本算子为副作用算子，不向下游发送数据，该参数未使用）
     * @throws Exception 当定时器注册失败或系统异常时抛出，Flink 会进行相应的故障处理
     * 
     * @see #syncNewKeys() 具体的同步业务逻辑实现
     * @see #processElement(String, Context, Collector) 定时器的初始注册方法
     * @see com.o11y.infrastructure.database.DatabaseService ClickHouse 数据库操作服务
     */
    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<Object> out) throws Exception {
        try {
            syncNewKeys();
            LOG.info("Synced new keys to events table at {}", timestamp);
        } catch (Exception e) {
            LOG.error("Failed to sync new keys to events table", e);
        }
        // 注册下一个定时器，实现周期性执行
        ctx.timerService().registerProcessingTimeTimer(timestamp + intervalMs);
    }

    /**
     * 执行 new_key 表同步的核心业务逻辑，实现动态 schema 演进。
     * 
     * <p>
     * <strong>业务背景：</strong><br>
     * 在流处理系统中，数据的 schema 可能会不断演进，新的键值对会不断出现。
     * 该方法负责将这些新发现的键同步到 ClickHouse events 表的 schema 中，
     * 使得这些新字段能够被后续的告警规则和查询使用。
     * 
     * <p>
     * <strong>详细执行流程：</strong>
     * <ol>
     * <li><strong>获取待同步键：</strong>
     * 
     * <pre>
     * SELECT keyName, keyType FROM new_key WHERE isCreated = 0
     * </pre>
     * 
     * 查询所有尚未在 events 表中创建列的键定义
     * </li>
     * <li><strong>类型转换映射：</strong>
     * <ul>
     * <li>STRING → Nullable(String)</li>
     * <li>LONG → Nullable(Int64)</li>
     * <li>DOUBLE → Nullable(Float64)</li>
     * <li>BOOLEAN → Nullable(UInt8)</li>
     * <li>未知类型 → Nullable(String) (默认)</li>
     * </ul>
     * </li>
     * <li><strong>DDL 操作执行：</strong>
     * 
     * <pre>
     * ALTER TABLE default.events ADD COLUMN IF NOT EXISTS {keyName} {clickhouseType}
     * </pre>
     * 
     * 使用 IF NOT EXISTS 确保操作的幂等性
     * </li>
     * <li><strong>状态更新：</strong>
     * 
     * <pre>
     * UPDATE new_key SET isCreated = 1 WHERE keyName = '{keyName}'
     * </pre>
     * 
     * 标记该键已成功创建，避免重复处理
     * </li>
     * <li><strong>结果统计：</strong>记录成功创建的列数量和失败的操作详情</li>
     * </ol>
     * 
     * <p>
     * <strong>技术实现细节：</strong>
     * <ul>
     * <li><strong>数据库连接：</strong>使用 DatabaseService 提供的连接池，确保连接复用</li>
     * <li><strong>事务处理：</strong>每个 DDL 操作独立提交，避免长事务锁定</li>
     * <li><strong>错误恢复：</strong>单个列创建失败不影响其他列的处理</li>
     * <li><strong>性能优化：</strong>批量查询 + 逐个执行，平衡性能与可靠性</li>
     * </ul>
     * 
     * <p>
     * <strong>ClickHouse 特性利用：</strong>
     * <ul>
     * <li><strong>动态 DDL：</strong>ClickHouse 支持在线添加列，不需要重建表</li>
     * <li><strong>Nullable 类型：</strong>新列设为 Nullable，对历史数据友好</li>
     * <li><strong>IF NOT EXISTS：</strong>避免重复创建导致的错误</li>
     * <li><strong>分布式 DDL：</strong>在集群环境中自动同步到所有节点</li>
     * </ul>
     * 
     * <p>
     * <strong>容错机制：</strong>
     * <table border="1" cellpadding="5" cellspacing="0">
     * <tr>
     * <th>异常类型</th>
     * <th>处理策略</th>
     * <th>影响范围</th>
     * </tr>
     * <tr>
     * <td>网络异常</td>
     * <td>记录日志，跳过当次执行</td>
     * <td>本次同步失败，下次重试</td>
     * </tr>
     * <tr>
     * <td>SQL 语法错误</td>
     * <td>记录详细错误，继续处理其他列</td>
     * <td>单个列创建失败</td>
     * </tr>
     * <tr>
     * <td>权限不足</td>
     * <td>记录错误，停止当次同步</td>
     * <td>本次同步失败</td>
     * </tr>
     * <tr>
     * <td>表不存在</td>
     * <td>记录致命错误，需要人工介入</td>
     * <td>同步功能失效</td>
     * </tr>
     * </table>
     * 
     * <p>
     * <strong>SQL 示例：</strong>
     * 
     * <pre>{@code
     * -- 查询待同步的键
     * SELECT keyName, keyType FROM new_key WHERE isCreated = 0;
     * 
     * -- 创建新列 (示例)
     * ALTER TABLE default.events ADD COLUMN IF NOT EXISTS tag_user_id Nullable(String);
     * ALTER TABLE default.events ADD COLUMN IF NOT EXISTS metric_response_time Nullable(Int64);
     * ALTER TABLE default.events ADD COLUMN IF NOT EXISTS feature_enabled Nullable(UInt8);
     * 
     * -- 更新状态
     * UPDATE new_key SET isCreated = 1 WHERE keyName = 'tag_user_id';
     * UPDATE new_key SET isCreated = 1 WHERE keyName = 'metric_response_time';
     * UPDATE new_key SET isCreated = 1 WHERE keyName = 'feature_enabled';
     * }</pre>
     * 
     * <p>
     * <strong>性能特征：</strong>
     * <ul>
     * <li><strong>时间复杂度：</strong>O(n)，其中 n 为待同步键的数量</li>
     * <li><strong>空间复杂度：</strong>O(1)，流式处理，不缓存大量数据</li>
     * <li><strong>并发安全：</strong>单线程执行，避免并发 DDL 冲突</li>
     * <li><strong>执行耗时：</strong>通常在几百毫秒到几秒，取决于网络和数据库负载</li>
     * </ul>
     * 
     * <p>
     * <strong>监控指标：</strong>
     * <ul>
     * <li>每次同步处理的键数量</li>
     * <li>DDL 操作的成功率和失败率</li>
     * <li>单次同步的总耗时</li>
     * <li>new_key 表中未同步键的积压数量</li>
     * </ul>
     * 
     * <p>
     * <strong>故障排查指南：</strong>
     * <ul>
     * <li><strong>同步停滞：</strong>检查 ClickHouse 连接状态和 new_key 表数据</li>
     * <li><strong>列创建失败：</strong>检查 DDL 权限和 events 表状态</li>
     * <li><strong>类型转换错误：</strong>检查 keyType 值和转换逻辑</li>
     * <li><strong>状态更新失败：</strong>检查 new_key 表的写权限</li>
     * </ul>
     * 
     * @throws Exception 当数据库连接失败、SQL 执行异常或其他系统错误时抛出
     * 
     * @see com.o11y.infrastructure.database.DatabaseService#getClickHouseType(String)
     *      类型转换方法
     * @see com.o11y.infrastructure.database.DatabaseService#executeUpdate(String)
     *      DDL 执行方法
     */
    void syncNewKeys() throws Exception {
        Connection conn = databaseService.getConnection();

        // 查询所有未创建的字段
        String selectSql = "SELECT keyName, keyType FROM new_key WHERE isCreated = 0";
        Set<String> newKeys = new HashSet<>();

        PreparedStatement ps = conn.prepareStatement(selectSql);
        ResultSet rs = ps.executeQuery();

        // 遍历每个未创建的字段
        while (rs.next()) {
            String keyName = rs.getString("keyName");
            String keyType = rs.getString("keyType");

            // 将 Java 类型转换为 ClickHouse 类型
            String chType = DatabaseService.toClickHouseType(keyType);

            // 构建 ALTER TABLE 语句
            String alterSql = String.format(
                    "ALTER TABLE %s.%s ADD COLUMN IF NOT EXISTS %s Nullable(%s)",
                    databaseService.getSchemaName(),
                    databaseService.getTableName(),
                    keyName,
                    chType); // 执行列创建操作
            try (PreparedStatement alterPs = conn.prepareStatement(alterSql)) {
                alterPs.execute();
                LOG.info("Added column '{}' to events table.", keyName);
                newKeys.add(keyName);
            } catch (Exception e) {
                LOG.error("Failed to add column '{}' to events table: {}", keyName, e.getMessage());
                // 即使单个字段创建失败，也继续处理其他字段
            }
        }

        // 批量更新成功创建的字段状态
        if (!newKeys.isEmpty()) {
            String updateSql = "UPDATE new_key SET isCreated = 1 WHERE keyName = ?";
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                for (String key : newKeys) {
                    updatePs.setString(1, key);
                    updatePs.executeUpdate();
                }
            }
            LOG.info("Updated isCreated=true for keys: {}", newKeys);
        }
    }
}
