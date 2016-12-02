package com.wzy.study.model;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

/**
 * Created by zhengyi.wzy on 2016/11/29.
 */

@Table(name = "class")
public class ClassDAO extends Model{
    @Column(name = "studentsNum")
    public String studentsNum;

    @Column(name = "name")
    public String name;
}
