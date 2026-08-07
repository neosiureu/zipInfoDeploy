import { useEffect } from "react";
import { useLocation } from "react-router-dom";

export default function NaverCallback() {
  const { hash } = useLocation();

  // 부모 창으로 메시지를 전송하는 헬퍼 함수
  const sendMessageToParent = (data) => {
    try {
      const target = window.opener || (window.parent !== window ? window.parent : null);
      if (target) {
        target.postMessage(data, window.location.origin);
      }
    } catch (e) {
      // postMessage 전송 실패 시 예외 무시
    }
  };

  useEffect(() => {
    // 메시지 전달 및 부모 창 리스너 처리 시간을 벌어주는 1초 지연 닫기 함수
    const closeWithDelay = (delay = 1000) => {
      setTimeout(() => {
        window.close();
      }, delay);
    };

    try {
      const params = new URLSearchParams(hash.replace(/^#/, "?"));
      const token = params.get("access_token");
      const error = params.get("error");
      const errorDescription = params.get("error_description");

      // 1. 네이버 OAuth 에러 응답 처리
      if (error) {
        sendMessageToParent({
          type: "NAVER_ERROR",
          error: error,
          description: errorDescription || "네이버 인증 중 오류가 발생했습니다.",
          timestamp: Date.now(),
        });
        closeWithDelay(1000);
        return;
      }

      // 2. 토큰 유효성 검사 (없거나 너무 짧은 경우)
      if (!token || token.length < 10) {
        sendMessageToParent({
          type: "NAVER_ERROR",
          error: !token ? "TOKEN_MISSING" : "TOKEN_INVALID",
          description: !token ? "네이버에서 토큰을 받지 못했습니다." : "받은 토큰이 유효하지 않습니다.",
          timestamp: Date.now(),
        });
        closeWithDelay(1000);
        return;
      }

      // 3. 정상 토큰 전송 및 팝업 닫기
      sendMessageToParent({
        type: "NAVER_TOKEN",
        accessToken: token,
        timestamp: Date.now(),
      });
      closeWithDelay(1000);

    } catch (err) {
      // 메시지 전송 시도 후, 실패하더라도 try...finally로 팝업 닫기 보장
      try {
        sendMessageToParent({
          type: "NAVER_ERROR",
          error: "CALLBACK_ERROR",
          description: `처리 중 오류: ${err.message}`,
          timestamp: Date.now(),
        });
      } finally {
        closeWithDelay(1000);
      }
    }
  }, [hash]);

  return (
    <div
      style={{
        padding: "20px",
        textAlign: "center",
        fontFamily: "Arial, sans-serif",
      }}
    >
      <h3>네이버 로그인 처리 중...</h3>
      <p>잠시만 기다려주세요.</p>
    </div>
  );
}