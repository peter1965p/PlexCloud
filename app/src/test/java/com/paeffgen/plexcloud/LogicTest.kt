package com.paeffgen.plexcloud

import com.paeffgen.plexcloud.auth.Jwt
import com.paeffgen.plexcloud.auth.Pkce
import com.paeffgen.plexcloud.data.CloudItem
import com.paeffgen.plexcloud.data.Parse
import com.paeffgen.plexcloud.data.ROOT
import com.paeffgen.plexcloud.domain.Format
import com.paeffgen.plexcloud.domain.Version
import com.paeffgen.plexcloud.update.UpdateSupport
import com.paeffgen.plexcloud.upload.Meter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Base64

class PkceTest {
    @Test fun verifierHasValidLengthAndAlphabet() {
        val v = Pkce.verifier()
        assertTrue(v.length in 43..128)
        assertTrue(v.all { it.isLetterOrDigit() || it in "-._~" })
        assertNotEquals(v, Pkce.verifier())
    }

    @Test fun challengeMatchesRfc7636Example() {
        // Beispiel aus RFC 7636, Anhang B
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun jwtPayloadIsReadWithoutPadding() {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val token = "x." + enc.encodeToString("""{"exp":1800000000,"email":"a@b.de"}""".toByteArray()) + ".sig"
        assertEquals(1_800_000_000_000L, Jwt.expiresAtMs(token))
        assertEquals("a@b.de", Jwt.payload(token)!!.getString("email"))
        assertEquals(0L, Jwt.expiresAtMs("kaputt"))
    }
}

class ParseTest {
    private val sample = """
        {"ansicht":"ordner","ordner":"f1","pfad":[{"itemId":"f0","name":"Kunden"},{"itemId":"f1","name":"Müller GmbH"}],
         "usage":{"used":13314398618,"limit":107374182400,"maxFile":5368709120,"plan":"plexora","unlimited":false,"downloaded":3,"downloadLimit":null,"files":42},
         "items":[
           {"itemId":"a","kind":"folder","name":"Baustelle","parentId":"f1","size":1900000000,"contentType":"","previewable":false,"links":1,"portal":false,"updatedAt":"2026-11-15T09:32:00.000Z","thumbUrl":null,"count":42},
           {"itemId":"b","kind":"file","name":"Angebot.pdf","parentId":"f1","size":290816,"contentType":"application/pdf","previewable":true,"links":2,"portal":true,"updatedAt":"2026-11-14T15:42:00.000Z","thumbUrl":"https://s3/t"}
         ]}
    """.trimIndent()

    @Test fun listingIsParsed() {
        val l = Parse.listing(JSONObject(sample))
        assertEquals("f1", l.folderId)
        assertEquals(listOf("Kunden", "Müller GmbH"), l.path.map { it.name })
        assertEquals(107374182400L, l.usage.limit)
        assertNull(l.usage.downloadLimit)
        assertEquals(CloudItem.Kind.Folder, l.items[0].kind)
        assertEquals(42, l.items[0].count)
        assertNull(l.items[0].thumbUrl)
        assertEquals(CloudItem.Kind.Pdf, l.items[1].kind)
        assertTrue(l.items[1].portal)
        assertEquals("https://s3/t", l.items[1].thumbUrl)
    }

    @Test fun rootListingDefaults() {
        val l = Parse.listing(JSONObject("""{"items":[],"pfad":[]}"""))
        assertEquals(ROOT, l.folderId)
        assertEquals(0L, l.usage.used)
    }

    @Test fun errorMessages() {
        assertEquals("Dein PlexCloud-Speicher reicht nicht", Parse.errorMessage("""{"statusCode":413,"message":"Dein PlexCloud-Speicher reicht nicht"}""", 413))
        assertEquals("Bitte neu anmelden.", Parse.errorMessage("", 401))
        assertEquals("Plexora ist gerade gestört (Fehler 502).", Parse.errorMessage("<html>", 502))
    }

    @Test fun links() {
        val l = Parse.links(JSONObject("""{"links":[{"typ":"contact","id":"c1","label":"Anna Müller","portal":true,"email":true},{"typ":"project","id":"p1","label":"Dach","portal":false,"email":false}]}"""))
        assertEquals("KON", l[0].badge)
        assertTrue(l[0].portal)
        assertEquals("Projekt", l[1].typLabel)
    }
}

class FormatTest {
    @Test fun bytes() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("284 KB", Format.bytes(290_816))
        assertEquals("4,6 MB", Format.bytes(4_823_449))
        assertEquals("12,40 GB", Format.bytes(13_314_398_618))
        assertEquals("12,4", Format.gb(13_314_398_618))
    }

    @Test fun etaAndAgo() {
        assertEquals("0:04", Format.eta(4))
        assertEquals("1:02:03", Format.eta(3723))
        assertEquals("vor 4 s", Format.ago(1_000, now = 5_000))
        assertEquals("vor 3 min", Format.ago(1_000, now = 181_000))
        assertEquals("—", Format.ago(0))
    }

    @Test fun dates() {
        val z = ZoneId.of("Europe/Berlin")
        assertEquals("14.11.", Format.shortDate("2026-11-14T15:42:00.000Z", z))
        assertEquals("14.11.2026 16:42", Format.dateTime("2026-11-14T15:42:00.000Z", z))
        assertEquals("", Format.shortDate("kein Datum", z))
    }

    @Test fun hex() = assertEquals("#FF7A2F", Format.hex(0xFFFF7A2FL))
}

class MeterTest {
    @Test fun reportsThrottledWithSmoothedSpeed() {
        var t = 0L
        val m = Meter(total = 3_000, everyMs = 400) { t }
        assertNull(m.add(100))               // zu früh
        t = 500
        val a = m.add(900)!!                 // 1000 B in 0,5 s = 2000 B/s
        assertEquals(1000, a.done)
        assertEquals(2000, a.bytesPerSec)
        t = 1000
        val b = m.add(500)!!                 // 500 B in 0,5 s = 1000 B/s → 0,7·2000 + 0,3·1000
        assertEquals(1700, b.bytesPerSec)
        assertEquals(3000, m.add(1500)!!.done) // Ende wird immer gemeldet
    }
}

class UpdateTest {
    @Test fun apkUrlUsesPlexCloudName() =
        assertEquals("https://github.com/peter1965p/PlexCloud/releases/download/v0.2.0/PlexCloud-0.2.0.apk", UpdateSupport.apkUrl("peter1965p/PlexCloud", Version(0, 2, 0)))

    @Test fun versionFromLocation() =
        assertEquals(Version(1, 3, 0), UpdateSupport.versionFromReleaseLocation("https://github.com/x/y/releases/tag/v1.3.0"))
}

class BackupTest {
    private val berlin = java.time.ZoneId.of("Europe/Berlin")

    @Test fun deviceNameWithoutDoubleManufacturer() {
        assertEquals("Google Pixel 8 Pro", com.paeffgen.plexcloud.backup.BackupPaths.deviceName("Google", "Pixel 8 Pro"))
        assertEquals("OnePlus 12", com.paeffgen.plexcloud.backup.BackupPaths.deviceName("OnePlus", "OnePlus 12"))
        assertEquals("Samsung SM-S918B", com.paeffgen.plexcloud.backup.BackupPaths.deviceName("samsung", "SM-S918B"))
        assertEquals("Android", com.paeffgen.plexcloud.backup.BackupPaths.deviceName("", "  "))
    }

    @Test fun mediaGoesIntoYearFolder() {
        val ms = java.time.Instant.parse("2026-12-31T23:30:00Z").toEpochMilli() // in Berlin schon 2027
        assertEquals(listOf("Bilder", "2027"), com.paeffgen.plexcloud.backup.BackupPaths.mediaPath(ms, zone = berlin))
        assertEquals(listOf("Videos", "2027"), com.paeffgen.plexcloud.backup.BackupPaths.mediaPath(ms, video = true, zone = berlin))
    }

    @Test fun monthFolderToYear() {
        val bp = com.paeffgen.plexcloud.backup.BackupPaths
        assertEquals("2026", bp.monthToYear("2026-11")); assertEquals("2025", bp.monthToYear(" 2025-01 "))
        assertEquals(null, bp.monthToYear("2026")); assertEquals(null, bp.monthToYear("Urlaub 2026-11")); assertEquals(null, bp.monthToYear("2026-1"))
    }

    @Test fun oldRootAndBaseName() {
        val bp = com.paeffgen.plexcloud.backup.BackupPaths
        assertTrue(bp.isOldRoot("Handy-Sicherung")); assertTrue(bp.isOldRoot("Handy-Sicherung (2)"))
        assertTrue(!bp.isOldRoot("Handy-Sicherung alt")); assertTrue(!bp.isOldRoot("Bilder"))
        assertEquals("img_1.jpg", bp.baseName("IMG_1 (2).jpg"))
        assertEquals("scan (2) final.pdf", bp.baseName("Scan (2) final.pdf"))
    }

    @Test fun folderPathIsCleaned() {
        assertEquals(listOf("Dokumente", "Download", "Rechnungen 2026"),
            com.paeffgen.plexcloud.backup.BackupPaths.folderPath("Download", listOf("Rechnungen/2026")))
        assertEquals("a b", com.paeffgen.plexcloud.backup.BackupPaths.clean("a:*?\"<>|b"))
    }

    @Test fun backupFolderRoundTrip() {
        val f = com.paeffgen.plexcloud.data.BackupFolder("content://com.android.externalstorage.documents/tree/primary%3ADownload", 1_700_000_000_000)
        assertEquals(f, com.paeffgen.plexcloud.data.BackupFolder.decode(f.encode()))
        assertNull(com.paeffgen.plexcloud.data.BackupFolder.decode("kaputt"))
        assertNull(com.paeffgen.plexcloud.data.BackupFolder.decode("12|"))
    }
}

class DeviceNameTest {
    @Test fun samsungUsesNameFromSettings() {
        assertEquals("Galaxy A16 5G", com.paeffgen.plexcloud.backup.BackupPaths.deviceName("samsung", "SM-A166B", "Galaxy A16 5G"))
        assertEquals("Samsung SM-A166B", com.paeffgen.plexcloud.backup.BackupPaths.deviceName("samsung", "SM-A166B", "  "))
        assertEquals("Samsung SM-A166B", com.paeffgen.plexcloud.backup.BackupPaths.deviceName("samsung", "SM-A166B", null))
        assertEquals("Peters Handy", com.paeffgen.plexcloud.backup.BackupPaths.deviceName("samsung", "SM-A166B", "Peters/Handy"))
    }
}

class ProfileTest {
    private val token = JSONObject("""{"email":"peter@example.de","given_name":"Peter","family_name":"Päffgen","picture":"https://lh3.googleusercontent.com/a/abc=s96-c"}""")

    @Test fun googlePictureAndNameFromToken() {
        val p = com.paeffgen.plexcloud.data.Profile.from(token, null)
        assertEquals("Peter Päffgen", p.name)
        assertEquals("PP", p.initials)
        assertEquals("https://lh3.googleusercontent.com/a/abc=s256-c", p.pictureUrl)
    }

    @Test fun plexoraProfileWins() {
        val p = com.paeffgen.plexcloud.data.Profile.from(token, JSONObject("""{"profile":{"name":"Krebsi Chef","avatarUrl":"https://cdn.example/a.png"}}"""))
        assertEquals("Krebsi Chef", p.name)
        assertEquals("https://cdn.example/a.png", p.pictureUrl)
    }

    @Test fun withoutPictureOnlyInitials() {
        val p = com.paeffgen.plexcloud.data.Profile.from(JSONObject("""{"email":"anna.berg@x.de","picture":"http://unsicher"}"""), null)
        assertNull(p.pictureUrl)
        assertEquals("AB", p.initials)
    }
}
