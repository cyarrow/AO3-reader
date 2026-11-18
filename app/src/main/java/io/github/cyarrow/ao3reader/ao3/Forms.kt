package io.github.cyarrow.ao3reader.ao3

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** A parsed HTML form: where it posts and its default field values. */
data class HtmlForm(val action: String, val method: String, val fields: List<Pair<String, String>>) {
    fun with(overrides: Map<String, String>): List<Pair<String, String>> {
        val names = overrides.keys
        return fields.filter { it.first !in names } + overrides.toList()
    }
}

object Forms {
    fun find(html: String, selector: String): HtmlForm? {
        val doc = Jsoup.parse(html, Ao3Client.BASE)
        val form = doc.selectFirst(selector) ?: return null
        return parse(form)
    }

    fun parse(form: Element): HtmlForm {
        val fields = mutableListOf<Pair<String, String>>()
        for (input in form.select("input[name], textarea[name], select[name]")) {
            val name = input.attr("name")
            when (input.normalName()) {
                "input" -> when (input.attr("type").lowercase()) {
                    "submit", "button", "image", "file" -> {}
                    "checkbox", "radio" -> if (input.hasAttr("checked")) fields += name to input.attr("value").ifEmpty { "on" }
                    else -> fields += name to input.attr("value")
                }
                "textarea" -> fields += name to input.text()
                "select" -> {
                    val opt = input.selectFirst("option[selected]") ?: input.selectFirst("option")
                    if (opt != null) fields += name to opt.attr("value")
                }
            }
        }
        // Rails forms use a hidden _method field for PATCH/DELETE.
        return HtmlForm(form.attr("abs:action"), form.attr("method").ifEmpty { "get" }.lowercase(), fields)
    }

    /** Logged-in username from the page header, if any. */
    fun loggedInUser(html: String): String? {
        val doc = Jsoup.parse(html, Ao3Client.BASE)
        val greeting = doc.selectFirst("#greeting") ?: return null
        val link = greeting.select("a[href^=/users/]").map { it.attr("href") }
            .firstOrNull { Regex("^/users/[^/]+$").matches(it) }
        if (link != null) return link.removePrefix("/users/")
        if (greeting.selectFirst("a[href=/users/login], #login") != null) return null
        return null
    }

    /** Text of AO3's flash error/notice messages. */
    fun flash(html: String): String? = Jsoup.parse(html).selectFirst("div.flash.error, div.flash.alert, #error")?.text()?.trim()
}
