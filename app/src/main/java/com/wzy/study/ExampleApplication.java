package com.wzy.study;

import android.app.Application;

import com.activeandroid.ActiveAndroid;


public class ExampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ActiveAndroid.initialize(this, true);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ActiveAndroid.dispose();
    }
}
