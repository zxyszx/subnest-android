package io.github.zxyszx.subnest

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zxyszx.subnest.data.ListFilter

@Composable
fun ListControls(
    query: String,
    onQuery: (String) -> Unit,
    platforms: List<String>,
    platform: String?,
    onPlatform: (String?) -> Unit,
    filters: List<ListFilter>,
    filter: ListFilter,
    onFilter: (ListFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            query,
            onQuery,
            Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            label = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                    }
                }
            },
        )
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(platform ?: stringResource(R.string.all_platforms))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 360.dp)) {
                (listOf<String?>(null) + platforms).forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value ?: stringResource(R.string.all_platforms)) },
                        onClick = {
                            onPlatform(value)
                            expanded = false
                        },
                        trailingIcon = { if (platform == value) Icon(Icons.Default.Check, null) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { value ->
                FilterChip(
                    selected = filter == value,
                    onClick = { onFilter(value) },
                    label = { Text(stringResource(value.label())) },
                    leadingIcon = if (filter == value) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

private fun ListFilter.label(): Int = when (this) {
    ListFilter.ALL -> R.string.all
    ListFilter.TODAY -> R.string.today
    ListFilter.UPCOMING -> R.string.upcoming
    ListFilter.OVERDUE -> R.string.overdue
    ListFilter.VACANT -> R.string.vacant
    ListFilter.FULL -> R.string.full
}
