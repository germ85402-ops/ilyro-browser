# ILYRO app-specific R8 rules.
# GeckoView and AndroidX ship their required consumer ProGuard/R8 rules.

# Keep WorkManager's generated Room database constructor in minified release builds.
# This protects on-demand WorkManager initialization under R8 full mode.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
