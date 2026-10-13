package com.personal.memostarter

/** Move to the target peer's index without changing hierarchy, pin state or content. */
object ReorderRules {
 fun moveTo(id: Long,targetId: Long,all: List<Note>): List<Note> {
  val moving=requireNotNull(all.find { it.id==id && it.trashedAt==null }) { "이동할 메모가 없습니다" }
  val target=requireNotNull(all.find { it.id==targetId && it.trashedAt==null }) { "대상 메모가 없습니다" }
  require(moving.parentId==target.parentId) { "같은 상위 메모 안에서 순서를 변경하세요" }
  require(moving.pinned==target.pinned) { "고정 메모와 일반 메모는 각각 순서를 변경하세요" }
  val peers=TreeRules.ordered(all.filter { it.trashedAt==null && it.parentId==moving.parentId && it.pinned==moving.pinned }).toMutableList()
  val from=peers.indexOfFirst { it.id==id };val to=peers.indexOfFirst { it.id==targetId }
  if(from==to)return emptyList()
  peers.add(to,peers.removeAt(from))
  return peers.mapIndexed { index,n -> n.copy(sortOrder=index.toLong()) }
 }
}
