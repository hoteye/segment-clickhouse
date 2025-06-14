package com.o11y.flink.operator.model;

import org.apache.flink.streaming.api.datastream.DataStream;

/**
 * 聚合结果和告警流的封装
 */
public class ServiceAggAndAlarm {
    public final DataStream<ServiceAggResult> aggStream;
    public final DataStream<String> alarmStream;

    public ServiceAggAndAlarm(DataStream<ServiceAggResult> aggStream,
            DataStream<String> alarmStream) {
        this.aggStream = aggStream;
        this.alarmStream = alarmStream;
    }
}
