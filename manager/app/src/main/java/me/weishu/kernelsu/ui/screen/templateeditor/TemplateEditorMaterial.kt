package me.weishu.kernelsu.ui.screen.templateeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressivePrimaryBar
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedTextField
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.profile.RootProfileConfig
import me.weishu.kernelsu.ui.component.statustag.StatusTag

@Composable
fun TemplateEditorScreenMaterial(
    state: TemplateEditorUiState,
    actions: TemplateEditorActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val canSave = !state.readOnly &&
        state.idErrorHint.isEmpty() &&
        isTemplateValid(state.template)

    ExpressiveScaffold(
        topBar = {
            TopBar(
                title = if (state.isCreation) {
                    stringResource(R.string.app_profile_template_create)
                } else if (state.readOnly) {
                    stringResource(R.string.app_profile_template_view)
                } else {
                    stringResource(R.string.app_profile_template_edit)
                },
                readOnly = state.readOnly,
                canSave = canSave,
                summary = state.titleSummary,
                onBack = actions.onBack,
                onDelete = actions.onDelete,
                onSave = actions.onSave,
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(top = 13.dp)
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            TemplateEditorHero(state = state)

            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = buildList {
                    add(
                        {
                            TemplateEditorListItem(
                                label = stringResource(id = R.string.app_profile_template_name),
                                value = state.template.name,
                                readOnly = state.readOnly,
                                onValueChange = actions.onNameChange,
                            )
                        }
                    )
                    if (state.isCreation) {
                        add(
                            {
                                TemplateEditorListItem(
                                    label = stringResource(id = R.string.app_profile_template_id),
                                    value = state.template.id,
                                    errorHint = state.idErrorHint,
                                    isError = state.idErrorHint.isNotEmpty(),
                                    readOnly = state.readOnly,
                                    onValueChange = actions.onIdChange,
                                )
                            }
                        )
                    }
                    add(
                        {
                            TemplateEditorListItem(
                                label = stringResource(id = R.string.module_author),
                                value = state.template.author,
                                readOnly = state.readOnly,
                                onValueChange = actions.onAuthorChange,
                            )
                        }
                    )
                    add(
                        {
                            TemplateEditorListItem(
                                label = stringResource(id = R.string.app_profile_template_description),
                                value = state.template.description,
                                multiline = true,
                                readOnly = state.readOnly,
                                onValueChange = actions.onDescriptionChange,
                            )
                        }
                    )
                }
            )

            RootProfileConfig(
                fixedName = true,
                enabled = !state.readOnly,
                profile = toNativeProfile(state.template),
                onProfileChange = actions.onProfileChange,
            )

            if (!state.readOnly) {
                ExpressivePrimaryBar(
                    label = stringResource(R.string.app_profile_template_save),
                    onClick = actions.onSave,
                    enabled = canSave,
                    icon = Icons.Filled.Save,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Spacer(
                Modifier.height(
                    16.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateEditorHero(state: TemplateEditorUiState) {
    val template = state.template
    val colorScheme = MaterialTheme.colorScheme
    val title = template.name.ifBlank {
        when {
            state.isCreation -> stringResource(R.string.app_profile_template_create)
            state.readOnly -> stringResource(R.string.app_profile_template_view)
            else -> stringResource(R.string.app_profile_template_edit)
        }
    }
    val summary = buildString {
        if (template.id.isNotEmpty()) append(template.id)
        if (template.author.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(template.author)
        }
    }.ifBlank { null }
    val containerColor = when {
        state.readOnly -> colorScheme.tertiaryContainer
        state.isCreation -> colorScheme.primaryContainer
        else -> colorScheme.secondaryContainer
    }
    ExpressiveHeroCard(
        title = title,
        summary = summary,
        icon = Icons.Outlined.Description,
        containerColor = containerColor,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 13.dp),
        tags = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (template.local) {
                    StatusTag(
                        label = stringResource(R.string.app_profile_template_local),
                        contentColor = colorScheme.onPrimary,
                        backgroundColor = colorScheme.primary,
                    )
                } else {
                    StatusTag(
                        label = stringResource(R.string.app_profile_template_remote),
                        contentColor = colorScheme.onPrimary,
                        backgroundColor = colorScheme.primary,
                    )
                }
            }
        },
    )
}

@Composable
private fun TemplateEditorListItem(
    label: String,
    value: String,
    errorHint: String = "",
    isError: Boolean = false,
    multiline: Boolean = false,
    readOnly: Boolean = false,
    onValueChange: (String) -> Unit
) {
    SegmentedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        supportingContent = if (isError && errorHint.isNotEmpty()) {
            { Text(errorHint, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
        } else null,
        isError = isError,
        singleLine = !multiline,
        minLines = 1,
        maxLines = if (multiline) 100 else 1,
        readOnly = readOnly
    )
}

@Composable
private fun TopBar(
    title: String,
    readOnly: Boolean,
    canSave: Boolean,
    summary: String = "",
    onBack: () -> Unit,
    onDelete: () -> Unit = {},
    onSave: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = {
            Column {
                Text(title)
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        navigationIcon = {
            TopBarBackButton(onClick = onBack)
        },
        actions = {
            if (readOnly) return@LargeFlexibleTopAppBar
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = stringResource(id = R.string.app_profile_template_delete)
                )
            }
            IconButton(onClick = onSave, enabled = canSave) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(id = R.string.app_profile_template_save)
                )
            }
        },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
