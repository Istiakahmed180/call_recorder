# App components are referenced from AndroidManifest.xml, so R8 keeps them.
# The app uses no reflection, so default optimized rules are sufficient.
# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
