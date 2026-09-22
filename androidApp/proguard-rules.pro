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
#   androidApp/build/outputs/mapping/release/configuration.txt   # every rule, with its origin
#   androidApp/build/outputs/mapping/release/usage.txt           # what R8 removed
#   androidApp/build/outputs/mapping/release/seeds.txt           # what the keeps matched
#
# Each section in configuration.txt is bracketed by a comment naming the file it came from, which is
# the only reliable way to tell an app rule from an inherited one.
