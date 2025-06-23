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
        props.setProperty("socket_timeout", "30000");
        props.setProperty("connection_timeout", "10000");
        props.setProperty("compress", "true");
        props.setProperty("decompress", "true");
        dataSource.setConnectionProperties(props);

        return dataSource;
    }

    /**
     * ClickHouse JdbcTemplate
     */
    @Bean
    public JdbcTemplate clickHouseJdbcTemplate() {
        return new JdbcTemplate(clickHouseDataSource());
    }
}
