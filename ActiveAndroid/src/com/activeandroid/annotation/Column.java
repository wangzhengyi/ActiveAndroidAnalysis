package com.activeandroid.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
