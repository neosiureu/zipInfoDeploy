import axios from "axios";
const KAKAO_REST_API_KEY = import.meta.env.VITE_KAKAO_REST_API_KEY;

export const axiosAPI = axios.create({
  baseURL: "http://localhost:8080",
  withCredentials: true,
  headers: { "Content-Type": "application/json" }, // axios는 자동 변환해주기 때문에 명시 안해도 괜찮다
  // withCredentials : true 쿠키 포함 설정
  // 서버에서도 클라이언트가 보낸 쿠키를 받아줄 준비 필요
  // credential 허용 설정 필요함
  // -> JWT 사용 시 중요한 옵션
});

axiosAPI.interceptors.request.use((config) => {
  if (config.url?.startsWith("https://dapi.kakao.com/")) {
    delete config.headers.Authorization;
    config.withCredentials = false;
    config.headers.Authorization = `KakaoAK ${KAKAO_REST_API_KEY}`;
    return config;
  }
  const token = localStorage.getItem("accessToken");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
axiosAPI.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    // 401 또는 403 응답 시 자동 로그아웃 이벤트 발생
    if (error.response?.status === 401 || error.response?.status === 403) {
      window.dispatchEvent(new CustomEvent("forceLogout"));
    }
    return Promise.reject(error);
  }
);
