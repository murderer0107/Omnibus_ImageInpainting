# OMNIBUS — 이미지 객체 제거 Android 앱

사용자가 터치한 영역을 마스크로 만들고 이미지 인페인팅 결과를 받아 비교·저장하는 Android 애플리케이션입니다.

[시연 영상](https://drive.google.com/file/d/1xGXKHYXrMVRUz4AyEryMsBmyeongP3nv/view)

## 프로젝트 정보

- 기간: 2024.03–2024.09
- 인원: 6명
- 역할: 팀장, Android 앱 개발 전반, 최종 통합
- 기술: Kotlin, Android Studio, OkHttp, Coroutine, Android Canvas, Clipdrop Cleanup API

## 담당 구현

- 카메라 촬영과 갤러리 이미지 입력
- 화면 터치 좌표를 원본 이미지 좌표로 변환
- 펜·지우개와 굵기 조절을 포함한 흑백 마스크 생성
- 원본 JPG와 마스크 PNG를 OkHttp multipart 요청으로 전송
- Coroutine IO 처리와 응답 이미지 저장
- 객체 제거 전후 비교와 기기 앨범 저장

## 실행 전 설정

프로젝트 루트의 `local.properties`에 Clipdrop API 키를 추가합니다. 이 파일은 Git에서 제외됩니다.

```properties
CLIPDROP_API_KEY=your_api_key_here
```

자체 인페인팅 모델은 다른 팀원이 개발했습니다. 컴퓨터에서는 실행됐지만 Android 환경에서 결과 이미지가 깨지는 문제가 해결되지 않았습니다.

## 기술적 판단

제출 가능한 사용자 흐름을 완성하기 위해 Clipdrop Cleanup API 전환을 제안·결정했습니다. 공식 문서에서 요청 형식과 이미지 조건을 확인하고 처음부터 API 통신을 구현했습니다.

## 결과와 한계

- 이미지 입력→마스킹→객체 제거→Before/After 비교→앨범 저장 흐름 완성
- 실제 스마트폰에서 인물과 물체 제거 사례 시연
- 졸업 심사 통과

초기 목표였던 자체 온디바이스 모델 탑재는 달성하지 못했고 정량 품질 평가는 수행하지 않았습니다. 다시 개발한다면 사전학습 모델과 모바일 변환 방식을 초기 단계에서 작은 입출력 단위로 검증하겠습니다.
