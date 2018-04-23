/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.abra.testwearapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {


  private static final Typeface TYPEFACE_1 =
      Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

  private static final Typeface TYPEFACE_2 =
      Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

  /**
   * Update rate in milliseconds for interactive mode. We update once a second since seconds are
   * displayed in interactive mode.
   */
  private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

  /**
   * Handler message id for updating the time periodically in interactive mode.
   */
  private static final int MSG_UPDATE_TIME = 0;
  private final Logger logger = Logger.getGlobal();

  private int phaseId = 0;


  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }

  private class Engine extends CanvasWatchFaceService.Engine {
    final Handler mUpdateTimeHandler = new EngineHandler(this);

    boolean mRegisteredTimeZoneReceiver = false;

    Paint phase1BackgroundPaint;
    Paint phase1TextPaint;


    Paint mBackgroundPaint;
    Paint mTextPaint1;
    Paint mTextPaint2;
    Paint mTextPaint3;

    boolean mAmbient;
    final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        mCalendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      }
    };
    Calendar mCalendar;

    float x1;
    float y1;
    float x2;
    float y2;
    float x3;
    float y3;
    float x4;
    float x4_2;
    float y4;

    /**
     * Whether the display supports fewer bits for each color in ambient mode. When true, we
     * disable anti-aliasing in ambient mode.
     */
    boolean lowBitAmbient;

    @Override
    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);

      setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
          .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
          .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
          .setShowSystemUiTime(false)
          .setAcceptsTapEvents(true)
          .build());
      Resources resources = MyWatchFace.this.getResources();

      phase1BackgroundPaint = new Paint();
      phase1BackgroundPaint.setColor(resources.getColor(R.color.phase1_background));

      phase1TextPaint = new Paint();
      phase1TextPaint.setColor(resources.getColor(R.color.phase1_text));
      phase1TextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
      phase1TextPaint.setAntiAlias(true);
      phase1TextPaint.setTextSize(resources.getDimension(R.dimen.phase1_text_size));


      ////////////

      mBackgroundPaint = new Paint();
      mBackgroundPaint.setColor(resources.getColor(R.color.background));

      mTextPaint1 = new Paint();
      mTextPaint1 = createTextPaint(resources.getColor(R.color.digital_text), TYPEFACE_1);
      mTextPaint2 = new Paint();
      mTextPaint2 = createTextPaint(resources.getColor(R.color.digital_text), TYPEFACE_2);
      mTextPaint3 = new Paint();
      mTextPaint3 = createTextPaint(resources.getColor(R.color.digital_text), TYPEFACE_2);

      mCalendar = Calendar.getInstance();
    }

    @Override
    public void onDestroy() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      super.onDestroy();
    }

    private boolean hasSound() {
      Context context = getApplicationContext();

      PackageManager packageManager = context.getPackageManager();
      AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

// Check whether the device has a speaker.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // Check FEATURE_AUDIO_OUTPUT to guard against false positives.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
          return false;
        }

        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
          if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            return true;
          }
        }
      }
      return false;

    }

    private Paint createTextPaint(int textColor, Typeface typeface) {
      Paint paint = new Paint();
      paint.setColor(textColor);
      paint.setTypeface(typeface);
      paint.setAntiAlias(true);
      return paint;
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      logger.info("Visibility changed");

      super.onVisibilityChanged(visible);

      if (visible) {
        registerReceiver();

        // Update time zone in case it changed while we weren't visible.
        mCalendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      } else {
        unregisterReceiver();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    private void registerReceiver() {
      logger.info("register receiver");

      if (mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
      MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
    }

    private void unregisterReceiver() {
      logger.info("unregister receiver");

      if (!mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = false;
      MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
    }

    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      logger.info("apply window insets");

      super.onApplyWindowInsets(insets);

      logger.info("inset " + insets.getStableInsetLeft());
      logger.info("inset " + insets.getStableInsetRight());
      logger.info("inset " + insets.getStableInsetTop());
      logger.info("inset " + insets.getStableInsetBottom());

      // Load resources that have alternate values for round watches.
      Resources resources = MyWatchFace.this.getResources();
      boolean isRound = insets.isRound();
//      mXOffset = resources.getDimension(isRound
//          ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//      float textSize = resources.getDimension(isRound
//          ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
//
//      mTextPaint.setTextSize(textSize);

      x1 = resources.getDimension(R.dimen.offset_x_line1);
      y1 = resources.getDimension(R.dimen.offset_y_line1);
      x2 = resources.getDimension(R.dimen.offset_x_line2);
      y2 = resources.getDimension(R.dimen.offset_y_line2);
      x3 = resources.getDimension(R.dimen.offset_x_line3);
      y3 = resources.getDimension(R.dimen.offset_y_line3);
      x4 = resources.getDimension(R.dimen.offset_x_line4);
      x4_2 = resources.getDimension(R.dimen.offset_x_line4_2);
      y4 = resources.getDimension(R.dimen.offset_y_line4);

      mTextPaint1.setTextSize(resources.getDimension(R.dimen.text_size_1));
      mTextPaint2.setTextSize(resources.getDimension(R.dimen.text_size_2));
      mTextPaint3.setTextSize(resources.getDimension(R.dimen.text_size_3));
    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
    }

    @Override
    public void onTimeTick() {
      logger.info("time tick"); // every second

//      logger.info("Sound " + hasSound());

      super.onTimeTick();
      invalidate();
    }

    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      logger.info("ambient mode change " + inAmbientMode);

      super.onAmbientModeChanged(inAmbientMode);
      if (mAmbient != inAmbientMode) {
        mAmbient = inAmbientMode;
        if (lowBitAmbient) {
          mTextPaint1.setAntiAlias(!inAmbientMode);
          mTextPaint2.setAntiAlias(!inAmbientMode);
        }
        invalidate();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    /**
     * Captures tap event (and tap type) and toggles the background color if the user finishes
     * a tap.
     */
    @Override
    public void onTapCommand(int tapType, int x, int y, long eventTime) {
      logger.info("tap command");

      switch (tapType) {
        case TAP_TYPE_TOUCH:
          // The user has started touching the screen.
          break;
        case TAP_TYPE_TOUCH_CANCEL:
          // The user has started a different gesture or otherwise cancelled the tap.
          break;
        case TAP_TYPE_TAP:
          // The user has completed the tap gesture.
          // TODO: Add code to handle the tap gesture.

//          backgroundColor[0] = (float) Math.random();
//          backgroundColor[1] = (float) Math.random();
//          backgroundColor[2] = (float) Math.random();

//          Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT).show();

          displayState ^= true;


          break;
      }
      invalidate();
    }

    boolean displayState = true;

    float[] backgroundColor = {0f, 1.0f, 1.0f};

    int prevYear = -1;

    int c = 0;

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      logger.info("draw");

      // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
      long now = System.currentTimeMillis();
//      mCalendar.setTimeInMillis(now + ((1 * 60 + 6) * 60) * 1000);
      mCalendar.setTimeInMillis(now);

//      String text;
      int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
      int minute = mCalendar.get(Calendar.MINUTE);
      int second = mCalendar.get(Calendar.SECOND);
      int year = mCalendar.get(Calendar.YEAR);

      if (year == 2017) {
        backgroundColor[0] = 0;
      }
      if (year == 2018) {
        backgroundColor[0] = 240;
      }

      // Draw the background.
      if (mAmbient) {
        canvas.drawColor(Color.BLACK);
      } else {
        canvas.drawColor(Color.HSVToColor(backgroundColor));
      }


//      if (mAmbient) {
//        text = String.format("%02d:%02d", hour, minute);
//      } else {
//        text = String.format("%02d:%02d:%02d", hour, minute, second);
//      }
//      canvas.drawText(text, mXOffset, mYOffset, mTextPaint);
//      canvas.drawText("Люблю", mXOffset, mYOffset + 60, mTextPaint);
//      canvas.drawText("Нику", mXOffset + 60, mYOffset + 120, mTextPaint);


      if (displayState) {
        canvas.drawText("current", x1, y1, mTextPaint1);
        canvas.drawText("year = ", x2, y2, mTextPaint1);

        String yearText = String.format("%4d", year);
        canvas.drawText(yearText, x3, y3, mTextPaint2);
      } else {
        canvas.drawText("current", x1, y1, mTextPaint1);
        canvas.drawText("time = ", x2, y2, mTextPaint1);

        if (mAmbient) {
          String text = String.format("%02d:%02d", hour, minute);
          canvas.drawText(text, x4_2, y4, mTextPaint3);
        } else {
          String text = String.format("%02d:%02d:%02d", hour, minute, second);
          canvas.drawText(text, x4, y4, mTextPaint3);
        }
      }


      if (prevYear != -1 && prevYear != year) {
        MediaPlayer mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.sng2);
        mediaPlayer.start();
      }
      prevYear = year;
    }

    /**
     * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
     * or stops it if it shouldn't be running but currently is.
     */
    private void updateTimer() {

      logger.info("update timer");

      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      if (shouldTimerBeRunning()) {
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
      }
    }

    /**
     * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
     * only run when we're visible and in interactive mode.
     */
    private boolean shouldTimerBeRunning() {
      return isVisible() && !isInAmbientMode();
    }

    /**
     * Handle updating the time periodically in interactive mode.
     */
    private void handleUpdateTimeMessage() {

      logger.info("update time message");

      invalidate();
      if (shouldTimerBeRunning()) {
        long timeMs = System.currentTimeMillis();
        long delayMs = INTERACTIVE_UPDATE_RATE_MS
            - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
        mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
      }
    }
  }

  private static class EngineHandler extends Handler {
    private final WeakReference<MyWatchFace.Engine> mWeakReference;

    public EngineHandler(MyWatchFace.Engine reference) {
      mWeakReference = new WeakReference<>(reference);
    }

    @Override
    public void handleMessage(Message msg) {
      MyWatchFace.Engine engine = mWeakReference.get();
      if (engine != null) {
        switch (msg.what) {
          case MSG_UPDATE_TIME:
            engine.handleUpdateTimeMessage();
            break;
        }
      }
    }
  }
}
