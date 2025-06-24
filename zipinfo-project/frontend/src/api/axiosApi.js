import axios from "axios";

export const axiosAPI = axios.create({
  baseURL: "http://localhost:8080",
  withCredentials: true,
  // withCredentials : true 쿠키 포함 설정
  // 서버에서도 클라이언트가 보낸 쿠키를 받아줄 준비 필요
  // credential 허용 설정 필요함
  // -> JWT 사용 시 중요한 옵션
});
