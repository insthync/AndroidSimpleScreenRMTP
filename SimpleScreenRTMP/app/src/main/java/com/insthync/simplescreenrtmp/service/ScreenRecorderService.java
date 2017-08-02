package com.insthync.simplescreenrtmp.service;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.insthync.simplescreenrtmp.App;
import com.insthync.simplescreenrtmp.receiver.ConnectionChangeReceiver;
import com.insthync.simplescreenrtmp.window.WindowRecordManager;

import net.butterflytv.rtmp_client.RTMPMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;

@TargetApi(21)
public class ScreenRecorderService extends Service {
	private final String TAG = "ScreenRecorderService";
	public static final String ACTION_STOP = "ACTION_RESTART";
	public static final String ACTION_SCREEN_RECORD_STATUS = "ACTION_SCREEN_RECORD_STATUS";
	// 连接状态
	public static final String SCREEN_RECORD_STATUS = "screenStatus";
	public static final int SCREEN_RECORD_STATUS_START = 100;
	public static final int SCREEN_RECORD_STATUS_STOP = -1;
	public static final int SCREEN_RECORD_STATUS_FAIL = -100;
	// Default Video Record Setting
	public static final int DEFAULT_SCREEN_DPI = App.metrics.densityDpi;
	/**
	 * 分辨率   视频码率
	 * 1080p	2000
	 * 720p		1200
	 * 480p		800
	 */
	public static final int DEFAULT_VIDEO_FPS = 30;
	// Video Record Setting
	private static final String VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
	private static final int VIDEO_IFRAME_INTERVAL = 4; // 1 seconds between I-frames	  帧间距 >=2s
	private static final int VIDEO_TIMEOUT_US = 11000;

	// Default Audio Record Setting
	public static final int DEFAULT_AUDIO_RECORDER_SOURCE = MediaRecorder.AudioSource.DEFAULT;
	public static final int DEFAULT_AUDIO_SAMPLE_RATE = 44100;
	/**
	 * 分辨率   音频码率
	 * 1080p	128
	 * 720p		128
	 * 480p		80
	 */
	// Audio Record Setting
	private static final String AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
	private static final int AUDIO_CHANNEL_COUNT = 1;
	private static final int AUDIO_MAX_INPUT_SIZE = 8820;
	private static final int AUDIO_TIMEOUT_US = 11000;
	private static final int AUDIO_RECORD_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
	private static final int AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

	public static final String EXTRA_RESULT_CODE = "result_code";
	public static final String EXTRA_RESULT_DATA = "result_data";
	public static final String EXTRA_RTMP_ADDRESS = "rtmp_address";

	public static final String EXTRA_RESULT_CLARITY = "clarityType";
	public static final String EXTRA_RESULT_SCREEN = "screen_oriention";

	public static final String EXTRA_SCREEN_DPI = "screen_dpi";

	public static final String EXTRA_AUDIO_RECORDER_SOURCE = "audio_recorder_source";
	public static final String EXTRA_AUDIO_SAMPLE_RATE = "audio_sample_rate";

	private MediaProjectionManager mMediaProjectionManager;
	private String mRtmpAddresss;

	private int mResultCode;
	private Intent mResultData;

	private int mCurrentScreenType = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;    // 视频方向：竖屏 横屏
	private int mClarityType = App.CLARITY_TYPE_HIGTH;        //视频质量：普清/高清/超清

	private int mSelectedVideoWidth;
	private int mSelectedVideoHeight;
	private int mSelectedVideoDpi;
	private int mSelectedVideoBitrate;

	private int mSelectedAudioRecordSource;
	private int mSelectedAudioSampleRate;
	private int mSelectedAudioBitrate;

	private MediaProjection mMediaProjection;
	private VirtualDisplay mVirtualDisplay;
	private Surface mInputSurface;
	private MediaCodec mVideoEncoder;
	private MediaCodec.BufferInfo mVideoBufferInfo;

	private AudioRecord mAudioRecord;
	private byte[] mAudioBuffer;
	private MediaCodec mAudioEncoder;
	private MediaCodec.BufferInfo mAudioBufferInfo;

	private RTMPMuxer mRTMPMuxer;
	private long mStartTime;
	private long mVideoTryingAgainTime;
	private boolean mIsSetVideoHeader;
	private boolean mIsSetAudioHeader;

	private Handler mHandler;
	private HandlerThread mHandlerThread;
	private Intent intentStatus;
	/* 网络监听广播 */
	private ConnectionChangeReceiver networkReceiver;
	private boolean mIsRecordingFlag = false;        // 是否正在录制
	private boolean mNetConnectedFlag = true;        //是否正常

	@Override
	public void onCreate() {
		super.onCreate();
		mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
	}

	@Override
	public void onDestroy() {
		stopScreenCapture();
		stopSelf();
		unregisterReceiver();
		dismissNotification();
		super.onDestroy();
		Log.e(TAG, "Destroy com.insthync.simplescreenrtmp.service.ScreenRecorderService");
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(final Intent intent, int flags, int startId) {
		if (intent == null)
			return START_NOT_STICKY;
		// 通过线程缓存放到队列中执行
		mRtmpAddresss = intent.getStringExtra(EXTRA_RTMP_ADDRESS);
		mResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
		mResultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
		intentStatus = new Intent(ACTION_SCREEN_RECORD_STATUS);
		registerReceiver();

		if (mResultCode != Activity.RESULT_OK || mResultData == null) {
			Log.e(TAG, "Failed to start service, mResultCode: " + mResultCode + ", mResultData: " + mResultData);
			return START_NOT_STICKY;
		}
		initRecord(intent);
		startScreenCapture();
		return START_STICKY;
//		if (!startScreenCapture()) {
//			Log.e(TAG, "Failed to start capture screen");
//			return START_NOT_STICKY;
//		}
//		return START_STICKY;
	}

	private void initRecord(Intent intent) {
		mCurrentScreenType = intent.getIntExtra(EXTRA_RESULT_SCREEN, mCurrentScreenType);
		mClarityType = intent.getIntExtra(EXTRA_RESULT_CLARITY, mClarityType);
		switch (mClarityType) {
			case 0:
				if (mCurrentScreenType == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
					mSelectedVideoWidth = 480;
					mSelectedVideoHeight = 854;
				} else {
					mSelectedVideoWidth = 854;
					mSelectedVideoHeight = 480;
				}
				mSelectedVideoBitrate = 1024 * 800;
				mSelectedAudioBitrate = 16 * 1000;
				break;
			case 1:
				// 竖屏
				if (mCurrentScreenType == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
					mSelectedVideoWidth = 720;
					mSelectedVideoHeight = 1280;
				} else {
					mSelectedVideoWidth = 1280;
					mSelectedVideoHeight = 720;
				}
				mSelectedVideoBitrate = 1024 * 1300;
				mSelectedAudioBitrate = 32 * 1000;
				break;
			case 2:
				mSelectedVideoWidth = App.getScreenWH()[0];
				mSelectedVideoHeight = App.getScreenWH()[1];
				mSelectedVideoBitrate = 1024 * 2000;
				mSelectedAudioBitrate = 32 * 1000;
				break;
		}
		Log.e(TAG, "video width:" + mSelectedVideoWidth + "video height:" + mSelectedVideoHeight);
		mSelectedVideoDpi = intent.getIntExtra(EXTRA_SCREEN_DPI, DEFAULT_SCREEN_DPI);

		mSelectedAudioRecordSource = intent.getIntExtra(EXTRA_AUDIO_RECORDER_SOURCE, DEFAULT_AUDIO_RECORDER_SOURCE);
		mSelectedAudioSampleRate = intent.getIntExtra(EXTRA_AUDIO_SAMPLE_RATE, DEFAULT_AUDIO_SAMPLE_RATE);
	}

	private void dismissNotification() {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(1);
	}

	private int getTimestamp() {
		if (mStartTime == 0)
			mStartTime = mVideoBufferInfo.presentationTimeUs / 1000;
		return (int) (mVideoBufferInfo.presentationTimeUs / 1000 - mStartTime);
	}

	private void startScreenCapture() {
		if (mResultCode != 0 && mResultData != null) {
			mStartTime = 0;
			mVideoTryingAgainTime = 0;
			mIsSetVideoHeader = false;
			// 开始录制
			startRecording();
		} else {
			// 录屏失败
			intentStatus.putExtra(SCREEN_RECORD_STATUS, SCREEN_RECORD_STATUS_FAIL);
			sendBroadcast(intentStatus);
		}
	}

	/**
	 * 建立推流连接，开始推流
	 */
	private void startRecording() {
		if (mRTMPMuxer == null) {
			mRTMPMuxer = new RTMPMuxer();
		}

		//  网络异常
		if (!mNetConnectedFlag) {
			return;
		}

		//video 录屏工具(系统自带)
		mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);

		// audio
		int minBufferSize = AudioRecord.getMinBufferSize(mSelectedAudioSampleRate, AUDIO_CHANNEL_CONFIG, AUDIO_RECORD_FORMAT);
		mAudioRecord = new AudioRecord(mSelectedAudioRecordSource, mSelectedAudioSampleRate, AUDIO_CHANNEL_CONFIG, AUDIO_RECORD_FORMAT, minBufferSize * 5);
		mAudioBuffer = new byte[mSelectedAudioSampleRate / 5];

		// 建立推流连接
		int result = mRTMPMuxer.open(mRtmpAddresss, mSelectedVideoWidth, mSelectedVideoHeight);
		// 当前的rtmp无法进行数据推流
		if (result == -1) {
			mRTMPMuxer = null;
			Toast.makeText(App.mAppContext, "推流错误", Toast.LENGTH_SHORT);
			return;
		}
		// 视频编码失败
		if (!prepareVideoEncoder()) {
			Toast.makeText(App.mAppContext, "视频编码失败", Toast.LENGTH_SHORT);
			return;
		}
		// 音频编码失败
		if (!prepareAudioEncoder()) {
			Toast.makeText(App.mAppContext, "音频编码失败", Toast.LENGTH_SHORT);
			return;
		}
		if (mVirtualDisplay == null) {
			// Start the video input.
			mVirtualDisplay = mMediaProjection.createVirtualDisplay("Recording Display", mSelectedVideoWidth,
					mSelectedVideoHeight, mSelectedVideoDpi, 0 /* flags */, mInputSurface,
					null/* callback */, null /* handler */);
		}
		// 开辟线程用于推流
		mHandlerThread = new HandlerThread("HandlerThread");
		mHandlerThread.start();
		mHandler = new Handler(mHandlerThread.getLooper());
		mHandler.post(mRunnable);
		// 开始录制，并发送状态通知
		mIsRecordingFlag = true;
		intentStatus.putExtra(SCREEN_RECORD_STATUS, SCREEN_RECORD_STATUS_START);
		sendBroadcast(intentStatus);
	}

	/**
	 * 启动视频编码
	 *
	 * @return
	 */
	private boolean prepareVideoEncoder() {
		mVideoBufferInfo = new MediaCodec.BufferInfo();
		MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mSelectedVideoWidth, mSelectedVideoHeight);
		int frameRate = DEFAULT_VIDEO_FPS;

		// Set some required properties. The media codec may fail if these aren't defined.
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, mSelectedVideoBitrate);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
		//format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate);
		//format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_IFRAME_INTERVAL);
		format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate);
		format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mSelectedVideoWidth * mSelectedVideoHeight);
		// Create a MediaCodec encoder and configure it. Get a Surface we can use for recording into.
		try {
			mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
			mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			mInputSurface = mVideoEncoder.createInputSurface();
			mVideoEncoder.start();
			return true;
		} catch (IOException e) {
			Log.e(TAG, "Failed to initial video encoder, e: " + e);
			mVideoEncoder.release();
			mVideoEncoder = null;
			intentStatus.putExtra(SCREEN_RECORD_STATUS, SCREEN_RECORD_STATUS_FAIL);
			sendBroadcast(intentStatus);
			return false;
		}
	}

	/**
	 * 启动音频编码
	 */
	private boolean prepareAudioEncoder() {
		mAudioBufferInfo = new MediaCodec.BufferInfo();

		MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, mSelectedAudioSampleRate, AUDIO_CHANNEL_COUNT);
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		format.setInteger(MediaFormat.KEY_BIT_RATE, mSelectedAudioBitrate);
		format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_MAX_INPUT_SIZE);

		try {
			mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
			mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			mAudioEncoder.start();
			return true;
		} catch (IOException e) {
			Log.e(TAG, "Failed to initial audio encoder, e: " + e);
			mAudioEncoder.release();
			mAudioEncoder = null;
			intentStatus.putExtra(SCREEN_RECORD_STATUS, SCREEN_RECORD_STATUS_FAIL);
			sendBroadcast(intentStatus);
			return false;
		}
	}

	/**
	 * 开始录制音频，加入音频数据队列
	 *
	 * @return
	 */
	private boolean recordAudio() {
		if (mAudioEncoder != null) {
			int timestamp = getTimestamp();
			// Read audio data from recorder then write to encoder
			int size = mAudioRecord.read(mAudioBuffer, 0, mAudioBuffer.length);
			if (size > 0) {
				int index = mAudioEncoder.dequeueInputBuffer(AUDIO_TIMEOUT_US);
				if (index >= 0) {
					ByteBuffer inputBuffer = mAudioEncoder.getInputBuffer(index);
					inputBuffer.position(0);
					inputBuffer.put(mAudioBuffer, 0, mAudioBuffer.length);
					mAudioEncoder.queueInputBuffer(index, 0, mAudioBuffer.length, timestamp * 1000, 0);
				}
			}
		}
		return true;
	}

	/**
	 * 推送视频流
	 *
	 * @return
	 */
	private boolean drainVideoEncoder() {
		if (mVideoEncoder != null) {
			while (true) {
				int timestamp = getTimestamp();
				int index = mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, VIDEO_TIMEOUT_US);

				if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
					Log.d(TAG, "Video Format changed " + mVideoEncoder.getOutputFormat());
				} else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
					if (mVideoTryingAgainTime == 0)
						mVideoTryingAgainTime = System.currentTimeMillis();
					//Log.d(TAG, "Contents are not ready, trying again...");
					break;
				} else if (index >= 0) {
					if (mVideoTryingAgainTime > 0) {
						long tryAgainAfterTime = System.currentTimeMillis() - mVideoTryingAgainTime;
						Log.d(TAG, "Tried again after " + tryAgainAfterTime + " ms");
						mVideoTryingAgainTime = 0;
					}
					ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(index);
					encodedData.position(mVideoBufferInfo.offset);
					encodedData.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);

					byte[] bytes = new byte[encodedData.remaining()];
					encodedData.get(bytes);

					if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
						// Pulling codec config data
						if (!mIsSetVideoHeader) {
							try {
								writeVideoMuxer(timestamp, bytes);
							} catch (Exception e) {
								e.printStackTrace();
								return false;
							}
							mIsSetVideoHeader = true;
						}
						mVideoBufferInfo.size = 0;
					}

					if (mVideoBufferInfo.size > 0) {
						try {
							writeVideoMuxer(timestamp, bytes);
						} catch (Exception e) {
							e.printStackTrace();
							return false;
						}
					}

					mVideoEncoder.releaseOutputBuffer(index, false);
				}
			}
		}
		return true;
	}

	/**
	 * 推送音频流
	 *
	 * @return
	 */
	private boolean drainAudioEncoder() {
		if (mAudioEncoder != null) {
			while (true) {
				int timestamp = getTimestamp();
				int index = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, AUDIO_TIMEOUT_US);

				if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
					Log.d(TAG, "Audio Format changed " + mAudioEncoder.getOutputFormat());
				} else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
					break;
				} else if (index >= 0) {
					ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(index);
					encodedData.position(mAudioBufferInfo.offset);
					encodedData.limit(mAudioBufferInfo.offset + mAudioBufferInfo.size);

					byte[] bytes = new byte[encodedData.remaining()];
					encodedData.get(bytes);

					if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
						// Pulling codec config data
						if (!mIsSetAudioHeader) {
							try {
								writeAudioMuxer(timestamp, bytes);
							} catch (Exception e) {
								e.printStackTrace();
								return false;
							}
							mIsSetAudioHeader = true;
						}
						mAudioBufferInfo.size = 0;
					}

					if (mAudioBufferInfo.size > 0) {
						writeAudioMuxer(timestamp, bytes);
					}
					mAudioEncoder.releaseOutputBuffer(index, false);
				}
			}
		}
		return true;
	}

	private void writeVideoMuxer(int timestamp, byte[] bytes) {
		int rtmpConnectionState = mRTMPMuxer != null ? mRTMPMuxer.isConnected() : 0;
		// 推流库连接正常
		if (mNetConnectedFlag) {
			if (rtmpConnectionState == 1) {
				mRTMPMuxer.writeVideo(bytes, 0, bytes.length, timestamp);
			} else {
//				mRTMPMuxer.close();
				mRTMPMuxer.open(mRtmpAddresss, mSelectedVideoWidth, mSelectedVideoHeight);
			}
		}
	}

	private void writeAudioMuxer(int timestamp, byte[] bytes) {
		int rtmpConnectionState = mRTMPMuxer != null ? mRTMPMuxer.isConnected() : 0;
		if (mNetConnectedFlag) {
			// 推流库连接正常
			if (rtmpConnectionState == 1) {
				mRTMPMuxer.writeAudio(bytes, 0, bytes.length, timestamp);
			} else {
//				mRTMPMuxer.close();
				mRTMPMuxer.open(mRtmpAddresss, mSelectedVideoWidth, mSelectedVideoHeight);
			}
		}
	}

	private void stopScreenCapture() {
		if (!mIsRecordingFlag) {
			return;
		}
		// 停止录制，标记并释放资源
		mIsRecordingFlag = false;
		if (mHandler != null) {
			// 释放录屏/录音
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					releaseEncoders();
				}
			});
		}
		intentStatus.putExtra(SCREEN_RECORD_STATUS, SCREEN_RECORD_STATUS_STOP);
		sendBroadcast(intentStatus);
	}

	private void releaseEncoders() {
		mHandler.removeCallbacks(mRunnable);
		// 释放音频相关
		if (mAudioEncoder != null) {
			mAudioEncoder.stop();
			mAudioEncoder.release();
			mAudioEncoder = null;
		}
		if (mAudioRecord != null) {
			mAudioRecord.stop();
			mAudioRecord.release();
			mAudioRecord = null;
		}
		//释放视频相关
		if (mRTMPMuxer != null) {
			mRTMPMuxer.close();
			mRTMPMuxer = null;
		}
		if (mVideoEncoder != null) {
			mVideoEncoder.stop();
			mVideoEncoder.release();
			mVideoEncoder = null;
		}
		if (mInputSurface != null) {
			mInputSurface.release();
			mInputSurface = null;
		}
		if (mMediaProjection != null) {
			mMediaProjection.stop();
			mMediaProjection = null;
		}
		if (mVirtualDisplay != null) {
			mVirtualDisplay.release();
			mVirtualDisplay = null;
		}
		mVideoBufferInfo = null;
	}

	private void registerReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		networkReceiver = new ConnectionChangeReceiver();
		networkReceiver.setOnNetChangeListener(new ConnectionChangeReceiver.NetwrokChangeCallback() {

			@Override
			public void wifiConnected() {
				Log.e(TAG, "ConnectionChangeReceiver wifiConnected");
				if (!mNetConnectedFlag && ping()) {
					mNetConnectedFlag = true;
					// 重连释放资源，资源释放部分需要在网络正常情况下进行
					try {
						releaseEncoders();
					} catch (Exception e) {
						e.printStackTrace();
					}
					startRecording();
				}
			}

			@Override
			public void noConnected() {
				mNetConnectedFlag = false;
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						mHandler.removeCallbacks(mRunnable);
					}
				});
				mIsRecordingFlag = false;
				intentStatus.putExtra(SCREEN_RECORD_STATUS, SCREEN_RECORD_STATUS_FAIL);
				sendBroadcast(intentStatus);
				Log.e(TAG, "ConnectionChangeReceiver noConnected");
				if (WindowRecordManager.getWindowRecordBig() != null) {
//					WindowRecordManager.getWindowRecordBig().onSysMsg(Constants.NETWORK_FAIL);
				}
			}

			@Override
			public void gprsConnected() {
				Log.e(TAG, "ConnectionChangeReceiver gprsConnected");
				if (WindowRecordManager.getWindowRecordBig() != null) {
//					WindowRecordManager.getWindowRecordBig().onSysMsg(getResources().getString(R.string.network_2G_msg_2));
				}
				if (!mNetConnectedFlag && ping()) {
					mNetConnectedFlag = true;
					// 重连释放资源，资源释放部分需要在网络正常情况下进行
					try {
						releaseEncoders();
					} catch (Exception e) {
						e.printStackTrace();
					}
					startRecording();
				}
			}
		});
		this.registerReceiver(networkReceiver, filter);
	}

	/**
	 * 注销广播
	 */
	private void unregisterReceiver() {
		if (networkReceiver != null)
			this.unregisterReceiver(networkReceiver);
	}

	private Runnable mRunnable = new Runnable() {
		@Override
		public void run() {
			if (mVideoEncoder != null)
				drainVideoEncoder();
			if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED && mAudioRecord.setPositionNotificationPeriod(mSelectedAudioBitrate / 10) == AudioRecord.SUCCESS) {
				if (mAudioEncoder != null) {
					mAudioRecord.startRecording();
					recordAudio();
					drainAudioEncoder();
				}
			}
			mHandler.post(mRunnable);
		}
	};

	private boolean ping() {
		try {
			Process process = Runtime.getRuntime().exec("ping -c 1 -w 100 www.baidu.com");
			int status = process.waitFor();
			return status == 0;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return false;
	}
}