# webrtc-android-codelab
This app is an attempt to use the camera stream from a remote device into a AR scene on a responding device. The application code is a merge betwwen  https://codelabs.developers.google.com/codelabs/webrtc-web/ (for AR features) and https://github.com/vivek1794/webrtc-android-codelab (for sharing cameras)

## Instructions
1. Replace the IP-adress in `phone/.../SignallingClient.java` and `tablet/.../SignallingClient.java` file with your current IP-adress.
2. Run the `phone` app on your offer device and hit the start button
3. Run the `tablet` app on your answer and hit the start button
4. Start the node server by navigate to the `signalling` folder in your cmd and type `node index` and hit enter.
5. The offer device's camera should now be mirrored on the answer device's screen plus, an AR-session should be started on the answere device. 
