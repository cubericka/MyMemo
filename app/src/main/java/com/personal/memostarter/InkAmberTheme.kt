@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.personal.memostarter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val Ink = Color(0xFF303847)
internal val Amber = Color(0xFFA96A13)
private val Paper = Color.White
private val Mist = Color(0xFFF5F6F8)
private val AmberWash = Color(0xFFF5EBDD)

@Composable fun MemoTheme(content: @Composable () -> Unit) {
 MaterialTheme(
  colorScheme = lightColorScheme(
   primary=Amber, onPrimary=Paper, primaryContainer=AmberWash, onPrimaryContainer=Ink,
   inversePrimary=Color(0xFFFFD596), secondary=Ink, onSecondary=Paper,
   secondaryContainer=AmberWash, onSecondaryContainer=Ink,
   tertiary=Amber, onTertiary=Paper, tertiaryContainer=AmberWash, onTertiaryContainer=Ink,
   background=Paper, onBackground=Ink, surface=Paper, onSurface=Ink,
   surfaceVariant=Mist, onSurfaceVariant=Color(0xFF535C69),
   outline=Color(0xFF7A828F), outlineVariant=Color(0xFFD5D9E0),
   surfaceTint=Color.Transparent, inverseSurface=Ink, inverseOnSurface=Paper,
   surfaceBright=Paper, surfaceDim=Mist, surfaceContainerLowest=Paper,
   surfaceContainerLow=Mist, surfaceContainer=Mist, surfaceContainerHigh=Mist,
   surfaceContainerHighest=Color(0xFFEBEDF1)
  ),
  shapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),
   medium=RoundedCornerShape(12.dp),large=RoundedCornerShape(16.dp),extraLarge=RoundedCornerShape(24.dp)),
  content=content
 )
}

/** Header styling only: caller retains every action, label and navigation callback. */
@Composable internal fun InkTopAppBar(
 title: @Composable () -> Unit,
 navigationIcon: @Composable () -> Unit = {},
 actions: @Composable RowScope.() -> Unit = {}
) {
 MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(primary=Paper, error=Color(0xFFFFB4AB))) {
  TopAppBar(title=title,navigationIcon=navigationIcon,actions=actions,
   colors=TopAppBarDefaults.topAppBarColors(containerColor=Ink,scrolledContainerColor=Ink,
    titleContentColor=Paper,navigationIconContentColor=Paper,actionIconContentColor=Paper))
 }
}

@Composable internal fun InkCard(content: @Composable ColumnScope.() -> Unit) {
 Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,
  color=MaterialTheme.colorScheme.surface, border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
  Column(Modifier.padding(16.dp),content=content)
 }
}
