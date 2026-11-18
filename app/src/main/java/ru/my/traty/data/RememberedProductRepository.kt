package ru.my.traty.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.my.traty.data.search.RussianStemmer
import ru.my.traty.data.search.SearchText

@Serializable
data class RememberedProduct(val phrase: String, val receiptNames: List<String>) {
    companion object {
        private val words = Regex("""[\p{L}\p{N}]+""")

        private fun phraseKey(phrase: String) =
            words.findAll(SearchText.fold(phrase)).map { it.value }.toList()

        private fun inflectedKey(phrase: String) = phraseKey(phrase).map(RussianStemmer::stem)

        fun match(phrase: String, products: List<RememberedProduct>): RememberedProduct? {
            val key = phraseKey(phrase).takeIf { it.isNotEmpty() } ?: return null
            return products.firstOrNull { phraseKey(it.phrase) == key }
                ?: products.singleOrNull { inflectedKey(it.phrase) == inflectedKey(phrase) }
        }
    }
}

class RememberedProductRepository(context: Context) {
    private val store = context.dataStore
    private val key = stringPreferencesKey("assistant_remembered_products")
    val products = store.data.map { decode(it[key]) }

    suspend fun current(): List<RememberedProduct> = products.first()

    suspend fun save(product: RememberedProduct, previousPhrase: String? = null): Boolean {
        val phrase = product.phrase.trim().replace(Regex("""\s+"""), " ")
        if (phrase.none(Char::isLetterOrDigit) || product.receiptNames.isEmpty()) return false
        var saved = false
        store.edit { prefs ->
            val all = decode(prefs[key])
            val others = all.filterNot { it.phrase == previousPhrase }
            if (previousPhrase != null && others.size == all.size) return@edit
            if (RememberedProduct.match(phrase, others) != null) return@edit
            prefs[key] = Json.encodeToString(others + product.copy(phrase = phrase))
            saved = true
        }
        return saved
    }

    suspend fun remove(phrase: String) {
        store.edit { prefs ->
            prefs[key] = Json.encodeToString(decode(prefs[key]).filterNot { it.phrase == phrase })
        }
    }

    private fun decode(raw: String?): List<RememberedProduct> = try {
        raw?.let { Json.decodeFromString<List<RememberedProduct>>(it) }.orEmpty()
    } catch (_: SerializationException) {
        emptyList()
    }
}
