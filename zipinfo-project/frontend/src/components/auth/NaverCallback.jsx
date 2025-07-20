import { useEffect } from "react";
import { useLocation } from "react-router-dom";

export default function NaverCallback() {
  const { hash } = useLocation();

  useEffect(() => {
    console.log("🔵 [NaverCallback] 시작");
    console.log("🔵 [NaverCallback] 전체 URL:", window.location.href);
    console.log("🔵 [NaverCallback] Hash:", hash);
    console.log("🔵 [NaverCallback] Origin:", window.location.origin);
    console.log("🔵 [NaverCallback] window.opener 존재:", !!window.opener);
    
    try {
      // Hash 검증
      if (!hash) {
        console.log("❌ [NaverCallback] Hash가 없음");
        sendErrorMessage("HASH_MISSING", "URL 해시가 없습니다.");
        return;
      }

      // 파라미터 파싱
      const params = new URLSearchParams(hash.replace(/^#/, "?"));
      console.log("🔧 [NaverCallback] 파싱된 모든 파라미터:");
      
      // 모든 파라미터 출력
      for (const [key, value] of params) {
        if (key === 'access_token') {
          console.log(`- ${key}: ${value.substring(0, 10)}...`);
        } else {
          console.log(`- ${key}: ${value}`);
        }
      }

      const token = params.get("access_token");
      const error = params.get("error");
      const errorDescription = params.get("error_description");
      const state = params.get("state");
      const tokenType = params.get("token_type");
      const expiresIn = params.get("expires_in");

      console.log("📋 [NaverCallback] 파싱 결과:");
      console.log("- Token 존재:", !!token);
      console.log("- Token 길이:", token?.length);
      console.log("- Error 존재:", !!error);
      console.log("- State:", state);
      console.log("- Token Type:", tokenType);
      console.log("- Expires In:", expiresIn);

      // 에러 체크
      if (error) {
        console.log("❌ [NaverCallback] 네이버 OAuth 에러:");
        console.log("- Error:", error);
        console.log("- Description:", errorDescription);
        
        sendErrorMessage(error, errorDescription || "네이버 인증 중 오류가 발생했습니다.");
        return;
      }

      // 토큰 유효성 체크
      if (!token) {
        console.log("❌ [NaverCallback] 토큰이 없음");
        sendErrorMessage("TOKEN_MISSING", "네이버에서 토큰을 받지 못했습니다.");
        return;
      }

      if (token.length < 10) {
        console.log("❌ [NaverCallback] 토큰이 너무 짧음:", token.length);
        sendErrorMessage("TOKEN_INVALID", "받은 토큰이 유효하지 않습니다.");
        return;
      }

      // window.opener 체크
      if (!window.opener) {
        console.log("❌ [NaverCallback] window.opener가 없음");
        console.log("- window.parent 존재:", !!window.parent);
        console.log("- window.top 존재:", !!window.top);
        
        // window.parent로 시도
        if (window.parent && window.parent !== window) {
          console.log("🔄 [NaverCallback] window.parent로 메시지 전송 시도");
          sendMessageToParent(token);
        } else {
          sendErrorMessage("OPENER_MISSING", "부모 창을 찾을 수 없습니다.");
        }
        return;
      }

      // 토큰 전송
      console.log("✅ [NaverCallback] 토큰 전송 준비 완료");
      sendTokenMessage(token);

    } catch (error) {
      console.error("❌ [NaverCallback] 처리 중 예외 발생:", error);
      sendErrorMessage("CALLBACK_ERROR", `처리 중 오류: ${error.message}`);
    }
  }, [hash]);

  // 토큰 메시지 전송
  const sendTokenMessage = (token) => {
    const messageData = {
      type: "NAVER_TOKEN",
      accessToken: token,
      timestamp: Date.now()
    };

    console.log("📤 [NaverCallback] 토큰 메시지 전송:");
    console.log("- Target:", window.opener);
    console.log("- Origin:", window.location.origin);
    console.log("- Data:", { ...messageData, accessToken: token.substring(0, 10) + "..." });

    try {
      window.opener.postMessage(messageData, window.location.origin);
      console.log("✅ [NaverCallback] 메시지 전송 성공");
      
      // 전송 확인 후 창 닫기
      setTimeout(() => {
        console.log("🔵 [NaverCallback] 창 닫기");
        window.close();
      }, 1000);
      
    } catch (error) {
      console.error("❌ [NaverCallback] 메시지 전송 실패:", error);
      sendErrorMessage("MESSAGE_SEND_FAILED", `메시지 전송 실패: ${error.message}`);
    }
  };

  // 에러 메시지 전송
  const sendErrorMessage = (errorCode, description) => {
    const errorData = {
      type: "NAVER_ERROR",
      error: errorCode,
      description: description,
      timestamp: Date.now()
    };

    console.log("📤 [NaverCallback] 에러 메시지 전송:", errorData);

    try {
      if (window.opener) {
        window.opener.postMessage(errorData, window.location.origin);
      } else if (window.parent && window.parent !== window) {
        window.parent.postMessage(errorData, window.location.origin);
      }
    } catch (error) {
      console.error("❌ [NaverCallback] 에러 메시지 전송도 실패:", error);
    }

    // 에러 발생 시 조금 더 기다린 후 창 닫기
    setTimeout(() => {
      console.log("🔵 [NaverCallback] 에러로 인한 창 닫기");
      window.close();
    }, 2000);
  };

  // window.parent로 메시지 전송 (fallback)
  const sendMessageToParent = (token) => {
    const messageData = {
      type: "NAVER_TOKEN",
      accessToken: token,
      timestamp: Date.now(),
      source: "parent" // opener가 아닌 parent를 통해 전송됨을 표시
    };

    try {
      window.parent.postMessage(messageData, window.location.origin);
      console.log("✅ [NaverCallback] parent로 메시지 전송 성공");
      
      setTimeout(() => {
        console.log("🔵 [NaverCallback] 창 닫기 (parent 경로)");
        window.close();
      }, 1000);
      
    } catch (error) {
      console.error("❌ [NaverCallback] parent 메시지 전송 실패:", error);
      sendErrorMessage("PARENT_MESSAGE_FAILED", `Parent 메시지 전송 실패: ${error.message}`);
    }
  };

  return (
    <div style={{ 
      padding: '20px', 
      textAlign: 'center',
      fontFamily: 'Arial, sans-serif'
    }}>
      <h3>네이버 로그인 처리 중...</h3>
      <p>잠시만 기다려주세요.</p>
      
      {/* 디버깅 정보 (개발 환경에서만) */}
      {import.meta.env.DEV && (
        <div style={{ 
          marginTop: '20px', 
          padding: '10px', 
          background: '#f5f5f5',
          fontSize: '12px',
          textAlign: 'left',
          border: '1px solid #ddd',
          borderRadius: '4px'
        }}>
          <strong>디버깅 정보:</strong>
          <div>URL: {window.location.href}</div>
          <div>Hash: {hash}</div>
          <div>Origin: {window.location.origin}</div>
          <div>Opener: {window.opener ? '있음' : '없음'}</div>
          <div>Parent: {window.parent !== window ? '있음' : '없음'}</div>
        </div>
      )}
    </div>
  );
}