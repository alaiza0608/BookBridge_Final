package dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class ConnectionFactory {
    private final String url;
    private final String user;
    private final String password;

    public ConnectionFactory() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("db.properties")) {
            props.load(fis);
        } catch (IOException ignored) {}
        this.url = props.getProperty("db.url", "jdbc:sqlite:bookbridge.db");
        this.user = props.getProperty("db.user", "");
        this.password = props.getProperty("db.password", "");
    }

    public Connection getConnection() throws SQLException {
        Connection conn = user.isEmpty()
                ? DriverManager.getConnection(url)
                : DriverManager.getConnection(url, user, password);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException ignored) {}
        return conn;
    }

    public void initializeSchemaFromFile(String schemaPath) throws SQLException, IOException {
        String sql = Files.readString(Path.of(schemaPath));
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
        }
    }
}