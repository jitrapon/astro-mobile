# R8 keep rules for the :androidApp release variant.
#
# WHY THIS FILE IS DECLARED EXPLICITLY
#
# Not to add a default that was missing. Before this file existed, the merged rule set for the
# release variant already opened with:
#
#   # The proguard configuration file for the following section is Android Gradle plugin 9.4.0
#   # (extracted file: .../intermediates/default_proguard_files/global/
#   #  proguard-android-optimize.txt-9.4.0)
#
# — AGP applies that default implicitly when a build type sets `isMinifyEnabled = true` and names no
# `proguardFiles`. So the explicit `proguardFiles(getDefaultProguardFile("proguard-android-
# optimize.txt"), "proguard-rules.pro")` in androidApp/build.gradle.kts does two things that the
# implicit behaviour did not: it pins WHICH default applies, and it gives the app's own rules a home.
#
# Pinning matters because the two defaults AGP ships differ in one decisive way:
# `proguard-android-optimize.txt` leaves R8's optimization passes on, while `proguard-android.txt`
# turns them off wholesale. Silently inheriting the choice means a future AGP change — or someone
# adding a `proguardFiles` line without the `-optimize` suffix — can disable optimization for the
# whole app with no diff that looks like it did.
#
# WHERE THE APP'S RULES GO
#
# This file is the app's section of the merged set. Everything else in that set arrives from
# somewhere the app does not control: the AGP default above, the `consumer-rules.pro` each library
# AAR publishes, and AAPT2's generated keeps for manifest-declared components (the framework
# instantiates those reflectively by name, so the shrinker must not touch them).
#
# HOW TO SEE THE MERGED RESULT
#
#   ./gradlew :androidApp:assembleRelease
#   androidApp/build/outputs/mapping/release/configuration.txt    # every rule, with its origin
#   androidApp/build/outputs/mapping/release/usage.txt            # what R8 removed
#   androidApp/build/outputs/mapping/release/seeds.txt            # what the keeps matched
#   androidApp/build/outputs/mapping/release/configanalyzer.html  # R8's keep-radius report
#
# Each section in configuration.txt is bracketed by a comment naming the file it came from, which is
# the only reliable way to tell an app rule from an inherited one.
#
# configanalyzer.html is the report that answers the question configuration.txt cannot: not "which
# rules exist" but "how much does each one actually lock down". Open it in a browser — it renders
# client-side from the protobuf embedded beside it, whose schema ships in the page itself under
# <script id="keepradius-proto">. Two of its fields carry the audit below: a per-rule constraint set
# (DONT_SHRINK / DONT_OPTIMIZE / DONT_OBFUSCATE — what the rule forbids) and a PACKAGE_WIDE tag R8
# puts on any rule whose class pattern spans a package rather than naming a type, a supertype or an
# annotation. configanalyzer.pb beside it is the same data as a standalone file.
#
# BROAD-RULE AUDIT — verdict: clean, no rule neutralized (audited 2026-09-22, AGP 9.4.0)
#
# A single third-party rule broad enough to switch off an optimization pass costs the whole app that
# pass, silently: nothing fails, the APK is just bigger and slower, and configuration.txt shows the
# rule as one unremarkable line among hundreds. So the merged set is audited rather than assumed,
# and the result is written down here — a clean audit that is re-derived from scratch each time is
# indistinguishable from one nobody ever ran.
#
# Verdict per category:
#
#   -dontoptimize / -dontshrink / -dontobfuscate   ABSENT from the merged set.
#   -optimizations (pass filter)                   ABSENT — no rule narrows the optimization set.
#   -ignorewarnings                                ABSENT — every -dontwarn is package-scoped.
#   whole-package -keep                            5 rules tagged PACKAGE_WIDE, none an offender.
#
# The five package-wide rules, with the radius R8 measured for each:
#
#   androidx.window:window            -keep interface androidx.window.area.reflectionguard.* {*;}
#                                     3 classes, 14 methods
#   androidx.graphics:graphics-path   -keepclasseswithmembers class androidx.graphics.path.**
#                                         { native <methods>; }          2 classes, 8 methods
#   io.ktor:ktor-utils-jvm            -keepclassmembers      class io.ktor.**
#                                         { volatile <fields>; }         13 fields
#   io.ktor:ktor-utils-jvm            -keepclassmembernames  class io.ktor.**
#                                         { volatile <fields>; }         13 fields
#   kotlinx-coroutines-core-jvm       -keepclassmembers class kotlinx.coroutines.**
#                                         { volatile <fields>; }         60 fields
#
# None is an offender, for the same reason in each case: the wildcard is confined to the declaring
# library's own package, so no rule can reach app code or another dependency. The three `volatile
# <fields>` rules are the standard atomic-field-updater protection — those updaters resolve fields
# reflectively by name, so renaming one breaks at runtime; they pin field names and nothing else.
# Their combined radius is 5 classes / 86 fields / 22 methods, against 7688 live classes / 15232
# fields / 39549 methods in the shrunk APK. For comparison, R8 removed 9152 of 16840 classes and
# 75649 of 115198 methods on that same build, which is the positive evidence a grep cannot give:
# shrinking and optimization did not merely go un-disabled, they demonstrably ran.
#
# The widest-reaching rule overall is not package-wide at all — kotlinx-serialization's
# `-if @kotlinx.serialization.Serializable class ** -keep, allowshrinking, allowoptimization,
# allowobfuscation, allowaccessmodification class <1>` reaches 57 classes but forbids nothing; it
# only ties a serializable class's fate to its generated serializer. The largest genuine
# optimization constraint is Compose's `-keep,allowobfuscation,allowshrinking class * extends
# androidx.compose.ui.node.ModifierNodeElement` (40 classes, DONT_OPTIMIZE only), which is scoped by
# supertype and is load-bearing for Compose's modifier-node machinery.
#
# THE REMEDY, IF A FUTURE DEPENDENCY BRINGS ONE
#
# Drop the offending rule at its source, in androidApp/build.gradle.kts:
#
#   android { optimization { keepRules { ignoreFrom("com.example:library") } } }
#
# Never a counter-keep. ProGuard-language rules are additive and have no negation — a `-keep` cannot
# undo a `-dontoptimize`, and piling on more keeps to compensate for a bad one only widens the
# locked-down surface. ignoreFrom is the only mechanism that removes a rule from the merged set.
#
# SCOPE OF THIS VERDICT
#
# It covers the dependency set below and no other. Adding a dependency, or bumping one, can
# introduce a rule this audit never saw, so re-run it when the release graph changes. 45 rule
# sections merge into the release build, from 44 distinct sources — kotlinx-serialization-core
# contributes two files. 31 of those sources contribute at least one rule; the other 13 are
# comment-only or declare themselves safe to shrink (this file, the <unknown> section, and the
# activity / activity-ktx / activity-compose / collection-ktx / core-ktx / core-viewtree /
# material-ripple / navigation3-runtime / navigation3-ui / savedstate-ktx / window-core artifacts).
#
# Contributing rules (rule count in parentheses):
#
#   Android Gradle plugin 9.4.0 default (26)      AAPT2 generated keep rules (5)
#   androidx.annotation:annotation-jvm (6)        androidx.compose.animation:animation-android (3)
#   androidx.compose.animation:animation-core (1) androidx.compose.foundation:foundation (1)
#   androidx.compose.foundation:foundation-layout (1)
#   androidx.compose.runtime:runtime-android (6)  androidx.compose.ui:ui-android (8)
#   androidx.compose.ui:ui-graphics-android (3)   androidx.compose.ui:ui-text-android (1)
#   androidx.compose.ui:ui-unit-android (1)       androidx.compose.ui:ui-util-android (1)
#   androidx.core:core (5)                        androidx.graphics:graphics-path (1)
#   androidx.lifecycle:lifecycle-process (1)      androidx.lifecycle:lifecycle-runtime-android (6)
#   androidx.lifecycle:lifecycle-viewmodel-android (2)
#   androidx.lifecycle:lifecycle-viewmodel-savedstate-android (2)
#   androidx.savedstate:savedstate-android (1)    androidx.savedstate:savedstate-compose-android (2)
#   androidx.startup:startup-runtime (3)          androidx.versionedparcelable:versionedparcelable (4)
#   androidx.window:window (7)                    com.github.skydoves:compose-stability-runtime (8)
#   com.squareup.okhttp3:okhttp-android (5)       io.ktor:ktor-utils-jvm (3)
#   kotlinx-coroutines-android (4)                kotlinx-coroutines-core-jvm (10)
#   kotlinx-datetime-jvm (2)                      kotlinx-serialization-core-jvm (13)
#
# All five okhttp rules are -dontwarn, which suppresses warnings about absent optional platform
# providers (Conscrypt, BouncyCastle) and constrains no shrinker pass.
