package com.o11y.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * ClickHouse 数据库配置
 * 
 * 由于 ClickHouse 对 JPA/Hibernate 支持有限，我们使用 JdbcTemplate 进行数据访问
 */
@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.connection.url}")
    private String url;

    @Value("${clickhouse.connection.username}")
    private String username;

    @Value("${clickhouse.connection.password}")
    private String password;

    @Value("${clickhouse.connection.socket-timeout:60000}")
    private int socketTimeout;

    @Value("${clickhouse.connection.connection-timeout:20000}")
    private int connectionTimeout;

    /**
     * ClickHouse 数据源配置
     */
    @Bean
    public DataSource clickHouseDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        // ClickHouse 连接属性
        java.util.Properties props = new java.util.Properties();
        props.setProperty("socket_timeout", String.valueOf(socketTimeout));
        props.setProperty("connection_timeout", String.valueOf(connectionTimeout));
        props.setProperty("compress", "true");
        props.setProperty("decompress", "true");
        props.setProperty("use_server_time_zone", "true");
        props.setProperty("use_time_zone", "UTC+8");
        props.setProperty("max_execution_time", "60"); // 查询超时时间（秒）
        props.setProperty("connect_timeout", String.valueOf(connectionTimeout));
        props.setProperty("max_total_rows_to_read", "1000000");
        props.setProperty("max_result_rows", "100000");
        dataSource.setConnectionProperties(props);

        return dataSource;
    }

    /**
     * ClickHouse JdbcTemplate
     */
    @Bean
    public JdbcTemplate clickHouseJdbcTemplate() {
        JdbcTemplate template = new JdbcTemplate(clickHouseDataSource());
        template.setQueryTimeout(60); // 设置查询超时时间为60秒
        return template;
    }
}
