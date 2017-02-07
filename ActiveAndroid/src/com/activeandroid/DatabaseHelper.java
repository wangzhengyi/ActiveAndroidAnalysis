package com.activeandroid;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.activeandroid.util.IOUtils;
import com.activeandroid.util.Log;
import com.activeandroid.util.NaturalOrderComparator;
import com.activeandroid.util.SQLiteUtils;
import com.activeandroid.util.SqlParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DatabaseHelper extends SQLiteOpenHelper {
    public final static String MIGRATION_PATH = "migrations";

    private final String mSqlParser;

    /**
     * 构造函数,传入当前数据库名称和版本号,并判断是否进行数据库拷贝动作.
     */
    public DatabaseHelper(Configuration configuration) {
        super(configuration.getContext(), configuration.getDatabaseName(), null, configuration.getDatabaseVersion());
        copyAttachedDatabase(configuration.getContext(), configuration.getDatabaseName());
        mSqlParser = configuration.getSqlParser();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        executePragmas(db);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        executePragmas(db);
        executeCreate(db);
        executeMigrations(db, -1, db.getVersion());
        executeCreateIndex(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        android.util.Log.w("wangzhengyi", "onUpgrade: is called");
        executePragmas(db);
        // 创建新表,因为建表语句是CRATE TABLE IF NOT EXIST,所以不用担心旧表被覆盖的问题
        executeCreate(db);
        // 旧表的修改使用asset/migrations/*.sql去修改
        executeMigrations(db, oldVersion, newVersion);
    }

    /**
     * 拷贝应用assets目录下同名SQLite db文件到应用所在的/data/data/packagename/database目录下
     */
    public void copyAttachedDatabase(Context context, String databaseName) {
        final File dbPath = context.getDatabasePath(databaseName);

        // 如果同名db文件已经存在,则不进行拷贝动作
        if (dbPath.exists()) {
            return;
        }

        // Make sure we have a path to the file
        dbPath.getParentFile().mkdirs();

        // Try to copy database file
        try {
            final InputStream inputStream = context.getAssets().open(databaseName);
            final OutputStream output = new FileOutputStream(dbPath);

            // 字节缓存为8KB
            byte[] buffer = new byte[8192];
            int length;

            while ((length = inputStream.read(buffer, 0, 8192)) > 0) {
                output.write(buffer, 0, length);
            }

            output.flush();
            output.close();
            inputStream.close();
        } catch (IOException e) {
            Log.e("Failed to open file", e);
        }
    }

    /**
     * 开启SQLite的外键支持
     */
    private void executePragmas(SQLiteDatabase db) {
        if (SQLiteUtils.FOREIGN_KEYS_SUPPORTED) {
            db.execSQL("PRAGMA foreign_keys=ON;");
            Log.i("Foreign Keys supported. Enabling foreign key features.");
        }
    }

    /**
     * 创建表的索引
     */
    private void executeCreateIndex(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            for (TableInfo tableInfo : Cache.getTableInfos()) {
                String[] definitions = SQLiteUtils.createIndexDefinition(tableInfo);

                for (String definition : definitions) {
                    db.execSQL(definition);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 生成SQLite数据库的所有表结构
     */
    private void executeCreate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            for (TableInfo tableInfo : Cache.getTableInfos()) {
                // 通过SQLiteUtils生成建表语句
                db.execSQL(SQLiteUtils.createTableDefinition(tableInfo));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 实现SQLite数据库版本升级
     *
     * @param db         SQLite数据库句柄
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号
     */
    private boolean executeMigrations(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean migrationExecuted = false;
        try {
            // 获取应用assets/migrations目录下的.sql文件,文件名为数据库新版本号
            final List<String> files = Arrays.asList(Cache.getContext().getAssets().list(MIGRATION_PATH));
            Collections.sort(files, new NaturalOrderComparator());

            db.beginTransaction();
            try {
                for (String file : files) {
                    try {
                        final int version = Integer.valueOf(file.replace(".sql", ""));

                        if (version > oldVersion && version <= newVersion) {
                            // 执行更新的SQL语句
                            executeSqlScript(db, file);
                            migrationExecuted = true;

                            Log.i(file + " executed successfully.");
                        }
                    } catch (NumberFormatException e) {
                        Log.w("Skipping invalidly named file: " + file, e);
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (IOException e) {
            Log.e("Failed to execute migrations.", e);
        }

        return migrationExecuted;
    }

    /**
     * 执行指定文件中的SQL语句
     */
    private void executeSqlScript(SQLiteDatabase db, String file) {

        InputStream stream = null;

        try {
            stream = Cache.getContext().getAssets().open(MIGRATION_PATH + "/" + file);

            if (Configuration.SQL_PARSER_DELIMITED.equalsIgnoreCase(mSqlParser)) {
                executeDelimitedSqlScript(db, stream);
            } else {
                executeLegacySqlScript(db, stream);
            }

        } catch (IOException e) {
            Log.e("Failed to execute " + file, e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private void executeDelimitedSqlScript(SQLiteDatabase db, InputStream stream) throws IOException {

        List<String> commands = SqlParser.parse(stream);

        for (String command : commands) {
            db.execSQL(command);
        }
    }

    private void executeLegacySqlScript(SQLiteDatabase db, InputStream stream) throws IOException {

        InputStreamReader reader = null;
        BufferedReader buffer = null;

        try {
            reader = new InputStreamReader(stream);
            buffer = new BufferedReader(reader);
            String line = null;

            while ((line = buffer.readLine()) != null) {
                line = line.replace(";", "").trim();
                if (!TextUtils.isEmpty(line)) {
                    db.execSQL(line);
                }
            }

        } finally {
            IOUtils.closeQuietly(buffer);
            IOUtils.closeQuietly(reader);

        }
    }
}
