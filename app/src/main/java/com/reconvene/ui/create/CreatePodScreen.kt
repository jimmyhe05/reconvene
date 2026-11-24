package com.reconvene.ui.create

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reconvene.R
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import com.reconvene.ui.discover.PodCard
import com.reconvene.ui.discover.dayLabel
import com.reconvene.ui.discover.hintRes
import com.reconvene.ui.discover.labelRes
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val shortTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun CreatePodScreen(
    onBack: () -> Unit,
    onCreated: (podId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePodViewModel = viewModel(factory = CreatePodViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    LaunchedEffect(uiState.createdPodId) {
        uiState.createdPodId?.let(onCreated)
    }
    CreatePodContent(
        uiState = uiState,
        onFormChange = viewModel::updateForm,
        onCreate = viewModel::create,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePodContent(
    uiState: CreatePodUiState,
    onFormChange: (CreatePodForm) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = uiState.form
    val errors = uiState.errors
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.create_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                text = stringResource(R.string.create_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormSection(stringResource(R.string.create_section_what)) {
                FormTextField(
                    value = form.title,
                    onValueChange = { onFormChange(form.copy(title = it)) },
                    label = stringResource(R.string.create_topic),
                    placeholder = stringResource(R.string.create_topic_placeholder),
                    maxLength = TOPIC_MAX,
                    error = errors.title?.message(),
                )
                FormTextField(
                    value = form.course,
                    onValueChange = { onFormChange(form.copy(course = it)) },
                    label = stringResource(R.string.create_course),
                    placeholder = "CS 501",
                    hint = stringResource(R.string.create_course_hint),
                    maxLength = COURSE_MAX,
                    capitalization = KeyboardCapitalization.Characters,
                )
            }
            FormSection(stringResource(R.string.create_section_where)) {
                BuildingField(form = form, hasError = errors.building != null, onFormChange = onFormChange)
                MeetingPointField(form = form, error = errors.meetingPoint?.message(), onFormChange = onFormChange)
            }
            FormSection(stringResource(R.string.create_when)) {
                WhenFields(
                    form = form,
                    today = uiState.today,
                    startSlots = uiState.startSlots,
                    error = errors.time?.message(),
                    onFormChange = onFormChange,
                )
            }
            FormSection(stringResource(R.string.create_section_who)) {
                FieldGroup(
                    label = stringResource(R.string.create_group_size),
                    hint = stringResource(R.string.create_group_size_hint, CAPACITY_MIN, CAPACITY_MAX),
                ) {
                    CapacityPicker(capacity = form.capacity, onChange = { onFormChange(form.copy(capacity = it)) })
                }
                FieldGroup(label = stringResource(R.string.create_vibe), hint = stringResource(form.mode.hintRes)) {
                    ChoiceRow(
                        options = PodMode.entries,
                        selected = form.mode,
                        label = { stringResource(it.labelRes) },
                        onSelect = { mode -> mode?.let { onFormChange(form.copy(mode = it)) } },
                    )
                }
            }
            FormSection(stringResource(R.string.create_preview)) {
                PodPreview(pod = uiState.preview, now = uiState.now)
            }
        }
        CreateBar(
            problem = when {
                uiState.signInRequired -> stringResource(R.string.create_sign_in_required)
                uiState.saveFailed -> stringResource(R.string.create_save_failed)
                !errors.isEmpty -> stringResource(R.string.create_fix_errors)
                else -> null
            },
            isSaving = uiState.isSaving,
            onCreate = onCreate,
        )
    }
}

// One titled part of the form. Only these titles are headings, so TalkBack can jump between parts.
@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

// The same card Discover shows, so the preview can't drift from what others will see.
// Fields that are still blank show placeholder words instead of gaps.
@Composable
private fun PodPreview(pod: Pod, now: Instant) {
    Text(
        text = stringResource(R.string.create_preview_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PodCard(
        pod = pod.copy(
            title = pod.title.ifEmpty { stringResource(R.string.create_preview_topic) },
            building = pod.building.ifEmpty { stringResource(R.string.create_building) },
            meetingPoint = pod.meetingPoint.ifEmpty { stringResource(R.string.create_meeting_point) },
            hostName = stringResource(R.string.create_preview_host),
        ),
        now = now,
    )
}

// Stays at the bottom of the screen, under the scrolling form, so Create is always in reach.
@Composable
private fun CreateBar(problem: String?, isSaving: Boolean, onCreate: () -> Unit) {
    Column {
        HorizontalDivider()
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (problem != null) {
                Text(
                    text = problem,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = onCreate,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_create_pod))
            }
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    error: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.toSingleLine().take(maxLength)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = error ?: hint.orEmpty(), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.create_counter, value.length, maxLength))
            }
        },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

// A menu of suggested buildings, plus "Other building", which opens a text field for any other name.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuildingField(
    form: CreatePodForm,
    hasError: Boolean,
    onFormChange: (CreatePodForm) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val other = stringResource(R.string.create_building_other)
    // With "Other building" chosen, the error belongs to the name field below, not to the menu.
    val menuError = hasError && !form.customBuilding

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = if (form.customBuilding) other else form.building.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.create_building)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                supportingText = if (menuError) {
                    { Text(stringResource(R.string.error_building_missing)) }
                } else {
                    null
                },
                isError = menuError,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Text(
                    text = stringResource(R.string.create_building_suggested),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                suggestedBuildings.forEach { building ->
                    DropdownMenuItem(
                        text = { Text(building) },
                        onClick = {
                            onFormChange(form.withBuilding(building))
                            expanded = false
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(other) },
                    onClick = {
                        // Choosing it again keeps a name that's already typed.
                        if (!form.customBuilding) onFormChange(form.withBuilding("", custom = true))
                        expanded = false
                    },
                )
            }
        }
        if (form.customBuilding) {
            FormTextField(
                value = form.building.orEmpty(),
                onValueChange = { onFormChange(form.copy(building = it)) },
                label = stringResource(R.string.create_building_name),
                placeholder = stringResource(R.string.create_building_name_placeholder),
                maxLength = BUILDING_MAX,
                error = if (hasError) stringResource(R.string.error_building_name_missing) else null,
                capitalization = KeyboardCapitalization.Words,
            )
        }
    }
}

// The meeting point text field, with the chosen building's common spots as one-tap chips under it.
@Composable
internal fun MeetingPointField(
    form: CreatePodForm,
    error: String?,
    onFormChange: (CreatePodForm) -> Unit,
) {
    val suggestions = form.meetingPointSuggestions
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FormTextField(
            value = form.meetingPoint,
            onValueChange = { onFormChange(form.copy(meetingPoint = it)) },
            label = stringResource(R.string.create_meeting_point),
            placeholder = stringResource(R.string.create_meeting_point_placeholder),
            hint = stringResource(R.string.create_meeting_point_hint),
            maxLength = MEETING_POINT_MAX,
            error = error,
        )
        if (suggestions.isNotEmpty()) {
            FieldLabel(stringResource(R.string.create_meeting_point_suggestions))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { spot ->
                    SuggestionChip(
                        onClick = { onFormChange(form.copy(meetingPoint = spot)) },
                        label = { Text(spot) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun FieldGroup(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        content()
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (hint != null) {
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// A wrapping row of chips where one can be selected. trailing adds extra chips after the options.
@Composable
internal fun <T> ChoiceRow(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
    enabled: (T) -> Boolean = { true },
    trailing: @Composable () -> Unit = {},
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else option) },
                enabled = enabled(option),
                label = { Text(label(option)) },
                leadingIcon = if (isSelected) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
            )
        }
        trailing()
    }
}

@Composable
internal fun WhenFields(
    form: CreatePodForm,
    today: LocalDate,
    startSlots: List<LocalTime>,
    error: String?,
    onFormChange: (CreatePodForm) -> Unit,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val quickDays = quickDays(today)
    val summary = if (form.length > Duration.ZERO) {
        stringResource(
            R.string.create_when_range,
            dayLabel(form.day, today),
            shortTime.format(form.startTime),
            shortTime.format(form.endTime),
        )
    } else {
        null
    }
    // A day or start time that isn't one of the quick chips shows on the row's last chip instead.
    val otherDay = if (form.day in quickDays) null else dayLabel(form.day, today)
    val otherTime = if (form.startTime in startSlots) null else shortTime.format(form.startTime)

    FieldGroup(label = stringResource(R.string.create_day)) {
        ChoiceRow(
            options = quickDays,
            selected = form.day,
            label = { dayLabel(it, today) },
            onSelect = { day -> day?.let { onFormChange(form.copy(day = it)) } },
        ) {
            PickerChip(
                picked = otherDay,
                label = stringResource(R.string.create_pick_date),
                icon = R.drawable.ic_calendar,
                onClick = { pickingDate = true },
            )
        }
    }
    FieldGroup(label = stringResource(R.string.create_starts)) {
        ChoiceRow(
            options = startSlots,
            selected = form.startTime,
            label = { shortTime.format(it) },
            onSelect = { time -> time?.let { onFormChange(form.withStartTime(it)) } },
        ) {
            PickerChip(
                picked = otherTime,
                label = stringResource(R.string.create_other_time),
                icon = R.drawable.ic_clock,
                onClick = { pickingTime = true },
            )
        }
    }
    // The summary or the time error goes under the last row, since it's about all three.
    FieldGroup(label = stringResource(R.string.create_length), hint = summary, error = error) {
        ChoiceRow(
            options = LENGTH_CHOICES,
            selected = form.length,
            label = { durationText(it.toMinutes()) },
            enabled = { form.canLast(it) },
            onSelect = { length -> length?.let { onFormChange(form.withLength(it)) } },
        )
    }

    if (pickingDate) {
        DateDialog(
            initial = form.day,
            today = today,
            onConfirm = {
                onFormChange(form.copy(day = it))
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        TimeDialog(
            title = stringResource(R.string.create_start_time),
            initial = form.startTime,
            onConfirm = {
                onFormChange(form.withStartTime(it))
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

// The last chip in a row, which opens a picker. Once something is picked there, the chip shows it.
@Composable
private fun PickerChip(
    picked: String?,
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = picked != null,
        onClick = onClick,
        label = { Text(picked ?: label) },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                // Read out with the picked value, so it's still clear what the chip opens.
                contentDescription = if (picked != null) label else null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

// A calendar where only the days a pod can start on are enabled.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initial: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val years = today.year..lastStartDay(today).year
    val state = rememberDatePickerState(
        // A saved form can hold a day that has passed, which the calendar can't open on.
        initialSelectedDateMillis = (if (canStartOn(initial, today)) initial else today).toPickerMillis(),
        yearRange = years,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = canStartOn(pickerMillisToDate(utcTimeMillis), today)

            override fun isSelectableYear(year: Int) = year in years
        },
    )
    // Null while the typed-date mode holds a date that isn't allowed.
    val picked = state.selectedDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picked?.let { onConfirm(pickerMillisToDate(it)) } }, enabled = picked != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    title: String,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        TimePicker(state = state)
    }
}

// Common sizes as one tap, and a stepper for any exact size from CAPACITY_MIN to CAPACITY_MAX.
// The stepper alone would take 48 taps to reach 50, and a slider is hard to stop on an exact number.
@Composable
private fun CapacityPicker(capacity: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow(
            options = CAPACITY_CHOICES,
            selected = capacity,
            label = { it.toString() },
            // Tapping the chosen size again keeps it; there is always a size.
            onSelect = { size -> size?.let(onChange) },
        )
        CapacityStepper(capacity = capacity, onChange = onChange)
    }
}

@Composable
private fun CapacityStepper(capacity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedIconButton(onClick = { onChange(capacity - 1) }, enabled = capacity > CAPACITY_MIN) {
            Icon(painterResource(R.drawable.ic_minus), contentDescription = stringResource(R.string.create_fewer_people))
        }
        Text(
            text = pluralStringResource(R.plurals.create_people, capacity, capacity),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        OutlinedIconButton(onClick = { onChange(capacity + 1) }, enabled = capacity < CAPACITY_MAX) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.create_more_people))
        }
    }
}

@Composable
private fun durationText(minutes: Long): String {
    val hours = (minutes / 60).toInt()
    val rest = (minutes % 60).toInt()
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes, rest)
        rest == 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, rest)
    }
}

@Composable
internal fun FormError.message(): String = when (this) {
    FormError.TOPIC_TOO_SHORT -> stringResource(R.string.error_topic_too_short, TOPIC_MIN)
    FormError.BUILDING_MISSING -> stringResource(R.string.error_building_missing)
    FormError.MEETING_POINT_TOO_SHORT -> stringResource(R.string.error_meeting_point_too_short)
    FormError.END_NOT_AFTER_START -> stringResource(R.string.error_end_not_after_start)
    FormError.ALREADY_OVER -> stringResource(R.string.error_already_over)
    FormError.TOO_SHORT -> stringResource(R.string.error_too_short, MIN_DURATION.toMinutes().toInt())
    FormError.TOO_LONG -> stringResource(R.string.error_too_long, MAX_DURATION.toHours().toInt())
}

private val previewNow = ZonedDateTime.of(2026, 11, 10, 14, 0, 0, 0, ZoneId.systemDefault())
private val previewForm = CreatePodForm(
    title = "Algorithms exam review",
    building = "Mugar Library",
    day = previewNow.toLocalDate(),
    startTime = LocalTime.of(15, 0),
    endTime = LocalTime.of(16, 30),
)

@Preview(showBackground = true, heightDp = 1900)
@Composable
private fun CreatePodContentPreview() {
    ReconveneTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CreatePodContent(
                uiState = CreatePodUiState(
                    form = previewForm,
                    today = previewNow.toLocalDate(),
                    now = previewNow.toInstant(),
                    preview = previewForm.toPreviewPod(previewNow.zone),
                    startSlots = listOf(LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(16, 0), LocalTime.of(16, 30)),
                    errors = FormErrors(meetingPoint = FormError.MEETING_POINT_TOO_SHORT),
                ),
                onFormChange = {},
                onCreate = {},
                onBack = {},
            )
        }
    }
}
