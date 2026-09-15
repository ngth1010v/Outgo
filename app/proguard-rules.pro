# Add project specific ProGuard rules here.
# Room, Compose and Kotlin coroutines ship their own consumer rules, so this
# file only needs project-specific exceptions.

# Keep Room entities/DAOs' field names for reflection-free generated code
# (not strictly required, kept for clarity when reading stack traces).
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
