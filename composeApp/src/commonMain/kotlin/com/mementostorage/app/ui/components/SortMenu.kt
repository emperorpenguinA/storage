package com.mementostorage.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

/**
 * One sortable criterion a list screen offers. [id] must be unique within the list of
 * criteria passed to [SortMenuButton] -- screens that sort by a user-defined schema field
 * (see EntryListScreen) key it off that field's id. [defaultDirection] is what a fresh
 * selection of this criterion starts at (e.g. newest-first for dates), separate from whatever
 * direction the sort is currently showing.
 */
data class SortCriterion(
    val id: String,
    val label: String,
    val defaultDirection: SortDirection = SortDirection.ASCENDING,
)

data class SortSpec(val criterionId: String, val direction: SortDirection)

/**
 * Shared between the library list and record list screens. The dropdown picks *what* to sort
 * by (defaulting to that criterion's own natural direction); the arrow button next to it
 * freely flips ascending/descending for whatever is currently selected.
 */
@Composable
fun SortMenuButton(criteria: List<SortCriterion>, spec: SortSpec, onSpecChange: (SortSpec) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "並び替え")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                criteria.forEach { criterion ->
                    DropdownMenuItem(
                        text = { Text(criterion.label) },
                        leadingIcon = if (criterion.id == spec.criterionId) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            if (criterion.id != spec.criterionId) {
                                onSpecChange(SortSpec(criterion.id, criterion.defaultDirection))
                            }
                            expanded = false
                        },
                    )
                }
            }
        }
        IconButton(onClick = { onSpecChange(spec.copy(direction = spec.direction.flipped())) }) {
            Icon(
                if (spec.direction == SortDirection.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (spec.direction == SortDirection.ASCENDING) "昇順" else "降順",
            )
        }
    }
}

private fun SortDirection.flipped(): SortDirection =
    if (this == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING

/** Orders [this] by [comparator] (always written ascending) and applies [spec]'s direction. */
fun <T> List<T>.sortedBySpec(comparator: Comparator<T>, spec: SortSpec): List<T> {
    val ascending = sortedWith(comparator)
    return if (spec.direction == SortDirection.DESCENDING) ascending.asReversed() else ascending
}
