from google import genai


client = genai.Client()

try:
    response = client.models.generate_content(
        model="gemini-3.5-flash",
        contents="안녕하세요. 연결 테스트입니다. 'Gemini 연결 성공'이라고만 답해주세요.",
    )

    print(response.text)

except Exception as e:
    print("Gemini 연결 실패")
    print(type(e).__name__)
    print(e)