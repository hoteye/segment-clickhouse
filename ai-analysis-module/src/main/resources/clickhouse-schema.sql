-- AI 分析模块相关的 ClickHouse 表结构

-- 创建 AI 性能分析报告表
CREATE TABLE IF NOT EXISTS ai_performance_reports (
    report_id String,
    content String,
    created_time DateTime,
    status String,
    analysis_window_start DateTime,
    analysis_window_end DateTime,
    service_name String,
    report_type String DEFAULT 'PERFORMANCE',
    metadata String DEFAULT '{}',
    PRIMARY KEY (report_id)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_time)
ORDER BY (created_time, report_id)
TTL created_time + INTERVAL 90 DAY;

-- 创建异常分析报告表
CREATE TABLE IF NOT EXISTS ai_anomaly_reports (
    anomaly_id String,
    report_id String,
    service_name String,
    anomaly_type String,
    severity String,
    detected_time DateTime,
    metric_name String,
    actual_value Float64,
    expected_value Float64,
    deviation_percentage Float64,
    description String,
    recommended_action String,
    status String DEFAULT 'DETECTED',
    PRIMARY KEY (anomaly_id)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(detected_time)
ORDER BY (detected_time, service_name, anomaly_type)
TTL detected_time + INTERVAL 30 DAY;

-- 创建优化建议表
CREATE TABLE IF NOT EXISTS ai_optimization_suggestions (
    suggestion_id String,
    report_id String,
    category String,
    title String,
    description String,
    priority String,
    impact_level String,
    implementation_complexity String,
    action_plan String,
    expected_benefit String,
    confidence_score Float64,
    created_time DateTime,
    status String DEFAULT 'PENDING',
    tags String DEFAULT '',
    service_name String DEFAULT '',
    PRIMARY KEY (suggestion_id)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_time)
ORDER BY (created_time, priority, category)
TTL created_time + INTERVAL 60 DAY;

-- 创建 AI 分析任务记录表
CREATE TABLE IF NOT EXISTS ai_analysis_tasks (
    task_id String,
    task_type String,
    status String,
    start_time DateTime,
    end_time DateTime DEFAULT toDateTime(0),
    duration_ms UInt64 DEFAULT 0,
    input_params String DEFAULT '{}',
    output_summary String DEFAULT '',
    error_message String DEFAULT '',
    PRIMARY KEY (task_id)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(start_time)
ORDER BY (start_time, task_type)
TTL start_time + INTERVAL 30 DAY;

-- 创建分析配置表
CREATE TABLE IF NOT EXISTS ai_analysis_config (
    config_key String,
    config_value String,
    config_type String DEFAULT 'STRING',
    description String DEFAULT '',
    updated_time DateTime DEFAULT now(),
    is_active UInt8 DEFAULT 1,
    PRIMARY KEY (config_key)
) ENGINE = ReplacingMergeTree(updated_time)
ORDER BY config_key;

-- 插入默认配置
INSERT INTO ai_analysis_config (config_key, config_value, config_type, description) VALUES
('analysis.window.default_hours', '1', 'INTEGER', '默认分析时间窗口（小时）'),
('analysis.thresholds.response_time_ms', '1000', 'INTEGER', '响应时间阈值（毫秒）'),
('analysis.thresholds.error_rate_percent', '5.0', 'FLOAT', '错误率阈值（百分比）'),
('analysis.thresholds.cpu_usage_percent', '80.0', 'FLOAT', 'CPU使用率阈值（百分比）'),
('analysis.thresholds.memory_usage_percent', '85.0', 'FLOAT', '内存使用率阈值（百分比）'),
('llm.provider', 'openai', 'STRING', 'LLM 提供商'),
('llm.model', 'gpt-3.5-turbo', 'STRING', 'LLM 模型名称'),
('llm.temperature', '0.7', 'FLOAT', 'LLM 温度参数'),
('report.retention_days', '30', 'INTEGER', '报告保留天数');
