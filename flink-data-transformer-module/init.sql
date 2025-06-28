CREATE TABLE events (
    trace_id String,               -- Globally unique Trace ID
    trace_segment_id String,       -- Current Segment ID
    service Nullable(String),                -- Service name
    service_instance Nullable(String),       -- Service instance name
    is_size_limited Nullable(UInt8),         -- Whether size is limited (0 or 1)
    span_id Int32,                 -- Span ID
    parent_span_id Int32,          -- Parent Span ID
    start_time DateTime64(3),      -- Span start time, accurate to milliseconds
    end_time DateTime64(3),        -- Span end time, accurate to milliseconds
    operation_name Nullable(String),         -- Operation name
    peer Nullable(String),                   -- Remote address
    span_type Nullable(String),              -- Span type (Entry, Exit, Local)
    span_layer Nullable(String),             -- Span layer (Http, Database, RPCFramework, etc.)
    component_id Nullable(Int32),            -- Component ID
    is_error Nullable(UInt8),                -- Whether it is an error Span (0 or 1)
    skip_analysis Nullable(UInt8),           -- Whether to skip analysis (0 or 1)
    refs_ref_type Nullable(String),          -- Reference type (CrossProcess, CrossThread)
    refs_trace_id Nullable(String),          -- Referenced Trace ID
    refs_parent_trace_segment_id Nullable(String), -- Parent Segment ID
    refs_parent_span_id Nullable(Int32),     -- Parent Span ID
    refs_parent_service Nullable(String),    -- Parent service name
    refs_parent_service_instance Nullable(String), -- Parent service instance name
    refs_parent_endpoint Nullable(String),   -- Parent endpoint name
    refs_network_address_used_at_peer Nullable(String), -- Network address
    tag_status_code Nullable(String),
    tag_method_access_count_type_int64 Nullable(Int64),
    log_error_kind Nullable(String),
    log_event Nullable(String),
    log_forward_url Nullable(String),
    log_message Nullable(String),
    log_stack Nullable(String),
    tag_Available_Memory_type_Int64 Nullable(Int64),
    tag_Processor_Name Nullable(String),
    tag_Total_Memory_type_Int64 Nullable(Int64),
    tag_application_name Nullable(String),
    tag_db_instance Nullable(String),
    tag_db_statement Nullable(String),
    tag_db_type Nullable(String),
    tag_dc Nullable(String),
    tag_dubbo_local_host Nullable(String),
    tag_dubbo_remote_host Nullable(String),
    tag_dubbo_remote_port Nullable(String),
    tag_env_APP_VERSION Nullable(String),
    tag_env_HOME Nullable(String),
    tag_env_HOSTNAME Nullable(String),
    tag_env_JAVA_HOME Nullable(String),
    tag_env_JAVA_VERSION Nullable(String),
    tag_env_LANG Nullable(String),
    tag_env_PATH Nullable(String),
    tag_env_TZ Nullable(String),
    tag_error_context Nullable(String),
    tag_error_message Nullable(String),
    tag_error_method Nullable(String),
    tag_error_parameters Nullable(String),
    tag_error_stack_trace Nullable(String),
    tag_error_type Nullable(String),
    tag_http_method Nullable(String),
    tag_http_status_code Nullable(String),
    tag_http_url Nullable(String),
    tag_jvm_heap_committed_type_Int64 Nullable(Int64),
    tag_jvm_heap_init_type_Int64 Nullable(Int64),
    tag_jvm_heap_max_type_Int64 Nullable(Int64),
    tag_jvm_heap_used_type_Int64 Nullable(Int64),
    tag_jvm_name Nullable(String),
    tag_jvm_nonheap_committed_type_Int64 Nullable(Int64),
    tag_jvm_nonheap_init_type_Int64 Nullable(Int64),
    tag_jvm_nonheap_max_type_Int64 Nullable(Int64),
    tag_jvm_nonheap_used_type_Int64 Nullable(Int64),
    tag_jvm_start_time Nullable(String),
    tag_jvm_uptime Nullable(String),
    tag_jvm_vendor Nullable(String),
    tag_jvm_version Nullable(String),
    tag_methodName Nullable(String),
    tag_method_result Nullable(String),
    tag_os_arch Nullable(String),
    tag_os_name Nullable(String),
    tag_os_version Nullable(String),
    tag_rpc_context_input Nullable(String),
    tag_rpc_context_remote_application Nullable(String),
    tag_rpc_context_sw8 Nullable(String),
    tag_rpc_context_sw8_correlation Nullable(String),
    tag_rpc_context_sw8_x Nullable(String),
    tag_rpc_method_name Nullable(String),
    tag_rpc_object_attachment_input Nullable(String),
    tag_rpc_object_attachment_remote_application Nullable(String),
    tag_rpc_object_attachment_sw8 Nullable(String),
    tag_rpc_object_attachment_sw8_correlation Nullable(String),
    tag_rpc_object_attachment_sw8_x Nullable(String),
    tag_rpc_protocol Nullable(String),
    tag_rpc_role Nullable(String),
    tag_rpc_service_interface Nullable(String),
    tag_rpc_service_url Nullable(String),
    tag_sys Nullable(String),
    tag_thread_count_type_Int64 Nullable(Int64),
    tag_thread_current_cpu_time_type_Int64 Nullable(Int64),
    tag_thread_current_user_time_type_Int64 Nullable(Int64),
    tag_thread_daemon_count_type_Int64 Nullable(Int64),
    tag_thread_peak_count_type_Int64 Nullable(Int64),
    tag_thread_total_started_count_type_Int64 Nullable(Int64),
    tag_url Nullable(String)
) ENGINE = MergeTree()
PARTITION BY toDate(start_time)  -- 按start_time分区
ORDER BY (start_time, trace_id); -- 主排序键：开始时间+追踪ID

CREATE TABLE new_key
(
    keyName String,
    keyType String,
    isCreated Boolean,
    createTime DateTime
)
ENGINE = MergeTree()
ORDER BY keyName;

CREATE TABLE IF NOT EXISTS param_config (
    namespace String,
    operatorClass String,
    paramKey String,
    paramValue String,
    createTime DateTime
) ENGINE = MergeTree()
ORDER BY (namespace, operatorClass, paramKey);
INSERT INTO param_config VALUES ('flinkParam', 'AggregateOperator', 'windowSize', '22', now());


CREATE TABLE IF NOT EXISTS flink_operator_agg_result (
    window_start  DateTime64(3),          -- 窗口起始时间
    windowSize    Int32,                  -- 窗口大小（秒）
    operator_class String,                -- 算子类全名
    operator_name Nullable(String),       -- 算子类名
    service       Nullable(String),       -- 服务名
    instance      Nullable(String),       -- 实例名
    method        Nullable(String),       -- 方法名
    avg_duration  Nullable(Float64),      -- 平均耗时
    max_duration  Nullable(Int64),        -- 最大耗时
    error_rate    Nullable(Float64),      -- 错误率
    data_center   Nullable(String),       -- 数据中心
    region        Nullable(String),       -- 区域
    env           Nullable(String),       -- 环境
    total_count   Nullable(Int64),        -- 总调用次数
    error_count   Nullable(Int64),        -- 错误调用次数
    success_count Nullable(Int64)         -- 成功调用次数
) ENGINE = MergeTree()
ORDER BY (window_start);

-- 小时级动态阈值规则表
-- 新设计：每个规则一条记录，不使用JSON存储
-- 主键：(hour_of_day, service, operator_name)
CREATE TABLE IF NOT EXISTS hourly_alarm_rules (
    hour_of_day UInt8,                    -- 小时序号 (0-23)
    service String,                       -- 服务名
    operator_name String,                 -- 操作员名
    operator_class String,                -- 操作员类
    
    -- 阈值字段（参考DynamicThresholdGenerator）
    avg_duration_low Float64,             -- 平均延迟低阈值
    avg_duration_mid Float64,             -- 平均延迟中阈值
    avg_duration_high Float64,            -- 平均延迟高阈值
    max_duration_low Float64,             -- 最大延迟低阈值
    max_duration_mid Float64,             -- 最大延迟中阈值
    max_duration_high Float64,            -- 最大延迟高阈值
    success_rate_low Float64,             -- 成功率低阈值
    success_rate_mid Float64,             -- 成功率中阈值
    success_rate_high Float64,            -- 成功率高阈值
    traffic_volume_low Float64,           -- 交易量低阈值
    traffic_volume_mid Float64,           -- 交易量中阈值
    traffic_volume_high Float64,          -- 交易量高阈值
    alarm_template String,                -- 告警模板
    
    -- 数据来源信息
    analysis_days UInt8,                  -- 分析的历史天数
    sample_count UInt32,                  -- 该规则的样本数量
    
    -- 元数据
    generated_time DateTime DEFAULT now(), -- 规则生成时间
    last_updated DateTime DEFAULT now(),   -- 最后更新时间
    version UInt32 DEFAULT 1,             -- 规则版本号
    
    PRIMARY KEY (hour_of_day, service, operator_name)
) ENGINE = MergeTree()
ORDER BY (hour_of_day, service, operator_name);
