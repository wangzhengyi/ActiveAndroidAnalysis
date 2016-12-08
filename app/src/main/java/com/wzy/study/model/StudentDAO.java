package com.wzy.study.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

@Table(name = "Student")
public class StudentDAO extends Model {
    @Column(name = "name", index = true, notNull = true, onNullConflict = Column.ConflictAction.ROLLBACK)
    public String name;

    @Column(name = "age", notNull = true)
    public int age;

    @Column(name = "sex")
    public String sex;

    @Column(name = "score")
    public int score;

    @Column(name = "cid", onDelete = Column.ForeignKeyAction.CASCADE, onUpdate = Column.ForeignKeyAction.CASCADE)
    public ClassDAO classId;
}
