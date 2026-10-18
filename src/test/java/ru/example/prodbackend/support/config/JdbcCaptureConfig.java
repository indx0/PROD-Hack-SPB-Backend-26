package ru.example.prodbackend.support.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import lombok.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Records actual JDBC executions, including batch sizes, only on the requesting test thread. */
@TestConfiguration(proxyBeanMethods = false)
public class JdbcCaptureConfig {

    @Bean
    static JdbcCapture jdbcCapture() {
        return new JdbcCapture();
    }

    public static class JdbcCapture implements BeanPostProcessor {
        private final ThreadLocal<List<Execution>> executions = new ThreadLocal<>();

        public void start() {
            executions.set(new ArrayList<>());
        }

        public List<Execution> stop() {
            List<Execution> result = executions.get();
            executions.remove();
            return result == null ? List.of() : List.copyOf(result);
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof DataSource dataSource)) {
                return bean;
            }
            return new DelegatingDataSource(dataSource) {
                @Override
                public Connection getConnection() throws SQLException {
                    return wrapConnection(super.getConnection());
                }

                @Override
                public Connection getConnection(String username, String password) throws SQLException {
                    return wrapConnection(super.getConnection(username, password));
                }
            };
        }

        private Connection wrapConnection(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        Object result = invoke(connection, method, args);
                        if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement statement) {
                            return wrapStatement(statement, (String) args[0]);
                        }
                        return result;
                    });
        }

        private PreparedStatement wrapStatement(PreparedStatement statement, String sql) {
            int[] batchSize = {0};
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("executeBatch") || name.equals("executeLargeBatch")) {
                            record(sql, batchSize[0]);
                            batchSize[0] = 0;
                        } else if (name.equals("execute") || name.equals("executeQuery")
                                || name.equals("executeUpdate") || name.equals("executeLargeUpdate")) {
                            record(sql, 0);
                        }
                        Object result = invoke(statement, method, args);
                        if (name.equals("addBatch")) {
                            batchSize[0]++;
                        } else if (name.equals("clearBatch")) {
                            batchSize[0] = 0;
                        }
                        return result;
                    });
        }

        private void record(String sql, int batchSize) {
            List<Execution> current = executions.get();
            if (current != null) {
                current.add(new Execution(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip(), batchSize));
            }
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }

    @Value
    public static class Execution {

        private final String sql;

        private final int batchSize;
    }
}
