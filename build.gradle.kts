
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Fisk's Heroes' ASM transformer leaves vanilla methods without stack-map frames; Java 8 rejects them in dev runs.
// Temurin ships no arm64 Java 8 for macOS, and an x86_64 JVM can't load the arm64 LWJGL natives; run on Azul Zulu 8.
val runLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(8))
    vendor.set(JvmVendorSpec.AZUL)
}
tasks.withType<com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask>().configureEach {
    extraJvmArgs.add("-noverify")
    javaLauncher.set(runLauncher)
    if (project.hasProperty("fcSelftest")) {
        extraJvmArgs.add("-Dfiskcompanions.selftest=true")
    }
}
