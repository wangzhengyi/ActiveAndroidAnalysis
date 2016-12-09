package com.activeandroid.query;

/**
 * 定义操作类转换成SQL语句的接口.
 */
public interface Sqlable {
    public String toSql();
}