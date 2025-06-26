import axios from "axios";

const BASE_URL = "/api/board/announce"; // vite proxy 사용 시 상대경로만

/** 공지사항 목록 조회 */
export const fetchPosts = async (page = 0, size = 10, keyword = "") => {
  try {
    const cp = page + 1;
    const params = { cp, size };
    if (keyword && keyword.trim() !== "") {
      params.key = "tc";
      params.query = keyword.trim();
    }
    const response = await axios.get(BASE_URL, { params });
    return response.data;
  } catch (error) {
    console.error("공지사항 목록 조회 실패", error);
    throw error;
  }
};

/** 공지사항 상세 조회 */
export const fetchPostDetail = async (postId) => {
  try {
    const response = await axios.get(`${BASE_URL}/${postId}`);
    return response.data;
  } catch (error) {
    console.error("공지사항 상세 조회 실패", error);
    throw error;
  }
};

/** 공지사항 등록 */
export const createPost = async (postData) => {
  try {
    const response = await axios.post(BASE_URL, postData);
    return response.data;
  } catch (error) {
    console.error("공지사항 등록 실패", error);
    throw error;
  }
};

/** 공지사항 수정 */
export const updatePost = async (postId, postData) => {
  try {
    const response = await axios.put(`${BASE_URL}/${postId}`, postData);
    return response.data;
  } catch (error) {
    console.error("공지사항 수정 실패", error);
    throw error;
  }
};

/** 공지사항 삭제 */
export const deletePost = async (postId) => {
  try {
    const response = await axios.delete(`${BASE_URL}/${postId}`);
    return response.data;
  } catch (error) {
    console.error("공지사항 삭제 실패", error);
    throw error;
  }
};
