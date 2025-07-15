import { useEffect } from "react";
import { useLocation } from "react-router-dom";

export default function NaverCallback() {
  const { hash } = useLocation();

  useEffect(() => {
    console.log("[NaverCallback] 해시:", hash);
    const params = new URLSearchParams(hash.replace(/^#/, "?"));
    const token = params.get("access_token");
    console.log("[NaverCallback] 파싱된 토큰:", token);
    console.log("[NaverCallback] window.opener:", window.opener);

    window.opener.postMessage(
      { type: "NAVER_TOKEN", accessToken: token },
      window.location.origin
    );
    // 팝업이 바로 닫히면 devtools 볼 시간도 없으니 잠시 지연
    setTimeout(() => window.close(), 300);
  }, [hash]);

  return <div>네이버 로그인 처리 중…</div>;
}
