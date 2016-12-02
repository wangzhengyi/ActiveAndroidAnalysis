package com.activeandroid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;

import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;
import com.activeandroid.util.ReflectionUtils;

public class Configuration {

    public final static String SQL_PARSER_LEGACY = "legacy";
    public final static String SQL_PARSER_DELIMITED = "delimited";

    //////////////////////////////////////////////////////////////////////////////////////
    // PRIVATE MEMBERS
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * 应用进程上下文
     */
    private Context mContext;

    /**
     * 数据库名称
     */
    private String mDatabaseName;

    /**
     * 数据库版本号
     */
    private int mDatabaseVersion;

    /**
     * 数据库SQL解释器名称
     */
    private String mSqlParser;

    /**
     * Table集合
     */
    private List<Class<? extends Model>> mModelClasses;

    /**
     * TODO:暂时不清楚用途
     */
    private List<Class<? extends TypeSerializer>> mTypeSerializers;

    /**
     * Cache Size
     */
    private int mCacheSize;

    //////////////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    //////////////////////////////////////////////////////////////////////////////////////

    private Configuration(Context context) {
        mContext = context;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    //////////////////////////////////////////////////////////////////////////////////////

    public Context getContext() {
        return mContext;
    }

    public String getDatabaseName() {
        return mDatabaseName;
    }

    public int getDatabaseVersion() {
        return mDatabaseVersion;
    }

    public String getSqlParser() {
        return mSqlParser;
    }

    public List<Class<? extends Model>> getModelClasses() {
        return mModelClasses;
    }

    public List<Class<? extends TypeSerializer>> getTypeSerializers() {
        return mTypeSerializers;
    }

    public int getCacheSize() {
        return mCacheSize;
    }

    /**
     * 判断当前的Configuration对象是否有效
     * 有效的依据是：当前表集合是否不为空
     */
    public boolean isValid() {
        return mModelClasses != null && mModelClasses.size() > 0;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // INNER CLASSES(构造器模式)
    //////////////////////////////////////////////////////////////////////////////////////

    public static class Builder {
        //////////////////////////////////////////////////////////////////////////////////////
        // PRIVATE CONSTANTS
        //////////////////////////////////////////////////////////////////////////////////////

        private static final String AA_DB_NAME = "AA_DB_NAME";
        private static final String AA_DB_VERSION = "AA_DB_VERSION";
        private final static String AA_MODELS = "AA_MODELS";
        private final static String AA_SERIALIZERS = "AA_SERIALIZERS";
        private final static String AA_SQL_PARSER = "AA_SQL_PARSER";

        private static final int DEFAULT_CACHE_SIZE = 1024;
        private static final String DEFAULT_DB_NAME = "Application.db";
        private static final String DEFAULT_SQL_PARSER = SQL_PARSER_LEGACY;

        //////////////////////////////////////////////////////////////////////////////////////
        // PRIVATE MEMBERS
        //////////////////////////////////////////////////////////////////////////////////////

        private Context mContext;

        private Integer mCacheSize;
        private String mDatabaseName;
        private Integer mDatabaseVersion;
        private String mSqlParser;
        private List<Class<? extends Model>> mModelClasses;
        private List<Class<? extends TypeSerializer>> mTypeSerializers;

        //////////////////////////////////////////////////////////////////////////////////////
        // CONSTRUCTORS
        //////////////////////////////////////////////////////////////////////////////////////

        public Builder(Context context) {
            mContext = context.getApplicationContext();
            mCacheSize = DEFAULT_CACHE_SIZE;
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // PUBLIC METHODS
        //////////////////////////////////////////////////////////////////////////////////////

        public Builder setCacheSize(int cacheSize) {
            mCacheSize = cacheSize;
            return this;
        }

        public Builder setDatabaseName(String databaseName) {
            mDatabaseName = databaseName;
            return this;
        }

        public Builder setDatabaseVersion(int databaseVersion) {
            mDatabaseVersion = databaseVersion;
            return this;
        }

        public Builder setSqlParser(String sqlParser) {
            mSqlParser = sqlParser;
            return this;
        }

        public Builder addModelClass(Class<? extends Model> modelClass) {
            if (mModelClasses == null) {
                mModelClasses = new ArrayList<Class<? extends Model>>();
            }

            mModelClasses.add(modelClass);
            return this;
        }

        public Builder addModelClasses(Class<? extends Model>... modelClasses) {
            if (mModelClasses == null) {
                mModelClasses = new ArrayList<Class<? extends Model>>();
            }

            mModelClasses.addAll(Arrays.asList(modelClasses));
            return this;
        }

        public Builder setModelClasses(Class<? extends Model>... modelClasses) {
            mModelClasses = Arrays.asList(modelClasses);
            return this;
        }

        public Builder addTypeSerializer(Class<? extends TypeSerializer> typeSerializer) {
            if (mTypeSerializers == null) {
                mTypeSerializers = new ArrayList<Class<? extends TypeSerializer>>();
            }

            mTypeSerializers.add(typeSerializer);
            return this;
        }

        public Builder addTypeSerializers(Class<? extends TypeSerializer>... typeSerializers) {
            if (mTypeSerializers == null) {
                mTypeSerializers = new ArrayList<Class<? extends TypeSerializer>>();
            }

            mTypeSerializers.addAll(Arrays.asList(typeSerializers));
            return this;
        }

        public Builder setTypeSerializers(Class<? extends TypeSerializer>... typeSerializers) {
            mTypeSerializers = Arrays.asList(typeSerializers);
            return this;
        }

        /**
         * 构建Configuration类.
         * 构建规则:
         * 1. 如果用户传入了自定义的Configuration类,且设置了相应配置的值,则直接使用用户的配置.
         * 2. 如果用户没有传入自定义的Configuration类,则使用用户传入的Context对象获取用户在AndroidManifest.xml中meta-data设置的配置.
         * 3. 单独针对用户自定义的Model类,如果用户没有传入Configuration类,也没有在AndroidManifest中配置,则ActiveAndroid会扫描应用的DexFile,通过反射查找所有用户自定义的Model对象.
         */
        public Configuration create() {
            Configuration configuration = new Configuration(mContext);
            configuration.mCacheSize = mCacheSize;

            // 获取数据库名称
            if (mDatabaseName != null) {
                configuration.mDatabaseName = mDatabaseName;
            } else {
                configuration.mDatabaseName = getMetaDataDatabaseNameOrDefault();
            }

            // 获取数据库版本
            if (mDatabaseVersion != null) {
                configuration.mDatabaseVersion = mDatabaseVersion;
            } else {
                configuration.mDatabaseVersion = getMetaDataDatabaseVersionOrDefault();
            }

            // 获取SQL解析器名称
            if (mSqlParser != null) {
                configuration.mSqlParser = mSqlParser;
            } else {
                configuration.mSqlParser = getMetaDataSqlParserOrDefault();
            }

            // 获取Model集合
            if (mModelClasses != null) {
                configuration.mModelClasses = mModelClasses;
            } else {
                final String modelList = ReflectionUtils.getMetaData(mContext, AA_MODELS);
                if (modelList != null) {
                    configuration.mModelClasses = loadModelList(modelList.split(","));
                }
            }

            // Get type serializer classes from meta-data
            if (mTypeSerializers != null) {
                configuration.mTypeSerializers = mTypeSerializers;
            } else {
                final String serializerList = ReflectionUtils.getMetaData(mContext, AA_SERIALIZERS);
                if (serializerList != null) {
                    configuration.mTypeSerializers = loadSerializerList(serializerList.split(","));
                }
            }

            return configuration;
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // PRIVATE METHODS
        //////////////////////////////////////////////////////////////////////////////////////

        // Meta-data methods

        /**
         * 获取meta-data中设定的数据库名称
         */
        private String getMetaDataDatabaseNameOrDefault() {
            String aaName = ReflectionUtils.getMetaData(mContext, AA_DB_NAME);
            if (aaName == null) {
                aaName = DEFAULT_DB_NAME;
            }

            return aaName;
        }

        /**
         * 获取meta-data中设定的数据库版本
         */
        private int getMetaDataDatabaseVersionOrDefault() {
            Integer aaVersion = ReflectionUtils.getMetaData(mContext, AA_DB_VERSION);
            if (aaVersion == null || aaVersion == 0) {
                aaVersion = 1;
            }

            return aaVersion;
        }


        /**
         * 获取meta-data中设定的SQL解析器.一般不指定，通常使用默认的legacy
         */
        private String getMetaDataSqlParserOrDefault() {
            final String mode = ReflectionUtils.getMetaData(mContext, AA_SQL_PARSER);
            if (mode == null) {
                return DEFAULT_SQL_PARSER;
            }
            return mode;
        }

        /**
         * 获取表的Class对象,存入List集合中
         */
        private List<Class<? extends Model>> loadModelList(String[] models) {
            final List<Class<? extends Model>> modelClasses = new ArrayList<Class<? extends Model>>();
            final ClassLoader classLoader = mContext.getClass().getClassLoader();
            for (String model : models) {
                try {
                    Class modelClass = Class.forName(model.trim(), false, classLoader);
                    if (ReflectionUtils.isModel(modelClass)) {
                        modelClasses.add(modelClass);
                    }
                } catch (ClassNotFoundException e) {
                    Log.e("Couldn't create class.", e);
                }
            }

            return modelClasses;
        }

        /**
         * TODO:暂时不清楚用途
         */
        private List<Class<? extends TypeSerializer>> loadSerializerList(String[] serializers) {
            final List<Class<? extends TypeSerializer>> typeSerializers = new ArrayList<Class<? extends TypeSerializer>>();
            final ClassLoader classLoader = mContext.getClass().getClassLoader();
            for (String serializer : serializers) {
                try {
                    Class serializerClass = Class.forName(serializer.trim(), false, classLoader);
                    if (ReflectionUtils.isTypeSerializer(serializerClass)) {
                        typeSerializers.add(serializerClass);
                    }
                } catch (ClassNotFoundException e) {
                    Log.e("Couldn't create class.", e);
                }
            }

            return typeSerializers;
        }

    }
}
