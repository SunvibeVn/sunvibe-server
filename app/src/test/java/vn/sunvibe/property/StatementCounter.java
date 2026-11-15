package vn.sunvibe.property;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Counts the JDBC statements a piece of code executes on the calling thread (E05-S6b SR-4, plan E05S6b-P4): every
 * {@code execute*} call of a {@link Statement}, {@link PreparedStatement} or {@link CallableStatement}, from JPA and
 * {@code JdbcTemplate} alike. Statements of other threads (scheduled jobs of the context) never count. Test support only:
 * {@link Config} wraps the context's {@link DataSource}.
 */
final class StatementCounter {

	private static final ThreadLocal<long[]> COUNTING = new ThreadLocal<>();

	/** Runs the action on this thread and answers how many statements it executed. */
	long count(Runnable action) {
		long[] counted = new long[1];
		COUNTING.set(counted);
		try {
			action.run();
		} finally {
			COUNTING.remove();
		}
		return counted[0];
	}

	private static void executed() {
		long[] counted = COUNTING.get();
		if (counted != null) {
			counted[0]++;
		}
	}

	/** The counter and the wrapper of the context's DataSource; a context that imports it is a context of its own. */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		StatementCounter statementCounter() {
			return new StatementCounter();
		}

		@Bean
		static BeanPostProcessor statementCountingDataSource() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					return bean instanceof DataSource dataSource && !(bean instanceof CountingDataSource)
							? new CountingDataSource(dataSource) : bean;
				}
			};
		}
	}

	/** Hands out connections whose statements count; every other call goes to the pool's connection. */
	static final class CountingDataSource extends DelegatingDataSource {

		CountingDataSource(DataSource target) {
			super(target);
		}

		@Override
		public Connection getConnection() throws SQLException {
			return connection(super.getConnection());
		}

		@Override
		public Connection getConnection(String username, String password) throws SQLException {
			return connection(super.getConnection(username, password));
		}
	}

	private static Connection connection(Connection target) {
		return (Connection) Proxy.newProxyInstance(StatementCounter.class.getClassLoader(), new Class<?>[] {Connection.class},
				(proxy, method, args) -> {
					if (isObjectMethod(method)) {
						return objectMethod(proxy, method, args);
					}
					Object result = invoke(target, method, args);
					String name = method.getName();
					boolean creates = name.equals("createStatement") || name.equals("prepareStatement")
							|| name.equals("prepareCall");
					return creates && result instanceof Statement statement ? statement(statement) : result;
				});
	}

	private static Statement statement(Statement target) {
		Class<?> type = target instanceof CallableStatement ? CallableStatement.class
				: target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
		return (Statement) Proxy.newProxyInstance(StatementCounter.class.getClassLoader(), new Class<?>[] {type},
				(proxy, method, args) -> {
					if (isObjectMethod(method)) {
						return objectMethod(proxy, method, args);
					}
					if (method.getName().startsWith("execute")) {
						executed();
					}
					return invoke(target, method, args);
				});
	}

	private static boolean isObjectMethod(Method method) {
		return method.getDeclaringClass() == Object.class;
	}

	/** A proxy is equal only to itself, so a held connection matches the one handed back. */
	private static Object objectMethod(Object proxy, Method method, Object[] args) {
		return switch (method.getName()) {
			case "equals" -> proxy == args[0];
			case "hashCode" -> System.identityHashCode(proxy);
			default -> proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
		};
	}

	private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
