package com.aidflow.pro.translate

import java.util.Locale

/**
 * The 20 languages AidFlow Pro ships with — chosen to cover the humanitarian use cases
 * (refugee aid, migrant medical translation, field disaster response). Each entry pairs
 * a BCP-47 tag (consumed by Android SpeechRecognizer + TextToSpeech) with a display name
 * shown to the user and an English label used in the Gemma 4 translation prompt.
 *
 * RTL is tracked so the UI can mirror text fields appropriately.
 */
enum class Language(
    val tag: String,
    val displayName: String,
    val englishName: String,
    val isRtl: Boolean = false,
) {
    English("en-US", "English", "English"),
    Spanish("es-ES", "Español", "Spanish"),
    French("fr-FR", "Français", "French"),
    Portuguese("pt-PT", "Português", "Portuguese"),
    Arabic("ar", "العربية", "Arabic", isRtl = true),
    Ukrainian("uk-UA", "Українська", "Ukrainian"),
    Russian("ru-RU", "Русский", "Russian"),
    Polish("pl-PL", "Polski", "Polish"),
    Turkish("tr-TR", "Türkçe", "Turkish"),
    Persian("fa-IR", "فارسی", "Persian", isRtl = true),
    Pashto("ps-AF", "پښتو", "Pashto", isRtl = true),
    Urdu("ur-PK", "اردو", "Urdu", isRtl = true),
    Hindi("hi-IN", "हिन्दी", "Hindi"),
    Bengali("bn-BD", "বাংলা", "Bengali"),
    Swahili("sw-KE", "Kiswahili", "Swahili"),
    Amharic("am-ET", "አማርኛ", "Amharic"),
    Somali("so-SO", "Soomaali", "Somali"),
    ChineseSimplified("zh-CN", "中文 (简体)", "Simplified Chinese"),
    Vietnamese("vi-VN", "Tiếng Việt", "Vietnamese"),
    Tagalog("tl-PH", "Tagalog", "Tagalog");

    fun toLocale(): Locale = Locale.forLanguageTag(tag)

    companion object {
        val Default = English
        val DefaultTarget = Arabic

        fun fromTag(tag: String?): Language? =
            tag?.let { t -> values().firstOrNull { it.tag.equals(t, ignoreCase = true) } }
    }
}
