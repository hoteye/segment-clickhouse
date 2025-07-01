-- 集成测试初始化脚本
-- 创建测试所需的表结构

-- 聚合结果表
CREATE TABLE IF NOT EXISTS flink_operator_agg_result (
    window_start DateTime64(3),
    windowSize Int32,
    operator_class String,
    operator_name String,
    service String,
    avg_duration Float64,
    max_duration Int64,
    error_rate Float64,
    total_count Int64,
    error_count Int64,
    success_count Int64
) ENGINE = MergeTree()
ORDER BY window_start;

-- 小时级规则表
CREATE TABLE IF NOT EXISTS hourly_alarm_rules (
    hour_of_day UInt8,
    service String,
    operator_name String,
    operator_class String,
    avg_duration_low Float64,
    avg_duration_mid Float64,
    avg_duration_high Float64,
    max_duration_low Float64,
    max_duration_mid Float64,
    max_duration_high Float64,
    success_rate_low Float64,
    success_rate_mid Float64,
    success_rate_high Float64,
    traffic_volume_low Float64,
    traffic_volume_mid Float64,
    traffic_volume_high Float64,
    alarm_template String,
    analysis_days UInt8,
    sample_count UInt32,
    generated_time DateTime,
    last_updated DateTime,
    version UInt32,
    PRIMARY KEY (hour_of_day, service, operator_name)
) ENGINE = MergeTree()
ORDER BY (hour_of_day, service, operator_name); 