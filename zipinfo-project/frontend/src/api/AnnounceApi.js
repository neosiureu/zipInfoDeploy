import { axiosAPI } from "./axiosApi";

// 기본 API 주소를 상수로 선언
const BASE_URL = "/api/announce";

/**
 * 공지사항 목록 조회 (페이징 및 검색어 포함)
 * @param {number} page - 현재 페이지 번호 (0부터 시작)
 * @param {number} size - 한 페이지당 게시글 수 (기본 10)
 * @param {string} keyword - 검색어 (없으면 전체 조회)
 * @returns {Promise<Array>} 공지사항 게시글 리스트를 반환하는 Promise
 */
// fetchPosts 함수 내부에서
export const fetchPosts = async (page = 0, size = 10, keyword = "") => {
  try {
    const cp = page + 1;
    const params = { cp, size };
    if (keyword && keyword.trim() !== "") {
      params.key = "tc";
      params.query = keyword.trim();
    }
    const response = await axiosAPI.get(BASE_URL, {
      params,
      withCredentials: true,
    });
    return response.data;
  } catch (error) {
    throw error;
  }
};

/**
 * 특정 공지사항 상세 조회
 * @param {number|string} postId - 상세 조회할 공지사항 게시글 번호
 * @returns {Promise<Object>} 공지사항 상세 데이터 반환 Promise
 */
export const fetchPostDetail = async (postId) => {
  try {
    // 게시글 번호를 URL에 포함시켜 GET 요청
    const response = await axiosAPI.get(`${BASE_URL}/${postId}`, {
      withCredentials: true, // 쿠키 포함
    });
    return response.data;
  } catch (error) {
    throw error;
  }
};

/**
 * 공지사항 등록
 * @param {Object} postData - 등록할 게시글 데이터 객체
 * @returns {Promise<Object>} 등록 성공 시 서버 응답 반환 Promise
 */
export const createPost = async (postData) => {
  try {
    const response = await axiosAPI.post("/api/announce/write", postData, {
      withCredentials: true,
    });
    return response.data;
  } catch (error) {
    throw error;
  }
};

/**
 * 공지사항 수정
 * @param {number|string} postId - 수정할 게시글 번호
 * @param {Object} postData - 수정할 게시글 데이터 객체
 * @returns {Promise<Object>} 수정 성공 시 서버 응답 반환 Promise
 */
export const updatePost = async (postId, postData) => {
  try {
    // ✅ 수정된 URL 경로 반영
    const response = await axiosAPI.put(
      `/api/announce/edit/${postId}`, // 👈 변경된 edit 경로
      postData,
      { withCredentials: true }
    );
    return response.data;
  } catch (error) {
    throw error;
  }
};

/**
 * 공지사항 삭제 (논리 삭제)
 * @param {number|string} postId - 삭제할 게시글 번호
 * @returns {Promise<Object>} 삭제 성공 시 서버 응답 반환 Promise
 */
export const deletePost = async (postId) => {
  try {
    // DELETE 요청으로 공지사항 삭제, 쿠키 포함
    const response = await axiosAPI.post(
      `/api/announce/detail/delete`,
      { announceNo: postId },
      {
        withCredentials: true,
      }
    );
    return response.data;
  } catch (error) {
    throw error;
  }
};

/*
 * 이미지 등록
 */
export const uploadImage = async (imageFile) => {
  const formData = new FormData();
  formData.append("image", imageFile);
  try {
    const response = await axiosAPI.post(`${BASE_URL}/uploadImage`, formData, {
      headers: { "Content-Type": "multipart/form-data" },
      withCredentials: true,
    });
    return response.data; // 이미지 URL 문자열
  } catch (error) {
    throw error;
  }
};
