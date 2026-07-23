/*
 * Copyright 2023 Stream.IO, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getstream.whatsappclone.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import io.getstream.whatsappclone.designsystem.component.WhatsAppCloneBackground
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.getTabPrimaryColor
import io.getstream.whatsappclone.designsystem.theme.getTabUnselectedColor
import io.getstream.whatsappclone.navigation.TOP_LEVEL_DESTINATIONS
import io.getstream.whatsappclone.navigation.WhatsAppPage
import io.getstream.whatsappclone.navigation.WhatsAppPagerContent
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@Composable
fun WhatsAppTabPager(
  modifier: Modifier = Modifier
) {
  val coroutineScope = rememberCoroutineScope()
  val activity = LocalContext.current as? Activity
  val pagerState = rememberPagerState(
    initialPage = WhatsAppPage.Chats.index,
    pageCount = { TOP_LEVEL_DESTINATIONS.size }
  )

  LaunchedEffect(Unit) {
    if (pagerState.currentPage == WhatsAppPage.Camera.index) {
      pagerState.scrollToPage(WhatsAppPage.Chats.index)
    }
  }

  BackHandler {
    if (pagerState.currentPage != WhatsAppPage.Chats.index) {
      coroutineScope.launch {
        pagerState.animateScrollToPage(WhatsAppPage.Chats.index)
      }
    } else {
      activity?.finish()
    }
  }

  WhatsAppCloneBackground {
    Column(modifier = modifier.fillMaxSize()) {
      TabRow(
        selectedTabIndex = pagerState.currentPage.coerceIn(0, TOP_LEVEL_DESTINATIONS.lastIndex),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = getTabPrimaryColor(),
        indicator = { tabPositions ->
          if (tabPositions.isNotEmpty()) {
            TabRowDefaults.SecondaryIndicator(
              modifier = Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
              height = 3.dp,
              color = getTabPrimaryColor()
            )
          }
        },
        divider = {}
      ) {
        TOP_LEVEL_DESTINATIONS.forEachIndexed { index, destination ->
          val selected = pagerState.currentPage == index
          Tab(
            modifier = Modifier.padding(vertical = 4.dp),
            selected = selected,
            onClick = {
              coroutineScope.launch {
                pagerState.animateScrollToPage(index)
              }
            },
            selectedContentColor = getTabPrimaryColor(),
            unselectedContentColor = getTabUnselectedColor()
          ) {
            if (destination.route != WhatsAppPage.Camera.route) {
              Text(
                modifier = Modifier.padding(vertical = 12.dp),
                text = destination.route.uppercase(),
                style = MaterialTheme.typography.titleSmall
              )
            } else {
              Icon(
                modifier = Modifier.padding(vertical = 10.dp),
                imageVector = WhatsAppIcons.Camera,
                contentDescription = null
              )
            }
          }
        }
      }

      HorizontalPager(
        modifier = Modifier.fillMaxSize(),
        state = pagerState,
        // Keep only the visible page composed — Stream Channels/Status are expensive.
        beyondViewportPageCount = 0,
        key = { page -> TOP_LEVEL_DESTINATIONS[page].route }
      ) { page ->
        val isSettled = pagerState.settledPage == page
        // Dispose heavy Stream/Status trees while the finger is mid-swipe to another tab.
        if (pagerState.currentPage == page || pagerState.targetPage == page) {
          WhatsAppPagerContent(
            page = page,
            isActive = isSettled
          )
        } else {
          Box(modifier = Modifier.fillMaxSize())
        }
      }
    }
  }
}

private fun Modifier.pagerTabIndicatorOffset(
  pagerState: PagerState,
  tabPositions: List<TabPosition>
): Modifier {
  val currentPage = pagerState.currentPage.coerceIn(0, tabPositions.lastIndex)
  val fraction = pagerState.currentPageOffsetFraction
  val currentTab = tabPositions[currentPage]
  val targetTab = tabPositions[
    (currentPage + if (fraction >= 0f) 1 else -1)
      .coerceIn(0, tabPositions.lastIndex)
  ]
  val indicatorOffset: Dp = lerp(currentTab.left, targetTab.left, fraction.absoluteValue)
  val indicatorWidth: Dp = lerp(currentTab.width, targetTab.width, fraction.absoluteValue)

  return this
    .fillMaxWidth()
    .wrapContentSize(Alignment.BottomStart)
    .offset(x = indicatorOffset)
    .width(indicatorWidth)
    .zIndex(1f)
}
