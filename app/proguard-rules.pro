# AndroidX/Room/Compose ship their own consumer rules. No blanket package keep.
# Release strips log calls, including accidental future debug logs.
-assumenosideeffects class android.util.Log {
 public static *** v(...);
 public static *** d(...);
 public static *** i(...);
 public static *** w(...);
 public static *** e(...);
 public static *** wtf(...);
 public static *** println(...);
}
-renamesourcefileattribute SourceFile
