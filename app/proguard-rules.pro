# JNI: native code calls these by name
-keep class io.github.professorengineergit.downlink.sdr.RtlNative { *; }
-keepclassmembers class * implements io.github.professorengineergit.downlink.sdr.SampleSink { void onSamples(byte[], int); }
-keep class io.github.professorengineergit.downlink.sdr.LrptChannel { *; }
-keep class io.github.professorengineergit.downlink.sdr.MeteorDecoder { *; }
-keepclassmembers class * implements io.github.professorengineergit.downlink.sdr.DecodeProgress { void onProgress(float, long, long, float); }
# Glance uses WorkManager, whose Room database is created by reflection
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.** { *; }
-keep interface io.github.professorengineergit.downlink.sdr.SampleSink { *; }
-keep interface io.github.professorengineergit.downlink.sdr.DecodeProgress { *; }
