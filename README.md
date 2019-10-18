# webrtc-android-codelab
An attempt to provide a codelab for Webrtc in Android - Similar to codelab for web at https://codelabs.developers.google.com/codelabs/webrtc-web/

More at : https://vivekc.xyz/getting-started-with-webrtc-for-android-daab1e268ff4

## Instructions
1. Replace the IP-adress in `phone/.../SignallingClient.java` and `tablet/.../SignallingClient.java` file with your current IP-adress.
2. Run the `phone` app on your offer device and hit the start button
3. Run the `tablet` app on your answer and hit the start button
4. Start the node server by navigate to the `signalling` folder in your cmd and type `node index` and enter.
5. The offer device's camera should now be mirrored on the answer device's screen plus, an AR-session should be started on the answere device. 
