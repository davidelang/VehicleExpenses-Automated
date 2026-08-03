package com.davidlang.vehicleexpensesautomated.ui.onboarding

import com.davidlang.vehicleexpensesautomated.R

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.components.FeatureScreenHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialPagerScreen(
    navController: NavHostController,
    tutorialId: String,
) {
    val context = LocalContext.current
    val tutorial = remember(tutorialId, context) { TutorialCatalog.get(context, tutorialId) }
    if (tutorial == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.onboarding_tutorial_not_found))
            TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.settings_back)) }
        }
        return
    }
    val pagerState = rememberPagerState(pageCount = { tutorial.steps.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        FeatureScreenHeader(tutorial.title)
        Text(
            "Step ${pagerState.currentPage + 1} of ${tutorial.steps.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val step = tutorial.steps[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(step.title, style = MaterialTheme.typography.titleLarge, softWrap = true)
                Text(step.body, style = MaterialTheme.typography.bodyLarge, softWrap = true)
                val asset = step.imageAsset
                if (asset != null) {
                    val bmp = remember(asset) {
                        runCatching {
                            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
                        }.getOrNull()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = step.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.onboarding_illustration_unavailable_offline_asset),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    } else {
                        navController.popBackStack()
                    }
                },
            ) {
                Text(if (pagerState.currentPage > 0) "Back" else "Close")
            }
            val last = pagerState.currentPage >= tutorial.steps.lastIndex
            Button(
                onClick = {
                    if (!last) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        navController.navigate(tutorial.endRoute) {
                            popUpTo("quickfill") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
            ) {
                Text(if (last) tutorial.endCtaLabel else "Next")
            }
        }
        if (pagerState.currentPage >= tutorial.steps.lastIndex) {
            TextButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.onboarding_done_without_navigating))
            }
        }
    }
}
