# ActiveAndroid源码分析

-------
# 基本使用

## ActiveAndroid集成
在AndroidStudio中,我们可以通过两种方式集成ActiveAndroid.

第一种是使用Gradle配置依赖:
```gradle
repositories {
    mavenCentral()
    maven { url "https://oss.sonatype.org/content/repositories/snapshots/" }
}

compile 'com.michaelpardo:activeandroid:3.1.0-SNAPSHOT'
```

第二种是集成ActiveAndroid的jar包.
[ActiveAndroid JAR包下载地址](https://github.com/pardom/ActiveAndroid/downloads)


## 自定义Model类

ActiveAndroid提供了一个Model类,用户可以继承Model,生成自己的表结构,一个Model就代表了SQLite数据库中的一张表结构.
这里我声明一个学生表,示例代码如下:
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

## 在AndroidManifest.xml中注册相关信息

为了ActiveAndroid的运行效率,使用者需要在AndroidManifest.xml中注册数据库名称、版本号、表的代码路径等相关信息,参考设置如下:
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

## 在Application中进行ActiveAndroid初始化和析构操作

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

### ActiveAndroid.java

我们先从ActiveAndroid的初始化过程入手,来分析ActiveAndroid的具体实现机制.

```java
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
```

从initialize构造函数来看,我们在分析initialize源码实现之前,需要先看一下```new Configuration.Builder(context).create()```的具体实现.

### Configuration.java

Configuration是ActiveAndroid的配置类,用来记录用户设置的数据库名称,数据库版本,数据库表等相关信息.
它采用构造者模式创建,我们只需要关注一下Configuration.Builder(context).create()的具体实现即可.源码如下:
```java
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
```
从上述源码可以分析出,Configuration类主要保存数据库名称、数据库版本、SQL解释器名称、TypeSerializers集合.
了解了Configuration类的构造过程,我们需要继续回到ActiveAndroid类的initialize方法.
```java
public static void initialize(Configuration configuration, boolean loggingEnabled) {
    // 设置日志开关(ps:优秀的开源项目都会控制日志输出)
    setLoggingEnabled(loggingEnabled);
    Cache.initialize(configuration);
}
```
其中,setLoggingEnabled是用来设置打印开关的,好的项目都会控制日志输出,这块操作都是大同小异,我们就不去深究了.
接下来,我们去跟踪一下Cache类,看一下Cache类的initialize做了什么操作.

### Cache.java

Cache.initialize()的源码如下:
```java
public static synchronized void initialize(Configuration configuration) {
    // 确保ActiveAndroid只初始化一次
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

### ModelInfo.java

中文注解的源码如下:
```java
final class ModelInfo {
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

    /**
     * ModelInfo的构造函数
     *
     * @param configuration ActiveAndroid的配置类
     */
    public ModelInfo(Configuration configuration) {
        // 首先解析AndroidManifest中对应的Model对象
        if (!loadModelFromMetaData(configuration)) {
            try {
                // 如果用户在AndroidManifest没声明自定义的Model类,
                // 则从apk安装目录中去扫描继承自Model的类
                scanForModel(configuration.getContext());
            } catch (IOException e) {
                Log.e("Couldn't open source path.", e);
            }
        }

        Log.i("ModelInfo loaded.");
    }

    /**
     * 从AndroidManifest.xml的meta-data中构建<Model, TableInfo>映射集合
     *
     * @return true: 构建成功; false:用户没有在AndroidManifest中声明AA_MODELS
     */
    private boolean loadModelFromMetaData(Configuration configuration) {
        // 判断用户是否在AndroidManifest中声明了自定义的Model类路径
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

        // TODO:了解TypeSerializer的作用
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
        // 获取应用的PackageName
        String packageName = context.getPackageName();
        // 获取应用的安装路径
        String sourcePath = context.getApplicationInfo().sourceDir;
        // 存储应用的所有文件集合
        List<String> paths = new ArrayList<String>();

        if (sourcePath != null && !(new File(sourcePath).isDirectory())) {
            // 将apk文件转成DexFile
            DexFile dexfile = new DexFile(sourcePath);
            Enumeration<String> entries = dexfile.entries();
            // 获取应用所有的文件路径集合
            while (entries.hasMoreElements()) {
                paths.add(entries.nextElement());
            }
        } else {
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
通过ModelInfo源码我们可以得知,用户在使用ActiveAndroid时,最好把自己自定义的Model类声明在AndroidManifest中,否则ActiveAndroid就需要根据安装应用对应的DexFile去扫描应用内部全部的类,找出用户自定义的Model类,这是很耗时的操作.

### TableInfo.java

同时在ModelInfo.java的源码中,我们可以看到mTableInfos存储的是Model的Class类类型和TableInfo的键值对.
因为需要看一下TableInfo是如何利用Model的Class类类型生成的.TableInfo的构造函数源码如下:
```java
/**
 * 构造函数,根据用户自定义的Model类生成TableInfo对象
 */
public TableInfo(Class<? extends Model> type) {
    mType = type;

    final Table tableAnnotation = type.getAnnotation(Table.class);

    if (tableAnnotation != null) {
        // 如果有Table注解,则使用Table注解中的表名和主键名
        mTableName = tableAnnotation.name();
        mIdName = tableAnnotation.id();
    } else {
        // 没有Table注解,使用类名作为表名
        mTableName = type.getSimpleName();
    }

    // 添加主键的Field和name的键值对(因为主键是不能在用户定义的Model类中进行声明的)
    Field idField = getIdField(type);
    mColumnNames.put(idField, mIdName);

    // 获取用户自定义Model中Field集合
    List<Field> fields = new LinkedList<Field>(ReflectionUtils.getDeclaredColumnFields(type));
    // Fields根据name进行字母升序排序
    Collections.reverse(fields);

    for (Field field : fields) {
        if (field.isAnnotationPresent(Column.class)) {
            final Column columnAnnotation = field.getAnnotation(Column.class);
            String columnName = columnAnnotation.name();
            if (TextUtils.isEmpty(columnName)) {
                columnName = field.getName();
            }

            mColumnNames.put(field, columnName);
        }
    }

}
```
从源码来看,TableInfo的构造函数就是通过注解来解析用户自定义Model的所有Field字段.
其中涉及到的两个注解非常是Table和Column.通过Table注解获取表名和主键key,通过Column注解获取表的其它字段名.

### Table.java
Table注解的源码如下:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {

	public static final String DEFAULT_ID_NAME = "Id";
	public String name();
	public String id() default DEFAULT_ID_NAME;
}
```
从Table注解的源码来看,Table注解是作用在类或者接口上,生命周期是运行时.其中name是表名,id是主键名.

### Column.java
Column是用来表示每一列字段的,因此成员属性会比较复杂,中文注释的源码如下:
```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
    /**
     * 约束冲突的执行算法.
     * ROLLBACK:当执行的SQL语句违反约束条件时,会停止当前执行的SQL语句,并将数据恢复到操作之前的状态.
     * ABORT:当执行的SQL语句违反约束条件时,会停止当前执行的SQL语句,并将数据恢复到操作之前的状态,不过当前事务下先前执行的SQL语句造成的数据变动并不会受到影响.
     * FAIL:当执行的SQL语句违反约束条件时,会停止当前执行的SQL语句,不过,先去执行的SQL语句造成的数据变化不会受到影响,而后面的SQL语句不会被执行.
     * IGNORE:当执行的SQL语句违反约束条件时,那么这次数据将不会生效,但是后续的SQL语句会被继续执行.
     * REPLACE:当插入或者修改数据时违反了唯一性的约束时,新的数据会替换掉旧的数据.
     */
    public enum ConflictAction {
        ROLLBACK, ABORT, FAIL, IGNORE, REPLACE
    }

    /**
     * 外键约束在ON DELETE和ON UPDATE的行为.
     * SET NULL:父键被删除(ON DELTETE SET NULL)或者修改(ON UPDATE SET NULL)时将外键字段设置为NULL.
     * SET_DEFAULT:父键被删除(ON DELETE SET DEFAULT)或者修改(ON UPDATE SET DEFAULT)时将外键字段设置为默认值.
     * CASCADE:将实施在父键上的删除或者更新操作传递给关联的子键.
     * RESTRICT:存在一个或者多个子键外键引用了相应的父键时,SQLite禁止删除(ON DELETE RESTRICT)或者更新(ON UPGRADE RESTRICT)父键.
     * NO ACTION:如果没有明确指定行为,那么默认的行为就是NO ACTION.表示父键被修改或者删除时,没有特别的行为发生.
     */
    public enum ForeignKeyAction {
        SET_NULL, SET_DEFAULT, CASCADE, RESTRICT, NO_ACTION
    }

    /**
     * 列名称
     */
    public String name() default "";

    /**
     * 列长度
     */
    public int length() default -1;

    /**
     * 列是否非空
     */
    public boolean notNull() default false;

    /**
     * 约束冲突的执行算法,默认为FAIL
     */
    public ConflictAction onNullConflict() default ConflictAction.FAIL;

    /**
     * 外键约束,ON DELETE的默认处理是NO ACTION
     */
    public ForeignKeyAction onDelete() default ForeignKeyAction.NO_ACTION;

    /**
     * 外键约束,ON UPDATE的默认处理是NO ACTION
     */
    public ForeignKeyAction onUpdate() default ForeignKeyAction.NO_ACTION;

    /**
     * SQLite的UNIQUE约束
     */
    public boolean unique() default false;

    public ConflictAction onUniqueConflict() default ConflictAction.FAIL;

    /**
     * SQLite多个列的UNIQUE约束
     */
    public String[] uniqueGroups() default {};

    public ConflictAction[] onUniqueConflicts() default {};

    /**
     * 如果设置index=true,会创建单列索引.
     * 例如:
     *
     * @Table(name = "table_name")
     * public class Table extends Model {
     * @Column(name = "member", index = true)
     * public String member;
     * }
     * 构建索引语句: CREATE INDEX index_table_name_member on table_name(member)
     */
    public boolean index() default false;

    /**
     * 如果设置indexGroups = ["group_name"],会创建组合索引.
     * 例如:
     *
     * @Table(name = "table_name")
     * public class Table extends Model {
     * @Column(name = "member1", indexGroups = {"group1"})
     * public String member1;
     * @Column(name = "member2", indexGroups = {"group1", "group2"})
     * public String member2;
     * @Column(name = "member3", indexGroups = {"group2"})
     * public String member3;
     * }
     * 构建索引语句:
     * CREATE INDEX index_table_name_group1 on table_name(member1, member2)
     * CREATE INDEX index_table_name_group2 on table_name(member2, member3)
     */
    public String[] indexGroups() default {};
}
```

### DatabaseHelper.java

了解了ModelInfo的初始化过程,知道了Table注解和Column注解的实现,接下来,我们需要看一下DatabaseHelper的源码实现了.
需要明确,我们看源码是要带着问题去看源码,这里我抛出两个问题:

1. DatabaseHelper是如何解析Table和Column注解,生成建表语句的.
2. DatabaseHelper是如何执行数据库升级的.

大家带着问题,来学习一下中文注解的DatabaseHelper源码:
```java
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
```

通过源码,我们来解答开始提出的两个问题.

### 创建SQLite数据库表结构

我们知道,当执行getWritableDatabase操作时会触发到DatabaseHelper类的onCreate操作.在onCreate方法中,我们可以观察到executeCreate是真正用户表创建的地方.
而建表语句是通过```java SQLiteUtils.createTableDefinition(tableInfo) ```实现的.这部分源码实现如下:
```java
/**
 * 生成建表语句.通过解析Column注解来完成每一列的构建,然后拼接成完整的建表语句.
 */
public static String createTableDefinition(TableInfo tableInfo) {
    final ArrayList<String> definitions = new ArrayList<String>();

    for (Field field : tableInfo.getFields()) {
        // 生成每一列的构建语句
        String definition = createColumnDefinition(tableInfo, field);
        if (!TextUtils.isEmpty(definition)) {
            definitions.add(definition);
        }
    }

    definitions.addAll(createUniqueDefinition(tableInfo));

    // 拼接建表语句
    return String.format("CREATE TABLE IF NOT EXISTS %s (%s);", tableInfo.getTableName(),
            TextUtils.join(", ", definitions));
}

/**
 * 创建每一列的构建语句
 */
@SuppressWarnings("unchecked")
public static String createColumnDefinition(TableInfo tableInfo, Field field) {
    StringBuilder definition = new StringBuilder();

    Class<?> type = field.getType();
    // 获取列名
    final String name = tableInfo.getColumnName(field);
    final TypeSerializer typeSerializer = Cache.getParserForType(field.getType());
    // 获取列的注解
    final Column column = field.getAnnotation(Column.class);

    if (typeSerializer != null) {
        type = typeSerializer.getSerializedType();
    }

    if (TYPE_MAP.containsKey(type)) {
        // 将Java的Field类型转换为SQLite的基本类型(INTEGER, REAL, TEXT, BLOB)
        definition.append(name);
        definition.append(" ");
        definition.append(TYPE_MAP.get(type).toString());
    } else if (ReflectionUtils.isModel(type)) {
        // 外键,使用Integer类型
        definition.append(name);
        definition.append(" ");
        definition.append(SQLiteType.INTEGER.toString());
    } else if (ReflectionUtils.isSubclassOf(type, Enum.class)) {
        // 枚举类型
        definition.append(name);
        definition.append(" ");
        definition.append(SQLiteType.TEXT.toString());
    }

    if (!TextUtils.isEmpty(definition)) {

        if (name.equals(tableInfo.getIdName())) {
            definition.append(" PRIMARY KEY AUTOINCREMENT");
        } else if (column != null) {
            if (column.length() > -1) {
                definition.append("(");
                definition.append(column.length());
                definition.append(")");
            }

            if (column.notNull()) {
                definition.append(" NOT NULL ON CONFLICT ");
                definition.append(column.onNullConflict().toString());
            }

            if (column.unique()) {
                definition.append(" UNIQUE ON CONFLICT ");
                definition.append(column.onUniqueConflict().toString());
            }
        }

        // 构建外键
        if (FOREIGN_KEYS_SUPPORTED && ReflectionUtils.isModel(type)) {
            definition.append(" REFERENCES ");
            definition.append(Cache.getTableInfo((Class<? extends Model>) type).getTableName());
            definition.append("(" + tableInfo.getIdName() + ")");
            definition.append(" ON DELETE ");
            definition.append(column.onDelete().toString().replace("_", " "));
            definition.append(" ON UPDATE ");
            definition.append(column.onUpdate().toString().replace("_", " "));
        }
    } else {
        Log.e("No type mapping for: " + type.toString());
    }

    return definition.toString();
}
```
ActiveAndroid的精髓就是将Java类转换为对应的SQL语句,大家有兴趣的可以单步跟一下建表语句的执行.

### 升级数据库表结构
数据库的升级需要参考onUpgrade函数的实现:
```java
@Override
public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    executePragmas(db);
    // 创建新表,因为建表语句是CRATE TABLE IF NOT EXIST,所以不用担心旧表被覆盖的问题
    executeCreate(db);
    // 旧表的修改使用asset/migrations/*.sql去修改
    executeMigrations(db, oldVersion, newVersion);
}
```
通过源码我们可以分析出:

1. 支持添加新表操作,因为数据库版本升级时会再次执行executeCreate()方法.
2. 支持对原有表结构的修改,参考实现executeMigrations函数.

executeMigrations的函数实现如下:
```java
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
```

------

### 小结

至此,ActiveAndroid的数据库创建过程就已经分析完成了.

--------
## 数据库增删改查

在ActiveAndroid中,数据库的操作类都放在query包中,里面的类都继承了Sqlable接口,所以我们需要先看一下Sqlable接口的实现.

### Sqlable.java

Sqlable这个接口里面只有一个函数,它的功能就是将类转化为各种SQL语句:
```java
public interface Sqlable {
	public String toSql();
}
```

### 插入数据和更新数据

ActiveAndroid中,插入操作和更新操作都是通过Model类的save方法实现的.这里以插入操作为例,对源码进行讲解.

我们先来看一下ActiveAndroid是如何插入数据的,例如我们插入一条学生数据,实现代码如下:

```java
public void onInsert(View view) {
    StudentDAO studentDAO = new StudentDAO();
    studentDAO.age = 1;
    studentDAO.name = "name1";
    studentDAO.score = 100;
    studentDAO.sex = "man";
    studentDAO.save();
}
```

深入到源码级别,我们来分析一下save的实现机制.

```java
public final Long save() {
    // 获取SQLite数据库写权限句柄
    final SQLiteDatabase db = Cache.openDatabase();
    final ContentValues values = new ContentValues();

    for (Field field : mTableInfo.getFields()) {
        final String fieldName = mTableInfo.getColumnName(field);
        Class<?> fieldType = field.getType();

        field.setAccessible(true);

        try {
            // 通过反射获取每一列的值
            Object value = field.get(this);

            if (value != null) {
                final TypeSerializer typeSerializer = Cache.getParserForType(fieldType);
                if (typeSerializer != null) {
                    // serialize data
                    value = typeSerializer.serialize(value);
                    // set new object type
                    if (value != null) {
                        fieldType = value.getClass();
                        // check that the serializer returned what it promised
                        if (!fieldType.equals(typeSerializer.getSerializedType())) {
                            Log.w(String.format("TypeSerializer returned wrong type: expected a %s but got a %s",
                                    typeSerializer.getSerializedType(), fieldType));
                        }
                    }
                }
            }

            // 根据File的type将Object value转成相应类型的值,存放在ContentValues中
            if (value == null) {
                values.putNull(fieldName);
            } else if (fieldType.equals(Byte.class) || fieldType.equals(byte.class)) {
                values.put(fieldName, (Byte) value);
            } else if (fieldType.equals(Short.class) || fieldType.equals(short.class)) {
                values.put(fieldName, (Short) value);
            } else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
                values.put(fieldName, (Integer) value);
            } else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
                values.put(fieldName, (Long) value);
            } else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
                values.put(fieldName, (Float) value);
            } else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
                values.put(fieldName, (Double) value);
            } else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
                values.put(fieldName, (Boolean) value);
            } else if (fieldType.equals(Character.class) || fieldType.equals(char.class)) {
                values.put(fieldName, value.toString());
            } else if (fieldType.equals(String.class)) {
                values.put(fieldName, value.toString());
            } else if (fieldType.equals(Byte[].class) || fieldType.equals(byte[].class)) {
                values.put(fieldName, (byte[]) value);
            } else if (ReflectionUtils.isModel(fieldType)) {
                values.put(fieldName, ((Model) value).getId());
            } else if (ReflectionUtils.isSubclassOf(fieldType, Enum.class)) {
                values.put(fieldName, ((Enum<?>) value).name());
            }
        } catch (IllegalArgumentException e) {
            Log.e(e.getClass().getName(), e);
        } catch (IllegalAccessException e) {
            Log.e(e.getClass().getName(), e);
        }
    }


    if (mId == null) {
        // 当前用户Id为null,则进行插入操作
        mId = db.insert(mTableInfo.getTableName(), null, values);
    } else {
        // 当前用户Id不为null,则进入更新操作
        db.update(mTableInfo.getTableName(), values, idName + "=" + mId, null);
    }

    // 通知ContentProvider
    Cache.getContext().getContentResolver()
            .notifyChange(ContentProvider.createUri(mTableInfo.getType(), mId), null);
    return mId;
}
```

从源码来看,实现还是很简单的.首先,通过反射机制,构造ContentValues.然后通过Model的mId是否为null,来判断是执行db.insert操作还是db.update操作.

### 删除数据

了解了插入和更新操作,我们继续来看一下删除操作的实现.

ActiveAndroid中,可以通过两种方法进行删除操作.

第一种是调用Model.delete方法,这种调用比较简单,我们直接上源码:
```java
public final void delete() {
    Cache.openDatabase().delete(mTableInfo.getTableName(), idName + "=?", new String[]{getId().toString()});
    Cache.removeEntity(this);

    Cache.getContext().getContentResolver()
            .notifyChange(ContentProvider.createUri(mTableInfo.getType(), mId), null);
}
```
从代码看,就是直接调用了db.delete方法,删除的条件是依赖于主键的值.

第二种是使用Delete对象,我们先看一下示例代码:
```java
public void onDelete(View view) {
    new Delete().from(StudentDAO.class).where("age = ?", new String[] {"1"}).execute();
}
```

从代码里也能隐约看出SQL拼接的影子,我们从源码来跟踪一下其具体实现.

注意:不要将Delete配合executeSingle使用,因为这样使用不可避免的会碰到两个坑:

1. SQLite默认不支持DELETE和LIMIT并存的操作.而executeSingle会使用Limt 1语句.
2. 使用Delete和executeSingle配合,其实是先执行SELETE操作,然后再执行Model的delte操作.但是ActiveAndroid源码中没有判空,会导致空指针.我已经提交PR解决该问题:https://github.com/pardom/ActiveAndroid/pull/510

### Delete.java

我们先看一下Delete.java的源码实现:
```java
public final class Delete implements Sqlable {
	public Delete() {
	}

	public From from(Class<? extends Model> table) {
		return new From(table, this);
	}

	@Override
	public String toSql() {
		return "DELETE ";
	}
}
```
Delete类继承自Sqlable,重写了toSql()方法,返回的是"DELETE ".同时,在它的from方法中,构造From类,并将自己传递给From.
我们继续跟踪一下From类的具体实现.

### From.java
From是ORM转换的核心实现,且这个类中包含了最后的数据动作execute和executeSingle,我们从源码来分析一下From类的具体实现:
```java
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

        mJoins = new ArrayList<Join>();
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
```
From类其实是对SQL语句的拼接具体实现,那SQL语句的具体执行其实是通过SQLiteUtils来执行的.

### SQLiteUtils.java

在SQLiteUtils.java中,执行SQL语句的方法非常简单:
```java
public static void execSql(String sql) {
    Cache.openDatabase().execSQL(sql);
}

public static void execSql(String sql, Object[] bindArgs) {
    Cache.openDatabase().execSQL(sql, bindArgs);
}
```
从源码看,其实就是调用了SQLiteDatabase的execSQL方法.


### 查找数据

查找数据的过程其实和删除数据的过程很类似,都是通过From类去拼接SQL,最后通过SQLiteUtils去执行.
我们首先看一下ActiveAndroid中如何查询数据:
```java
public void onSelect(View view) {
    List<StudentDAO> list = new Select().from(StudentDAO.class).execute();
    for (StudentDAO dao : list) {
        Log.e("wangzhengyi", dao.toString());
    }
}
```

### Select.java

```java
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
```

可以看到,在Selete类中,我们可以指定需要查找的列,并声明是否为DISTINCT.

### SQLiteUtils.java

接下来,直接讲解一下SELETE的具体实现.
首先,分析一下From类中SELETE真正执行的代码:
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
```
从源码中可以看出,SELETE的执行其实最终对应着SQLiteUtils的rawQuery和rawQuerySingle这两个方法.

rawQuery的源码如下:
```java
public static <T extends Model> List<T> rawQuery(Class<? extends Model> type, String sql, String[] selectionArgs) {
    Cursor cursor = Cache.openDatabase().rawQuery(sql, selectionArgs);
    List<T> entities = processCursor(type, cursor);
    cursor.close();

    return entities;
}
```
从源码中,可以看到,获取cursor的过程都是通过SQLiteDatabase的rawQuery方法,处理Cursor的方法如下:
```java
@SuppressWarnings("unchecked")
public static <T extends Model> List<T> processCursor(Class<? extends Model> type, Cursor cursor) {
    TableInfo tableInfo = Cache.getTableInfo(type);
    String idName = tableInfo.getIdName();
    final List<T> entities = new ArrayList<T>();

    try {
        Constructor<?> entityConstructor = type.getConstructor();

        if (cursor.moveToFirst()) {
            /**
             * Obtain the columns ordered to fix issue #106 (https://github.com/pardom/ActiveAndroid/issues/106)
             * when the cursor have multiple columns with same name obtained from join tables.
             */
            List<String> columnsOrdered = new ArrayList<String>(Arrays.asList(cursor.getColumnNames()));
            do {
                // 判断LruCache缓存中是否存在Model类.
                Model entity = Cache.getEntity(type, cursor.getLong(columnsOrdered.indexOf(idName)));
                if (entity == null) {
                    entity = (T) entityConstructor.newInstance();
                }

                // 解析Cursor,填充用户自定义的Model对象的field成员
                entity.loadFromCursor(cursor);
                entities.add((T) entity);
            }
            while (cursor.moveToNext());
        }

    } catch (NoSuchMethodException e) {
        throw new RuntimeException(
                "Your model " + type.getName() + " does not define a default " +
                        "constructor. The default constructor is required for " +
                        "now in ActiveAndroid models, as the process to " +
                        "populate the ORM model is : " +
                        "1. instantiate default model " +
                        "2. populate fields"
        );
    } catch (Exception e) {
        Log.e("Failed to process cursor.", e);
    }

    return entities;
}
```

到此为止,ActiveAndroid的增删改查操作基本介绍完毕了.
