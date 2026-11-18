package com.hearu.app.e2e.tier1_2

import com.hearu.app.e2e.harness.E2ETestHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PlayStoreReadinessE2ETest {

    private val root: File by lazy { E2ETestHarness.resolveProjectRoot() }

    private fun projectFile(relativePath: String): File = File(root, relativePath)

    // ==================== TIER 1: HAPPY PATHS ====================

    @Test
    fun tier1_gradleBuildAndSdk_target35PlusMinificationAndStableDeps() {
        val buildGradle = stripCStyleComments(projectFile("app/build.gradle.kts").readText())
        val compileSdk = Regex("""\bcompileSdk\s*=?\s*(\d+)""").find(buildGradle)?.groupValues?.get(1)?.toIntOrNull()
        val targetSdk = Regex("""\btargetSdk\s*=?\s*(\d+)""").find(buildGradle)?.groupValues?.get(1)?.toIntOrNull()
        val minSdk = Regex("""\bminSdk\s*=?\s*(\d+)""").find(buildGradle)?.groupValues?.get(1)?.toIntOrNull()
        val applicationId = Regex("""\bapplicationId\s*=\s*"([^"]+)"""").find(buildGradle)?.groupValues?.get(1)
        val versionCode = Regex("""\bversionCode\s*=\s*(\d+)""").find(buildGradle)?.groupValues?.get(1)?.toIntOrNull()
        val versionName = Regex("""\bversionName\s*=\s*"([^"]+)"""").find(buildGradle)?.groupValues?.get(1)

        assertNotNull("compileSdk must be declared", compileSdk)
        assertTrue("compileSdk ($compileSdk) must be >= 35 for Play Store compliance", compileSdk!! >= 35)
        assertNotNull("targetSdk must be declared", targetSdk)
        assertTrue("targetSdk ($targetSdk) must be >= 35 for Play Store compliance", targetSdk!! >= 35)
        assertNotNull("minSdk must be declared", minSdk)
        assertTrue("minSdk ($minSdk) must be >= 26", minSdk!! >= 26)
        assertEquals("com.hearu.app", applicationId)
        assertTrue("versionCode must be >= 1", (versionCode ?: 0) >= 1)
        assertFalse("versionName must be non-blank", versionName.isNullOrBlank())

        val buildTypesBlock = extractBraceBlock(buildGradle, "buildTypes") ?: buildGradle
        val releaseBlock = extractRegexBraceBlock(buildTypesBlock, Regex("""\brelease\s*\{"""))
            ?: error("release buildType block not found in app/build.gradle.kts")
        assertTrue("release must set isMinifyEnabled = true", Regex("""isMinifyEnabled\s*=\s*true""").containsMatchIn(releaseBlock))
        assertTrue("release must set isShrinkResources = true", Regex("""isShrinkResources\s*=\s*true""").containsMatchIn(releaseBlock))

        val libsToml = projectFile("gradle/libs.versions.toml").readText()
        val biometricLine = libsToml.lineSequence().firstOrNull { it.trim().startsWith("biometric") }
            ?: error("biometric version missing from libs.versions.toml")
        val lowerBiometric = biometricLine.lowercase()
        assertFalse("biometric must be stable (found alpha): $biometricLine", lowerBiometric.contains("-alpha"))
        assertFalse("biometric must be stable (found beta): $biometricLine", lowerBiometric.contains("-beta"))
        assertFalse("biometric must be stable (found rc): $biometricLine", lowerBiometric.contains("-rc"))
        assertFalse("biometric must be stable (found SNAPSHOT): $biometricLine", lowerBiometric.contains("snapshot"))
    }

    @Test
    fun tier1_manifestAndBackup_allowBackupFalseAdIdRemovedAndFcmNotificationIcon() {
        val manifestFile = projectFile("app/src/main/AndroidManifest.xml")
        val doc = parseXml(manifestFile)
        val manifestEl = doc.documentElement
        val androidNs = "http://schemas.android.com/apk/res/android"
        val toolsNs = "http://schemas.android.com/tools"

        assertFalse(
            "Deprecated package attribute should not be on <manifest>",
            manifestEl.hasAttribute("package")
        )

        val appEl = manifestEl.getElementsByTagName("application").item(0) as Element
        assertEquals("false", appEl.getAttributeNS(androidNs, "allowBackup"))

        val dataExtractionRules = appEl.getAttributeNS(androidNs, "dataExtractionRules")
        val fullBackupContent = appEl.getAttributeNS(androidNs, "fullBackupContent")
        assertTrue("dataExtractionRules must reference @xml/...", dataExtractionRules.startsWith("@xml/"))
        assertTrue("fullBackupContent must reference @xml/...", fullBackupContent.startsWith("@xml/"))

        val dataXml = projectFile("app/src/main/res/xml/${dataExtractionRules.removePrefix("@xml/")}.xml")
        val backupXml = projectFile("app/src/main/res/xml/${fullBackupContent.removePrefix("@xml/")}.xml")
        assertTrue("Referenced ${dataXml.name} must exist", dataXml.isFile)
        assertTrue("Referenced ${backupXml.name} must exist", backupXml.isFile)
        parseXml(dataXml)
        parseXml(backupXml)

        val permissions = (0 until manifestEl.getElementsByTagName("uses-permission").length)
            .map { manifestEl.getElementsByTagName("uses-permission").item(it) as Element }
        val adIdPerm = permissions.firstOrNull {
            it.getAttributeNS(androidNs, "name") == "com.google.android.gms.permission.AD_ID"
        }
        assertNotNull("AD_ID permission removal directive must exist in AndroidManifest.xml", adIdPerm)
        assertEquals("remove", adIdPerm!!.getAttributeNS(toolsNs, "node"))

        val metaDataNodes = (0 until appEl.getElementsByTagName("meta-data").length)
            .map { appEl.getElementsByTagName("meta-data").item(it) as Element }
        val fcmIconMeta = metaDataNodes.firstOrNull {
            it.getAttributeNS(androidNs, "name") == "com.google.firebase.messaging.default_notification_icon"
        }
        assertNotNull("default_notification_icon meta-data must be declared", fcmIconMeta)
        val iconRef = fcmIconMeta!!.getAttributeNS(androidNs, "resource")
        assertTrue("default_notification_icon must point to @drawable/..., was '$iconRef'", iconRef.startsWith("@drawable/"))
        val iconName = iconRef.removePrefix("@drawable/")
        val hasMatchingDrawable = projectFile("app/src/main/res").walkTopDown().any { file ->
            file.isFile &&
                file.parentFile.name.startsWith("drawable") &&
                file.nameWithoutExtension == iconName
        }
        assertTrue("Drawable resource '$iconName' must exist under res/drawable*", hasMatchingDrawable)

        val services = (0 until appEl.getElementsByTagName("service").length)
            .map { appEl.getElementsByTagName("service").item(it) as Element }
        val fcmService = services.firstOrNull {
            it.getAttributeNS(androidNs, "name").endsWith("HearUFirebaseMessagingService")
        }
        assertNotNull("HearUFirebaseMessagingService must be declared", fcmService)
        assertEquals("false", fcmService!!.getAttributeNS(androidNs, "exported"))
    }

    @Test
    fun tier1_resourcesAndProguard_cleanResTreeAndReleaseLogStripping() {
        val resDir = projectFile("app/src/main/res")
        val storeAssetsInRes = resDir.walkTopDown().filter {
            it.isFile && it.name.startsWith("ic_launcher_store")
        }.toList()
        assertTrue("Store assets must not be packaged inside res/: $storeAssetsInRes", storeAssetsInRes.isEmpty())

        val fcmSource = stripCStyleComments(
            projectFile("app/src/main/java/com/hearu/app/service/HearUFirebaseMessagingService.kt").readText()
        )
        assertFalse("Notification small icon must not use R.mipmap.ic_launcher", fcmSource.contains("R.mipmap.ic_launcher"))
        assertTrue("Notification small icon should use R.drawable.*", fcmSource.contains("R.drawable."))

        val themesXml = projectFile("app/src/main/res/values/themes.xml").readText()
        assertFalse("Deprecated android:statusBarColor should be removed from themes.xml", themesXml.contains("android:statusBarColor"))
        assertFalse("Deprecated android:navigationBarColor should be removed from themes.xml", themesXml.contains("android:navigationBarColor"))

        val proguard = projectFile("app/proguard-rules.pro").readText()
        assertTrue("ProGuard must keep Room classes", proguard.contains("RoomDatabase") && proguard.contains("Entity") && proguard.contains("Dao"))
        assertTrue("ProGuard must keep Hilt classes", proguard.contains("HiltAndroidApp") && proguard.contains("HiltViewModel"))
        assertTrue("ProGuard must keep models", proguard.contains("com.hearu.app.model.**"))
        assertTrue("ProGuard must keep HearUFirebaseMessagingService", proguard.contains("HearUFirebaseMessagingService"))
        assertTrue(
            "ProGuard must strip debug/verbose logs via -assumenosideeffects android.util.Log",
            proguard.contains("-assumenosideeffects") && proguard.contains("android.util.Log")
        )
    }

    @Test
    fun tier1_playStoreListingAndPrivacyPolicy_lengthLimitsAndRequiredDisclosures() {
        val listingLines = projectFile("distribution/play_store_listing.md").readLines()
        val appTitle = extractFirstContentLineAfterHeading(listingLines, "App Title")
            .trim('*', '#', ' ', '\t')
        assertTrue("App Title '$appTitle' length (${appTitle.length}) must be in 1..30 chars", appTitle.length in 1..30)

        val shortDesc = extractFirstContentLineAfterHeading(listingLines, "Short Description")
            .trim('*', '#', ' ', '\t')
        assertTrue("Short Description '$shortDesc' length (${shortDesc.length}) must be in 1..80 chars", shortDesc.length in 1..80)

        val fullDesc = extractSectionAfterHeading(listingLines, "Full Description")
        assertTrue("Full Description length (${fullDesc.length}) must be in 100..4000 chars", fullDesc.length in 100..4000)

        val privacyPolicy = projectFile("PRIVACY_POLICY.md").readText().lowercase()
        assertTrue("PRIVACY_POLICY.md must disclose 18+ age policy", privacyPolicy.contains("18+"))
        assertTrue("PRIVACY_POLICY.md must disclose 30-day retention", privacyPolicy.contains("30 day") || privacyPolicy.contains("30-day"))
        assertTrue("PRIVACY_POLICY.md must disclose biometric handling", privacyPolicy.contains("biometric"))
        assertTrue("PRIVACY_POLICY.md must disclose AI/Gemini usage", privacyPolicy.contains("gemini") || privacyPolicy.contains("ai "))
        assertTrue("PRIVACY_POLICY.md must disclose crisis escalation", privacyPolicy.contains("988") || privacyPolicy.contains("crisis"))
        assertTrue(
            "PRIVACY_POLICY.md must disclose voice/audio/microphone usage",
            privacyPolicy.contains("voice") || privacyPolicy.contains("audio") || privacyPolicy.contains("microphone")
        )
        assertTrue("PRIVACY_POLICY.md must include contact email", privacyPolicy.contains("privacy@hearu.app") || privacyPolicy.contains("@"))
    }

    @Test
    fun tier1_firestoreRulesAndFirebaseConfigs_supportModerationSessionsAndValidJson() {
        val rules = stripCStyleComments(projectFile("firestore.rules").readText())
        val usersBlock = extractMatchBlock(rules, "/users/{userId}")
            ?: error("/users/{userId} match block missing from firestore.rules")
        assertTrue("/users/{userId} update must allow blockedUsers", usersBlock.contains("'blockedUsers'"))
        assertTrue("/users/{userId} update must allow role", usersBlock.contains("'role'"))
        assertTrue(
            "/users/{userId} create must forbid self-assigned rating",
            Regex("""!request\.resource\.data\.keys\(\)\.hasAny\(\s*\[[^\]]*'rating'[^\]]*\]\s*\)""").containsMatchIn(usersBlock)
        )
        assertTrue(
            "/users/{userId} must support owner-only account deletion",
            Regex("""allow\s+delete\s*:\s*if\s+isOwner\(\s*userId\s*\)""").containsMatchIn(usersBlock)
        )

        val sessionsBlock = extractMatchBlock(rules, "/sessions/{sessionId}")
            ?: error("/sessions/{sessionId} match block missing from firestore.rules")
        val sessionsOwnBlock = sessionsBlock.substringBefore("match /messages/")
        assertTrue("/sessions/{sessionId} update must allow status and identityRevealed", sessionsOwnBlock.contains("'status'") && sessionsOwnBlock.contains("'identityRevealed'"))
        assertTrue("/sessions/{sessionId} update must validate active/ended status", sessionsOwnBlock.contains("'active'") && sessionsOwnBlock.contains("'ended'"))

        val messagesBlock = extractMatchBlock(rules, "/messages/{messageId}")
            ?: error("/messages/{messageId} match block missing from firestore.rules")
        assertTrue(
            "/messages/{messageId} rules must enforce isSystemMessage == false or systemMessage == false",
            Regex("""\b(?:isSystemMessage|systemMessage)\s*==\s*false\b""").containsMatchIn(messagesBlock)
        )
        assertTrue("/messages/{messageId} rules should account for waveformAmplitudes", messagesBlock.contains("waveformAmplitudes"))

        val reportsBlock = extractMatchBlock(rules, "/reports/{reportId}")
            ?: error("/reports/{reportId} match block missing from firestore.rules")
        assertTrue(
            "/reports/{reportId} must require reporterId == request.auth.uid",
            Regex("""reporterId\s*==\s*request\.auth\.uid""").containsMatchIn(reportsBlock)
        )

        val firebaseJsonFile = projectFile("firebase.json")
        val indexesJsonFile = projectFile("firestore.indexes.json")
        val packageJsonFile = projectFile("functions/package.json")
        assertTrue("firebase.json must exist", firebaseJsonFile.isFile)
        assertTrue("firestore.indexes.json must exist", indexesJsonFile.isFile)
        assertTrue("functions/package.json must exist", packageJsonFile.isFile)

        val firebaseJson = parseJsonObject(firebaseJsonFile.readText())
        val indexesJson = parseJsonObject(indexesJsonFile.readText())
        val packageJson = parseJsonObject(packageJsonFile.readText())

        assertTrue("firebase.json must configure firestore", firebaseJson.containsKey("firestore"))
        assertTrue("firebase.json must configure functions", firebaseJson.containsKey("functions"))
        assertTrue("firestore.indexes.json must contain indexes", indexesJson.containsKey("indexes"))
        assertTrue(
            "functions/package.json must contain dependencies and engines",
            packageJson.containsKey("dependencies") && packageJson.containsKey("engines")
        )
    }

    @Test
    fun tier1_cloudFunctionsAndCi_matchmakingPaginationAndCiReleaseLint() {
        val functionsJs = stripCStyleComments(projectFile("functions/index.js").readText())
        val matchFn = extractBraceBlock(functionsJs, "exports.matchSeekerWithGiver")
            ?: error("matchSeekerWithGiver missing")
        val purgeFn = extractBraceBlock(functionsJs, "exports.purgeExpiredChats")
            ?: error("purgeExpiredChats missing")

        assertTrue("matchSeekerWithGiver must return giverId", matchFn.contains("giverId"))
        assertTrue("matchSeekerWithGiver must return giverDisplayName", matchFn.contains("giverDisplayName"))
        assertTrue("matchSeekerWithGiver must filter blockedUsers", matchFn.contains("blockedUsers"))

        val purgeFnCode = stripJsStringLiterals(purgeFn)
        assertTrue(
            "purgeExpiredChats must paginate/loop to handle >100 expired sessions",
            Regex("""\b(while|for|do)\b|startAfter|purgeExpiredChats\s*\(""").containsMatchIn(purgeFnCode)
        )

        val ciYml = projectFile(".github/workflows/android_ci.yml").readText()
        assertTrue("CI must run unit tests", ciYml.contains("testDebugUnitTest") || Regex("""gradlew\s+[^\n]*\btest\b""").containsMatchIn(ciYml))
        assertTrue("CI must run Android Lint", Regex("""lint(Debug|Release)?\b""").containsMatchIn(ciYml))
        assertTrue("CI must verify release build", ciYml.contains("assembleRelease") || ciYml.contains("bundleRelease"))
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_releaseBuild_neverUsesDebugSigningAndGitignoreProtectsSecrets() {
        val buildGradle = stripCStyleComments(projectFile("app/build.gradle.kts").readText())
        val buildTypesBlock = extractBraceBlock(buildGradle, "buildTypes") ?: buildGradle
        val releaseBlock = extractRegexBraceBlock(buildTypesBlock, Regex("""\brelease\s*\{"""))
            ?: error("release buildType block not found")

        assertFalse(
            "Release buildType must NEVER use debug signingConfig",
            Regex("""signingConfigs\s*(?:\.\s*getByName\s*\(\s*"debug"\s*\)|\[\s*"debug"\s*\])""").containsMatchIn(releaseBlock)
        )
        if (buildGradle.contains("keystore.properties")) {
            val hasConditionalKeystoreLoad = Regex(
                """if\s*\([^)]*(?:exists\(\)|isFile)[^)]*\)\s*\{[^}]*(?:keystoreProperties|load\s*\()""",
                RegexOption.DOT_MATCHES_ALL
            ).containsMatchIn(buildGradle) || Regex(
                """(?:keystorePropertiesFile|file\(\s*"keystore\.properties"\s*\))\.(?:exists\(\)|isFile)"""
            ).containsMatchIn(buildGradle)
            assertTrue(
                "keystore.properties loading must be conditional (.exists() / .isFile) so CI succeeds without secrets",
                hasConditionalKeystoreLoad
            )
        }

        val gitignore = projectFile(".gitignore").readText()
        val lines = gitignore.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        assertTrue(
            ".gitignore must ignore build directories",
            lines.any { line -> Regex("""^(\*\*?/)?(app/)?build/?$""").matches(line) || line == "/build" || line == "/build/" }
        )
        assertTrue(".gitignore must ignore keystore.properties", lines.any { it.contains("keystore.properties") })
        assertTrue(".gitignore must ignore *.jks", lines.any { it.contains("*.jks") })
        assertTrue(".gitignore must ignore *.keystore", lines.any { it.contains("*.keystore") })
    }

    @Test
    fun tier2_manifestAndBackupRules_denyCleartextDebuggableAndExcludeLocalDbPrefs() {
        val manifestFile = projectFile("app/src/main/AndroidManifest.xml")
        val doc = parseXml(manifestFile)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val appEl = doc.documentElement.getElementsByTagName("application").item(0) as Element

        assertNotEquals("true", appEl.getAttributeNS(androidNs, "usesCleartextTraffic"))
        assertNotEquals("true", appEl.getAttributeNS(androidNs, "debuggable"))
        assertEquals("false", appEl.getAttributeNS(androidNs, "allowBackup"))

        val activities = (0 until appEl.getElementsByTagName("activity").length)
            .map { appEl.getElementsByTagName("activity").item(it) as Element }
        val nonMainExportedActivities = activities.filter {
            it.getAttributeNS(androidNs, "exported") == "true" && !it.getAttributeNS(androidNs, "name").endsWith("MainActivity")
        }
        assertTrue("Unexpected exported activities: $nonMainExportedActivities", nonMainExportedActivities.isEmpty())

        val dataXml = projectFile("app/src/main/res/xml/${appEl.getAttributeNS(androidNs, "dataExtractionRules").removePrefix("@xml/")}.xml")
        val backupXml = projectFile("app/src/main/res/xml/${appEl.getAttributeNS(androidNs, "fullBackupContent").removePrefix("@xml/")}.xml")
        val dataContent = dataXml.readText().lowercase()
        val backupContent = backupXml.readText().lowercase()

        assertTrue("data_extraction_rules.xml must exclude database", dataContent.contains("<exclude") && dataContent.contains("database"))
        assertTrue("data_extraction_rules.xml must exclude preferences/datastore", dataContent.contains("<exclude") && (dataContent.contains("sharedpref") || dataContent.contains("datastore") || dataContent.contains("files")))
        assertTrue("backup_rules.xml must exclude database", backupContent.contains("<exclude") && backupContent.contains("database"))
        assertTrue("backup_rules.xml must exclude preferences/datastore", backupContent.contains("<exclude") && (backupContent.contains("sharedpref") || backupContent.contains("datastore") || backupContent.contains("files")))
    }

    @Test
    fun tier2_docsAndListing_noUntruthfulClaimsOrModelMismatch() {
        val privacyPolicy = projectFile("PRIVACY_POLICY.md").readText()
        val playListing = projectFile("distribution/play_store_listing.md").readText()
        val stringsXml = projectFile("app/src/main/res/values/strings.xml").readText()
        val authRepo = projectFile("app/src/main/java/com/hearu/app/repository/AuthRepository.kt").readText()
        val dbModule = projectFile("app/src/main/java/com/hearu/app/di/DatabaseModule.kt").readText()
        val buildGradle = projectFile("app/build.gradle.kts").readText()
        val geminiService = projectFile("app/src/main/java/com/hearu/app/ai/GeminiAiService.kt").readText()

        assertFalse("PRIVACY_POLICY.md must not claim 'cryptographically blurred'", privacyPolicy.contains("cryptographically blurred", ignoreCase = true))
        assertFalse("play_store_listing.md must not claim 'cryptographically blurred'", playListing.contains("cryptographically blurred", ignoreCase = true))

        if (!authRepo.contains("PhoneAuth", ignoreCase = true)) {
            assertFalse("PRIVACY_POLICY.md must not falsely claim verified phone number auth", privacyPolicy.contains("verified phone number", ignoreCase = true))
        }

        val usesSqlCipher = dbModule.contains("sqlcipher", ignoreCase = true) || buildGradle.contains("sqlcipher", ignoreCase = true)
        if (!usesSqlCipher) {
            assertFalse("play_store_listing.md must not falsely claim Encrypted local database without SQLCipher", playListing.contains("Encrypted local database", ignoreCase = true))
        }

        val configuredModel = Regex("""modelName\s*=\s*"([^"]+)"""").find(geminiService)?.groupValues?.get(1).orEmpty()
        if (!configuredModel.contains("3.7")) {
            assertFalse("play_store_listing.md must not mismatch Gemini model (Gemini 3.7)", playListing.contains("Gemini 3.7", ignoreCase = true))
            assertFalse("PRIVACY_POLICY.md must not mismatch Gemini model (Gemini 3.7)", privacyPolicy.contains("Gemini 3.7", ignoreCase = true))
            assertFalse("strings.xml must not mismatch Gemini model (Gemini 3.7)", stringsXml.contains("Gemini 3.7", ignoreCase = true))
        }
    }

    @Test
    fun tier2_firestoreRules_blockWildcardAccessPrivilegeEscalationAndTampering() {
        val rules = stripCStyleComments(projectFile("firestore.rules").readText())

        assertFalse("Wildcard allow read, write: if true forbidden", Regex("""allow\s+read\s*,\s*write\s*:\s*if\s+true""").containsMatchIn(rules))
        assertFalse("Wildcard /{document=**} auth-only access forbidden", rules.contains("{document=**}"))

        val isOwnerBlock = extractRegexBraceBlock(rules, Regex("""function\s+isOwner\s*\(\s*userId\s*\)\s*\{"""))
            ?: error("isOwner(userId) helper missing from firestore.rules")
        assertTrue(
            "isOwner(userId) must require authentication and uid match",
            (isOwnerBlock.contains("isAuthenticated()") || Regex("""request\.auth\s*!=\s*null""").containsMatchIn(isOwnerBlock)) &&
                Regex("""request\.auth\.uid\s*==\s*userId""").containsMatchIn(isOwnerBlock)
        )

        val usersBlock = extractMatchBlock(rules, "/users/{userId}")
            ?: error("/users/{userId} match block missing from firestore.rules")
        val usersDeleteRule = Regex("""allow\s+delete\s*:\s*([^;]+);""").find(usersBlock)?.groupValues?.get(1)?.trim().orEmpty()
        assertTrue(
            "/users/{userId} must require owner-only deletion (allow delete: if isOwner(userId))",
            Regex("""^if\s+isOwner\(\s*userId\s*\)$""").matches(usersDeleteRule)
        )
        assertFalse(
            "/users/{userId} must not allow unauthenticated/unrestricted delete (if true)",
            Regex("""\bif\s+true\b""").containsMatchIn(usersDeleteRule)
        )
        assertFalse(
            "/users/{userId} must not allow non-owner authenticated delete (if request.auth != null / isAuthenticated())",
            Regex("""\bif\s+(?:request\.auth\s*!=\s*null|isAuthenticated\(\s*\))(?=\s|$)""").containsMatchIn(usersDeleteRule)
        )

        val updateSection = Regex("""allow\s+update\s*:\s*([^;]+);""").find(usersBlock)?.groupValues?.get(1).orEmpty()
        assertTrue("/users/{userId} allow update rule must exist", updateSection.isNotBlank())
        assertFalse(
            "/users update must not bypass ownership with '|| true'",
            Regex("""\|\|\s*true\b""").containsMatchIn(updateSection)
        )
        assertFalse(
            "/users update must not allow standalone isAuthenticated() / request.auth != null",
            Regex("""\bif\s+(?:request\.auth\s*!=\s*null|isAuthenticated\(\s*\))(?:\s|$)""").containsMatchIn(updateSection)
        )
        assertTrue(
            "/users update must require isOwner(userId)",
            Regex("""^\s*if\s+isOwner\(\s*userId\s*\)\s*(?:&&|$)""").containsMatchIn(updateSection)
        )
        val hasOnlyMatch = Regex("""hasOnly\(\s*\[([^\]]+)\]\s*\)""").find(updateSection)?.groupValues?.get(1).orEmpty()
        assertTrue("/users update must restrict affectedKeys via hasOnly([...])", hasOnlyMatch.isNotBlank())
        assertTrue("/users update must allow 'blockedUsers'", hasOnlyMatch.contains("'blockedUsers'"))
        assertTrue("/users update must allow 'role' for in-app Role Switcher", hasOnlyMatch.contains("'role'"))
        assertTrue(
            "/users update must validate 'role' in ['seeker', 'giver', 'both']",
            Regex("""request\.resource\.data\.role\s+in\s+\[\s*'seeker'\s*,\s*'giver'\s*,\s*'both'\s*\]""").containsMatchIn(updateSection)
        )
        assertFalse(
            "/users update must not allow empty role string",
            Regex("""request\.resource\.data\.role\s+in\s+\[[^\]]*''[^\]]*\]""").containsMatchIn(updateSection)
        )
        assertFalse("/users update must not allow tampering 'rating'", hasOnlyMatch.contains("'rating'"))
        assertFalse("/users update must not allow tampering 'createdAt'", hasOnlyMatch.contains("'createdAt'"))
        assertFalse("/users update must not allow tampering 'uid'", hasOnlyMatch.contains("'uid'"))
        assertFalse("/users update must not allow tampering 'email'", hasOnlyMatch.contains("'email'"))

        val sessionsBlock = extractMatchBlock(rules, "/sessions/{sessionId}")
            ?: error("/sessions/{sessionId} match block missing from firestore.rules")
        val sessionsOwnBlock = sessionsBlock.substringBefore("match /messages/")
        assertTrue("/sessions/{sessionId} must forbid client create", Regex("""allow\s+create\s*:\s*if\s+false""").containsMatchIn(sessionsOwnBlock))
        assertTrue("/sessions/{sessionId} must forbid client delete", Regex("""allow\s+delete\s*:\s*if\s+false""").containsMatchIn(sessionsOwnBlock))

        val messagesBlock = extractMatchBlock(rules, "/messages/{messageId}")
            ?: error("/messages/{messageId} match block missing from firestore.rules")
        assertTrue(
            "/messages/{messageId} must forbid update and delete",
            Regex("""allow\s+update\s*,\s*delete\s*:\s*if\s+false""").containsMatchIn(messagesBlock) ||
                Regex("""allow\s+delete\s*,\s*update\s*:\s*if\s+false""").containsMatchIn(messagesBlock) ||
                (Regex("""allow\s+update\s*:\s*if\s+false""").containsMatchIn(messagesBlock) &&
                    Regex("""allow\s+delete\s*:\s*if\s+false""").containsMatchIn(messagesBlock))
        )
        assertTrue("/messages/{messageId} must enforce senderId == request.auth.uid", Regex("""senderId\s*==\s*request\.auth\.uid""").containsMatchIn(messagesBlock))

        val reportsBlock = extractMatchBlock(rules, "/reports/{reportId}")
            ?: error("/reports/{reportId} match block missing from firestore.rules")
        assertTrue("/reports/{reportId} must forbid read, update, delete", Regex("""allow\s+read\s*,\s*update\s*,\s*delete\s*:\s*if\s+false""").containsMatchIn(reportsBlock))
    }

    @Test
    fun tier2_cloudFunctionsAndFcm_enforceAuthNoSelfMatchAndNoTokenPiiLogging() {
        val functionsJs = stripCStyleComments(projectFile("functions/index.js").readText())
        val matchFn = extractBraceBlock(functionsJs, "exports.matchSeekerWithGiver")
            ?: error("matchSeekerWithGiver missing")
        val reportFn = extractBraceBlock(functionsJs, "exports.onReportSubmitted")
            ?: error("onReportSubmitted missing")

        assertTrue("matchSeekerWithGiver must reject unauthenticated calls", matchFn.contains("!context.auth") && matchFn.contains("unauthenticated"))
        assertTrue("matchSeekerWithGiver must prevent self-matching", matchFn.contains("doc.id === seekerId") || matchFn.contains("doc.id !== seekerId"))

        assertFalse("onReportSubmitted must never log snap.data()", reportFn.contains("snap.data()"))
        assertFalse("onReportSubmitted must never log raw reason", reportFn.contains(".reason"))
        assertFalse("onReportSubmitted must never log reporterId", reportFn.contains("reporterId"))
        assertFalse("onReportSubmitted must never log reportedUserId", reportFn.contains("reportedUserId"))

        val fcmSource = stripCStyleComments(
            projectFile("app/src/main/java/com/hearu/app/service/HearUFirebaseMessagingService.kt").readText()
        )
        val onNewTokenBody = extractBraceBlock(fcmSource, "fun onNewToken")
            ?: fcmSource.substringAfter("fun onNewToken")
        assertFalse(
            "onNewToken must never log raw FCM token",
            containsRawTokenLog(onNewTokenBody)
        )
    }

    @Test
    fun tier2_resAndRoomHygiene_noDuplicateLauncherForegroundsAndSchemaExportEnabled() {
        val adaptiveIcon = projectFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        if (adaptiveIcon.isFile && adaptiveIcon.readText().contains("@mipmap/ic_launcher_foreground")) {
            val duplicateDrawables = projectFile("app/src/main/res").walkTopDown().filter {
                it.isFile && it.parentFile.name.startsWith("drawable") && it.nameWithoutExtension == "ic_launcher_foreground"
            }.toList()
            assertTrue("Duplicate drawable*/ic_launcher_foreground.* files must be removed: $duplicateDrawables", duplicateDrawables.isEmpty())
        }

        val appDatabase = stripCStyleComments(
            projectFile("app/src/main/java/com/hearu/app/data/local/AppDatabase.kt").readText()
        )
        assertTrue("AppDatabase must enable exportSchema = true", Regex("""exportSchema\s*=\s*true""").containsMatchIn(appDatabase))

        val buildGradle = projectFile("app/build.gradle.kts").readText()
        val hasSchemasDir = projectFile("app/schemas").isDirectory
        val hasKspSchemaArg = buildGradle.contains("room.schemaLocation")
        assertTrue("app/schemas directory or room.schemaLocation KSP arg must be configured", hasSchemasDir || hasKspSchemaArg)
    }

    // ==================== HELPERS ====================

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

    private fun containsRawTokenLog(body: String): Boolean {
        val logStartRegex = Regex("""\bLog\.\w+\s*\(""")
        for (match in logStartRegex.findAll(body)) {
            val openParen = match.range.last
            var depth = 0
            var inDouble = false
            var closeParen = -1
            var i = openParen
            while (i < body.length) {
                val c = body[i]
                if (inDouble) {
                    if (c == '\\' && i + 1 < body.length) {
                        i += 2
                        continue
                    }
                    if (c == '"') inDouble = false
                } else {
                    when (c) {
                        '"' -> inDouble = true
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) {
                                closeParen = i
                                break
                            }
                        }
                    }
                }
                i++
            }
            val args = if (closeParen > openParen) body.substring(openParen + 1, closeParen) else body.substring(openParen + 1)
            if (Regex("""\$\{?[^}\n]*\btoken\b""").containsMatchIn(args)) return true
            val withoutQuotedStrings = args.replace(Regex(""""(?:\\.|[^"\\])*""""), " ")
            if (Regex("""\btoken\b""").containsMatchIn(withoutQuotedStrings)) return true
        }
        return false
    }

    private fun stripJsStringLiterals(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '"' || c == '\'' || c == '`') {
                val quote = c
                out.append(' ')
                i++
                while (i < source.length) {
                    val cur = source[i]
                    if (cur == '\\' && i + 1 < source.length) {
                        i += 2
                        continue
                    }
                    if (cur == quote) {
                        i++
                        break
                    }
                    i++
                }
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun stripCStyleComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var inSingle = false
        var inDouble = false
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
            if (!inSingle && !inDouble && c == '/' && next == '/') {
                i += 2
                while (i < source.length && source[i] != '\n') i++
                continue
            }
            if (!inSingle && !inDouble && c == '/' && next == '*') {
                i += 2
                while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) i++
                i += 2
                continue
            }
            if (c == '\\' && (inSingle || inDouble) && i + 1 < source.length) {
                out.append(c).append(next)
                i += 2
                continue
            }
            if (c == '"' && !inSingle) inDouble = !inDouble
            else if (c == '\'' && !inDouble) inSingle = !inSingle
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun extractBraceBlock(text: String, headerToken: String): String? {
        val startIdx = text.indexOf(headerToken)
        if (startIdx < 0) return null
        val openBrace = text.indexOf('{', startIdx)
        if (openBrace < 0) return null
        return sliceBalancedBraces(text, openBrace)
    }

    private fun extractRegexBraceBlock(text: String, pattern: Regex): String? {
        val match = pattern.find(text) ?: return null
        val openBrace = if (text[match.range.last] == '{') match.range.last else text.indexOf('{', match.range.last)
        if (openBrace < 0) return null
        return sliceBalancedBraces(text, openBrace)
    }

    private fun sliceBalancedBraces(text: String, openBrace: Int): String? {
        var depth = 0
        for (i in openBrace until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openBrace + 1, i)
                }
            }
        }
        return null
    }

    private fun extractMatchBlock(rules: String, pathFragment: String): String? {
        val match = Regex("""\bmatch\s+${Regex.escape(pathFragment)}\s*\{""").find(rules) ?: return null
        return sliceBalancedBraces(rules, match.range.last)
    }

    private fun extractFirstContentLineAfterHeading(lines: List<String>, headingFragment: String): String {
        val headingIndex = lines.indexOfFirst { it.startsWith("#") && it.contains(headingFragment) }
        assertTrue("Heading '$headingFragment' missing from listing", headingIndex >= 0)
        return lines.drop(headingIndex + 1)
            .firstOrNull { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && trimmed != "---" && !trimmed.startsWith("#")
            } ?: error("No content line found under '$headingFragment'")
    }

    private fun extractSectionAfterHeading(lines: List<String>, headingFragment: String): String {
        val headingIndex = lines.indexOfFirst { it.startsWith("#") && it.contains(headingFragment) }
        assertTrue("Heading '$headingFragment' missing from listing", headingIndex >= 0)
        val sectionLines = mutableListOf<String>()
        for (i in (headingIndex + 1) until lines.size) {
            val line = lines[i]
            if (line.startsWith("## ")) break
            if (line.trim() == "---") break
            sectionLines.add(line)
        }
        return sectionLines.joinToString("\n").trim()
    }

    private fun parseJsonObject(raw: String): Map<String, Any?> {
        val parser = SimpleJsonParser(raw)
        val value = parser.parseValue()
        parser.skipWhitespace()
        assertEquals("Unexpected trailing characters in JSON", raw.length, parser.pos)
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: error("Expected top-level JSON object")
    }

    private class SimpleJsonParser(private val src: String) {
        var pos = 0

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) error("Unexpected end of JSON")
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> expectLiteral("true", true)
                'f' -> expectLiteral("false", false)
                'n' -> expectLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected JSON char '$c' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // {
            skipWhitespace()
            val map = linkedMapOf<String, Any?>()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (pos >= src.length || src[pos] != '"') error("Expected JSON string key at $pos")
                val key = parseString()
                skipWhitespace()
                if (pos >= src.length || src[pos] != ':') error("Expected ':' after key '$key' at $pos")
                pos++
                map[key] = parseValue()
                skipWhitespace()
                if (pos >= src.length) error("Unterminated JSON object")
                when (src[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> error("Expected ',' or '}' in JSON object at $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // [
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                if (pos >= src.length) error("Unterminated JSON array")
                when (src[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> error("Expected ',' or ']' in JSON array at $pos")
                }
            }
        }

        private fun parseString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) error("Unterminated escape in JSON string")
                        val esc = src[pos++]
                        when (esc) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("Invalid JSON escape \\$esc")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            error("Unterminated JSON string")
        }

        private fun expectLiteral(literal: String, value: Any?): Any? {
            if (!src.startsWith(literal, pos)) error("Expected '$literal' at $pos")
            pos += literal.length
            return value
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos].isDigit()) pos++
            if (pos < src.length && src[pos] == '.') {
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            val rawNum = src.substring(start, pos)
            return rawNum.toLongOrNull() ?: rawNum.toDouble()
        }
    }
}
