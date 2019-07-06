package com.invtos.voice_theta;

import static android.os.SystemClock.sleep;

import com.invtos.voice_theta.env.Logger;
import com.invtos.voice_theta.task.TakePictureTask;

import com.theta360.pluginlibrary.activity.PluginActivity;


public abstract class ThetaControl extends PluginActivity{

    private static final Logger LOGGER = new Logger();
    private TakePictureTask.Callback mTakePictureTaskCallback = new TakePictureTask.Callback() {
        @Override
        public void onTakePicture(String fileUrl) {
            //fileUrl = "http://127.0.0.1:8080/files/150100525831424d420703bede5d2400/100RICOH/R0010231.JPG"
            LOGGER.d("onTakePicture: " + fileUrl);
            }
        };

    protected void TakePicture() {
        notificationCameraOpen();
        sleep(600);
        // Take Picture
        new TakePictureTask(mTakePictureTaskCallback).execute();
        notificationCameraClose();
        sleep(400);
    }



}
