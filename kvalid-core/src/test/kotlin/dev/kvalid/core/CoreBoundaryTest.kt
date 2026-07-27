package dev.kvalid.core

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

/** kvalid-core es dominio puro: ni KSP, ni KotlinPoet, ni frameworks. */
class CoreBoundaryTest {

    private val forbidden = listOf("com.google.devtools.ksp", "com.squareup.kotlinpoet", "org.springframework")

    @Test
    fun `kvalid-core no importa APIs de compilador ni frameworks`() {
        val files = Konsist.scopeFromProject(moduleName = "kvalid-core", sourceSetName = "main").files
        assertTrue(files.isNotEmpty(), "Konsist no encontró archivos en kvalid-core/main")
        files.assertFalse { file -> file.imports.any { imp -> forbidden.any { imp.name.startsWith(it) } } }
    }
}
