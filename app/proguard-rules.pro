# JNI: native code calls these by name
-keep class io.github.professorengineergit.downlink.sdr.RtlNative { *; }
-keepclassmembers class * implements io.github.professorengineergit.downlink.sdr.SampleSink { void onSamples(byte[], int); }
