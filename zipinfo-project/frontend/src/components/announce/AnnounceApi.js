// src/api/AnnounceApi.js

const boardSubject = "announce";
const BACKEND_URL = "http://localhost:8080";

/**
 * 공지사항 목록 조회 (검색 + 페이징)
 * @param {number} page - 현재 페이지 (0부터 시작)
 * @param {number} size - 페이지당 게시글 수 (기본값 10)
 * @param {string} keyword - 검색어 (선택)
 * @returns {Promise<Object>} 공지사항 목록 및 페이지 정보
 */
export const fetchPosts = async (page = 0, size = 10, keyword = "") => {
  const params = new URLSearchParams();
  params.append("cp", page + 1); // 페이지 번호 (1부터 시작)
  if (keyword) {
    params.append("key", "title"); // 검색 키 (제목 기준)
    params.append("query", keyword); // 검색어
  }

  const headers = {
    "Content-Type": "application/json",
  };

  const url = `${BACKEND_URL}/api/board/${boardSubject}?${params.toString()}`;
  const res = await fetch(url, {
    headers,
    credentials: "include", // 쿠키(세션) 포함
  });

  const text = await res.text();

  if (!res.ok) {
    console.error("서버 에러 응답:", text);
    throw new Error(`공지사항 목록 불러오기 실패 (status: ${res.status})`);
  }

  try {
    const data = JSON.parse(text);
    return data;
  } catch (e) {
    console.error("서버 응답이 JSON이 아닙니다:", text);
    throw e;
  }
};

/**
 * 공지사항 단일 게시글 조회
 * @param {number} id - 공지사항 번호
 * @returns {Promise<Object>} 공지사항 상세 정보
 */
export const fetchPostById = async (id) => {
  const headers = {
    "Content-Type": "application/json",
  };

  const res = await fetch(
    `${BACKEND_URL}/api/board/${boardSubject}/detail/${id}`,
    {
      headers,
      credentials: "include",
    }
  );

  if (!res.ok) {
    throw new Error("공지사항을 불러오지 못했습니다.");
  }
  return res.json();
};

/**
 * 공지사항 게시글 삭제 요청
 * @param {number} id - 삭제할 공지사항 번호
 * @returns {Promise<void>}
 */
export const deletePost = async (id) => {
  const headers = {};

  const res = await fetch(`${BACKEND_URL}/api/board/${boardSubject}/${id}`, {
    method: "DELETE",
    headers,
    credentials: "include",
  });

  if (!res.ok) {
    throw new Error("공지사항 삭제 실패");
  }
};

/**
 * 공지사항 게시글 수정 (이미지 제외, JSON 방식)
 * @param {number} id - 수정할 공지사항 번호
 * @param {Object} postData - 수정할 게시글 데이터 (announceTitle, announce 등)
 * @returns {Promise<void>}
 */
export const updatePost = async (id, postData) => {
  const res = await fetch(`${BACKEND_URL}/api/board/${boardSubject}/${id}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(postData),
  });

  if (!res.ok) {
    throw new Error("공지사항 수정 실패");
  }
};

/**
 * 공지사항 게시글 등록 (이미지 제외, JSON 방식)
 * @param {Object} postData - 등록할 게시글 데이터 (announceTitle, announce 등)
 * @returns {Promise<Object>} 등록된 공지사항 정보 (announceNo 등)
 */
export const createPost = async (postData) => {
  const res = await fetch(`${BACKEND_URL}/api/board/${boardSubject}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(postData),
  });

  if (!res.ok) {
    throw new Error("공지사항 등록 실패");
  }
  return res.json();
};
