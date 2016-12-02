package com.activeandroid.query;

import android.text.TextUtils;

import com.activeandroid.Cache;
import com.activeandroid.Model;

public final class Join implements Sqlable {

    /**
     * 支持的表连接操作包括:左连接，外连接，内连接和交叉连接
     * 左连接：取得左表完全记录，即是右表并无对应匹配记录
     * 内连接：取得两个表中存在连接匹配关系的记录
     * 交叉连接：返回两表相乘的数据集合
     */
    enum JoinType {
        LEFT, OUTER, INNER, CROSS
    }

    /**
     * 左表数据
     */
    private From mFrom;

    /**
     * 右表数据
     */
    private Class<? extends Model> mType;

    /**
     * 右表的别名
     */
    private String mAlias;

    /**
     * 连接方式
     */
    private JoinType mJoinType;

    /**
     * 连接条件
     */
    private String mOn;
    private String[] mUsing;

    Join(From from, Class<? extends Model> table, JoinType joinType) {
        mFrom = from;
        mType = table;
        mJoinType = joinType;
    }

    public Join as(String alias) {
        mAlias = alias;
        return this;
    }

    public From on(String on) {
        mOn = on;
        return mFrom;
    }

    public From on(String on, Object... args) {
        mOn = on;
        mFrom.addArguments(args);
        return mFrom;
    }

    public From using(String... columns) {
        mUsing = columns;
        return mFrom;
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder();

        if (mJoinType != null) {
            sql.append(mJoinType.toString()).append(" ");
        }

        sql.append("JOIN ");
        sql.append(Cache.getTableName(mType));
        sql.append(" ");

        if (mAlias != null) {
            sql.append("AS ");
            sql.append(mAlias);
            sql.append(" ");
        }

        if (mOn != null) {
            sql.append("ON ");
            sql.append(mOn);
            sql.append(" ");
        } else if (mUsing != null) {
            sql.append("USING (");
            sql.append(TextUtils.join(", ", mUsing));
            sql.append(") ");
        }

        return sql.toString();
    }
}
