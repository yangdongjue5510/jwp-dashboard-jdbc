package nextstep.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import nextstep.jdbc.jdbcparam.JdbcParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger(JdbcTemplate.class);
    private static final int INITIAL_PARAM_INDEX = 1;
    private static final int SINGLE_RESULT_INDEX = 0;
    private static final int SINGLE_RESULT_SIZE = 1;

    private final DataSource dataSource;

    public JdbcTemplate(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void update(final String sql, final Object... params) {
        runContext(sql, statement -> {
            setParams(statement, params);
            return statement.executeUpdate();
        });
    }

    private Object runContext(final String sql, JdbcAction action) {
        try (final Connection connection = dataSource.getConnection();
             final PreparedStatement statement = connection.prepareStatement(sql)) {
            log.debug("query : {}", sql);
            return action.doAction(statement);
        } catch (SQLException exception) {
            log.warn("SQL Exception alert!!! : {}", exception.getMessage(), exception);
            return Optional.empty();
        }
    }

    private void setParams(final PreparedStatement statement, final Object... params) throws SQLException {
        int paramIndex = INITIAL_PARAM_INDEX;
        for (Object param : params) {
            JdbcParamType.setParam(statement, paramIndex, param);
            paramIndex++;
        }
    }

    public <T> List<T> find(final String sql, RowMapper<T> rowMapper, Object... params) {
        return (List<T>) runContext(sql, statement -> {
            setParams(statement, params);
            final ResultSet resultSet = statement.executeQuery();
            return mapWithMapper(resultSet, rowMapper);
        });
    }

    private <T> List<T> mapWithMapper(final ResultSet resultSet, final RowMapper<T> rowMapper) throws SQLException {
        List<T> result = new ArrayList<>();
        while (resultSet.next()) {
            final T mappedValue = rowMapper.mapRow(resultSet);
            result.add(mappedValue);
        }
        resultSet.close();
        return result;
    }

    public <T> T findSingleResult(final String sql, RowMapper<T> rowMapper, Object... params) {
        final List<T> results = find(sql, rowMapper, params);
        validateSingleResult(results);
        return results.get(SINGLE_RESULT_INDEX);
    }

    private <T> void validateSingleResult(final List<T> results) {
        final int size = results.size();
        if (size != SINGLE_RESULT_SIZE) {
            log.warn("결과 값이 한 개가 아닙니다. 결과 값 = {}", size);
            throw new RuntimeException();
        }
    }
}
