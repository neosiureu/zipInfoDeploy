import axios from "axios";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

/**
 * 게시글 목록 조회 (GET)
 * @param {number} page 페이지 번호
 * @param {number} size 페이지 크기
 * @param {string} keyword 검색 키워드
 * @param {object} filters 필터링 옵션 (city, district, category 등)
 * @returns 게시글 리스트 Promise
 */
export const fetchPosts = (page, size, keyword, filters) => {
  const params = { page, size, keyword, ...filters };
  return axios
    .get(`${API_BASE_URL}/neighborhood/posts`, { params })
    .then((res) => res.data);
};

/**
 * 게시글 상세 조회 (GET)
 * @param {number} id 게시글 번호
 * @returns 게시글 데이터 Promise
 */
export const fetchPostById = (id) => {
  return axios
    .get(`${API_BASE_URL}/neighborhood/${id}`)
    .then((res) => res.data);
};

/**
 * 게시글 작성 (POST) - JSON 형식
 * @param {object} postData 게시글 데이터 (title, content, category 등)
 * @returns 서버 응답 Promise
 */
export const createPost = (postData) => {
  return axios.post(`${API_BASE_URL}/neighborhood`, postData);
};

/**
 * 게시글 수정 (PUT)
 * @param {number} id 게시글 번호
 * @param {object} postData 수정할 게시글 데이터
 * @returns 서버 응답 Promise
 */
export const updatePost = (id, postData) => {
  return axios.put(`${API_BASE_URL}/neighborhood/${id}`, postData);
};

/**
 * 게시글 삭제 (DELETE)
 * @param {number} id 게시글 번호
 * @returns 서버 응답 Promise
 */
export const deletePost = (id) => {
  return axios.delete(`${API_BASE_URL}/neighborhood/${id}`);
};

/**
 * 게시글 작성 (POST) - 파일 업로드 포함 (multipart/form-data)
 * @param {FormData} formData FormData 객체 (JSON 데이터 + 파일 포함)
 * @returns 서버 응답 Promise
 */
export const createPostWithFile = (formData) => {
  return axios.post(`${API_BASE_URL}/neighborhood`, formData, {
    // axios가 multipart/form-data boundary 헤더 자동 설정해주므로 생략 가능
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
};
