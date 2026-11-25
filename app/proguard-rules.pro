# 안전 우선: 코드 축소(tree shaking)만 수행하고 난독화·최적화는 끕니다.
# (릴리스에서 시작 직후 종료되는 문제를 리플렉션/이름 기반 접근 쪽에서 배제하기 위함 — 스택 트레이스도 읽을 수 있음)
-dontobfuscate
-dontoptimize
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Zero-Log: 릴리스 빌드에서 android.util.Log 호출을 제거합니다.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# SQLCipher (JNI)
-keep class net.zetetic.database.** { *; }

# Anthropic Java SDK: JAR 에 R8 consumer rules(META-INF/proguard/anthropic-java-core.pro)가 포함되어 있어
# 별도 -keep 은 두지 않습니다. 아래는 Android 런타임에 없는 JVM 전용 클래스 참조 경고만 억제.
-dontwarn org.slf4j.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**

# victools jsonschema-generator (anthropic-java 의 structured output 헬퍼 의존성, 사용 안 함)
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn com.github.victools.**

# PDFBox-Android: JPEG2000 디코더(gemalto) 미포함 — JPX 이미지 필터만 비활성
-dontwarn com.gemalto.jp2.**

# 릴리스 시작 크래시 수정: R8 full mode 에서는 `-keep class * extends RoomDatabase`(room-runtime 2.2.5 규칙)가
# 기본 생성자를 유지하지 않아 WorkManager 의 WorkDatabase_Impl 생성이 실패함(Room 이 리플렉션으로 <init>() 호출).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# WorkManager(Glance 위젯 의존): 클래스 이름으로 리플렉션 생성되는 InputMerger / Worker 의 생성자 유지
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# ML Kit / Firebase Components: 컴포넌트(레지스트라)와 의존성 주입이 리플렉션·람다 기반이라 R8 이 일부를 지우면
# `LanguageIdentification.getClient` 등이 NPE 로 죽음(릴리스 크래시 2차). SDK 패키지는 통째로 유지한다(크기 영향 작음).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.firebase.components.** { *; }
# 시작 시 초기화(androidx.startup)·WorkManager(Glance 위젯 의존)
-keep class androidx.startup.** { *; }
-keep class androidx.work.** { *; }
# 폴더블 자세 감지(androidx.window)·CameraX: 리플렉션/JNI 사용 → 통째로 유지
-keep class androidx.window.** { *; }
-keep class androidx.camera.** { *; }
