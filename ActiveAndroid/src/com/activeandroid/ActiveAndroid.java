package com.activeandroid;

/*
 * Copyright (C) 2010 Michael Pardo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.activeandroid.util.Log;

public final class ActiveAndroid {
    //////////////////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * 构造函数
     * @param context 通过应用上下文去初始化Configuration配置类
     */
    public static void initialize(Context context) {
        initialize(new Configuration.Builder(context).create());
    }

    /**
     * 构造函数
     * @param configuration 直接传入用户构造的Configuration配置类
     */
    public static void initialize(Configuration configuration) {
        initialize(configuration, false);
    }

    /**
     * 构造函数
     * @param context 应用进程上下文
     * @param loggingEnabled 日志开关
     */
    public static void initialize(Context context, boolean loggingEnabled) {
        initialize(new Configuration.Builder(context).create(), loggingEnabled);
    }

    /**
     * 构造函数
     * @param configuration 用户构造的Configuration配置类
     * @param loggingEnabled 日志开关
     */
    public static void initialize(Configuration configuration, boolean loggingEnabled) {
        // 设置日志开关(ps:优秀的开源项目都会控制日志输出)
        setLoggingEnabled(loggingEnabled);
        Cache.initialize(configuration);
    }

    public static void clearCache() {
        Cache.clear();
    }

    public static void dispose() {
        Cache.dispose();
    }

    public static void setLoggingEnabled(boolean enabled) {
        Log.setEnabled(enabled);
    }

    public static SQLiteDatabase getDatabase() {
        return Cache.openDatabase();
    }

    public static void beginTransaction() {
        Cache.openDatabase().beginTransaction();
    }

    public static void endTransaction() {
        Cache.openDatabase().endTransaction();
    }

    public static void setTransactionSuccessful() {
        Cache.openDatabase().setTransactionSuccessful();
    }

    public static boolean inTransaction() {
        return Cache.openDatabase().inTransaction();
    }

    public static void execSQL(String sql) {
        Cache.openDatabase().execSQL(sql);
    }

    public static void execSQL(String sql, Object[] bindArgs) {
        Cache.openDatabase().execSQL(sql, bindArgs);
    }
}
