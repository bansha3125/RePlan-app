from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Any, Optional


@dataclass
class GeminiResult:
    data: dict[str, Any]
    used_fallback: bool
    error: Optional[str] = None


class GeminiAssistant:
    """
    Gemini는 작업 분해와 자연어 설명만 담당한다.
    일정의 실제 배치 판단은 scheduler.py가 담당한다.

    API 키나 SDK가 없거나 요청이 실패하면 항상 기본 결과를 반환한다.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        model: Optional[str] = None,
    ) -> None:
        self.api_key = api_key or os.getenv("GEMINI_API_KEY")
        self.model = model or os.getenv("GEMINI_MODEL", "gemini-3.5-flash")
        self.client = None
        self.initialization_error: Optional[str] = None

        if not self.api_key:
            self.initialization_error = "GEMINI_API_KEY가 설정되지 않았습니다."
            return

        try:
            from google import genai

            self.client = genai.Client(api_key=self.api_key)
        except Exception as exc:  # SDK 미설치 포함
            self.initialization_error = str(exc)

    def _generate_json(
        self,
        prompt: str,
        schema: dict[str, Any],
    ) -> dict[str, Any]:
        if self.client is None:
            raise RuntimeError(
                self.initialization_error or "Gemini client unavailable"
            )

        response = self.client.models.generate_content(
            model=self.model,
            contents=prompt,
            config={
                "response_mime_type": "application/json",
                "response_json_schema": schema,
            },
        )

        if not response.text:
            raise RuntimeError("Gemini 응답이 비어 있습니다.")

        return json.loads(response.text)

    def decompose_task(
        self,
        task_title: str,
        desired_steps: int,
        total_estimated_minutes: int,
        context: str = "",
    ) -> GeminiResult:
        desired_steps = max(1, min(desired_steps, 10))
        total_estimated_minutes = max(total_estimated_minutes, desired_steps * 10)

        schema = {
            "type": "object",
            "properties": {
                "steps": {
                    "type": "array",
                    "minItems": desired_steps,
                    "maxItems": desired_steps,
                    "items": {
                        "type": "object",
                        "properties": {
                            "order": {"type": "integer"},
                            "title": {"type": "string"},
                            "estimated_minutes": {"type": "integer"},
                            "difficulty": {"type": "integer"},
                            "focus_required": {"type": "integer"},
                            "depends_on_order": {
                                "type": ["integer", "null"]
                            },
                        },
                        "required": [
                            "order",
                            "title",
                            "estimated_minutes",
                            "difficulty",
                            "focus_required",
                            "depends_on_order",
                        ],
                    },
                }
            },
            "required": ["steps"],
        }

        prompt = f"""
사용자의 큰 작업을 실제 일정에 넣을 수 있는 작은 단계로 분해하세요.
작업: {task_title}
사용자가 선택한 단계 수: 정확히 {desired_steps}개
전체 예상 시간: 약 {total_estimated_minutes}분
추가 상황: {context or '없음'}

규칙:
- 단계 수를 정확히 지키세요.
- 각 단계는 구체적인 행동으로 작성하세요.
- estimated_minutes의 합은 전체 예상 시간과 비슷하게 만드세요.
- difficulty와 focus_required는 1~5 정수입니다.
- 첫 단계의 depends_on_order는 null, 이후 단계는 필요한 선행 단계 번호를 작성하세요.
- 한국어로 작성하세요.
""".strip()

        try:
            data = self._generate_json(prompt, schema)
            return GeminiResult(data=data, used_fallback=False)
        except Exception as exc:
            return GeminiResult(
                data=self._fallback_decomposition(
                    task_title,
                    desired_steps,
                    total_estimated_minutes,
                ),
                used_fallback=True,
                error=str(exc),
            )

    def recommendation_reason(
        self,
        task_title: str,
        scheduled_time: str,
        deadline: str,
        score_components: dict[str, float],
        extra_context: str = "",
    ) -> GeminiResult:
        schema = {
            "type": "object",
            "properties": {
                "reason": {"type": "string"},
                "key_factors": {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 1,
                    "maxItems": 3,
                },
            },
            "required": ["reason", "key_factors"],
        }
        prompt = f"""
다음 자동 일정 배치 결과를 사용자에게 2문장 이내로 설명하세요.
작업: {task_title}
배치 시간: {scheduled_time}
마감: {deadline}
점수 요소: {json.dumps(score_components, ensure_ascii=False)}
추가 상황: {extra_context or '없음'}
과장하지 말고, 실제 점수와 시간 조건에 근거해 한국어로 설명하세요.
""".strip()

        try:
            data = self._generate_json(prompt, schema)
            return GeminiResult(data=data, used_fallback=False)
        except Exception as exc:
            factors = sorted(
                score_components.items(),
                key=lambda item: item[1],
                reverse=True,
            )[:3]
            labels = [name for name, _ in factors]
            return GeminiResult(
                data={
                    "reason": (
                        f"'{task_title}'은(는) 마감과 우선순위를 고려해 "
                        f"{scheduled_time}에 배치했습니다."
                    ),
                    "key_factors": labels or ["마감 시간"],
                },
                used_fallback=True,
                error=str(exc),
            )

    def postponement_message(
        self,
        task_title: str,
        postpone_count: int,
        next_scheduled_time: Optional[str],
    ) -> GeminiResult:
        schema = {
            "type": "object",
            "properties": {
                "message": {"type": "string"},
                "suggestion": {"type": "string"},
            },
            "required": ["message", "suggestion"],
        }
        prompt = f"""
사용자가 '{task_title}' 작업을 {postpone_count}회 미뤘습니다.
다음 배치 시간: {next_scheduled_time or '배치 불가'}
비난하지 말고 짧고 실용적인 안내 문구와 한 가지 실행 제안을 한국어로 작성하세요.
""".strip()

        try:
            return GeminiResult(
                data=self._generate_json(prompt, schema),
                used_fallback=False,
            )
        except Exception as exc:
            if next_scheduled_time:
                message = (
                    f"'{task_title}'을(를) {postpone_count}회 미뤄 "
                    f"{next_scheduled_time}로 다시 배치했습니다."
                )
            else:
                message = (
                    f"'{task_title}'을(를) 다시 배치할 빈 시간이 부족합니다."
                )
            return GeminiResult(
                data={
                    "message": message,
                    "suggestion": "우선 10분만 시작하거나 예상 시간을 줄여 다시 배치해 보세요.",
                },
                used_fallback=True,
                error=str(exc),
            )

    def personalized_feedback(
        self,
        feedback_data: dict[str, Any],
    ) -> GeminiResult:
        schema = {
            "type": "object",
            "properties": {
                "summary": {"type": "string"},
                "strength": {"type": "string"},
                "improvement": {"type": "string"},
                "next_action": {"type": "string"},
            },
            "required": ["summary", "strength", "improvement", "next_action"],
        }
        prompt = f"""
다음 시간 관리 분석 데이터를 바탕으로 사용자 맞춤 피드백을 작성하세요.
데이터: {json.dumps(feedback_data, ensure_ascii=False, default=str)}
한국어로 작성하고, 근거 없는 성격 판단은 하지 마세요.
각 항목은 1~2문장으로 짧게 작성하세요.
""".strip()

        try:
            return GeminiResult(
                data=self._generate_json(prompt, schema),
                used_fallback=False,
            )
        except Exception as exc:
            rate = feedback_data.get("completion_rate_percent", 0)
            repeated = feedback_data.get("repeated_postponement", [])
            improvement = (
                "반복해서 미룬 작업은 30~60분 단위로 더 작게 나누는 것이 좋습니다."
                if repeated
                else "현재 기록을 조금 더 쌓으면 시간 예측을 더 정확하게 보정할 수 있습니다."
            )
            return GeminiResult(
                data={
                    "summary": f"현재 일정 수행률은 {rate}%입니다.",
                    "strength": "완료 기록을 기반으로 실제 수행 패턴을 확인할 수 있습니다.",
                    "improvement": improvement,
                    "next_action": "다음 일정부터 실제 소요 시간을 기록해 예상 시간을 보정하세요.",
                },
                used_fallback=True,
                error=str(exc),
            )

    @staticmethod
    def _fallback_decomposition(
        task_title: str,
        desired_steps: int,
        total_minutes: int,
    ) -> dict[str, Any]:
        templates = [
            "목표와 요구사항 확인",
            "필요한 자료 수집",
            "세부 계획과 순서 정리",
            "초안 또는 1차 구현",
            "핵심 내용 보완",
            "검토 및 테스트",
            "오류 수정",
            "최종 결과 정리",
            "제출 형식 확인",
            "제출 또는 완료 처리",
        ]
        selected = templates[:desired_steps]
        base, remainder = divmod(total_minutes, desired_steps)
        steps = []

        for index, label in enumerate(selected, start=1):
            minutes = base + (1 if index <= remainder else 0)
            steps.append(
                {
                    "order": index,
                    "title": f"{task_title}: {label}",
                    "estimated_minutes": minutes,
                    "difficulty": min(5, 2 + (index // 3)),
                    "focus_required": 3 if index < desired_steps else 2,
                    "depends_on_order": index - 1 if index > 1 else None,
                }
            )

        return {"steps": steps}
