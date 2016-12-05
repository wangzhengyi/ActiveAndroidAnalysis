package com.activeandroid;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.text.TextUtils;
import android.util.Log;

import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.util.ReflectionUtils;

public final class TableInfo {
    /**
     * 当前用户自定义Model的Class对象
     */
    private Class<? extends Model> mType;

    /**
     * 表名
     */
    private String mTableName;

    /**
     * 表主键的名称,默认为"id"
     */
    private String mIdName = Table.DEFAULT_ID_NAME;

    /**
     * 表的每一列和其名称的Map映射
     */
    private Map<Field, String> mColumnNames = new LinkedHashMap<Field, String>();

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

    //////////////////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    //////////////////////////////////////////////////////////////////////////////////////

    public Class<? extends Model> getType() {
        return mType;
    }

    public String getTableName() {
        return mTableName;
    }

    public String getIdName() {
        return mIdName;
    }

    public Collection<Field> getFields() {
        return mColumnNames.keySet();
    }

    public String getColumnName(Field field) {
        return mColumnNames.get(field);
    }

    /**
     * 获取主键的Field,即mId成员代表的Field.
     */
    private Field getIdField(Class<?> type) {
        if (type.equals(Model.class)) {
            try {
                return type.getDeclaredField("mId");
            } catch (NoSuchFieldException e) {
                Log.e("Impossible!", e.toString());
            }
        } else if (type.getSuperclass() != null) {
            return getIdField(type.getSuperclass());
        }

        return null;
    }

}
