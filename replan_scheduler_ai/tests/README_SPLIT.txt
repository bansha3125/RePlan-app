RePlan API 테스트 파일 분리본

1. 기존 tests/test_api.py는 삭제하지 말고 먼저 다음처럼 백업합니다.
   Copy-Item tests\test_api.py tests\test_api_backup.txt

2. 이 압축 파일의 아래 6개 Python 파일을 프로젝트의 tests 폴더에 넣습니다.
   - api_test_base.py
   - test_api_generate.py
   - test_api_replan.py
   - test_api_failure.py
   - test_api_replay.py
   - test_api_contract.py

3. 기존 tests/test_api.py를 삭제합니다.
   Remove-Item tests\test_api.py

4. 문법 검사를 실행합니다.
   python -m py_compile tests\api_test_base.py
   python -m py_compile tests\test_api_generate.py
   python -m py_compile tests\test_api_replan.py
   python -m py_compile tests\test_api_failure.py
   python -m py_compile tests\test_api_replay.py
   python -m py_compile tests\test_api_contract.py

5. 전체 테스트를 실행합니다.
   python -m unittest discover -s tests -p "test_*.py" -v

예상 결과:
Ran 30 tests
OK
