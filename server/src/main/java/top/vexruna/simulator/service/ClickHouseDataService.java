package top.vexruna.simulator.service;

import com.clickhouse.jdbc.ClickHouseDataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import top.vexruna.simulator.dto.DeviceData;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickHouseDataService {

    @Qualifier("clickHouseDataSource")
    private final ClickHouseDataSource dataSource;

    private volatile boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            log.info("[ClickHouse] 初始化连接...");
            createTableIfNotExists();
            initialized = true;
            log.info("[ClickHouse] 初始化完成");
        } catch (Exception e) {
            log.warn("[ClickHouse] 初始化失败: {}，将使用降级模式", e.getMessage());
            log.warn("[ClickHouse] 请确保 ClickHouse 服务已启动并检查配置");
        }
    }

    private void createTableIfNotExists() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS device_data (
                device_id String,
                timestamp DateTime64(3),
                temperature Float64,
                humidity Float64,
                voltage Float64,
                status String,
                error Nullable(String),
                topic String,
                qos UInt8,
                created_at DateTime DEFAULT now()
            ) ENGINE = MergeTree()
            ORDER BY (device_id, timestamp)
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            log.info("[ClickHouse] 表 device_data 创建成功");
        }
    }

    public void saveDeviceData(DeviceData data) {
        if (!initialized || dataSource == null) {
            log.debug("[ClickHouse] 未初始化，跳过存储 - DeviceId: {}", data.getDeviceId());
            return;
        }

        String insertSQL = """
            INSERT INTO device_data
            (device_id, timestamp, temperature, humidity, voltage, status, error, topic, qos)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, data.getDeviceId());
            pstmt.setTimestamp(2, new Timestamp(data.getTimestamp() != null ? data.getTimestamp() : System.currentTimeMillis()));
            pstmt.setDouble(3, data.getTemperature() != null ? data.getTemperature() : 0.0);
            pstmt.setDouble(4, data.getHumidity() != null ? data.getHumidity() : 0.0);
            pstmt.setDouble(5, data.getVoltage() != null ? data.getVoltage() : 0.0);
            pstmt.setString(6, data.getStatus());
            pstmt.setString(7, data.getError());
            pstmt.setString(8, data.getTopic());
            pstmt.setInt(9, data.getQos() != null ? data.getQos() : 0);

            pstmt.executeUpdate();
            log.info("[ClickHouse] 数据保存成功: {}", data.getDeviceId());

        } catch (SQLException e) {
            log.error("[ClickHouse] 数据保存失败: {}", e.getMessage());
        }
    }

    public DeviceData getLatestData(String deviceId) {
        String querySQL = """
            SELECT * FROM device_data
            WHERE device_id = ?
            ORDER BY timestamp DESC
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                DeviceData data = new DeviceData();
                data.setDeviceId(rs.getString("device_id"));
                data.setTimestamp(rs.getTimestamp("timestamp").getTime());
                data.setTemperature(rs.getDouble("temperature"));
                data.setHumidity(rs.getDouble("humidity"));
                data.setVoltage(rs.getDouble("voltage"));
                data.setStatus(rs.getString("status"));
                data.setError(rs.getString("error"));
                return data;
            }

        } catch (SQLException e) {
            log.error("[ClickHouse] 查询失败: {}", e.getMessage());
        }

        return null;
    }

    public List<DeviceData> getHistoryData(String deviceId, Long startTime, Long endTime, int limit) {
        List<DeviceData> result = new ArrayList<>();
        if (!initialized || dataSource == null) {
            return result;
        }

        String sql = """
            SELECT * FROM device_data
            WHERE device_id = ?
            AND timestamp >= ?
            AND timestamp <= ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, deviceId);
            pstmt.setTimestamp(2, new Timestamp(startTime != null ? startTime : 0L));
            pstmt.setTimestamp(3, new Timestamp(endTime != null ? endTime : System.currentTimeMillis()));
            pstmt.setInt(4, limit > 0 ? limit : 100);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                result.add(mapRowToDeviceData(rs));
            }
        } catch (SQLException e) {
            log.error("[ClickHouse] 查询历史数据失败: {}", e.getMessage());
        }
        return result;
    }

    public List<DeviceData> getLatestAllDevicesData() {
        List<DeviceData> result = new ArrayList<>();
        if (!initialized || dataSource == null) {
            return result;
        }

        String sql = """
            SELECT d.* FROM device_data d
            INNER JOIN (
                SELECT device_id, MAX(timestamp) AS max_ts
                FROM device_data
                GROUP BY device_id
            ) latest ON d.device_id = latest.device_id AND d.timestamp = latest.max_ts
            ORDER BY d.device_id
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                result.add(mapRowToDeviceData(rs));
            }
        } catch (SQLException e) {
            log.error("[ClickHouse] 查询所有设备最新数据失败: {}", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        if (!initialized || dataSource == null) {
            stats.put("totalCount", 0L);
            stats.put("onlineDevices", 0L);
            stats.put("dataRate", 0.0);
            return stats;
        }

        String countSql = "SELECT count(*) AS cnt FROM device_data";
        String deviceCountSql = "SELECT count(DISTINCT device_id) AS cnt FROM device_data WHERE timestamp >= ?";
        String rateSql = "SELECT count(*) AS cnt FROM device_data WHERE timestamp >= ?";

        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                stats.put("totalCount", rs.next() ? rs.getLong("cnt") : 0L);
            }

            long fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000;
            try (PreparedStatement pstmt = conn.prepareStatement(deviceCountSql)) {
                pstmt.setTimestamp(1, new Timestamp(fiveMinutesAgo));
                ResultSet rs = pstmt.executeQuery();
                stats.put("onlineDevices", rs.next() ? rs.getLong("cnt") : 0L);
            }

            try (PreparedStatement pstmt = conn.prepareStatement(rateSql)) {
                pstmt.setTimestamp(1, new Timestamp(System.currentTimeMillis() - 60_000));
                ResultSet rs = pstmt.executeQuery();
                long recentCount = rs.next() ? rs.getLong("cnt") : 0L;
                stats.put("dataRate", Math.round(recentCount / 60.0 * 100.0) / 100.0);
            }
        } catch (SQLException e) {
            log.error("[ClickHouse] 查询统计数据失败: {}", e.getMessage());
        }
        return stats;
    }

    private DeviceData mapRowToDeviceData(ResultSet rs) throws SQLException {
        DeviceData data = new DeviceData();
        data.setDeviceId(rs.getString("device_id"));
        data.setTimestamp(rs.getTimestamp("timestamp").getTime());
        data.setTemperature(rs.getDouble("temperature"));
        data.setHumidity(rs.getDouble("humidity"));
        data.setVoltage(rs.getDouble("voltage"));
        data.setStatus(rs.getString("status"));
        data.setError(rs.getString("error"));
        return data;
    }
}