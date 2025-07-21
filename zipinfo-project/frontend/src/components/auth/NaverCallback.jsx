import { useEffect } from "react";
import { useLocation } from "react-router-dom";

export default function NaverCallback() {
  const { hash } = useLocation();

  // 부모 창으로 로그 전송하는 함수
  const logToParent = (level, message, data = null) => {
    const logData = {
      type: "NAVER_LOG",
      level: level,
      message: message,
      data: data,
      timestamp: new Date().toLocaleTimeString()
    };

    // 팝업 자체 콘솔에도 출력
    console[level](`[NaverCallback] ${message}`, data || '');

    // 부모 창으로도 전송
    try {
      if (window.opener) {
        window.opener.postMessage(logData, window.location.origin);
      } else if (window.parent && window.parent !== window) {
        window.parent.postMessage(logData, window.location.origin);
      }
    } catch (error) {
      console.error("로그 전송 실패:", error);
    }
  };

  useEffect(() => {
    logToParent("info", "네이버 콜백 시작");
    logToParent("info", "전체 URL", window.location.href);
    logToParent("info", "Hash 값", hash);
    logToParent("info", "Origin", window.location.origin);
    logToParent("info", "window.opener 존재", !!window.opener);
    logToParent("info", "window.parent 존재", !!(window.parent && window.parent !== window));

    try {
      const params = new URLSearchParams(hash.replace(/^#/, "?"));

      // 모든 파라미터 로그
      const allParams = {};
      for (const [key, value] of params) {
        if (key === "access_token") {
          allParams[key] = value.substring(0, 10) + "...";
        } else {
          allParams[key] = value;
        }
      }
      logToParent("info", "파싱된 모든 파라미터", allParams);

      const token = params.get("access_token");
      const error = params.get("error");
      const errorDescription = params.get("error_description");

      logToParent("info", "토큰 파싱 결과", {
        tokenExists: !!token,
        tokenLength: token?.length,
        hasError: !!error,
        error: error,
        errorDescription: errorDescription
      });

      // 에러 체크
      if (error) {
        logToParent("error", "네이버 OAuth 에러 발생", { error, errorDescription });

        const errorData = {
          type: "NAVER_ERROR",
          error: error,
          description: errorDescription || "네이버 인증 중 오류가 발생했습니다.",
          timestamp: Date.now()
        };

        if (window.opener) {
          window.opener.postMessage(errorData, window.location.origin);
        } else if (window.parent && window.parent !== window) {
          window.parent.postMessage(errorData, window.location.origin);
        }

        setTimeout(() => window.close(), 2000);
        return;
      }

      // 토큰 유효성 체크
      if (!token) {
        logToParent("error", "토큰이 없음");

        const errorData = {
          type: "NAVER_ERROR",
          error: "TOKEN_MISSING",
          description: "네이버에서 토큰을 받지 못했습니다.",
          timestamp: Date.now()
        };

        if (window.opener) {
          window.opener.postMessage(errorData, window.location.origin);
        } else if (window.parent && window.parent !== window) {
          window.parent.postMessage(errorData, window.location.origin);
        }

        setTimeout(() => window.close(), 2000);
        return;
      }

      if (token.length < 10) {
        logToParent("error", "토큰이 너무 짧음", { length: token.length });

        const errorData = {
          type: "NAVER_ERROR",
          error: "TOKEN_INVALID",
          description: "받은 토큰이 유효하지 않습니다.",
          timestamp: Date.now()
        };

        if (window.opener) {
          window.opener.postMessage(errorData, window.location.origin);
        } else if (window.parent && window.parent !== window) {
          window.parent.postMessage(errorData, window.location.origin);
        }

        setTimeout(() => window.close(), 2000);
        return;
      }

      // 토큰 전송
      logToParent("info", "토큰 전송 준비 완료", { tokenLength: token.length });

      const messageData = {
        type: "NAVER_TOKEN",
        accessToken: token,
        timestamp: Date.now()
      };

      logToParent("info", "부모에게 토큰 메시지 전송");

      if (window.opener) {
        window.opener.postMessage(messageData, window.location.origin);
        logToParent("info", "opener로 메시지 전송 완료");
      } else if (window.parent && window.parent !== window) {
        window.parent.postMessage(messageData, window.location.origin);
        logToParent("info", "parent로 메시지 전송 완료");
      } else {
        logToParent("error", "opener와 parent 모두 없음");
      }

      // 창 닫기
      setTimeout(() => {
        logToParent("info", "창 닫기");
        window.close();
      }, 1000);

    } catch (error) {
      logToParent("error", "처리 중 예외 발생", {
        message: error.message,
        stack: error.stack
      });

      const errorData = {
        type: "NAVER_ERROR",
        error: "CALLBACK_ERROR",
        description: `처리 중 오류: ${error.message}`,
        timestamp: Date.now()
      };

      try {
        if (window.opener) {
          window.opener.postMessage(errorData, window.location.origin);
        } else if (window.parent && window.parent !== window) {
          window.parent.postMessage(errorData, window.location.origin);
        }
      } catch (sendError) {
        logToParent("error", "에러 메시지 전송도 실패", { message: sendError.message });
      }

      setTimeout(() => window.close(), 2000);
    }
  }, [hash]);

  return (
    <div style={{
      padding: "20px",
      textAlign: "center",
      fontFamily: "Arial, sans-serif"
    }}>
      <h3>네이버 로그인 처리 중...</h3>
      <p>잠시만 기다려주세요.</p>
    </div>
  );
}




