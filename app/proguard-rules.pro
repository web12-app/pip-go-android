# Keep the JavascriptInterface bridge methods (renaming breaks addJavascriptInterface)
-keepclassmembers class com.pipgo.app.bridge.PipGoBridge { public *; }
-keep class com.pipgo.app.bridge.PipGoBridge { *; }
