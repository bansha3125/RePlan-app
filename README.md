# RePlan-app



선택한 유저의 이번 주 '고정 일정'과 'AI 생성 일정' 조회 [연동 규격]

요청 주소: GET http://localhost:8080/schedules/weekly

파라미터: userId (현재 테스트용으로 1번 사용 중)

응답 데이터(JSON 예시):
JSON
{
  "fixedSchedules": [
    {
      "title": "데이터구조 강의",
      "startTime": "2026-06-29T10:00:00",
      "endTime": "2026-06-29T11:30:00",
      "repeatDay": null
    }
  ],
  "generatedSchedules": []
}
