// src/components/announce/AnnounceApi.js

// 공지사항 게시판 subject를 고정 설정
const boardSubject = "announce";
const BACKEND_URL = "http://localhost:8080";

/**
 * 공지사항 목록 조회 (검색 + 페이징)
 *
 * 📌 예시 요청 URL: /api/board/announce?cp=1&key=title&query=검색어
 *
 * @param {number} page - 현재 페이지 번호 (0부터 시작)
 * @param {number} size - 한 페이지당 게시글 수 (백엔드에서 사용 시 포함)
 * @param {string} keyword - 제목 기준 검색어
 * @returns {Promise<Object>} 게시글 목록 및 페이징 정보 객체 반환
 */
export const fetchPosts = async (page = 0, size = 10, keyword = "") => {
  const params = new URLSearchParams();
  params.append("cp", page + 1);
  if (keyword) {
    params.append("key", "title");
    params.append("query", keyword);
  }

  const token = localStorage.getItem("accessToken");

  const url = `${BACKEND_URL}/api/board/announce?${params.toString()}`;
  const res = await fetch(url, {
    headers: {
      Authorization: token ? `Bearer ${token}` : "",
      "Content-Type": "application/json",
    },
    credentials: "include",
  });

  const text = await res.text();

  try {
    const data = JSON.parse(text);
    if (!res.ok) {
      throw new Error("공지사항 목록을 불러오지 못했습니다.");
    }
    return data;
  } catch (e) {
    console.error("서버 응답이 JSON이 아닙니다:", text);
    throw e;
  }
};

/**
 * 공지사항 단일 게시글 조회
 *
 * 📌 예시 요청 URL: /api/board/announce/detail/123
 *
 * @param {number|string} id - 게시글 ID 또는 번호
 * @returns {Promise<Object>} 게시글 상세 정보 객체 반환
 */
export const fetchPostById = async (id) => {
  const token = localStorage.getItem("accessToken");

  const res = await fetch(
    `${BACKEND_URL}/api/board/${boardSubject}/detail/${id}`,
    {
      headers: {
        Authorization: token ? `Bearer ${token}` : "",
        "Content-Type": "application/json",
      },
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
 *
 * 📌 예시 요청 URL: DELETE /api/board/announce/123
 *
 * @param {number|string} id - 삭제할 게시글 ID
 * @returns {Promise<void>} 성공 시 반환 값 없음
 */
export const deletePost = async (id) => {
  const token = localStorage.getItem("accessToken");

  const res = await fetch(`${BACKEND_URL}/api/board/${boardSubject}/${id}`, {
    method: "DELETE",
    headers: {
      Authorization: token ? `Bearer ${token}` : "",
    },
    credentials: "include",
  });

  if (!res.ok) {
    throw new Error("공지사항 삭제 실패");
  }
};

/**
 * 공지사항 게시글 수정 (이미지 포함 가능)
 *
 * 📌 예시 요청 URL: PUT /api/board/announce/123
 *
 * @param {number|string} id - 수정할 게시글 ID
 * @param {FormData} formData - 수정할 데이터 (title, content, images 배열 등 포함)
 * @returns {Promise<void>} 성공 시 반환 값 없음
 */
export const updatePostWithImage = async (id, formData) => {
  const token = localStorage.getItem("accessToken");

  const res = await fetch(`${BACKEND_URL}/api/board/${boardSubject}/${id}`, {
    method: "PUT",
    headers: {
      Authorization: token ? `Bearer ${token}` : "",
      // Content-Type 생략, fetch가 자동으로 multipart/form-data 헤더 설정
    },
    body: formData,
    credentials: "include",
  });

  if (!res.ok) {
    throw new Error("공지사항 수정 실패");
  }
};

/**
 * 공지사항 게시글 등록 (이미지 포함 가능)
 *
 * 📌 예시 요청 URL: POST /api/board/announce
 *
 * @param {FormData} formData - 등록할 게시글 데이터 (title, content, images 배열 등 포함)
 * @returns {Promise<Object>} 등록된 게시글 정보 JSON 반환 (예: { id: 123, message: "성공" })
 */
export const createPostWithImage = async (formData) => {
  const token = localStorage.getItem("accessToken");

  const res = await fetch(`${BACKEND_URL}/api/board/${boardSubject}`, {
    method: "POST",
    headers: {
      Authorization: token ? `Bearer ${token}` : "",
      // Content-Type 생략, fetch가 자동으로 multipart/form-data 헤더 설정
    },
    body: formData,
    credentials: "include",
  });

  if (!res.ok) {
    throw new Error("공지사항 등록 실패");
  }
  return res.json();
};
