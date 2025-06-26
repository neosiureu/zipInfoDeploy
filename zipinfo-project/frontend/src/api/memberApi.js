import axios from "axios";

const BASE_URL = "/member"; // Vite 프록시 설정에 맞춰 /member로 설정

/**
 * 로그인 함수
 * @param {string} email - 회원 이메일
 * @param {string} password - 회원 비밀번호
 * @returns {Promise<Object>} 로그인 성공 시 회원 정보 반환
 */
export const login = async (email, password) => {
  try {
    const response = await axios.post(
      `${BASE_URL}/login`,
      { memberEmail: email, memberPw: password },
      { withCredentials: true } // 쿠키(세션) 전달을 위해 반드시 필요
    );
    return response.data;
  } catch (error) {
    console.error("로그인 실패", error);
    throw error;
  }
};

/**
 * 세션 확인 함수
 * @returns {Promise<Object|null>} 로그인된 회원 정보 또는 null 반환
 */
export const checkSession = async () => {
  try {
    const response = await axios.get(`${BASE_URL}/getMember`, {
      withCredentials: true, // 쿠키를 전달해서 세션 유지 확인
    });
    return response.data;
  } catch (error) {
    console.error("세션 확인 실패", error);
    throw error;
  }
};

/**
 * 로그아웃 함수
 * @returns {Promise<Object>} 로그아웃 성공 메시지 등 반환
 */
export const logout = async () => {
  try {
    const response = await axios.post(
      `${BASE_URL}/logout`,
      {},
      { withCredentials: true } // 쿠키 전달
    );
    return response.data;
  } catch (error) {
    console.error("로그아웃 실패", error);
    throw error;
  }
};
