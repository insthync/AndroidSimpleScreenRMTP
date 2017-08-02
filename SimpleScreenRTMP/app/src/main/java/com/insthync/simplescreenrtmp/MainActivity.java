package com.insthync.simplescreenrtmp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.insthync.simplescreenrtmp.service.ScreenRecorderService;

public class MainActivity extends Activity implements View.OnClickListener {
	private static final int REQUEST_CODE = 1;
	private static final int REQUEST_STREAM = 2;
	private static String[] PERMISSIONS_STREAM = {
			Manifest.permission.CAMERA,
			Manifest.permission.RECORD_AUDIO,
			Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.CAPTURE_VIDEO_OUTPUT,
			Manifest.permission.CAPTURE_AUDIO_OUTPUT,
	};
	// 推流地址
	private String mPublishURL = "rtmp://188.166.191.129/live/test";
	// 横竖屏
	private int mCurrentScreenType = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
	// 分辨率   普清/高清/超清（手机最大支持）
	private int mClarityType = App.CLARITY_TYPE_HIGTH;

	private MediaProjectionManager mMediaProjectionManager;
	private int mCreateScreenCaptureResultCode;
	private Intent mCreateScreenCaptureResultData;
	// Now, Using service
	//private ScreenRecorder mRecorder;
	private Button mButton;
	boolean authorized = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mButton = (Button) findViewById(R.id.toggle);
		mButton.setOnClickListener(this);
		//noinspection ResourceType
		mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
		verifyPermissions();
	}

	public void verifyPermissions() {
		for (String permission : PERMISSIONS_STREAM) {
			int permissionResult = ActivityCompat.checkSelfPermission(MainActivity.this, permission);
			if (permissionResult != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(
						MainActivity.this,
						PERMISSIONS_STREAM,
						REQUEST_STREAM
				);
				authorized = false;
				return;
			}
		}
		authorized = true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQUEST_STREAM) {
			for (int grantResult : grantResults) {
				if (grantResult != PackageManager.PERMISSION_GRANTED) {
					authorized = false;
					return;
				}
			}
			authorized = true;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE) {
			// Now, Using service
			/*
			final int width = 640;
            final int height = 480;
            final int bitrate = 1000000;
            mRecorder = new ScreenRecorder(width, height, bitrate, 1, mediaProjection);
            mRecorder.start();
            */
			mCreateScreenCaptureResultCode = resultCode;
			mCreateScreenCaptureResultData = data;
			if (mCreateScreenCaptureResultCode != 0 && mCreateScreenCaptureResultData != null) {
				mButton.setText("Stop Recorder");
				Log.d("MainActivity", "ScreenRecorderService...");
				startRecordService();
				Toast.makeText(this, "Screen recorder is running...", Toast.LENGTH_SHORT).show();
			}
			//moveTaskToBack(true);
		}
	}

	// 启动录屏service
	protected void startRecordService() {
		// 当前没有在录屏
		if (!TextUtils.isEmpty(mPublishURL)) {
			Intent intent = new Intent(this, ScreenRecorderService.class);
			intent.putExtra(ScreenRecorderService.EXTRA_RESULT_SCREEN, mCurrentScreenType);
			intent.putExtra(ScreenRecorderService.EXTRA_RESULT_CLARITY, mClarityType);
			intent.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, mCreateScreenCaptureResultCode);
			intent.putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, mCreateScreenCaptureResultData);
			intent.putExtra(ScreenRecorderService.EXTRA_RTMP_ADDRESS, mPublishURL);
			Log.d("MainActivity", "publishUrl:" + mPublishURL);
			startService(intent);
		}
	}

	@Override
	public void onClick(View v) {
		// Now, Using service
		if (/*mRecorder != null*/ mCreateScreenCaptureResultCode != 0 && mCreateScreenCaptureResultData != null) {
			/*
			mRecorder.quit();
            mRecorder = null;
            */
			final Intent stopCastIntent = new Intent(ScreenRecorderService.ACTION_STOP);
			sendBroadcast(stopCastIntent);
			mButton.setText("Restart recorder");
		} else {
			Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
			startActivityForResult(captureIntent, REQUEST_CODE);
		}
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Now, Using service
		/*
		if(mRecorder != null){
            mRecorder.quit();
            mRecorder = null;
        }
        */
	}
}
