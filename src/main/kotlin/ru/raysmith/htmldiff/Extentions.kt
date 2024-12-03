package ru.raysmith.htmldiff

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import java.io.File
import java.net.URL

fun Document.downloadResourcesAndReplacePaths(
    outputDir: File,
    saveIndex: Boolean = true,
    removeComments: Boolean = false
) {
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    val outer by lazy { outerHtml() }

    val htmlFile = File(outputDir, "index.html")

    val elementsWithSrc = select("[src]")
    val elementsWithHref = select("link[href]")
    val resourceElements = elementsWithSrc + elementsWithHref

    for (element in resourceElements) {
        val attributeKey = if (element.hasAttr("src")) "src" else "href"
        val resourceUrl = element.absUrl(attributeKey)
        if (resourceUrl.isEmpty()) continue

        val connection = Jsoup.connect(resourceUrl).ignoreContentType(true)
        val response = connection.execute()
        val resourceData = response.bodyAsBytes()

        val resourcePath = URL(resourceUrl).path
        val resourceFile = File(outputDir, resourcePath).apply {
            parentFile?.mkdirs()
        }

        resourceFile.writeBytes(resourceData)

        val relativePath = resourceFile.relativeTo(outputDir).path
        element.attr(attributeKey, relativePath)
    }

    if (removeComments) {
        removeComments()
    }

    if (saveIndex) {
        htmlFile.writeText(outer)
    }
}

fun Node.removeComments() {
    childNodes().stream().filter { n -> "#comment" == n.nodeName() }.forEach(Node::remove)
    childNodes().forEach { it.removeComments() }
}