import { useEffect } from "react";

export default function NaverCallback() {
  useEffect(() => {
    const hash = new URLSearchParams(window.location.hash.slice(1));
    const query = new URLSearchParams(window.location.search);
    const accessToken = hash.get("access_token");
    const code = query.get("code");
    const state = hash.get("state");

    if (window.opener) {
      window.opener.postMessage(
        { type: "NAVER_TOKEN", accessToken, code, state },
        window.location.origin
      );
    }
    window.close();
  }, []);
  return null;
}
