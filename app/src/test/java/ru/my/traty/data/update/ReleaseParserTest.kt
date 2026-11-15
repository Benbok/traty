package ru.my.traty.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    private fun release(
        tag: String = "v0.1.1",
        draft: Boolean = false,
        prerelease: Boolean = false,
        htmlUrl: String = "https://github.com/Benbok/traty/releases/tag/v0.1.1",
        assets: String = """[{"name":"traty-0.1.1.apk","browser_download_url":"https://github.com/Benbok/traty/releases/download/v0.1.1/traty-0.1.1.apk"}]""",
    ) = """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"html_url":"$htmlUrl","body":"Что нового","assets":$assets}"""

    @Test
    fun takesVersionAndApkLink() {
        val info = ReleaseParser.parse(release())!!
        assertEquals("0.1.1", info.version)
        assertEquals("https://github.com/Benbok/traty/releases/download/v0.1.1/traty-0.1.1.apk", info.downloadUrl)
    }

    @Test
    fun fallsBackToReleasePageWhenNoApk() {
        val info = ReleaseParser.parse(release(assets = "[]"))!!
        assertEquals("https://github.com/Benbok/traty/releases/tag/v0.1.1", info.downloadUrl)
    }

    @Test
    fun ignoresNonApkAssets() {
        val info = ReleaseParser.parse(
            release(assets = """[{"name":"notes.txt","browser_download_url":"https://github.com/Benbok/traty/releases/download/v0.1.1/notes.txt"}]"""),
        )!!
        assertEquals("https://github.com/Benbok/traty/releases/tag/v0.1.1", info.downloadUrl)
    }

    @Test
    fun linksToOtherPlacesAreNotTrusted() {
        val evil = release(
            htmlUrl = "https://evil.example.com/release",
            assets = """[{"name":"traty.apk","browser_download_url":"https://evil.example.com/traty.apk"}]""",
        )
        assertEquals(RELEASES_PAGE, ReleaseParser.parse(evil)!!.downloadUrl)
        assertFalse(ReleaseParser.isTrusted("http://github.com/Benbok/traty/releases/latest"))
        assertFalse(ReleaseParser.isTrusted("https://github.com/Other/traty/releases/latest"))
        assertTrue(ReleaseParser.isTrusted(RELEASES_PAGE))
    }

    @Test
    fun draftsAndPrereleasesAreIgnored() {
        assertNull(ReleaseParser.parse(release(draft = true)))
        assertNull(ReleaseParser.parse(release(prerelease = true)))
    }

    @Test
    fun oddVersionTagsAreIgnored() {
        assertNull(ReleaseParser.parse(release(tag = "v0.0.1-test")))
        assertNull(ReleaseParser.parse(release(tag = "nightly")))
    }

    @Test
    fun garbageIsIgnored() {
        assertNull(ReleaseParser.parse(""))
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("[]"))
        assertNull(ReleaseParser.parse("""{"message":"Not Found"}"""))
    }
}
