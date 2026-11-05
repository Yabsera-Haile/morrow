plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself; this line only pins the Kotlin version it uses.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// The project lives in OneDrive. Build output is thousands of short-lived files that OneDrive
// would try to sync (and sometimes lock mid-build), so it goes to a local folder instead.
val localAppData: String? = System.getenv("LOCALAPPDATA")
if (localAppData != null) {
    allprojects {
        layout.buildDirectory.set(file("$localAppData/MorrowBuild/${project.name}"))
    }
}
