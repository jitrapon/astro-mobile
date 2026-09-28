# R8 rules for the :androidApp instrumented-test APK — never the shipping app.
#
# Instrumented tests run against the minified `minifiedTest` variant (`testBuildType` in
# androidApp/build.gradle.kts), and AGP shrinks the test APK whenever the variant under test is
# minified, applying the app's mapping so the test code still links against the renamed app
# classes. That test-APK pass reads this file, declared with `testProguardFiles`; the app's own
# `proguard-rules.pro` never sees these rules, so nothing here can loosen what ships.
#
# Every rule below must name the single class that needs it. A package-wide `-dontwarn` would
# also silence the next genuinely missing class, which is the failure R8's missing-class check
# exists to report.

# androidx.test's tracing API (`androidx.test.platform.tracing.Tracer$Span`) annotates its span
# factories with Error Prone's `@MustBeClosed`: a static-analysis annotation androidx.test compiles
# against but does not declare as a runtime dependency, so its class is absent from the test APK's
# classpath and R8 reports the reference as missing. The annotation guides a compile-time checker;
# nothing reads it at runtime, so there is nothing to keep — only a warning to silence.
-dontwarn com.google.errorprone.annotations.MustBeClosed
