import com.android.build.api.artifact.SingleArtifact
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.customroutes.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.customroutes.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                abiFilters += "arm64-v8a"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("internal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

val privacyAudit = tasks.register("privacyAudit") {
    group = "verification"
    description = "Audits merged manifests and runtime dependencies for privacy drift."
}
val dataExtractionRules = layout.projectDirectory.file("src/main/res/xml/data_extraction_rules.xml")

androidComponents.onVariants { variant ->
    val variantName = variant.name
    val capitalizedName = variantName.replaceFirstChar { it.titlecase(Locale.US) }
    val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
    val runtimeClasspath = configurations.named("${variantName}RuntimeClasspath")
    val packagedSourceSetNames = buildSet {
        add("main")
        android.buildTypes.names.forEach(::add)
        android.productFlavors.names.forEach(::add)
    }
    val resourceDirectories: List<File> = android.sourceSets
        .matching { it.name in packagedSourceSetNames }
        .flatMap { sourceSet -> sourceSet.res.srcDirs.toList() }
    val auditVariant = tasks.register("audit${capitalizedName}Privacy") {
        group = "verification"
        inputs.file(mergedManifest)
        inputs.file(dataExtractionRules)
        inputs.files(resourceDirectories)
        doLast {
            val androidNamespace = "http://schemas.android.com/apk/res/android"
            val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(mergedManifest.get().asFile)
            val application = document.getElementsByTagName("application").item(0)
                ?: error("Merged manifest has no application element")
            check(application.attributes.getNamedItemNS(androidNamespace, "allowBackup")?.nodeValue == "false") {
                "Privacy audit failed: android:allowBackup must be false"
            }
            check(application.attributes.getNamedItemNS(androidNamespace, "usesCleartextTraffic")?.nodeValue == "false") {
                "Privacy audit failed: cleartext traffic must be disabled"
            }
            check(application.attributes.getNamedItemNS(androidNamespace, "networkSecurityConfig") == null) {
                "Privacy audit failed: networkSecurityConfig requires explicit privacy review"
            }
            check(application.attributes.getNamedItemNS(androidNamespace, "dataExtractionRules")?.nodeValue == "@xml/data_extraction_rules") {
                "Privacy audit failed: data extraction rules must remain configured"
            }

            val expectedPermissions = setOf(
                "android.permission.INTERNET",
                "io.github.customroutes.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            )
            val permissions = buildSet {
                listOf("uses-permission", "uses-permission-sdk-23").forEach { tag ->
                    val nodes = document.getElementsByTagName(tag)
                    for (index in 0 until nodes.length) {
                        add(nodes.item(index).attributes.getNamedItemNS(androidNamespace, "name").nodeValue)
                    }
                }
            }
            check(permissions == expectedPermissions) {
                "Privacy audit failed: permissions changed. Expected $expectedPermissions, found $permissions"
            }
            val declaredPermissions = document.getElementsByTagName("permission")
            check(declaredPermissions.length == 1) {
                "Privacy audit failed: app-defined permissions changed"
            }
            check(
                declaredPermissions.item(0).attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue ==
                    "io.github.customroutes.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" &&
                    declaredPermissions.item(0).attributes.getNamedItemNS(androidNamespace, "protectionLevel")?.nodeValue ==
                    "signature",
            ) {
                "Privacy audit failed: dynamic receiver permission must remain signature-protected"
            }

            val componentExports = buildMap {
                listOf("activity", "activity-alias", "service", "receiver", "provider").forEach { tag ->
                    val nodes = document.getElementsByTagName(tag)
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index)
                        put(
                            node.attributes.getNamedItemNS(androidNamespace, "name").nodeValue,
                            node.attributes.getNamedItemNS(androidNamespace, "exported")?.nodeValue to
                                node.attributes.getNamedItemNS(androidNamespace, "permission")?.nodeValue,
                        )
                    }
                }
            }
            check(componentExports["io.github.customroutes.app.MainActivity"]?.first == "true") {
                "Privacy audit failed: launcher activity must remain explicitly exported"
            }
            val unsafeComponents = componentExports.filter { (name, attributes) ->
                val (exported, permission) = attributes
                when (name) {
                    "io.github.customroutes.app.MainActivity" -> exported != "true"
                    "androidx.profileinstaller.ProfileInstallReceiver" ->
                        exported != "true" || permission != "android.permission.DUMP"
                    else -> exported != "false"
                }
            }
            check(unsafeComponents.isEmpty()) {
                "Privacy audit failed: unexpected exported or unprotected components: $unsafeComponents"
            }
            check(document.getElementsByTagName("provider").let { providers ->
                (0 until providers.length).none { index ->
                    providers.item(index).attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue ==
                        "ai.onnxruntime.TelemetryInitializer"
                }
            }) {
                "Privacy audit failed: ONNX Runtime telemetry provider is present"
            }

            val configuredExtractionRules = mutableListOf<File>()
            resourceDirectories.forEach { directoryFile ->
                directoryFile.walkTopDown().forEach { file ->
                    if (file.isFile && file.name == "data_extraction_rules.xml") configuredExtractionRules.add(file)
                }
            }
            check(configuredExtractionRules == listOf(dataExtractionRules.asFile)) {
                "Privacy audit failed: generated or variant data extraction rule overlays are not allowed: " +
                    configuredExtractionRules
            }
            val rulesDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(dataExtractionRules.asFile)
            val expectedExcludedDomains = setOf(
                "root",
                "file",
                "database",
                "sharedpref",
                "external",
                "device_root",
                "device_file",
                "device_database",
                "device_sharedpref",
            )
            listOf("cloud-backup", "device-transfer").forEach { sectionName ->
                val section = rulesDocument.getElementsByTagName(sectionName).item(0)
                    ?: error("Privacy audit failed: missing $sectionName rules")
                val exclusions = buildSet {
                    for (index in 0 until section.childNodes.length) {
                        val child = section.childNodes.item(index)
                        if (child.nodeName == "exclude") {
                            check(child.attributes.getNamedItem("path")?.nodeValue == ".") {
                                "Privacy audit failed: $sectionName exclusion must cover its full domain"
                            }
                            add(child.attributes.getNamedItem("domain").nodeValue)
                        }
                    }
                }
                check(exclusions == expectedExcludedDomains) {
                    "Privacy audit failed: $sectionName exclusions changed: $exclusions"
                }
            }

            val approvedGroups = setOf(
                "androidx.activity",
                "androidx.annotation",
                "androidx.arch.core",
                "androidx.autofill",
                "androidx.collection",
                "androidx.compose",
                "androidx.compose.animation",
                "androidx.compose.foundation",
                "androidx.compose.material",
                "androidx.compose.material3",
                "androidx.compose.runtime",
                "androidx.compose.ui",
                "androidx.concurrent",
                "androidx.core",
                "androidx.customview",
                "androidx.emoji2",
                "androidx.graphics",
                "androidx.interpolator",
                "androidx.lifecycle",
                "androidx.profileinstaller",
                "androidx.savedstate",
                "androidx.startup",
                "androidx.tracing",
                "androidx.versionedparcelable",
                "com.google.guava",
                "com.microsoft.onnxruntime",
                "org.jspecify",
                "org.jetbrains",
                "org.jetbrains.kotlin",
                "org.jetbrains.kotlinx",
            )
            val resolvedClasspath = runtimeClasspath.get()
            val rootIdentifier = resolvedClasspath.incoming.resolutionResult.rootComponent.get().id
            val componentIdentifiers = (
                resolvedClasspath.incoming.resolutionResult.allComponents.map { it.id } +
                    resolvedClasspath.incoming.artifacts.artifacts.map { it.id.componentIdentifier }
                ).toSet()
            val unapprovedLocalComponents = componentIdentifiers.filterNot { identifier ->
                identifier is ModuleComponentIdentifier || identifier == rootIdentifier
            }
            check(unapprovedLocalComponents.isEmpty()) {
                "Privacy audit failed: local runtime dependencies require explicit review: $unapprovedLocalComponents"
            }
            val dependencyGroups = componentIdentifiers
                .mapNotNull { (it as? ModuleComponentIdentifier)?.group }
                .toSet()
            val unapprovedGroups = dependencyGroups - approvedGroups
            check(unapprovedGroups.isEmpty()) {
                "Privacy audit failed: unapproved runtime dependency groups: $unapprovedGroups"
            }
        }
    }
    privacyAudit.configure { dependsOn(auditVariant) }
    tasks.matching { it.name == "assemble$capitalizedName" }.configureEach { dependsOn(auditVariant) }
    tasks.matching { it.name == "bundle$capitalizedName" }.configureEach { dependsOn(auditVariant) }
}

tasks.named("check").configure { dependsOn(privacyAudit) }
