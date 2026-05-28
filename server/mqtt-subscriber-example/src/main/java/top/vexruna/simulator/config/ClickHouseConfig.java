package top.vexruna.simulator.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;
import java.util.Properties;

/**
 * ClickHouse 数据源配置（时序数据）
 */
@Data
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "spring.datasource.clickhouse")
public class ClickHouseConfig {

    private String url;
    private String username;
    private String password;
    private String driverClassName;

    @Bean(name = "clickHouseDataSource")
    public ClickHouseDataSource clickHouseDataSource() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password != null ? password : "");
        return new ClickHouseDataSource(url, properties);
    }
}
