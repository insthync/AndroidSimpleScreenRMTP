package com.insthync.simplescreenrtmp;

import android.app.Application;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Created by Elvis on 2017/8/2.
 */

public class App extends Application {
	public static DisplayMetrics metrics;
	/* 视频流程类型 流程，高清，超清 */
	public static final int CLARITY_TYPE_STADART = 0;
	public static final int CLARITY_TYPE_HIGTH = 1;
	public static final int CLARITY_TYPE_SUPER = 2;
	public static Context mAppContext;

	@Override
	public void onCreate() {
		super.onCreate();
		metrics = this.getResources().getDisplayMetrics();
		mAppContext = this.getApplicationContext();
	}

	@SuppressWarnings("deprecation")
	public static int[] getScreenWH() {
		WindowManager wm = (WindowManager) mAppContext.getSystemService(Context.WINDOW_SERVICE);
		int width = wm.getDefaultDisplay().getWidth();
		int height = wm.getDefaultDisplay().getHeight();
		return new int[]{width, height};
	}
}
