import axios from "axios";
import { jwtDecode } from "jwt-decode";

const KAKAO_REST_API_KEY = import.meta.env.VITE_KAKAO_REST_API_KEY;

export const axiosAPI = axios.create({
  baseURL: "http://localhost:8080",
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

// ========== 새로운 코드: 파일 로드 시 즉시 인터셉터 등록 ==========
axiosAPI.interceptors.request.use((config) => {
  // 카카오 API 요청은 바로 보내기
  if (config.url?.startsWith("https://dapi.kakao.com/")) {
    delete config.headers.Authorization;
    config.withCredentials = false;
    config.headers.Authorization = `KakaoAK ${KAKAO_REST_API_KEY}`;
    return config;
  }

  // 로그인 요청은 토큰 체크 없이 바로 보내기
  if (config.url?.includes("/login")) {
    return config;
  }

  // 그 외 요청은 토큰 체크
  const token = localStorage.getItem("accessToken");
  if (token) {
    try {
      const { exp } = jwtDecode(token);
      if (exp * 1000 < Date.now()) {
        window.dispatchEvent(new CustomEvent("forceLogout"));
        delete config.headers.Authorization;
        return config;
      }
    } catch {
      window.dispatchEvent(new CustomEvent("forceLogout"));
      return Promise.reject(new Error("Invalid token"));
    }
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

// 새로운 코드: 응답 인터셉터도 즉시 등록
axiosAPI.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const justLoggedOut = sessionStorage.getItem("justLoggedOut") === "yes";

    if (status === 401 || status === 403) {
      if (justLoggedOut) {
        sessionStorage.removeItem("justLoggedOut");
        return Promise.reject(error);
      }
      window.dispatchEvent(new CustomEvent("forceLogout"));
    }
    return Promise.reject(error);
  }
);

// ========== 변경된 코드: 이벤트 리스너만 등록 ==========
export function setupAxiosInterceptors({ navigate, toast }) {
  const handleForceLogout = () => {
    toast.error("다른 PC의 로그인이 감지되어 로그아웃 되었습니다.");
    navigate("/login", { replace: true });
  };

  window.addEventListener("forceLogout", handleForceLogout);

  return () => {
    window.removeEventListener("forceLogout", handleForceLogout);
  };
}

/* ========== 기존 코드 (주석 처리) ==========
export function setupAxiosInterceptors({ navigate, toast }) {
  // 요청 인터셉터 ---------------------------------------------------
  axiosAPI.interceptors.request.use((config) => {
    //  카카오 API 요청은 바로 보내기
    if (config.url?.startsWith("https://dapi.kakao.com/")) {
      delete config.headers.Authorization;
      config.withCredentials = false;
      config.headers.Authorization = `KakaoAK ${KAKAO_REST_API_KEY}`;
      return config;
    }

    //  로그인 요청은 토큰 체크 없이 바로 보내기. 토큰이 없는상태에서 로그인요청을 보내는데 토큰이 없다고 거절하면 안되니까
    if (config.url?.includes("/login")) {
      return config;
    }

    // 그 외 요청은 토큰 체크
    const token = localStorage.getItem("accessToken");
    if (token) {
      try {
        const { exp } = jwtDecode(token);
        if (exp * 1000 < Date.now()) {
          window.dispatchEvent(new CustomEvent("forceLogout")); //강제 로그아웃 이벤트함수를 수행시켜 발생시킨다.
          delete config.headers.Authorization;
          return config;
        }
      } catch {
        window.dispatchEvent(new CustomEvent("forceLogout"));
        return Promise.reject(new Error("Invalid token"));
      }
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  });

  // 응답 인터셉터 ---------------------------------------------------
  axiosAPI.interceptors.response.use(
    (response) => response,
    (error) => {
      const status = error.response?.status;
      const justLoggedOut = sessionStorage.getItem("justLoggedOut") === "yes";

      if (status === 401 || status === 403) {
        if (justLoggedOut) {
          sessionStorage.removeItem("justLoggedOut");
          return Promise.reject(error);
        }

        window.dispatchEvent(new CustomEvent("forceLogout"));
        toast.error("다른 PC의 로그인이 감지되어 로그아웃 되었습니다.");
        navigate("/login", { replace: true });
      }
      return Promise.reject(error);
    }
  );
}
========== 기존 코드 끝 ========== */
