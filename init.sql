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
-- The following are dynamic fields
    tag_status_code Nullable(String),
    log_stack Nullable(String),
    tag_Available_Memory Nullable(String),
    tag_http_status_code Nullable(String),
    tag_http_url Nullable(String),
    tag_Processor_Name Nullable(String),
    log_forward_url Nullable(String),
    tag_http_method Nullable(String),
    tag_Total_Memory Nullable(String),
    log_event Nullable(String),
    log_message Nullable(String),
    tag_url Nullable(String),
    log_error_kind Nullable(String)
) ENGINE = MergeTree()
ORDER BY (start_time, trace_id);

CREATE TABLE new_key
(
    keyName String,
    keyType String,
    isCreated UInt8,
    createTime DateTime
)
ENGINE = MergeTree()
ORDER BY keyName;

CREATE TABLE IF NOT EXISTS param_config (
    namespace String,
    operatorClass String,
    paramKey String,
    paramValue String
) ENGINE = MergeTree()
ORDER BY (namespace, operatorClass, paramKey);

CREATE TABLE IF NOT EXISTS flink_operator_agg_result (
    window_start  Int64,          -- 窗口起始时间
    window_end    Int64,          -- 窗口结束时间
    operator_name Nullable(String),         -- 算子类名
    service       Nullable(String),         -- 服务名
    instance      Nullable(String),         -- 实例名
    method        Nullable(String),         -- 方法名
    avg_duration  Nullable(Float64),        -- 平均耗时
    max_duration  Nullable(Int64),          -- 最大耗时
    error_rate    Nullable(Float64),        -- 错误率
    data_center    Nullable(String),         -- 数据中心
    region        Nullable(String),         -- 区域
    env           Nullable(String),         -- 环境
    total_count  Nullable(Int64),          -- 总调用次数
    error_count  Nullable(Int64),          -- 错误调用次数
    success_count Nullable(Int64),          -- 成功调用次数
) ENGINE = MergeTree()
ORDER BY (window_start);