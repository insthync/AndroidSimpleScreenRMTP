package com.insthync.simplescreenrtmp.window;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;


/**
 * Created by Elvis on 2017/2/23.
 */

public class WindowRecordManager {
	private static final String TAG = "windowManager";

	//用于控制在屏幕上添加或移除悬浮窗
	private WindowManager mWindowManager;

	//小悬浮窗View的实例
	private static WindowRecordSmall mWindowRecordSmall;
	private static WindowRecordBig mWindowRecordBig;

	//小悬浮View的参数
	private WindowManager.LayoutParams recordSmallWindowParams;
	private WindowManager.LayoutParams recordBigWindowParams;

	public static WindowRecordSmall getWindowRecordSmall() {
		return mWindowRecordSmall;
	}

	public static WindowRecordBig getWindowRecordBig() {
		return mWindowRecordBig;
	}

	/* **************************************************************** 小窗口 ***************************************************************************************** */
	// 1. 创建
	public void createSmallWindow(Context context) {
		Log.e(TAG, "createSmallWindow");
		//WindowManager基本用到:addView，removeView，updateViewLayout
		WindowManager windowManager = getWindowManager(context);
		//获取屏幕宽高 abstract Display  getDefaultDisplay()；  //获取默认显示的 Display 对象
		//设置小悬浮窗口的位置以及相关参数
		if (mWindowRecordSmall == null) {
			mWindowRecordSmall = new WindowRecordSmall(context);
			if (recordSmallWindowParams == null) {
				recordSmallWindowParams = new WindowManager.LayoutParams();
				recordSmallWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
				recordSmallWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
				recordSmallWindowParams.type = WindowManager.LayoutParams.TYPE_PHONE;//设置窗口的window type
				recordSmallWindowParams.format = PixelFormat.RGBA_8888;//设置图片格式，效果为背景透明
				recordSmallWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
						| WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;//下面的flags属性的效果形同“锁定”。 悬浮窗不可触摸，不接受任何事件,同时不影响后面的事件响应。
				recordSmallWindowParams.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;//调整悬浮窗口位置在左边中间
			}
			mWindowRecordSmall.setParams(recordSmallWindowParams);
			windowManager.addView(mWindowRecordSmall, recordSmallWindowParams);//将需要加到悬浮窗口中的View加入到窗口中
		}
	}

	/**
	 * 移除小窗口，未销毁
	 *
	 * @param context
	 */
	public void removeSamllWindow(Context context) {
		Log.e(TAG, "removeSamllWindow");
		if (mWindowRecordSmall != null) {
			WindowManager windowManager = getWindowManager(context);
			if (mWindowRecordSmall.isShown()) {
				//移除悬浮窗口
				windowManager.removeView(mWindowRecordSmall);
			}
		}
	}

	/**
	 * 移除并销毁小窗口
	 * 将小悬浮窗从屏幕上移除。
	 * abstract void removeViewImmediate(View view)；//是removeView(View) 的一个特殊扩展，
	 * 在方法返回前能够立即调用该视图层次的View.onDetachedFromWindow() 方法。
	 *
	 * @param context 必须为应用程序的Context.
	 */
	public void destroySmallWindow(Context context) {
		Log.e(TAG, "destroySmallWindow");
		if (mWindowRecordSmall != null) {
			WindowManager windowManager = getWindowManager(context);
			if (mWindowRecordSmall.isShown()) {
				//移除悬浮窗口
//				windowManager.removeView(mWindowRecordSmall);
				windowManager.removeViewImmediate(mWindowRecordSmall);
			}
			mWindowRecordSmall = null;
		}
	}

	/* **************************************************************** 大窗口 ***************************************************************************************** */
	//创建一个大悬浮窗，位于屏幕顶端
	public void createBigWindow(Context context) {
		Log.e(TAG, "createBigWindow");
		WindowManager windowManager = getWindowManager(context);
		if (mWindowRecordBig == null) {
			mWindowRecordBig = new WindowRecordBig(context);
			if (recordBigWindowParams == null) {
				recordBigWindowParams = new WindowManager.LayoutParams();
				recordBigWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
				recordBigWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
				recordBigWindowParams.format = PixelFormat.RGBA_8888;
				recordBigWindowParams.type = WindowManager.LayoutParams.TYPE_PHONE;
				recordBigWindowParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;//调整悬浮窗口位置在左边中间
				recordBigWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
						| WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;//下面的flags属性的效果形同“锁定”。 悬浮窗不可触摸，不接受任何事件,同时不影响后面的事件响应。
			}
		}
		windowManager.addView(mWindowRecordBig, recordBigWindowParams);
	}

	/**
	 * 移除大窗口 未销毁
	 * abstract void removeViewImmediate(View view)；//是removeView(View) 的一个特殊扩展，
	 * 在方法返回前能够立即调用该视图层次的View.onDetachedFromWindow() 方法。
	 *
	 * @param context
	 */
	public void removeBigWindow(Context context) {
		Log.e(TAG, "removeBigWindow");
		if (mWindowRecordBig != null) {
			WindowManager windowManager = getWindowManager(context);
			if (mWindowRecordBig.isShown()) {
				//移除悬浮窗口
				windowManager.removeView(mWindowRecordBig);
			}
		}
	}

	/**
	 * 移除并销毁大窗口
	 *
	 * @param context
	 */
	public void destoryBigWindow(Context context) {
		Log.e(TAG, "destoryBigWindow");
		if (mWindowRecordBig != null) {
			WindowManager windowManager = getWindowManager(context);
			if (mWindowRecordBig.isShown()) {
				windowManager.removeViewImmediate(mWindowRecordBig);
			}
			mWindowRecordBig = null;
		}
	}

	/**
	 * 销毁所有窗口
	 *
	 * @param context
	 */
	public void destoryWindow(Context context) {
		destoryBigWindow(context);
		destroySmallWindow(context);
	}

	/**
	 * 如果WindowManager还未创建，则创建一个新的WindowManager返回。否则返回当前已创建的WindowManager。
	 *
	 * @param context 必须为应用程序的Context.
	 * @return WindowManager的实例，用于控制在屏幕上添加或移除悬浮窗。
	 */
	public WindowManager getWindowManager(Context context) {
		if (mWindowManager == null) {
			mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		}
		return mWindowManager;
	}

}
