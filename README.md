# ActiveAndroid

-------
# 基本使用

### 声明Table

类似声明一个Students的表，需要集成Model类，代码如下:
```java
@Table(name = "Student")
public class StudentDAO extends Model {
    @Column(name = "name")
    public String name;

    @Column(name = "age")
    public int age;

    @Column(name = "sex")
    public String sex;

    @Column(name = "score")
    public int score;
}
```

### AndroidManifest.xml中注册相关信息

使用者需要在AndroidManifest.xml中注册数据库名称、版本号、表的路径等相关信息，参考设置如下：
```xml
<meta-data
    android:name="AA_DB_NAME"
    android:value="example.db"/>
<meta-data
    android:name="AA_DB_VERSION"
    android:value="1"/>
<meta-data
    android:name="AA_MODELS"
    android:value="com.wzy.study.model.StudentDAO, com.wzy.study.model.ClassDAO"/>
```

### 在自定义的Application中进行ActiveAndroid初始化

```java
public class ExampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ActiveAndroid.initialize(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ActiveAndroid.dispose();
    }
}
```

-------
# 源码分析

## 初始化过程

我们先从ActiveAndroid的初始化过程入手，来分析ActiveAndroid的具体实现机制.

```java
public static void initialize(Context context) {
    initialize(new Configuration.Builder(context).create());
}

public static void initialize(Configuration configuration) {
    initialize(configuration, false);
}

public static void initialize(Context context, boolean loggingEnabled) {
    initialize(new Configuration.Builder(context).create(), loggingEnabled);
}

public static void initialize(Configuration configuration, boolean loggingEnabled) {
    // Set logging enabled first
    setLoggingEnabled(loggingEnabled);
    Cache.initialize(configuration);
}
```

从上述代码中，我们在分析initialize源码之前，需要先看一下new Configuration.Builder(context).create()的具体实现。源码如下：
```java
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
```
代码非常简单，就是解析AndroidManifest.xml，就是生成Configuration配置信息类。
回到initialize函数，省去log开关的设置，我们来看一下Cache.initialize中做了哪些操作.源码如下：
```java
public static synchronized void initialize(Configuration configuration) {
    if (sIsInitialized) {
        Log.v("ActiveAndroid already initialized.");
        return;
    }

    // 获取进程上下文
    sContext = configuration.getContext();
    // 初始化Model信息，在这里找到所有的数据库表
    sModelInfo = new ModelInfo(configuration);
    // 初始化DatabaseHelper，在这里执行数据库创建、数据库升级等操作
    sDatabaseHelper = new DatabaseHelper(configuration);

    // LruCache保存映射关系
    sEntities = new LruCache<String, Model>(configuration.getCacheSize());

    // 创建数据库
    openDatabase();

    sIsInitialized = true;

    Log.v("ActiveAndroid initialized successfully.");
}
```

从上述代码中，我们首先需要跟进ModelInfo，看一下数据库表信息是如何获取的：
```java
public ModelInfo(Configuration configuration) {
    // 首先解析AndroidManifest中对应的Model对象
    if (!loadModelFromMetaData(configuration)) {
        try {
            // 如果AndroidManifest没声明，则从文件目录中去扫描继承自Model的类
            scanForModel(configuration.getContext());
        }
        catch (IOException e) {
            Log.e("Couldn't open source path.", e);
        }
    }

    Log.i("ModelInfo loaded.");
}
```

接下来，我们需要分析一下DatabaseHelper类的构造函数实现：
```java
public DatabaseHelper(Configuration configuration) {
    super(configuration.getContext(), configuration.getDatabaseName(), null, configuration.getDatabaseVersion());
    // 如果assets目录下存在相应的db文件，需要用该db文件创建数据库
    copyAttachedDatabase(configuration.getContext(), configuration.getDatabaseName());
    mSqlParser = configuration.getSqlParser();
}
```

同时，代码中openDatabase()的方法会调用DatabaseHelper类的onCreate方法：
```java
@Override
public void onCreate(SQLiteDatabase db) {
    // 启动外键
    executePragmas(db);
    // 创建数据库表
    executeCreate(db);
    // 迁移数据库，升级数据库
    executeMigrations(db, -1, db.getVersion());
    // 创建索引
    executeCreateIndex(db);
}

private void executeCreate(SQLiteDatabase db) {
    // 开启事务
    db.beginTransaction();
    try {
        for (TableInfo tableInfo : Cache.getTableInfos()) {
            // 创建不同的表
            db.execSQL(SQLiteUtils.createTableDefinition(tableInfo));
        }
        db.setTransactionSuccessful();
    }
    finally {
        db.endTransaction();
    }
}
```

可见，ActiveAndroid其实就是帮助我们拼接SQL，最后通过db的execSQL去执行相应的操作。

拼接构建表SQL语句如下：
```java
public static String createTableDefinition(TableInfo tableInfo) {
    final ArrayList<String> definitions = new ArrayList<String>();

    for (Field field : tableInfo.getFields()) {
        String definition = createColumnDefinition(tableInfo, field);
        if (!TextUtils.isEmpty(definition)) {
            definitions.add(definition);
        }
    }

    definitions.addAll(createUniqueDefinition(tableInfo));

    return String.format("CREATE TABLE IF NOT EXISTS %s (%s);", tableInfo.getTableName(),
            TextUtils.join(", ", definitions));
}
```

至此，数据库的创建过程就已经完成了。

--------
# 数据库查询

在ActiveAndroid中，数据库操作的类都被放在query包中，里面的类都继承了Sqlable接口，这个接口里面只有一个函数，它的功能就是将类转化为各种SQL语句：
```java
public interface Sqlable {
	public String toSql();
}
```

比较特殊的是里面的From类。它包含了最后的数据动作，实际的数据库动作都是发生在这个类里面的两个函数中，最后执行execute时，会将数据库操作类的内容先转换为SQL语句，之后执行数据库操作。
执行多个操作：
```java
public <T extends Model> List<T> execute() {
    if (mQueryBase instanceof Select) {
        return SQLiteUtils.rawQuery(mType, toSql(), getArguments());
        
    } else {
        SQLiteUtils.execSql(toSql(), getArguments());
        Cache.getContext().getContentResolver().notifyChange(ContentProvider.createUri(mType, null), null);
        return null;
        
    }
}
```
