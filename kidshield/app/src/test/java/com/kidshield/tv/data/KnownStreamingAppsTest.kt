package com.kidshield.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownStreamingAppsTest {

    // ── Package name lookup ───────────────────────────────────────

    @Test
    fun `findByPackage returns known app for Netflix`() {
        val app = KnownStreamingApps.findByPackage("com.netflix.ninja")
        assertNotNull(app)
        assertEquals("Netflix", app!!.displayName)
    }

    @Test
    fun `findByPackage returns known app for YouTube TV`() {
        val app = KnownStreamingApps.findByPackage("com.google.android.youtube.tv")
        assertNotNull(app)
        assertEquals("YouTube", app!!.displayName)
    }

    @Test
    fun `findByPackage returns null for unknown package`() {
        val app = KnownStreamingApps.findByPackage("com.unknown.package")
        assertNull(app)
    }

    // ── Fire TV variants ──────────────────────────────────────────

    @Test
    fun `Fire TV YouTube variant is recognized`() {
        val app = KnownStreamingApps.findByPackage("com.amazon.firetv.youtube")
        assertNotNull("Fire TV YouTube should be in known apps", app)
        assertEquals("YouTube", app!!.displayName)
    }

    @Test
    fun `Fire TV YouTube Kids variant is recognized`() {
        val app = KnownStreamingApps.findByPackage("com.amazon.firetv.youtube.kids")
        assertNotNull("Fire TV YouTube Kids should be in known apps", app)
        assertEquals("YouTube Kids", app!!.displayName)
        assertTrue(app.isKidsVariant)
    }

    @Test
    fun `Fire TV Prime Video variant is recognized`() {
        val app = KnownStreamingApps.findByPackage("com.amazon.firetv.pvod")
        assertNotNull("Fire TV Prime Video should be in known apps", app)
        assertEquals("Prime Video", app!!.displayName)
    }

    @Test
    fun `JioHotstar TV variant is recognized`() {
        val app = KnownStreamingApps.findByPackage("in.startv.hotstar.dplus.tv")
        assertNotNull("JioHotstar TV variant should be in known apps", app)
        assertEquals("JioHotstar", app!!.displayName)
    }

    @Test
    fun `Disney Plus TV variant is recognized`() {
        val app = KnownStreamingApps.findByPackage("com.disney.disneyplus.tv")
        assertNotNull("Disney+ TV variant should be in known apps", app)
        assertEquals("Disney+", app!!.displayName)
    }

    // ── Variant lookup ────────────────────────────────────────────

    @Test
    fun `findVariants returns both Android TV and Fire TV YouTube`() {
        val variants = KnownStreamingApps.findVariants("YouTube")
        assertTrue("Should have at least 2 YouTube variants", variants.size >= 2)

        val packages = variants.map { it.packageName }
        assertTrue(packages.contains("com.google.android.youtube.tv"))
        assertTrue(packages.contains("com.amazon.firetv.youtube"))
    }

    @Test
    fun `findVariants returns both Prime Video variants`() {
        val variants = KnownStreamingApps.findVariants("Prime Video")
        assertTrue("Should have at least 2 Prime Video variants", variants.size >= 2)

        val packages = variants.map { it.packageName }
        assertTrue(packages.contains("com.amazon.avod"))
        assertTrue(packages.contains("com.amazon.firetv.pvod"))
    }

    @Test
    fun `findVariants returns empty for unknown app name`() {
        val variants = KnownStreamingApps.findVariants("NonExistentApp")
        assertTrue(variants.isEmpty())
    }

    // ── Unique names ──────────────────────────────────────────────

    @Test
    fun `uniqueAppNames deduplicates Fire TV and Android TV variants`() {
        val names = KnownStreamingApps.uniqueAppNames

        // YouTube should appear once, not twice
        assertEquals(
            "YouTube should only appear once",
            1,
            names.count { it == "YouTube" }
        )

        assertEquals(
            "Prime Video should only appear once",
            1,
            names.count { it == "Prime Video" }
        )
    }

    // ── All apps have valid data ──────────────────────────────────

    @Test
    fun `all known apps have non-empty package names`() {
        KnownStreamingApps.apps.forEach { app ->
            assertTrue(
                "Package name should not be empty for ${app.displayName}",
                app.packageName.isNotEmpty()
            )
        }
    }

    @Test
    fun `all known apps have non-empty display names`() {
        KnownStreamingApps.apps.forEach { app ->
            assertTrue(
                "Display name should not be empty for ${app.packageName}",
                app.displayName.isNotEmpty()
            )
        }
    }

    @Test
    fun `no duplicate package names in the registry`() {
        val packages = KnownStreamingApps.apps.map { it.packageName }
        assertEquals(
            "Should have no duplicate package names",
            packages.size,
            packages.distinct().size
        )
    }

    @Test
    fun `YouTube Kids variants are marked as kids variant`() {
        val youtubeKidsApps = KnownStreamingApps.apps.filter {
            it.displayName == "YouTube Kids"
        }
        youtubeKidsApps.forEach {
            assertTrue(
                "${it.packageName} should be marked as kids variant",
                it.isKidsVariant
            )
        }
    }
}
