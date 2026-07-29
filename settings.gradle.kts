pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()   // genkit publicado localmente (publishToMavenLocal) cuando no está el hermano
        google()
    }
}

rootProject.name = "kvalid"

// genkit es la base compartida (REPO SEPARADO), PUBLICADA en Maven Central
// (io.github.kuroxbyte:genkit-* / kspkit / aptkit) — de ahí se resuelve POR DEFECTO.
// Co-desarrollo (OPT-IN): con -Pkvalid.useGenkitSource=true (o esa línea en un gradle.properties
// local) y ../genkit clonado como hermano, el composite build sustituye genkit por el código
// fuente. Apagado por defecto para que genkit NO se importe dentro de kvalid en el IDE.
// Regla de release: genkit se publica ANTES que kvalid.
val useGenkitSource = providers.gradleProperty("kvalid.useGenkitSource").orNull.toBoolean()
val genkitDir = rootDir.resolve("../genkit")
if (useGenkitSource && genkitDir.exists()) {
    includeBuild(genkitDir)
}

include(
    ":kvalid-annotations",
    ":kvalid-runtime",
    ":kvalid-core",
    ":kvalid-processor",
    ":kvalid-i18n",
    ":kvalid-ktor",
    ":kvalid-spring",
    ":kvalid-spring-boot-starter",
    ":kvalid-benchmarks",
    ":kvalid-apt",
    ":kvalid-samples",
    ":kvalid-samples-spring",
    ":kvalid-integration-tests",
    ":kvalid-incremental-tests",
)
