/*
 * Copyright 2017 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Demonstrates how to run an audio recognition model in Android.

This example loads a simple speech recognition model trained by the tutorial at
https://www.tensorflow.org/tutorials/audio_training

The model files should be downloaded automatically from the TensorFlow website,
but if you have a custom model you can update the LABEL_FILENAME and
MODEL_FILENAME constants to point to your own files.

The example application displays a list view with all of the known audio labels,
and highlights each one when it thinks it has detected one through the
microphone. The averaging of results to give a more reliable signal happens in
the RecognizeCommands helper class.
*/

package com.invtos.voice_theta;

import android.Manifest;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.invtos.voice_theta.task.StopVideoTask;
import com.invtos.voice_theta.task.TakePictureTask;
import com.invtos.voice_theta.task.TakeVideoTask;
import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;
import com.theta360.pluginlibrary.values.LedColor;
import com.theta360.pluginlibrary.values.LedTarget;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import static android.os.SystemClock.sleep;

import com.invtos.voice_theta.R;

import com.invtos.voice_theta.env.Logger;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

/**
 * An activity that listens for audio and then uses a TensorFlow model to detect particular classes,
 * by default a small set of action words.
 */
public class SpeechActivity extends PluginActivity {

  // Constants that control the behavior of the recognition code and model
  // settings. See the audio recognition tutorial for a detailed explanation of
  // all these, but you should customize them to match your training settings if
  // you are running your own model.
  private static final int SAMPLE_RATE = 16000;
  private static final int SAMPLE_DURATION_MS = 1000;
  private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
  private static final long AVERAGE_WINDOW_DURATION_MS = 500;
  private static final float DETECTION_THRESHOLD = 0.70f;
  private static final int SUPPRESSION_MS = 1500;
  private static final int MINIMUM_COUNT = 3;
  private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;
  private static final String LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt";
  private static final String MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.pb";
  private static final String INPUT_DATA_NAME = "decoded_sample_data:0";
  private static final String SAMPLE_RATE_NAME = "decoded_sample_data:1";
  private static final String OUTPUT_SCORES_NAME = "labels_softmax";

  private static final String PERMISSION_AUDIO = Manifest.permission.RECORD_AUDIO;

  // UI elements.
  private static final int REQUEST_RECORD_AUDIO = 13;
  private Button quitButton;
  private ListView labelsListView;
  private static final String LOG_TAG = SpeechActivity.class.getSimpleName();

  // Working variables.
  short[] recordingBuffer = new short[RECORDING_LENGTH];
  int recordingOffset = 0;
  boolean shouldContinue = true;
  private Thread recordingThread;
  boolean shouldContinueRecognition = true;
  private Thread recognitionThread;
  private final ReentrantLock recordingBufferLock = new ReentrantLock();
  private TensorFlowInferenceInterface inferenceInterface;
  private List<String> labels = new ArrayList<String>();
  private List<String> displayedLabels = new ArrayList<>();
  private RecognizeCommands recognizeCommands = null;
  private static final Logger LOGGER = new Logger();

  private Handler handler;
  private HandlerThread handlerThread;
  private boolean isEnded = false;
  private boolean isTakingPicture = false;
  private boolean isVideoMode = false;   // Default camera


  private TakePictureTask.Callback mTakePictureTaskCallback = new TakePictureTask.Callback() {
    @Override
    public void onTakePicture(String fileUrl) {
      //fileUrl = "http://127.0.0.1:8080/files/150100525831424d420703bede5d2400/100RICOH/R0010231.JPG"
      LOGGER.d("onTakePicture: " + fileUrl);
    }
  };


  private StopVideoTask.Callback mStopVideoTaskCallback = new StopVideoTask.Callback() {
    @Override
    public void onStopVideo(String fileUrl) {
      //fileUrl = "http://127.0.0.1:8080/files/150100525831424d420703bede5d2400/100RICOH/R0010231.JPG"
      LOGGER.d("onStopVideo: " + fileUrl);
    }
  };
  private TakeVideoTask.Callback mTakeVideoTaskCallback = new TakeVideoTask.Callback() {
    @Override
    public void onTakeVideo(String fileUrl) {
      //fileUrl = "http://127.0.0.1:8080/files/150100525831424d420703bede5d2400/100RICOH/R0010231.JPG"
      LOGGER.d("onTakeVideo: " + fileUrl);
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // Set up the UI.
    super.onCreate(savedInstanceState);

    // Step1: Uncomment for THETA, 1
    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE); // for THETA
    am.setParameters("RicUseBFormat=false"); // for THETA

// Default is Camera mode
    isVideoMode = false;
    notificationLedHide(LedTarget.LED5);
    notificationLedShow(LedTarget.LED4);
    notificationLedBlink(LedTarget.LED3, LedColor.WHITE, 1000);
    setContentView(R.layout.activity_speech);
    quitButton = (Button) findViewById(R.id.quit);
    quitButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                moveTaskToBack(true);
                notificationLedHide(LedTarget.LED3);
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
              }
            });
    labelsListView = (ListView) findViewById(R.id.list_view);

    // Load the labels for the model, but only display those that don't start
    // with an underscore.
    String actualFilename = LABEL_FILENAME.split("file:///android_asset/")[1];
    Log.i(LOG_TAG, "Reading labels from: " + actualFilename);
    BufferedReader br = null;
    try {
      br = new BufferedReader(new InputStreamReader(getAssets().open(actualFilename)));
      String line;
      while ((line = br.readLine()) != null) {
        labels.add(line);
        if (line.charAt(0) != '_') {
          displayedLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));
        }
      }
      br.close();
    } catch (IOException e) {
      throw new RuntimeException("Problem reading label file!", e);
    }

    // Build a list view based on these labels.
    ArrayAdapter<String> arrayAdapter =
            new ArrayAdapter<String>(this, R.layout.list_text_item, displayedLabels);
    labelsListView.setAdapter(arrayAdapter);

    // Set up an object to smooth recognition results to increase accuracy.
    recognizeCommands =
            new RecognizeCommands(
                    labels,
                    AVERAGE_WINDOW_DURATION_MS,
                    DETECTION_THRESHOLD,
                    SUPPRESSION_MS,
                    MINIMUM_COUNT,
                    MINIMUM_TIME_BETWEEN_SAMPLES_MS);

    // Load the TensorFlow model.
    inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILENAME);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    // Start the recording and recognition threads.
    if (!hasPermission()) {
      notificationError("Permissions are not granted.");
    }
    startRecording();
    startRecognition();
//====================
// Add Key Call back

    setKeyCallback(new KeyCallback() {
      @Override
      public void onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
          // Step3: Uncomment when taking a photo with WebAPI, 2
          if (isVideoMode)   // Stop VIdeo
          {
            stopRecognition();
            stopRecording();
            notificationLedBlink(LedTarget.LED3, LedColor.WHITE, 100);
            new StopVideoTask(mStopVideoTaskCallback).execute();
            sleep (1000);
            isTakingPicture = false;
            toggleMode();
            endProcess();
     //       startRecording();      // Cannot start to Voice recognition again  Exit at the moments
     //       startRecognition();
          }
          else {
            notificationLedBlink(LedTarget.LED4, LedColor.GREEN, 1000);
            isTakingPicture = true;
            new TakePictureTask(mTakePictureTaskCallback).execute();
            isTakingPicture = false;
          }
        }

        if (keyCode == KeyReceiver.KEYCODE_MEDIA_RECORD) {
          toggleMode();
       }

      }

      @Override
      public void onKeyUp(int keyCode, KeyEvent event) {


      }
      @Override
      public void onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyReceiver.KEYCODE_MEDIA_RECORD) {
          if (!isTakingPicture) {
            endProcess();
          }
        }
      }
    });


//========
  }   // End of onCreate

  //Toggle between Video and Camera Mode
  private void toggleMode()
  {

    isVideoMode = !isVideoMode;
    String mode;

    if (isVideoMode) {
      mode = "Video";
      notificationLedHide(LedTarget.LED4);
      notificationLedShow(LedTarget.LED5);
    }
    else //Camera Mode
    {
      mode = "Camera";
      notificationLedHide(LedTarget.LED5);
      notificationLedShow(LedTarget.LED4);
    }
    Log.v(LOG_TAG, mode);
  }



    private void endProcess() {
      LOGGER.d("Speechctivity::endProcess(): "+ isEnded);
      notificationLedHide(LedTarget.LED3);
      notificationSuccess();

      if (!isEnded) {
        isEnded = true;
        close();
      }
    }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_AUDIO) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestMicrophonePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(
          new String[]{PERMISSION_AUDIO}, REQUEST_RECORD_AUDIO);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == REQUEST_RECORD_AUDIO
        && grantResults.length > 0
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      startRecording();
      startRecognition();
    }
  }

  public synchronized void startRecording() {
    if (recordingThread != null) {
      return;
    }
    shouldContinue = true;
    recordingThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                record();
              }
            });
    recordingThread.start();
  }


  public synchronized void stopRecording() {
    if (recordingThread == null) {
      return;
    }
    shouldContinue = false;
    recordingThread = null;
  }

  private void record() {
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

    // Estimate the buffer size we'll need for this device.
    int bufferSize =
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
      bufferSize = SAMPLE_RATE * 2;
    }
    short[] audioBuffer = new short[bufferSize / 2];

    AudioRecord arecord =
        new AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize);

    if (arecord.getState() != AudioRecord.STATE_INITIALIZED) {
      Log.e(LOG_TAG, "Audio Record can't initialize!");
      return;
    }

    arecord.startRecording();
    Log.v(LOG_TAG, "Start recording");

    // Loop, gathering audio data and copying it to a round-robin buffer.
    while (shouldContinue) {
      int numberRead = arecord.read(audioBuffer, 0, audioBuffer.length);
      int maxLength = recordingBuffer.length;
      int newRecordingOffset = recordingOffset + numberRead;
      int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
      int firstCopyLength = numberRead - secondCopyLength;
      // We store off all the data for the recognition thread to access. The ML
      // thread will copy out of this buffer into its own, while holding the
      // lock, so this should be thread safe.
      recordingBufferLock.lock();
      try {
        System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
        System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
        recordingOffset = newRecordingOffset % maxLength;
      } finally {
        recordingBufferLock.unlock();
      }
    }

    arecord.stop();
    arecord.release();
  }

  public synchronized void startRecognition() {
    if (recognitionThread != null) {
      return;
    }
    shouldContinueRecognition = true;
    recognitionThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                recognize();
              }
            });
    recognitionThread.start();
  }

  public synchronized void stopRecognition() {
    if (recognitionThread == null) {
      return;
    }
    shouldContinueRecognition = false;
    recognitionThread = null;
  }



  private void recognize() {
    Log.v(LOG_TAG, "Start recognition");

    short[] inputBuffer = new short[RECORDING_LENGTH];
    float[] floatInputBuffer = new float[RECORDING_LENGTH];
    float[] outputScores = new float[labels.size()];
    String[] outputScoresNames = new String[] {OUTPUT_SCORES_NAME};
    int[] sampleRateList = new int[] {SAMPLE_RATE};

    // Loop, grabbing recorded data and running the recognition model on it.
    while (shouldContinueRecognition) {
      // The recording thread places data in this round-robin buffer, so lock to
      // make sure there's no writing happening and then copy it to our own
      // local version.
      recordingBufferLock.lock();
      try {
        int maxLength = recordingBuffer.length;
        int firstCopyLength = maxLength - recordingOffset;
        int secondCopyLength = recordingOffset;
        System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
        System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
      } finally {
        recordingBufferLock.unlock();
      }

      // We need to feed in float values between -1.0f and 1.0f, so divide the
      // signed 16-bit inputs.
      for (int i = 0; i < RECORDING_LENGTH; ++i) {
        floatInputBuffer[i] = inputBuffer[i] / 32767.0f;
      }

      // Run the model.
      inferenceInterface.feed(SAMPLE_RATE_NAME, sampleRateList);
      inferenceInterface.feed(INPUT_DATA_NAME, floatInputBuffer, RECORDING_LENGTH, 1);
      inferenceInterface.run(outputScoresNames);
      inferenceInterface.fetch(OUTPUT_SCORES_NAME, outputScores);

      // Use the smoother to figure out if we've had a real recognition event.
      long currentTime = System.currentTimeMillis();
     final RecognizeCommands.RecognitionResult result =
              recognizeCommands.processLatestResults(outputScores, currentTime);

      runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              // If we do have a new command, highlight the right list entry.
              if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                int labelIndex = -1;
                for (int i = 0; i < labels.size(); ++i) {
                  if (labels.get(i).equals(result.foundCommand)) {
                    labelIndex = i;
                  }
                }
                // Add to take command action

                String  chk = result.foundCommand;

                Log.v(LOG_TAG, chk);
                switch (chk)
                {

                  case "on":
                    notificationLedBlink(LedTarget.LED3, LedColor.RED, 100);
                    isTakingPicture = true;
                    if (isVideoMode)
                    {
                       stopRecording();  // Cannot recording video with another voice recording
            //           stopRecognition();
                      new TakeVideoTask(mTakeVideoTaskCallback).execute();
                      isTakingPicture = false;
                    }else{
                      new TakePictureTask(mTakePictureTaskCallback).execute();
                    }
                    isTakingPicture = false;

                    break;
   /*    To avoid confuse just one command for both Video and Camera  by manual switch mode between video and camera
                  case "yes":
                    if (!isVideoMode) // Not video toggle to Video
                    {
                      toggleMode();
                    }
                    isTakingPicture = true;
                    notificationLedBlink(LedTarget.LED3, LedColor.RED, 100);
                    stopRecording();  // Cannot recording video with another voice recording
                    stopRecognition();
                    new TakeVideoTask(mTakeVideoTaskCallback).execute();
                    isTakingPicture = false;
                    break;
    */
                  case  "stop":
                    stopRecognition();
                    stopRecording();
                    endProcess();
                    break;
                  default:
                    notificationLedBlink(LedTarget.LED3, LedColor.WHITE, 1000);

                }

                //----

                final View labelView = labelsListView.getChildAt(labelIndex - 2);

                AnimatorSet colorAnimation =
                    (AnimatorSet)
                        AnimatorInflater.loadAnimator(
                            SpeechActivity.this, R.animator.color_animation);
                colorAnimation.setTarget(labelView);
                colorAnimation.start();
              }
            }
          });
      try {
        // We don't need to run too frequently, so snooze for a bit.
        Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
      } catch (InterruptedException e) {
        // Ignore
      }
    }

    Log.v(LOG_TAG, "End recognition");
  }


// Plugin Activity Process

@Override
public synchronized void onResume() {
  LOGGER.d("onResume " + this);
  super.onResume();
//????
  handlerThread = new HandlerThread("inference");
  handlerThread.start();
  handler = new Handler(handlerThread.getLooper());
}

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }


  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    if (!isFinishing()) {
      LOGGER.d("Requesting finish");

      // Step2: Uncomment when using pluginlibrary 10
      close();
    }
    super.onDestroy();
  }


}
