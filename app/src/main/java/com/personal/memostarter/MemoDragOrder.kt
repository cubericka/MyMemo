package com.personal.memostarter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Keep the floating row outside LazyColumn so recycling the source cannot end a drag. */
@Stable
class MemoDragOrder {
 var source by mutableStateOf<Note?>(null); private set
 var dragging by mutableStateOf(false); private set
 var pointer by mutableStateOf(Offset.Zero); private set
 var startBounds by mutableStateOf(Rect.Zero); private set
 var top by mutableFloatStateOf(0f); private set
 var previewIds by mutableStateOf(emptyList<Long>()); private set
 var settling by mutableStateOf(false); private set
 private var originalIds = emptyList<Long>()
 private var grabY = 0f
 private var lastMoveLayout: LazyListLayoutInfo? = null
 private var committed by mutableStateOf(false)
 var layout: LazyListState? = null
 var viewport = Rect.Zero
 val rows = mutableMapOf<Long,Rect>()
 val titles = mutableMapOf<Long,Rect>()
 var notes: List<Note> = emptyList()
 var drop: (Long,Long,()->Unit)->Unit = { _,_,done -> done() }

 fun start(id: Long, point: Offset): Boolean {
  if(source!=null)return false
  val n=notes.find { it.id==id && it.trashedAt==null } ?: return false
  val bounds=rows[id] ?: return false
  originalIds=TreeRules.ordered(notes.filter { it.trashedAt==null && it.parentId==n.parentId && it.pinned==n.pinned }).map { it.id }
  lastMoveLayout=null;previewIds=originalIds;startBounds=bounds;pointer=point;grabY=point.y-bounds.top;top=bounds.top
  committed=false;settling=false;source=n;dragging=true
  return true
 }
 fun move(point: Offset) { if(dragging) { pointer=point;top=point.y-grabY;layout?.let(::updateTarget) } }
 fun projected(all: List<Note>): List<Note> {
  if(previewIds.isEmpty())return all
  val order=previewIds.withIndex().associate { it.value to it.index.toLong() }
  return all.map { n -> order[n.id]?.let { n.copy(sortOrder=it) } ?: n }
 }
 fun updateTarget(list: LazyListState) {
  val moving=source ?: return
  if(!dragging || pointer.x !in viewport.left..viewport.right)return
  val info=list.layoutInfo
  if(info===lastMoveLayout)return
  val from=previewIds.indexOf(moving.id)
  val center=top+startBounds.height/2f
  val candidates=info.visibleItemsInfo.mapNotNull { item ->
   val id=item.key as? Long ?: return@mapNotNull null
   val index=previewIds.indexOf(id)
   if(index<0 || id==moving.id)return@mapNotNull null
   val middle=viewport.top+item.offset-info.viewportStartOffset+item.size/2f
   if(index>from && center>=middle || index<from && center<=middle) index else null
  }
  val target=candidates.filter { it>from }.maxOrNull() ?: candidates.filter { it<from }.minOrNull()
  if(target!=null) {
   lastMoveLayout=info
   val index=list.firstVisibleItemIndex;val offset=list.firstVisibleItemScrollOffset
   previewIds=previewIds.toMutableList().apply { add(target,removeAt(from)) }
   list.requestScrollToItem(index,offset)
  }
 }
 fun finish() {
  val n=source ?: return
  if(!dragging)return
  dragging=false;settling=true
  val target=originalIds.getOrNull(previewIds.indexOf(n.id))
  if(target!=null && target!=n.id)drop(n.id,target) { committed=true } else committed=true
 }
 fun settled() { settling=false;if(committed)clear() }
 fun afterCommit() { if(!dragging && !settling && committed)clear() }
 fun cancel() { clear() }
 private fun clear() { source=null;dragging=false;settling=false;previewIds=emptyList();originalIds=emptyList() }
 fun updateAnimatedTop(value: Float) { top=value }
}

@Composable fun rememberMemoDragOrder(notes: List<Note>,list: LazyListState,enabled: Boolean,onDrop: (Long,Long,()->Unit)->Unit): MemoDragOrder {
 val state=remember { MemoDragOrder() }
 SideEffect { state.notes=notes;state.layout=list;state.drop=onDrop;state.afterCommit() }
 val density=LocalDensity.current
 val edge=with(density) { 64.dp.toPx() };val speed=with(density) { 14.dp.toPx() }
 LaunchedEffect(enabled) { if(!enabled && state.dragging)state.cancel() }
 LaunchedEffect(state.dragging) {
  while(isActive && state.dragging) {
   val p=state.pointer;val r=state.viewport
   val dy=if(p.x !in r.left..r.right)0f else when {
    p.y<r.top+edge -> -speed*((r.top+edge-p.y)/edge).coerceIn(0f,1f)
    p.y>r.bottom-edge -> speed*((p.y-r.bottom+edge)/edge).coerceIn(0f,1f)
    else -> 0f
   }
   if(dy!=0f)list.scrollBy(dy)
   state.updateTarget(list);delay(16)
  }
 }
 LaunchedEffect(state.settling) {
  if(state.settling) {
   withFrameNanos { };withFrameNanos { }
   val to=list.layoutInfo.visibleItemsInfo.find { it.key==state.source?.id }?.let { state.viewport.top+it.offset-list.layoutInfo.viewportStartOffset } ?: state.top
   Animatable(state.top).animateTo(to,tween(160)) { state.updateAnimatedTop(value) }
   state.settled()
  }
 }
 DisposableEffect(state) { onDispose { state.cancel() } }
 return state
}

fun Modifier.memoDragList(state: MemoDragOrder,enabled: Boolean): Modifier = composed {
 val haptics=LocalHapticFeedback.current
 onGloballyPositioned { state.viewport=it.boundsInWindow() }.pointerInput(state,enabled) {
  if(enabled)awaitEachGesture {
   val down=awaitFirstDown(requireUnconsumed=false)
   val point=Offset(state.viewport.left+down.position.x,state.viewport.top+down.position.y)
   val id=state.titles.entries.firstOrNull { it.value.contains(point) }?.key
   if(id!=null) {
    val held=awaitLongPressOrCancellation(down.id)
    if(held!=null && state.start(id,Offset(state.viewport.left+held.position.x,state.viewport.top+held.position.y))) {
     held.consume();haptics.performHapticFeedback(HapticFeedbackType.LongPress)
     try {
      while(true) {
       val event=awaitPointerEvent(PointerEventPass.Initial)
       val change=event.changes.find { it.id==held.id } ?: break
       if(event.changes.count { it.pressed }>1 || change.isConsumed)break
       state.move(Offset(state.viewport.left+change.position.x,state.viewport.top+change.position.y))
       change.consume()
       if(!change.pressed) { state.finish();break }
      }
     } finally { if(state.dragging)state.cancel() }
    }
   }
  }
 }
}
fun Modifier.memoDragRow(state: MemoDragOrder?,id: Long): Modifier = composed {
 if(state==null)this else {
  DisposableEffect(state,id) { onDispose { state.rows.remove(id) } }
  onGloballyPositioned { state.rows[id]=Rect(it.positionInWindow(),Size(it.size.width.toFloat(),it.size.height.toFloat())) }
 }
}
fun Modifier.memoDragTitle(state: MemoDragOrder?,id: Long): Modifier = composed {
 if(state==null)this else {
  DisposableEffect(state,id) { onDispose { state.titles.remove(id) } }
  onGloballyPositioned { state.titles[id]=it.boundsInWindow() }
 }
}
