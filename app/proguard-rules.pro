# Pertahankan method yang menggunakan anotasi JavascriptInterface
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Pertahankan inner class WebAppInterface agar bridge JS tetap terhubung
-keep class com.offline.photopea.MainActivity$WebAppInterface {
    public *;
}

# Pertahankan class MainActivity
-keep class com.offline.photopea.MainActivity {
    public *;
}

# Reduksi peringatan bawaan webkit
-dontwarn android.webkit.**
