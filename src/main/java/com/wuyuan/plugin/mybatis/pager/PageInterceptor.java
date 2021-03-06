package com.wuyuan.plugin.mybatis.pager;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Created by Tiejun Sun on 2016-04-21. 通过拦截<code>StatementHandler</code>的
 * <code>prepare</code>方法，重写sql语句实现物理分页。
 *
 * @author Tiejun Sun
 */
@Intercepts({ @Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class  }) })
public class PageInterceptor implements Interceptor {

	private static final Log logger = LogFactory.getLog(PageInterceptor.class);
	private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
	private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
	private static final DefaultReflectorFactory DEFAULT_REFLECTION_FACTORY = new DefaultReflectorFactory();
	private static final String defaultDialect = "mysql"; // 数据库类型(默认为mysql)
	private static final String defaultPageSqlId = ".*Page$"; // 需要拦截的ID(正则匹配)

	private static ThreadLocal<String> dialect = new ThreadLocal<String>(); // 数据库类型(默认为mysql)
	private static ThreadLocal<String> pageSqlId = new ThreadLocal<String>(); // 需要拦截的ID(正则匹配)

	public Object intercept(Invocation invocation) throws Throwable {
		StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
		MetaObject metaStatementHandler = MetaObject.forObject(statementHandler, DEFAULT_OBJECT_FACTORY,
				DEFAULT_OBJECT_WRAPPER_FACTORY, DEFAULT_REFLECTION_FACTORY);
		// 分离代理对象链(由于目标类可能被多个拦截器拦截，从而形成多次代理，通过下面的两次循环可以分离出最原始的的目标类)
		while (metaStatementHandler.hasGetter("h")) {
			Object object = metaStatementHandler.getValue("h");
			metaStatementHandler = MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY,
					DEFAULT_REFLECTION_FACTORY);
		}
		// 分离最后一个代理对象的目标类
		while (metaStatementHandler.hasGetter("target")) {
			Object object = metaStatementHandler.getValue("target");
			metaStatementHandler = MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY,
					DEFAULT_REFLECTION_FACTORY);
		}
		Configuration configuration = (Configuration) metaStatementHandler.getValue("delegate.configuration");
		// dialect = configuration.getVariables().getProperty("dialect");
		dialect.set(configuration.getVariables().getProperty("dialect"));
		String currentThreadDialect = dialect.get();
		if (null == currentThreadDialect || "".equals(currentThreadDialect)) {
			logger.warn("Property dialect is not setted,use default 'mysql' ");
			// dialect = defaultDialect;
			dialect.set(defaultDialect);
		}
		pageSqlId.set(configuration.getVariables().getProperty("pageSqlId"));
		String currentThreadPageSqlId = pageSqlId.get();
		if (null == currentThreadPageSqlId || "".equals(currentThreadPageSqlId)) {
			logger.warn("Property pageSqlId is not setted,use default '.*Page$' ");
			pageSqlId.set(defaultPageSqlId);
		}
		MappedStatement mappedStatement = (MappedStatement) metaStatementHandler.getValue("delegate.mappedStatement");
		// 只重写需要分页的sql语句。通过MappedStatement的ID匹配，默认重写以Page结尾的MappedStatement的sql
		if (mappedStatement.getId().matches(currentThreadPageSqlId)) {
			BoundSql boundSql = (BoundSql) metaStatementHandler.getValue("delegate.boundSql");
			Object parameterObject = boundSql.getParameterObject();
			if (parameterObject == null) {
				throw new NullPointerException("parameterObject is null!");
			} else {
				PageParameter page = (PageParameter) metaStatementHandler
						.getValue("delegate.boundSql.parameterObject.page");
				String sql = boundSql.getSql();

				String pageSql = "";

				Connection connection = (Connection) invocation.getArgs()[0];
				// 重设分页参数里的总页数等
				setPageParameter(sql, connection, mappedStatement, boundSql, page);

				// 处理排序字段
				String orderBy = (String) metaStatementHandler.getValue("delegate.boundSql.parameterObject.orderBy");
				if (orderBy == null || "".equals(orderBy.trim())) {// 无排序字段的情况
					// 重写sql
					pageSql = buildPageSql(sql, page);
				} else {// 有排序字段的情况
					orderBy = getOrderBy(orderBy);
					// 重写sql
					pageSql = buildPageSql(sql, page, orderBy);
				}

				metaStatementHandler.setValue("delegate.boundSql.sql", pageSql);
				// 采用物理分页后，就不需要mybatis的内存分页了，所以重置下面的两个参数
				metaStatementHandler.setValue("delegate.rowBounds.offset", RowBounds.NO_ROW_OFFSET);
				metaStatementHandler.setValue("delegate.rowBounds.limit", RowBounds.NO_ROW_LIMIT);

			}
		}
		// 将执行权交给下一个拦截器
		return invocation.proceed();
	}

	/**
	 * 获得排序的sql
	 * 
	 * @param orderBy
	 * @return
	 */
	private String getOrderBy(String orderBy) {
		// orderBy已经判空过了，这里不用判空了

		String[] arr = orderBy.split(",");
		StringBuilder sb = new StringBuilder();
		sb.append(" order by ");
		for (String orderByStr : arr) {
			if (!"sqlserver".equals(dialect.get())) {
				orderByStr = toUnderLine(orderByStr);
			}
			if (orderByStr.startsWith("-")) {
				orderByStr = orderByStr.substring(1) + " desc, ";
			} else {
				orderByStr = orderByStr + " asc, ";
			}
			sb.append(orderByStr);
		}
		String result = sb.toString();
		return result.substring(0, result.lastIndexOf(","));
	}

	/**
	 * 将驼峰命名转换为下划线命名
	 * 
	 * @param name
	 * @return
	 */
	private String toUnderLine(String name) {
		StringBuilder result = new StringBuilder();
		if ((name != null) && (name.length() > 0)) {
			for (int i = 0; i < name.length(); ++i) {
				String s = name.substring(i, i + 1);
				char c = name.charAt(i);
				if (('A' <= c) && c <= 'Z') {
					result.append("_");
					result.append(s.toLowerCase());
				} else {
					result.append(s);
				}
			}
		}
		return result.toString();
	}

	/**
	 * 从数据库里查询总的记录数并计算总页数，回写进分页参数<code>PageParameter</code>,这样调用者就可用通过 分页参数
	 * <code>PageParameter</code>获得相关信息。
	 *
	 * @param sql
	 * @param connection
	 * @param mappedStatement
	 * @param boundSql
	 * @param page
	 */
	private void setPageParameter(String sql, Connection connection, MappedStatement mappedStatement, BoundSql boundSql,
			PageParameter page) {
		// 记录总记录数
		String countSql = "select count(0) from (" + sql + ") as total";
		PreparedStatement countStmt = null;
		ResultSet rs = null;
		try {
			countStmt = connection.prepareStatement(countSql);
			// ---- 下面的代码是为了修复 bean中有list的时候参数出问题的情况，跟踪源码后发现是这里出的问题。所以注释掉。
			// BoundSql countBS = new
			// BoundSql(mappedStatement.getConfiguration(), countSql,
			// boundSql.getParameterMappings(), boundSql.getParameterObject());

			setParameters(countStmt, mappedStatement, boundSql, boundSql.getParameterObject());
			rs = countStmt.executeQuery();
			int totalCount = 0;
			if (rs.next()) {
				totalCount = rs.getInt(1);
			}
			page.setTotalCount(totalCount);
			int totalPage = totalCount / page.getPageSize() + ((totalCount % page.getPageSize() == 0) ? 0 : 1);
			page.setTotalPage(totalPage);
			int currentPage = page.getCurrentPage();
			page.setPrePage(currentPage - 1);
			page.setNextPage(currentPage + 1);
			if (page.getPageSize() == -1) { // 如果为-1，就查询所有。
				page.setPageSize(totalCount);
			}

		} catch (SQLException e) {
			logger.error("Ignore this exception", e);
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
			} catch (SQLException e) {
				logger.error("Ignore this exception", e);
			}
			try {
				if (countStmt != null) {
					countStmt.close();
				}
			} catch (SQLException e) {
				logger.error("Ignore this exception", e);
			}
		}

	}

	/**
	 * 对SQL参数(?)设值
	 *
	 * @param ps
	 * @param mappedStatement
	 * @param boundSql
	 * @param parameterObject
	 * @throws SQLException
	 */
	private void setParameters(PreparedStatement ps, MappedStatement mappedStatement, BoundSql boundSql,
			Object parameterObject) throws SQLException {
		ParameterHandler parameterHandler = new DefaultParameterHandler(mappedStatement, parameterObject, boundSql);
		parameterHandler.setParameters(ps);
	}

	/**
	 * 根据数据库类型，生成特定的分页sql
	 *
	 * @param sql
	 * @param page
	 * @return
	 */
	private String buildPageSql(String sql, PageParameter page) {
		if (page != null) {
			StringBuilder pageSql = new StringBuilder();
			String currentThreadDialect = dialect.get();
			if ("mysql".equals(currentThreadDialect)) {
				pageSql = buildPageSqlForMysql(sql, page);
			} else if ("oracle".equals(currentThreadDialect)) {
				pageSql = buildPageSqlForOracle(sql, page);
			} else if ("sqlserver".equals(currentThreadDialect)) {
				pageSql = buildPageSqlForSqlserver(sql, page);
			} else {
				return sql;
			}
			return pageSql.toString();
		} else {
			return sql;
		}
	}

	/**
	 * 根据数据库类型，生成特定的分页sql
	 *
	 * @param sql
	 * @param page
	 * @return
	 */
	private String buildPageSql(String sql, PageParameter page, String orderBy) {
		if (page != null) {
			StringBuilder pageSql = new StringBuilder();
			String currentThreadDialect = dialect.get();
			if ("mysql".equals(currentThreadDialect)) {
				pageSql = buildPageSqlForMysql(sql, page, orderBy);
			} else if ("oracle".equals(currentThreadDialect)) {
				pageSql = buildPageSqlForOracle(sql, page, orderBy);
			} else if ("sqlserver".equals(currentThreadDialect)) {
				pageSql = buildPageSqlForSqlserver(sql, page, orderBy);
			} else {
				return sql;
			}
			return pageSql.toString();
		} else {
			return sql;
		}
	}

	/**
	 * sqlserver的分页语句
	 *
	 * @param sql
	 * @param page
	 * @return String
	 */
	private StringBuilder buildPageSqlForSqlserver(String sql, PageParameter page) {
		StringBuilder pageSql = new StringBuilder(100);
		String beginrow = String.valueOf((page.getCurrentPage() - 1) * page.getPageSize());

		pageSql.append(sql);
		pageSql.append(" order by 1");
		pageSql.append(" offset " + beginrow + " rows fetch next " + page.getPageSize() + " rows only ");
		return pageSql;
	}

	/**
	 * sqlserver的分页语句
	 *
	 * @param sql
	 * @param page
	 * @return String
	 */
	private StringBuilder buildPageSqlForSqlserver(String sql, PageParameter page, String orderBy) {
		StringBuilder pageSql = new StringBuilder(100);
		String beginrow = String.valueOf((page.getCurrentPage() - 1) * page.getPageSize());

		pageSql.append(sql);
		pageSql.append(orderBy);
		pageSql.append(" offset " + beginrow + " rows fetch next " + page.getPageSize() + " rows only ");
		return pageSql;
	}

	/**
	 * mysql的分页语句
	 *
	 * @param sql
	 * @param page
	 * @return String
	 */
	public StringBuilder buildPageSqlForMysql(String sql, PageParameter page) {
		StringBuilder pageSql = new StringBuilder(100);
		String beginrow = String.valueOf((page.getCurrentPage() - 1) * page.getPageSize());
		pageSql.append(sql);
		pageSql.append(" limit " + beginrow + "," + page.getPageSize());
		return pageSql;
	}

	/**
	 * mysql的分页语句
	 *
	 * @param sql
	 * @param page
	 * @return String
	 */
	public StringBuilder buildPageSqlForMysql(String sql, PageParameter page, String orderBy) {
		StringBuilder pageSql = new StringBuilder(100);
		String beginrow = String.valueOf((page.getCurrentPage() - 1) * page.getPageSize());
		pageSql.append(sql);
		pageSql.append(orderBy);
		pageSql.append(" limit " + beginrow + "," + page.getPageSize());
		return pageSql;
	}

	/**
	 * 参考hibernate的实现完成oracle的分页
	 *
	 * @param sql
	 * @param page
	 * @return String
	 */
	public StringBuilder buildPageSqlForOracle(String sql, PageParameter page) {
		StringBuilder pageSql = new StringBuilder(100);
		String beginrow = String.valueOf((page.getCurrentPage() - 1) * page.getPageSize());
		String endrow = String.valueOf(page.getCurrentPage() * page.getPageSize());

		pageSql.append("select * from ( select temp.*, rownum row_id from ( ");
		pageSql.append(sql);
		pageSql.append(" ) temp where rownum <= ").append(endrow);
		pageSql.append(" ) where row_id > ").append(beginrow);
		return pageSql;
	}

	/**
	 * 参考hibernate的实现完成oracle的分页
	 *
	 * @param sql
	 * @param page
	 * @return String
	 */
	public StringBuilder buildPageSqlForOracle(String sql, PageParameter page, String orderBy) {
		StringBuilder pageSql = new StringBuilder(100);
		String beginrow = String.valueOf((page.getCurrentPage() - 1) * page.getPageSize());
		String endrow = String.valueOf(page.getCurrentPage() * page.getPageSize());

		pageSql.append("select * from ( select temp.*, rownum row_id from ( ");
		pageSql.append(sql);
		pageSql.append("orderBy");
		pageSql.append(" ) temp where rownum <= ").append(endrow);
		pageSql.append(") where row_id > ").append(beginrow);
		return pageSql;
	}

	public Object plugin(Object target) {
		// 当目标类是StatementHandler类型时，才包装目标类，否者直接返回目标本身,减少目标被代理的次数
		if (target instanceof StatementHandler) {
			return Plugin.wrap(target, this);
		} else {
			return target;
		}
	}

	public void setProperties(Properties properties) {
	}
}
