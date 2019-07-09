# voice_theta
Control theta V camera using voice command
-------------------------------------------
Use voice to activate the camera.  By speak "on" the camera will start shooting picture with 5 seconds delay. Say "stop" to exit the plug in mode.  You can select  "Video mode" by pressing  mode button.   But to stop video recording. Need to press shuttle switch to stop recording.


Abstract

Theta is an amazing 360 camera. You can shoot without worry what angle you are now. It will keep picture 360 degree around you. However it will always take your hand too, because you need finger press the button to take picture. This project is going to solve this problem. You can place thetaV far away like you take normal camera and use your voice to activate the camera or let camera detect if there are people in the frame, it's will automatic take picture with 5 seconds delay to allow you to pose before camera take picture.

Project Background

This project use tensorflow sample for Theta V project by @mktshhr at https://github.com/mktshhr/tensorflow-theta as a starting point. Because complexity of the project if we need to do both voice recognition and image recognition at the same time. We decided to develop separate two plugins, one for voice control and another for gesture control. For details on explanation on tensorflow sample for theta, you can read article written by Craig Oda at https://medium.com/theta360-guide/howto-build-tensorflow-apps-for-ricoh-theta-1b64da06a0bd for starting point

Tensorflow is mainly used in the project. As you may know, training tensorflow is time consuming you need to get many samples to learn for precise results. Because of short period of time, we decide to use current model that been used in the sample. which mean you cannot change word or object to recognition beyond the model provided. From our experiment, we found that word "on" is more accuracy that others word. So we decide to use word "on" to activate the camera and "stop" for plugin exit. For gesture control, we decide to use person to activate at this moments and we can develop real gesture control once we have more time to create sample and model for tensorflow.

Project implementation

Develop theta V plugin just like develop normal Android application. The difference is you don't have screen to display and keyboard to input. You only have four buttons for input that is

o Shuttle button

o Power /Sleep button

o Wifi mode selected

o Mode select ( Video, Camera, Live)

In this project we use Android studio to develop project with gradle to build project. Download project from github https://github.com/wtos03/gesture_theta for gesture control and https://github.com/wtos03/voice_theta for voice control.

To develop you need to enable developer mode on Theta by submit application to Ricoh with Theta serial number. For details on Theta plugin development go to

https://api.ricoh/products/theta-plugin/

For output checking and testing, you need to use Vysor that will emulate Theta V screen for you. https://www.vysor.io/ This program very helpful when you want to see output of the program.

Project configuration and setup

When you are install program for first time, you need to give permission to the program for resource usage ( Camera, Microphone, Storage) if not program will exit and red LED will blink and stop. That is the reasons why you need to use Vysor to set up program permission. However if you install plug in from the store. This process will not necessary. Theta can run plug in one at a time by pressing mode button until the LED light turn from blue to white. You can set default plugin both from Theta mobile application and PC application.

When plugin start the LED will become white. If you start voice_theta plugin say "on" to take picture. The camera will hold 5 seconds before take picture. This allow you to pose what you like. To stop plugin you can say "stop" to exit and LED become blue again. During plugin, you can short press mode switch to switch to Video mode. and say "on" to start video. However for video mode, the voice recognition cannot work anymore. So you must press shuttle switch to stop video recording and plug in will exits. You need to restart plugin again if you want to continue using voice command.

For gesture control plugin, just show your face and body to the camera, Or whenever camera detect person pass by, it will automatic take picture with 5 seconds delay same as voice.
