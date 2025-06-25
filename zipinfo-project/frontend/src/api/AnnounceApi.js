// AnnounceApi.js
import axios from "axios";

const BASE_URL = "http://localhost:8080";

// 공지사항 목록 조회 API (페이징 + 검색어)
export const fetchPosts = async (page = 0, size = 10, keyword = "") => {
  try {
    const cp = page + 1;
    const params = { cp };

    if (keyword && keyword.trim() !== "") {
      params.key = "tc"; // 제목+내용 검색 예시
      params.query = keyword.trim();
    }

    const response = await axios.get(`${BASE_URL}/api/board/announce`, {
      params,
      withCredentials: true,
    });

    return response.data;
  } catch (error) {
    console.error("공지사항 목록 조회 실패", error);
    throw error;
  }
};

// 공지사항 상세 조회 API
export const fetchPostDetail = async (postId) => {
  try {
    const response = await axios.get(
      `${BASE_URL}/api/board/announce/${postId}`,
      { withCredentials: true }
    );
    return response.data;
  } catch (error) {
    console.error("공지사항 상세 조회 실패", error);
    throw error;
  }
};

// 공지사항 등록 API
export const createPost = async (postData) => {
  try {
    const response = await axios.post(
      `${BASE_URL}/api/board/announce`,
      postData,
      { withCredentials: true }
    );
    return response.data;
  } catch (error) {
    console.error("공지사항 등록 실패", error);
    throw error;
  }
};

// 공지사항 수정 API
export const updatePost = async (postId, postData) => {
  try {
    const response = await axios.put(
      `${BASE_URL}/api/board/announce/${postId}`,
      postData,
      { withCredentials: true }
    );
    return response.data;
  } catch (error) {
    console.error("공지사항 수정 실패", error);
    throw error;
  }
};
