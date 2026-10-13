package com.personal.memostarter

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Stable
class MemoDragOrder {
 var source by mutableStateOf<Note?>(null); private set
 var targetId by mutableStateOf<Long?>(null); private set
 var pointer by mutableStateOf(Offset.Zero); private set
 var viewport = Rect.Zero
 val rows = mutableMapOf<Long, Rect>()
 var notes: Map<Long, Note> = emptyMap()
 var drop: (Long,Long)->Unit = { _,_ -> }
 fun start(point: Offset) {
  cancel()
  val id=rows.entries.firstOrNull { it.value.contains(point) }?.key ?: return
  val note=notes[id] ?: return
  if(note.trashedAt!=null)return
  source=note;pointer=point;updateTarget()
 }
 fun move(delta: Offset) { if(source!=null) { pointer+=delta;updateTarget() } }
 fun updateTarget() {
  val moving=source
  targetId=if(moving==null || !viewport.contains(pointer))null else rows.entries.firstOrNull { (id,rect) ->
   val n=notes[id]
   id!=moving.id && rect.contains(pointer) && n!=null && n.trashedAt==null && n.parentId==moving.parentId && n.pinned==moving.pinned
  }?.key
 }
 fun finish() {
  updateTarget();val from=source?.id;val to=targetId;cancel()
  if(from!=null && to!=null)drop(from,to)
 }
 fun cancel() { source=null;targetId=null }
}

@Composable fun rememberMemoDragOrder(notes: List<Note>,list: LazyListState,enabled: Boolean,onDrop: (Long,Long)->Unit): MemoDragOrder {
 val state=remember { MemoDragOrder() }
 SideEffect { state.notes=notes.associateBy { it.id };state.drop=onDrop }
 val density=LocalDensity.current
 val edge=with(density) { 56.dp.toPx() };val speed=with(density) { 12.dp.toPx() }
 LaunchedEffect(enabled) { if(!enabled)state.cancel() }
 LaunchedEffect(state.source?.id,enabled) {
  if(!enabled)return@LaunchedEffect
  while(isActive && state.source!=null) {
   val p=state.pointer;val r=state.viewport
   val dy=if(p.x !in r.left..r.right)0f else when {
    p.y<r.top+edge -> -speed*((r.top+edge-p.y)/edge).coerceIn(0f,1f)
    p.y>r.bottom-edge -> speed*((p.y-r.bottom+edge)/edge).coerceIn(0f,1f)
    else -> 0f
   }
   if(dy!=0f)list.scrollBy(dy)
   state.updateTarget();delay(16)
  }
 }
 DisposableEffect(state) { onDispose { state.cancel() } }
 return state
}

// Gestures belong to the list, so scrolling the source row offscreen does not cancel the drag.
fun Modifier.memoDragList(state: MemoDragOrder,enabled: Boolean): Modifier =
 onGloballyPositioned { state.viewport=it.boundsInWindow() }
 .pointerInput(state,enabled) {
  if(enabled)detectDragGesturesAfterLongPress(
   onDragStart={ local -> state.start(Offset(state.viewport.left+local.x,state.viewport.top+local.y)) },
   onDragEnd=state::finish,onDragCancel=state::cancel,
   onDrag={ change,delta -> if(state.source!=null) { change.consume();state.move(delta) } }
  )
 }

fun Modifier.memoDragRow(state: MemoDragOrder?,id: Long): Modifier = composed {
 if(state==null)this else {
  DisposableEffect(state,id) { onDispose { state.rows.remove(id) } }
  val color=when(id) {
   state.targetId -> MaterialTheme.colorScheme.primary.copy(alpha=.20f)
   state.source?.id -> MaterialTheme.colorScheme.primary.copy(alpha=.08f)
   else -> androidx.compose.ui.graphics.Color.Transparent
  }
  this.background(color).onGloballyPositioned { state.rows[id]=it.boundsInWindow() }
 }
}

@Composable fun MemoDragHandle(note: Note) {
 Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
  Icon(Icons.Outlined.DragIndicator,"${note.displayTitle} 길게 눌러 순서 변경",tint=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
