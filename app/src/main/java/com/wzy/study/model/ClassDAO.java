package com.wzy.study.model;

import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

/**
 * Created by zhengyi.wzy on 2016/11/29.
 */

@Table(name = "class")
public class ClassDAO {
    @Column(name = "studentsNum")
    public String studentsNum;

    @Column(name = "name")
    public String name;
}
