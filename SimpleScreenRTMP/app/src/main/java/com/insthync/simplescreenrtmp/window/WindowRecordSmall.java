package com.insthync.simplescreenrtmp.window;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.insthync.simplescreenrtmp.App;

/**
 * Created by Elvis on 2017/2/23.
 */

public class WindowRecordSmall extends LinearLayout implements View.OnTouchListener {

	private WindowManager windowManager;

	private WindowRecordManager recordWindowManager;

	private WindowManager.LayoutParams mParams; //小悬浮窗的参数
	// 触屏监听
	int oldOffsetX, oldOffsetY, tag = 0;// 悬浮球 所需成员变量
	float lastX, lastY;

	public WindowRecordSmall(Context context) {
		super(context);
		recordWindowManager = new WindowRecordManager();
		windowManager = recordWindowManager.getWindowManager(context);
//		LayoutInflater.from(context).inflate(R.layout.activity_record_window_small, this);
		// 设置手动监听，使其跟随手指移动
		setOnTouchListener(this);
		setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (recordWindowManager.getWindowRecordBig() == null || !recordWindowManager.getWindowRecordBig().isShown()) {
					recordWindowManager.createBigWindow(App.mAppContext);
				} else {
					recordWindowManager.removeBigWindow(App.mAppContext);
				}
			}
		});
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		float x = event.getX();
		float y = event.getY();
		if (tag == 0) {
			oldOffsetX = mParams.x; // 偏移量
			oldOffsetY = mParams.y; // 偏移量
		}
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				lastX = x;
				lastY = y;
				break;
			// 手指移动的时候更新小悬浮窗的位置
			case MotionEvent.ACTION_MOVE:
				mParams.x += (int) (x - lastX) / 3; // 减小偏移量,防止过度抖动
				mParams.y += (int) (y - lastY) / 3; // 减小偏移量,防止过度抖动
				tag = 1;
				windowManager.updateViewLayout(v, mParams);
				break;
			case MotionEvent.ACTION_UP:
				int newOffsetX = mParams.x;
				int newOffsetY = mParams.y;
				// 如果手指离开屏幕时，横纵偏移量都小于5，则视为点击事件，开启大悬浮窗
				if (Math.abs(oldOffsetX - newOffsetX) <= 20
						&& Math.abs(oldOffsetY - newOffsetY) <= 20) {
				} else {
					tag = 0;
				}
				break;
		}
		return super.onTouchEvent(event);
	}

	//设置小悬浮窗的参数
	public void setParams(WindowManager.LayoutParams params) {
		mParams = params;
	}
}
