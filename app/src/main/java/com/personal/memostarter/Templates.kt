package com.personal.memostarter

import org.json.JSONObject

data class MemoTemplate(val id: String, val name: String, val description: String, val titleHint: String, val bodyHint: String, val fields: List<String>)
object MemoTemplates {
 val all = listOf(
  MemoTemplate("quick", "빠른 메모", "제목과 본문만으로 자유롭게 기록", "제목 없음", "내용을 적어 주세요", listOf("태그")),
  MemoTemplate("idea", "아이디어", "떠오른 발상과 발전 가능성 정리", "아이디어 제목", "떠오른 아이디어를 자유롭게 적어 주세요", listOf("떠오른 계기", "기대 효과", "다음 단계")),
  MemoTemplate("checklist", "체크리스트", "쇼핑 목록, 준비물, 간단한 할 일", "목록 제목", "목록에 관한 메모를 자유롭게 적어 주세요", listOf("마감일")),
  MemoTemplate("study", "학습 노트", "공부한 내용을 요약하고 이해하기", "주제", "학습 내용을 자유롭게 적어 주세요", listOf("핵심 개념", "요약", "질문", "복습")),
  MemoTemplate("decision", "의사결정", "선택지를 비교하고 판단 근거 남기기", "고민", "고민에 대해 자유롭게 적어 주세요", listOf("선택지", "장단점", "결론", "결정 이유")),
  MemoTemplate("research", "자료 수집 / 리서치", "참고 자료와 발견한 정보 기록", "주제", "수집한 내용을 자유롭게 적어 주세요", listOf("출처 URL", "핵심 정보", "내 생각")),
  MemoTemplate("plan", "목표 / 계획", "목표와 달성 과정 기록", "목표", "계획을 자유롭게 적어 주세요", listOf("이유", "마감일", "실행 단계", "진행 상황")),
  MemoTemplate("information", "정보 보관", "나중에 다시 찾을 실용적인 정보", "항목명", "상세 정보를 자유롭게 적어 주세요", listOf("카테고리", "관련 링크"))
 )
 fun get(id: String) = all.find { it.id == id } ?: all.first()
}
object TemplateDetails {
 fun encode(values: Map<String, String>): String = JSONObject(values).toString()
 fun decode(raw: String): Map<String, String> {
  val json = JSONObject(raw)
  return json.keys().asSequence().associateWith { json.getString(it) }
 }
 fun searchable(raw: String): String = decode(raw).values.joinToString("\n")
}
