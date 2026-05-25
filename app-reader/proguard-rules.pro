# Keep all app activities declared in manifest
-keep public class * extends android.app.Activity

# Keep Room database, entities and DAO classes
-keep class androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class *
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep AndroidX fragments used by manifest/activity transactions
-keep public class * extends androidx.fragment.app.Fragment

-keep class de.tu_darmstadt.seemoo.nfcgate.reader.db.** { *; }
-keep class de.tu_darmstadt.seemoo.nfcgate.reader.model.** { *; }
-keep class de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector { *; }
-keep class de.tu_darmstadt.seemoo.nfcgate.reader.BuildConfig { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
