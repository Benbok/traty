package ru.my.traty.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.my.traty.data.RememberedProduct
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

@Composable
internal fun RememberedProductsDialog(
    vm: AssistantViewModel,
    draft: RememberedProduct?,
    onClose: () -> Unit,
) {
    val products by vm.rememberedProducts.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(draft) }
    var phrase by remember { mutableStateOf(draft?.phrase.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val product = editing

    fun change(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Не удалось изменить сохранённое название."
            } finally {
                busy = false
            }
        }
    }

    fun closeEditor() {
        if (draft != null) onClose() else {
            editing = null
            error = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) if (product == null) onClose() else closeEditor() },
        title = { Text(if (product == null) "Сохранённые названия" else "Название для поиска") },
        text = {
            if (product == null) {
                if (products.isEmpty()) Text("Названия можно сохранить после выбора товара в ответе помощника.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                    items(products, key = { it.phrase }) { saved ->
                        TratyTextButton(onClick = {
                            editing = saved
                            phrase = saved.phrase
                        }) { Text(saved.phrase) }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                    OutlinedTextField(
                        value = phrase,
                        onValueChange = { phrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        label = { Text("Как вы называете товар") },
                    )
                    Text(product.receiptNames.joinToString("\n"), style = TratyTheme.typography.bodySmall)
                    error?.let { Text(it, color = TratyTheme.colors.expense) }
                    if (draft == null) {
                        TratyTextButton(enabled = !busy, onClick = {
                            change {
                                vm.removeProduct(product.phrase)
                                closeEditor()
                            }
                        }) { Text("Удалить название") }
                    }
                }
            }
        },
        confirmButton = {
            if (product == null) TratyTextButton(onClick = onClose) { Text("Готово") }
            else TratyTextButton(enabled = !busy && phrase.isNotBlank(), onClick = {
                change {
                    if (vm.saveProduct(product.copy(phrase = phrase), product.phrase.takeIf { draft == null })) closeEditor()
                    else error = "Такое название уже используется. Введите другое."
                }
            }) { Text("Сохранить") }
        },
        dismissButton = {
            if (product != null) TratyTextButton(enabled = !busy, onClick = ::closeEditor) { Text("Отмена") }
        },
    )
}
