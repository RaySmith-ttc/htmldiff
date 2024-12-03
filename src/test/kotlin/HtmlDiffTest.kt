import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import ru.raysmith.htmldiff.HtmlDiff

class HtmlDiffTest {

    fun file(path: String): String {
        return File(ClassLoader.getSystemClassLoader().getResource(path)!!.toURI()).readText()
    }

    @Test
    fun test() {
        val oldText = file("html1.html")
        val newText = file("html2.html")
        val diff = HtmlDiff(oldText, newText)
        diff.build()
        val actual = HtmlDiff.execute(oldText, newText)

        val expected = file("expected.html")

        actual shouldBe expected
    }
}