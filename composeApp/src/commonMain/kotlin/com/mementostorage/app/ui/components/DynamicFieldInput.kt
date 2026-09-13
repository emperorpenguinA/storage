package com.mementostorage.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mementostorage.app.di.AppContainer
import com.mementostorage.app.domain.model.EntryAttachment
import com.mementostorage.app.domain.model.FieldType
import com.mementostorage.app.domain.model.LibraryField
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Renders the right input widget for one [LibraryField], reading/writing the raw string value
 * that ends up in [com.mementostorage.app.domain.model.Entry.values]. PHOTO is the odd one
 * out: its "value" is an [EntryAttachment.id], and picking a new photo creates the attachment
 * row as a side effect (needs a real, already-saved [entryId] — see EntryEditorScreen, which
 * saves a draft entry immediately so this always has one to attach to).
 */
@Composable
fun DynamicFieldInput(
    field: LibraryField,
    value: String,
    onValueChange: (String) -> Unit,
    container: AppContainer,
    entryId: String,
    attachments: List<EntryAttachment>,
    onAttachmentsChanged: () -> Unit,
) {
    when (field.type) {
        FieldType.TEXT, FieldType.LINK -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(field.name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        FieldType.LONG_TEXT -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(field.name) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        FieldType.NUMBER -> OutlinedTextField(
            value = value,
            onValueChange = { input -> if (input.isEmpty() || input.toDoubleOrNull() != null) onValueChange(input) },
            label = { Text(field.name) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        FieldType.DATE -> DateFieldInput(field = field, value = value, onValueChange = onValueChange)

        FieldType.BOOLEAN -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = value == "true", onCheckedChange = { onValueChange(it.toString()) })
            Text(field.name)
        }

        FieldType.CHOICE -> ChoiceFieldInput(field = field, value = value, onValueChange = onValueChange)

        FieldType.PHOTO -> PhotoFieldInput(
            field = field,
            value = value,
            onValueChange = onValueChange,
            container = container,
            entryId = entryId,
            attachments = attachments,
            onAttachmentsChanged = onAttachmentsChanged,
        )
    }
}

/**
 * The stored value is an ISO date string ("yyyy-MM-dd", [LocalDate]'s own [toString] format).
 * [DatePicker] works in UTC-midnight epoch millis, so conversion happens at UTC to avoid the
 * selected day shifting by one depending on the device's time zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldInput(field: LibraryField, value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.ifBlank { "日付を選択" },
            onValueChange = {},
            readOnly = true,
            label = { Text(field.name) },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true },
        )
    }

    if (showPicker) {
        val initialMillis = value.toLocalDateOrNull()?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis -> onValueChange(millis.toIsoDateString()) }
                    showPicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("キャンセル")
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun Long.toIsoDateString(): String =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date.toString()

@Composable
private fun ChoiceFieldInput(field: LibraryField, value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.ifBlank { "選択してください" },
            onValueChange = {},
            readOnly = true,
            label = { Text(field.name) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // See the identical pattern in LibraryEditorScreen's field-type picker: a
        // transparent overlay reliably catches the click, unlike a clickable modifier on
        // the text field itself.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PhotoFieldInput(
    field: LibraryField,
    value: String,
    onValueChange: (String) -> Unit,
    container: AppContainer,
    entryId: String,
    attachments: List<EntryAttachment>,
    onAttachmentsChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentAttachment = attachments.firstOrNull { it.id == value }
    val launchPicker = rememberImagePicker { fileName, mimeType, bytes ->
        scope.launch {
            val localPath = container.attachmentFileStore.writeBytes(fileName, bytes)
            val attachment = container.attachmentRepository.addAttachment(
                entryId = entryId,
                fieldId = field.id,
                fileName = fileName,
                localPath = localPath,
                mimeType = mimeType,
            )
            onValueChange(attachment.id)
            onAttachmentsChanged()
        }
    }

    Column {
        Text(field.name, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        PhotoThumbnail(
            fileStore = container.attachmentFileStore,
            attachment = currentAttachment,
            size = 160.dp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = launchPicker) {
            Text(if (currentAttachment != null) currentAttachment.fileName else "${field.name}を選択")
        }
    }
}
