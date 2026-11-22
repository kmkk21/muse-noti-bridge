# JavaMail / Android Mail (SMTP) reflection keep rules
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }
-keep class org.apache.harmony.awt.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-dontwarn java.awt.**
-dontwarn javax.naming.**
-dontwarn java.beans.**

# OkHttp3
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room & WorkManager
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.work.CoroutineWorker { *; }
