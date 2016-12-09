package com.activeandroid.query;


import android.text.TextUtils;

import com.activeandroid.Cache;
import com.activeandroid.Model;
import com.activeandroid.content.ContentProvider;
import com.activeandroid.query.Join.JoinType;
import com.activeandroid.util.Log;
import com.activeandroid.util.SQLiteUtils;

import java.util.ArrayList;
import java.util.List;

public final class From implements Sqlable {
    /**
     * SQL执行语句集合
     */
    private Sqlable mQueryBase;

    /**
     * 用户自定义Model的Class对象
     */
    private Class<? extends Model> mType;

    /**
     * 当前表的别名
     */
    private String mAlias;

    /**
     * 表级联的集合
     */
    private List<Join> mJoins;

    /**
     * WHERE从句
     */
    private final StringBuilder mWhere = new StringBuilder();

    /**
     * GROUP BY从句
     */
    private String mGroupBy;

    /**
     * HAVING从句
     */
    private String mHaving;

    /**
     * ORDER BY从句
     */
    private String mOrderBy;

    /**
     * LIMIT从句
     */
    private String mLimit;

    /**
     * LIMIT的偏移量
     */
    private String mOffset;

    /**
     * WHERE从句占位符参数集合
     */
    private List<Object> mArguments;

    /**
     * 构造函数
     */
    public From(Class<? extends Model> table, Sqlable queryBase) {
        mType = table;
        mJoins = new ArrayList<Join>();
        mQueryBase = queryBase;
        mArguments = new ArrayList<Object>();
    }

    /**
     * 设置表别名.
     * 例如: From(AAA.class).as("a")
     */
    public From as(String alias) {
        mAlias = alias;
        return this;
    }

    /**
     * 两表连接操作
     * 例如: From(AAA.class).join(BBB.class)
     */
    public Join join(Class<? extends Model> table) {
        Join join = new Join(this, table, null);
        mJoins.add(join);
        return join;
    }

    /**
     * 左连接
     */
    public Join leftJoin(Class<? extends Model> table) {
        Join join = new Join(this, table, JoinType.LEFT);
        mJoins.add(join);
        return join;
    }

    /**
     * 外连接
     */
    public Join outerJoin(Class<? extends Model> table) {
        Join join = new Join(this, table, JoinType.OUTER);
        mJoins.add(join);
        return join;
    }

    /**
     * 内连接
     */
    public Join innerJoin(Class<? extends Model> table) {
        Join join = new Join(this, table, JoinType.INNER);
        mJoins.add(join);
        return join;
    }

    /**
     * 交叉连接
     */
    public Join crossJoin(Class<? extends Model> table) {
        Join join = new Join(this, table, JoinType.CROSS);
        mJoins.add(join);
        return join;
    }

    /**
     * Where从句
     */
    public From where(String clause) {
        // Chain conditions if a previous condition exists.
        if (mWhere.length() > 0) {
            mWhere.append(" AND ");
        }
        mWhere.append(clause);
        return this;
    }

    /**
     * 带占位符的WHERE从句
     */
    public From where(String clause, Object... args) {
        where(clause).addArguments(args);
        return this;
    }

    /**
     * WHERE从句中的AND连接
     */
    public From and(String clause) {
        return where(clause);
    }

    /**
     * 带占位符的AND连接
     */
    public From and(String clause, Object... args) {
        return where(clause, args);
    }

    /**
     * WHERE从句中的OR连接
     */
    public From or(String clause) {
        if (mWhere.length() > 0) {
            mWhere.append(" OR ");
        }
        mWhere.append(clause);
        return this;
    }

    /**
     * 带占位符的OR连接
     */
    public From or(String clause, Object... args) {
        or(clause).addArguments(args);
        return this;
    }

    /**
     * GROUP BY从句
     */
    public From groupBy(String groupBy) {
        mGroupBy = groupBy;
        return this;
    }

    /**
     * HAVING从句
     */
    public From having(String having) {
        mHaving = having;
        return this;
    }

    /**
     * ORDER BY从句
     */
    public From orderBy(String orderBy) {
        mOrderBy = orderBy;
        return this;
    }

    public From limit(int limit) {
        return limit(String.valueOf(limit));
    }

    public From limit(String limit) {
        mLimit = limit;
        return this;
    }

    public From offset(int offset) {
        return offset(String.valueOf(offset));
    }

    public From offset(String offset) {
        mOffset = offset;
        return this;
    }

    void addArguments(Object[] args) {
        for (Object arg : args) {
            if (arg.getClass() == boolean.class || arg.getClass() == Boolean.class) {
                arg = (arg.equals(true) ? 1 : 0);
            }
            mArguments.add(arg);
        }
    }

    /**
     * 拼接FROM语句
     */
    private void addFrom(final StringBuilder sql) {
        sql.append("FROM ");
        sql.append(Cache.getTableName(mType)).append(" ");

        if (mAlias != null) {
            sql.append("AS ");
            sql.append(mAlias);
            sql.append(" ");
        }
    }

    /**
     * 拼接JOIN语句
     */
    private void addJoins(final StringBuilder sql) {
        for (final Join join : mJoins) {
            sql.append(join.toSql());
        }
    }

    /**
     * 拼接WHERE语句
     */
    private void addWhere(final StringBuilder sql) {
        if (mWhere.length() > 0) {
            sql.append("WHERE ");
            sql.append(mWhere);
            sql.append(" ");
        }
    }

    /**
     * 拼接GROUP BY语句
     */
    private void addGroupBy(final StringBuilder sql) {
        if (mGroupBy != null) {
            sql.append("GROUP BY ");
            sql.append(mGroupBy);
            sql.append(" ");
        }
    }

    /**
     * 拼接HAVING语句
     */
    private void addHaving(final StringBuilder sql) {
        if (mHaving != null) {
            sql.append("HAVING ");
            sql.append(mHaving);
            sql.append(" ");
        }
    }

    /**
     * 拼接ORDER BY语句
     */
    private void addOrderBy(final StringBuilder sql) {
        if (mOrderBy != null) {
            sql.append("ORDER BY ");
            sql.append(mOrderBy);
            sql.append(" ");
        }
    }

    /**
     * 拼接LIMIT语句
     */
    private void addLimit(final StringBuilder sql) {
        if (mLimit != null) {
            sql.append("LIMIT ");
            sql.append(mLimit);
            sql.append(" ");
        }
    }

    /**
     * 拼接OFFSET语句
     */
    private void addOffset(final StringBuilder sql) {
        if (mOffset != null) {
            sql.append("OFFSET ");
            sql.append(mOffset);
            sql.append(" ");
        }
    }

    private String sqlString(final StringBuilder sql) {

        final String sqlString = sql.toString().trim();

        // Don't waste time building the string
        // unless we're going to log it.
        if (Log.isEnabled()) {
            Log.v(sqlString + " " + TextUtils.join(",", getArguments()));
        }

        return sqlString;
    }

    /**
     * 生成可执行的SQL语句
     */
    @Override
    public String toSql() {
        final StringBuilder sql = new StringBuilder();
        sql.append(mQueryBase.toSql());

        addFrom(sql);
        addJoins(sql);
        addWhere(sql);
        addGroupBy(sql);
        addHaving(sql);
        addOrderBy(sql);
        addLimit(sql);
        addOffset(sql);

        return sqlString(sql);
    }

    public String toExistsSql() {

        final StringBuilder sql = new StringBuilder();
        sql.append("SELECT EXISTS(SELECT 1 ");

        addFrom(sql);
        addJoins(sql);
        addWhere(sql);
        addGroupBy(sql);
        addHaving(sql);
        addLimit(sql);
        addOffset(sql);

        sql.append(")");

        return sqlString(sql);
    }

    public String toCountSql() {

        final StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) ");

        addFrom(sql);
        addJoins(sql);
        addWhere(sql);
        addGroupBy(sql);
        addHaving(sql);
        addLimit(sql);
        addOffset(sql);

        return sqlString(sql);
    }

    /**
     * 执行SQL语句
     */
    public <T extends Model> List<T> execute() {
        if (mQueryBase instanceof Select) {
            return SQLiteUtils.rawQuery(mType, toSql(), getArguments());

        } else {
            SQLiteUtils.execSql(toSql(), getArguments());
            Cache.getContext().getContentResolver().notifyChange(ContentProvider.createUri(mType, null), null);
            return null;

        }
    }

    /**
     * 指定带LIMIT 1的SQL语句
     */
    public <T extends Model> T executeSingle() {
        if (mQueryBase instanceof Select) {
            limit(1);
            return (T) SQLiteUtils.rawQuerySingle(mType, toSql(), getArguments());

        } else {
            //limit(1);
            Model model = SQLiteUtils.rawQuerySingle(mType, toSql(), getArguments());
            if (model != null) {
                model.delete();
            }
            return null;

        }
    }

    /**
     * Gets a value indicating whether the query returns any rows.
     *
     * @return <code>true</code> if the query returns at least one row; otherwise, <code>false</code>.
     */
    public boolean exists() {
        return SQLiteUtils.intQuery(toExistsSql(), getArguments()) != 0;
    }

    /**
     * Gets the number of rows returned by the query.
     */
    public int count() {
        return SQLiteUtils.intQuery(toCountSql(), getArguments());
    }

    public String[] getArguments() {
        final int size = mArguments.size();
        final String[] args = new String[size];

        for (int i = 0; i < size; i++) {
            args[i] = mArguments.get(i).toString();
        }

        return args;
    }
}
