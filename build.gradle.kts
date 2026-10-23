plugins {
    idea
    eclipse
    `java-library`
    id("net.minecraftforge.gradle") version "6.0.54"
}

val minecraftVersion = property("minecraft_version") as String
val forgeVersion = property("forge_version") as String
val createVersion = property("create_maven_version") as String
val ponderVersion = property("ponder_version") as String
val flywheelVersion = property("flywheel_version") as String
val registrateVersion = property("registrate_version") as String
val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modVersion = property("mod_version") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String
val modLicense = property("mod_license") as String

group = "com.bettercontent"
version = modVersion

base { archivesName.set(property("artifact_name") as String) }

fun deobf(notation: String): Any =
    requireNotNull(extensions.getByName("fg").withGroovyBuilder { "deobf"(notation) })

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

minecraft {
    mappings("official", minecraftVersion)
    copyIdeResources = true
    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "info")
            property("forge.enabledGameTestNamespaces", modId)
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
            mods { create(modId) { source(sourceSets.main.get()) } }
        }
        create("client")
        create("server") { arg("--nogui") }
        create("gameTestServer")
    }
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/mirror")
    maven("https://www.cursemaven.com") { content { includeGroup("curse.maven") } }
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")
    implementation(deobf("com.simibubi.create:create-$minecraftVersion:$createVersion:slim"))
    implementation(deobf("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion"))
    compileOnly(deobf("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion"))
    runtimeOnly(deobf("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:$flywheelVersion"))
    implementation(deobf("com.tterrag.registrate:Registrate:$registrateVersion"))
    implementation("io.github.llamalad7:mixinextras-forge:0.5.4")
    compileOnly(deobf("curse.maven:ars-nouveau-401955:6688854"))
    compileOnly(deobf("curse.maven:pneumaticcraft-repressurized-281849:7307654"))
    compileOnly(deobf("curse.maven:goety-586095:8087429"))
    runtimeOnly(deobf("curse.maven:ars-nouveau-401955:6688854"))
    runtimeOnly(deobf("curse.maven:pneumaticcraft-repressurized-281849:7307654"))
    runtimeOnly(deobf("curse.maven:blood-magic-224791:7956981"))
    runtimeOnly(deobf("curse.maven:goety-586095:8087429"))
    runtimeOnly(deobf("curse.maven:malum-484064:6646111"))
    // Actual production JEED 2.2.5 (CF532286/6172935), raw JAR SHA1
    // 9f6b1884ad8b81c0e8796daf7871eeb54271f27b; keep declared transitive dependencies.
    runtimeOnly(deobf("curse.maven:jeed-532286:6172935"))
    runtimeOnly(deobf("curse.maven:power-grid-1321420:7714613"))
    runtimeOnly(deobf("curse.maven:patchouli-306770:7731017"))
    runtimeOnly(deobf("curse.maven:curios-309927:6418456"))
    runtimeOnly(deobf("curse.maven:geckolib-388172:7553267"))
    runtimeOnly(deobf("curse.maven:architectury-api-419699:5137938"))
    runtimeOnly(deobf("curse.maven:lodestone-616457:6213794"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.processResources {
    val props = mapOf(
        "minecraftVersion" to minecraftVersion,
        "forgeVersion" to forgeVersion,
        "modId" to modId,
        "modName" to modName,
        "modVersion" to modVersion,
        "modAuthors" to modAuthors,
        "modDescription" to modDescription,
        "modLicense" to modLicense,
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.withType<Test>().configureEach { useJUnitPlatform() }
tasks.named<Jar>("jar") { finalizedBy("reobfJar") }

val stageRuntimeJar by tasks.registering(Copy::class) {
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$modVersion.jar" }
}

tasks.named("assemble") { dependsOn(stageRuntimeJar) }
tasks.register("verifyFast") { dependsOn(tasks.named("test"), tasks.named("assemble")) }
tasks.register("verifyFull") { dependsOn(tasks.named("verifyFast"), tasks.named("runGameTestServer")) }

apply(from = "gradle/gametest-evidence.gradle")
apply(from = "gradle/native-gametest-fixture.gradle")
apply(from = "gradle/native-canonical-policy.gradle")
apply(from = "gradle/native-glm.gradle")
apply(from = "gradle/native-canonical-providers.gradle")

// Audit every runtime source set, including nonshipping native hooks; exclude only declared JUnit controls.
tasks.named<Test>("test") {
    val runtimeSets = sourceSets.filter { it.name !in setOf("test", "nativeFixtureTest") }
    systemProperty("bc.anchors.reflection.sourceRoots", runtimeSets.flatMap { it.allSource.srcDirs }
        .joinToString(System.getProperty("path.separator")) { it.absolutePath })
    systemProperty("bc.anchors.reflection.classRoots", runtimeSets.flatMap { it.output.classesDirs.files }
        .joinToString(System.getProperty("path.separator")) { it.absolutePath })
}
