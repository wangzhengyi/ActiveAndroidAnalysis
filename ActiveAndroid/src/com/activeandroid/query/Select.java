package com.activeandroid.query;

import android.text.TextUtils;

import com.activeandroid.Model;

public final class Select implements Sqlable {
    private String[] mColumns;
    private boolean mDistinct = false;
    private boolean mAll = false;

    public Select() {
    }

    /**
     * 指定选择的列
     */
    public Select(String... columns) {
        mColumns = columns;
    }

    /**
     * 指定选择的列和列的别名
     */
    public Select(Column... columns) {
        final int size = columns.length;
        mColumns = new String[size];
        for (int i = 0; i < size; i++) {
            mColumns[i] = columns[i].name + " AS " + columns[i].alias;
        }
    }

    public Select distinct() {
        mDistinct = true;
        mAll = false;

        return this;
    }

    public Select all() {
        mDistinct = false;
        mAll = true;

        return this;
    }

    public From from(Class<? extends Model> table) {
        return new From(table, this);
    }

    /**
     * 静态内部类,用于描述选择的列和列的别名
     */
    public static class Column {
        String name;
        String alias;

        public Column(String name, String alias) {
            this.name = name;
            this.alias = alias;
        }
    }

    @Override
    public String toSql() {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ");

        if (mDistinct) {
            sql.append("DISTINCT ");
        } else if (mAll) {
            sql.append("ALL ");
        }

        // 如果指定列,则拼接具体的列的名字;否则,使用SELETE *
        if (mColumns != null && mColumns.length > 0) {
            sql.append(TextUtils.join(", ", mColumns) + " ");
        } else {
            sql.append("* ");
        }

        return sql.toString();
    }
}