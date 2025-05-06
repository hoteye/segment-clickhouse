CREATE TABLE events (
    trace_id String,               -- 全局唯一的 Trace ID
    trace_segment_id String,       -- 当前 Segment 的 ID
    service Nullable(String),                -- 服务名称
    service_instance Nullable(String),       -- 服务实例名称
    is_size_limited Nullable(UInt8),         -- 是否大小受限 (0 或 1)
    span_id Int32,                 -- Span 的 ID
    parent_span_id Int32,          -- 父 Span 的 ID
    start_time DateTime64(3),      -- Span 开始时间，精确到毫秒
    end_time DateTime64(3),        -- Span 结束时间，精确到毫秒
    operation_name Nullable(String),         -- 操作名称
    peer Nullable(String),                   -- 远程地址
    span_type Nullable(String),              -- Span 类型 (Entry, Exit, Local)
    span_layer Nullable(String),             -- Span 层 (Http, Database, RPCFramework, etc.)
    component_id Nullable(Int32),            -- 组件 ID
    is_error Nullable(UInt8),                -- 是否为错误 Span (0 或 1)
    skip_analysis Nullable(UInt8),           -- 是否跳过分析 (0 或 1)
    refs_ref_type Nullable(String),          -- 引用类型 (CrossProcess, CrossThread)
    refs_trace_id Nullable(String),          -- 引用的 Trace ID
    refs_parent_trace_segment_id Nullable(String), -- 父 Segment ID
    refs_parent_span_id Nullable(Int32),     -- 父 Span ID
    refs_parent_service Nullable(String),    -- 父服务名称
    refs_parent_service_instance Nullable(String), -- 父服务实例名称
    refs_parent_endpoint Nullable(String),   -- 父端点名称
    refs_network_address_used_at_peer Nullable(String), -- 网络地址
--以下是动态字段
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