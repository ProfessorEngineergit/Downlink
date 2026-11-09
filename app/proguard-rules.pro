# JNI: native code calls these by name
-keep class io.github.professorengineergit.downlink.sdr.RtlNative { *; }
-keepclassmembers class * implements io.github.professorengineergit.downlink.sdr.SampleSink { void onSamples(byte[], int); }
-keep class io.github.professorengineergit.downlink.sdr.LrptChannel { *; }
-keep class io.github.professorengineergit.downlink.sdr.MeteorDecoder { *; }
-keepclassmembers class * implements io.github.professorengineergit.downlink.sdr.DecodeProgress { void onProgress(float, long, long, float); }
