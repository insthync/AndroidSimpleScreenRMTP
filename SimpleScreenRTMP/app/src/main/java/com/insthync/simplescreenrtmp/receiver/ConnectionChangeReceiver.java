/**
 * Project Name:feizao File Name:ConnectionChangeReceiver.java Package
 * Name:com.efeizao.feizao.receiver Date:2015-6-23下午6:37:38
 */

package com.insthync.simplescreenrtmp.receiver;

/**
 * ClassName:ConnectionChangeReceiver Function: TODO ADD FUNCTION. Reason: TODO
 * ADD REASON. Date: 2015-6-23 下午6:37:38
 *
 * @version 1.0
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class ConnectionChangeReceiver extends BroadcastReceiver {

	private NetwrokChangeCallback callback;

	/**
	 * 网络状态改变回调接口
	 * @version ConnectionChangeReceiver
	 * @since JDK 1.6
	 */
	public interface NetwrokChangeCallback {
		/** WiFi网路 */
		void wifiConnected();

		/** 移动网络（2G、3G、4G） */
		void gprsConnected();

		/** 无网络 */
		void noConnected();
	}

	public void setOnNetChangeListener(NetwrokChangeCallback callback) {
		this.callback = callback;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		ConnectivityManager connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mobNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		NetworkInfo wifiNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		// 如果获取不到网络状态（备注：因为有些手机获取不到网络状态）则认为是有网络；或者isConnected
		if (wifiNetInfo == null || wifiNetInfo.isConnected()) {
			if (callback != null) {
				callback.wifiConnected();
			}
		} else if (mobNetInfo == null || mobNetInfo.isConnected()) {
			if (callback != null) {
				callback.gprsConnected();
			}
		} else {
			if (callback != null) {
				callback.noConnected();
			}
		}
	}
}
