package com.wzy.study;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.activeandroid.util.Log;
import com.wzy.study.model.ClassDAO;
import com.wzy.study.model.StudentDAO;

import java.util.List;

public class MainActivity extends AppCompatActivity {


    private static int sAge = 1;
    private static int num = 1;
    private static int sScore = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onInsert(View view) {
        StudentDAO studentDAO = new StudentDAO();
        studentDAO.age = sAge ++;
        studentDAO.name = "name" + (num ++);
        studentDAO.score = sScore;
        studentDAO.save();
    }

    public void onDelete(View view) {
        new Delete().from(StudentDAO.class).where("age = 100").executeSingle();
    }

    public void onSelect(View view) {
        List<StudentDAO> list = new Select().from(StudentDAO.class).execute();
        for (StudentDAO dao : list) {
            Log.e("wangzhengyi", dao.toString());
        }
    }
}
