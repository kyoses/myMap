# 百度导航 SDK
-keep class com.baidu.** { *; }
-keep interface com.baidu.** { *; }
-keep class vi.com.gdi.** { *; }
-dontwarn com.baidu.**
-dontwarn com.baidu.navisdk.**
-dontwarn com.baidu.navi.**

-keep class com.google.protobuf.** { *; }
-keep interface com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

-keepattributes Exceptions, InnerClasses, Signature, Deprecated, SourceFile, LineNumberTable, LocalVariableTable, *Annotation*, Synthetic, EnclosingMethod
