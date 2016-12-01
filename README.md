# ActiveAndroid

-------
# 基本使用

### 声明Table

类似声明一个Students的表,需要集成Model类,代码如下:
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

使用者需要在AndroidManifest.xml中注册数据库名称、版本号、表的路径等相关信息,参考设置如下：
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

我们先从ActiveAndroid的初始化过程入手,来分析ActiveAndroid的具体实现机制.

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

从上述代码中,我们在分析initialize源码之前,需要先看一下new Configuration.Builder(context).create()的具体实现.

### Configuration.java

Configuration是ActiveAndroid的配置类,采用构造者模式创建,我们只需要关注一下Configuration.Builder(context).create()的具体实现即可.
源码如下：

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
代码非常简单,就是解析AndroidManifest.xml,就是生成Configuration配置信息类.

### Cache.java

回到initialize函数,省去log开关的设置,我们来看一下Cache.initialize中做了哪些操作.源码如下：
```java
public static synchronized void initialize(Configuration configuration) {
    if (sIsInitialized) {
        Log.v("ActiveAndroid already initialized.");
        return;
    }

    // 获取进程上下文
    sContext = configuration.getContext();
    // 初始化Model信息,在这里找到所有的数据库表
    sModelInfo = new ModelInfo(configuration);
    // 初始化DatabaseHelper,在这里执行数据库创建、数据库升级等操作
    sDatabaseHelper = new DatabaseHelper(configuration);

    // LruCache保存映射关系
    sEntities = new LruCache<String, Model>(configuration.getCacheSize());

    // 创建数据库
    openDatabase();

    sIsInitialized = true;

    Log.v("ActiveAndroid initialized successfully.");
}
```
从上述代码中,我们首先需要跟进ModelInfo,看一下数据库表信息是如何获取的.

#### ModelInfo.java

中文注解的源码如下：
```java
final class ModelInfo {
    //////////////////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * 存储Model和TableInfo的键值对Map
     * TableInfo是通过用户自定义Model解析出来的
     */
    private Map<Class<? extends Model>, TableInfo> mTableInfos = new HashMap<Class<? extends Model>, TableInfo>();
    private Map<Class<?>, TypeSerializer> mTypeSerializers = new HashMap<Class<?>, TypeSerializer>() {
        {
            put(Calendar.class, new CalendarSerializer());
            put(java.sql.Date.class, new SqlDateSerializer());
            put(java.util.Date.class, new UtilDateSerializer());
            put(java.io.File.class, new FileSerializer());
        }
    };

    //////////////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    //////////////////////////////////////////////////////////////////////////////////////

    public ModelInfo(Configuration configuration) {
        // 首先解析AndroidManifest中对应的Model对象
        if (!loadModelFromMetaData(configuration)) {
            try {
                // 如果AndroidManifest没声明,则从文件目录中去扫描继承自Model的类
                scanForModel(configuration.getContext());
            } catch (IOException e) {
                Log.e("Couldn't open source path.", e);
            }
        }

        Log.i("ModelInfo loaded.");
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    //////////////////////////////////////////////////////////////////////////////////////

    public Collection<TableInfo> getTableInfos() {
        return mTableInfos.values();
    }

    public TableInfo getTableInfo(Class<? extends Model> type) {
        return mTableInfos.get(type);
    }

    public TypeSerializer getTypeSerializer(Class<?> type) {
        return mTypeSerializers.get(type);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * 从AndroidManifest.xml的meta-data中构建<Model, TableInfo>映射集合
     *
     * @return true: 构建成功; false:用户没有在AndroidManifest中声明AA_MODELS
     */
    private boolean loadModelFromMetaData(Configuration configuration) {
        if (!configuration.isValid()) {
            return false;
        }

        // 解析每个Model对象,构造Model和TableInfo的键值对
        final List<Class<? extends Model>> models = configuration.getModelClasses();
        if (models != null) {
            for (Class<? extends Model> model : models) {
                mTableInfos.put(model, new TableInfo(model));
            }
        }

        final List<Class<? extends TypeSerializer>> typeSerializers = configuration.getTypeSerializers();
        if (typeSerializers != null) {
            for (Class<? extends TypeSerializer> typeSerializer : typeSerializers) {
                try {
                    TypeSerializer instance = typeSerializer.newInstance();
                    mTypeSerializers.put(instance.getDeserializedType(), instance);
                } catch (InstantiationException e) {
                    Log.e("Couldn't instantiate TypeSerializer.", e);
                } catch (IllegalAccessException e) {
                    Log.e("IllegalAccessException", e);
                }
            }
        }

        return true;
    }

    /**
     * 扫描apk对应的DexFile,获取所有的用户自定义Model类,并生成Model和TableInfo的Map集合
     */
    private void scanForModel(Context context) throws IOException {
        String packageName = context.getPackageName();
        // 获取应用的安装路径
        String sourcePath = context.getApplicationInfo().sourceDir;
        // 存储应用的所有文件集合
        List<String> paths = new ArrayList<String>();

        if (sourcePath != null && !(new File(sourcePath).isDirectory())) {
            // 将apk文件转成DexFile
            DexFile dexfile = new DexFile(sourcePath);
            Enumeration<String> entries = dexfile.entries();

            while (entries.hasMoreElements()) {
                paths.add(entries.nextElement());
            }
        }
        // Robolectric fallback
        else {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources("");

            while (resources.hasMoreElements()) {
                String path = resources.nextElement().getFile();
                if (path.contains("bin") || path.contains("classes")) {
                    paths.add(path);
                }
            }
        }

        for (String path : paths) {
            File file = new File(path);
            scanForModelClasses(file, packageName, context.getClassLoader());
        }
    }

    /**
     * 通过ClassLoader根据文件路径生成对应的Class类,查找Model类
     */
    private void scanForModelClasses(File path, String packageName, ClassLoader classLoader) {
        if (path.isDirectory()) {
            for (File file : path.listFiles()) {
                scanForModelClasses(file, packageName, classLoader);
            }
        } else {
            String className = path.getName();

            // Robolectric fallback
            if (!path.getPath().equals(className)) {
                className = path.getPath();

                if (className.endsWith(".class")) {
                    className = className.substring(0, className.length() - 6);
                } else {
                    return;
                }

                className = className.replace(System.getProperty("file.separator"), ".");

                int packageNameIndex = className.lastIndexOf(packageName);
                if (packageNameIndex < 0) {
                    return;
                }

                className = className.substring(packageNameIndex);
            }

            try {
                Class<?> discoveredClass = Class.forName(className, false, classLoader);
                if (ReflectionUtils.isModel(discoveredClass)) {
                    Log.e("wangzhengyi", "className=" + discoveredClass.getSimpleName());
                    @SuppressWarnings("unchecked")
                    Class<? extends Model> modelClass = (Class<? extends Model>) discoveredClass;
                    mTableInfos.put(modelClass, new TableInfo(modelClass));
                } else if (ReflectionUtils.isTypeSerializer(discoveredClass)) {
                    TypeSerializer instance = (TypeSerializer) discoveredClass.newInstance();
                    mTypeSerializers.put(instance.getDeserializedType(), instance);
                }
            } catch (ClassNotFoundException e) {
                Log.e("Couldn't create class.", e);
            } catch (InstantiationException e) {
                Log.e("Couldn't instantiate TypeSerializer.", e);
            } catch (IllegalAccessException e) {
                Log.e("IllegalAccessException", e);
            }
        }
    }
}
```
通过ModelInfo源码,我们可以得知,用户再使用ActiveAndroid时,最好把自己自定义的Model类声明在AndroidManifest中,否则ActiveAndroid就需要根据应用对应的DexFile去扫描应用内部全部的类,找出用户自定义的Model类,这是很耗时的操作.

#### DatabaseHelper.java

DatabaseHelper的中文注释源码如下：
```java
public final class DatabaseHelper extends SQLiteOpenHelper {
    //////////////////////////////////////////////////////////////////////////////////////
    // PUBLIC CONSTANTS
    //////////////////////////////////////////////////////////////////////////////////////

    public final static String MIGRATION_PATH = "migrations";

    //////////////////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    //////////////////////////////////////////////////////////////////////////////////////

    private final String mSqlParser;

    //////////////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    //////////////////////////////////////////////////////////////////////////////////////

    public DatabaseHelper(Configuration configuration) {
        super(configuration.getContext(), configuration.getDatabaseName(), null, configuration.getDatabaseVersion());
        copyAttachedDatabase(configuration.getContext(), configuration.getDatabaseName());
        mSqlParser = configuration.getSqlParser();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // OVERRIDEN METHODS
    //////////////////////////////////////////////////////////////////////////////////////

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
        executePragmas(db);
        // 创建新表,因为建表语句是CRATE TABLE IF NOT EXIST,所以不用担心旧表被覆盖的问题
        executeCreate(db);
        // 旧表的修改使用asset/migrations/*.sql去改变
        executeMigrations(db, oldVersion, newVersion);
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    //////////////////////////////////////////////////////////////////////////////////////

    /**
     * 拷贝应用assets目录下同名SQLite db文件到应用所在的/data/data/package/database目录下
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

    //////////////////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    //////////////////////////////////////////////////////////////////////////////////////

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
     * 生成数据库的所有表结构
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
```

回顾Cache类的initialize方法:
```java
sDatabaseHelper = new DatabaseHelper(configuration);
openDatabase();
```

其中：

1. DatabaseHelper的构造函数会帮助我们设置当前数据库的名称和版本号,同时如果asset目录下存在同名db文件且当前应用还没创建过该db文件,会帮助我们做一个迁移操作.
2. openDatabase()方法会帮助回调到DatabaseHelper类的onCreate方法创建所有的表结构,如果涉及到数据库升级,还会帮助我们回调onUpgarde方法.

------

### 小结

至此,ActiveAndroid的数据库创建过程就已经分析完成了.

--------
# 数据库查询

在ActiveAndroid中,数据库操作的类都被放在query包中,里面的类都继承了Sqlable接口,这个接口里面只有一个函数,它的功能就是将类转化为各种SQL语句：
```java
public interface Sqlable {
	public String toSql();
}
```

比较特殊的是里面的From类.它包含了最后的数据动作,实际的数据库动作都是发生在这个类里面的两个函数中,最后执行execute时,会将数据库操作类的内容先转换为SQL语句,之后执行数据库操作.
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
