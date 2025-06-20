// src/components/announce/AnnounceApi.js

// 게시판 주제는 '공지사항'으로 고정 (백엔드에서 boardSubject로 처리)
const boardSubject = "announce";

/**
 * 공지사항 목록 조회 (페이징 + 검색 포함)
 * @param {number} page - 현재 페이지 (0부터 시작)
 * @param {number} size - 한 페이지당 게시글 수
 * @param {string} keyword - 검색어 (제목 기준)
 * @returns 게시글 목록 및 페이징 정보
 */
export const fetchPosts = async (page = 0, size = 10, keyword = "") => {
  const params = new URLSearchParams();
  params.append("cp", page + 1); // 백엔드 페이지는 1부터 시작

  if (keyword) {
    params.append("key", "title"); // key: 제목 기준 검색
    params.append("query", keyword);
  }

  const res = await fetch(`/api/board/${boardSubject}?${params.toString()}`);

  if (!res.ok) {
    throw new Error("공지사항 목록을 불러오지 못했습니다.");
  }

  return res.json(); // List<Board> 또는 페이징 포맷
};

/**
 * 공지사항 상세 조회
 * @param {number} id - 게시글 번호
 * @returns 단일 게시글 데이터
 */
export const fetchPostById = async (id) => {
  const res = await fetch(`/api/board/${boardSubject}/detail/${id}`);

  if (!res.ok) {
    throw new Error("공지사항을 불러오지 못했습니다.");
  }

  return res.json();
};

/**
 * 공지사항 삭제
 * @param {number} id - 게시글 번호
 */
export const deletePost = async (id) => {
  const res = await fetch(`/api/board/${boardSubject}/${id}`, {
    method: "DELETE",
  });

  if (!res.ok) {
    throw new Error("공지사항 삭제 실패");
  }
};

/**
 * 공지사항 수정 (이미지 포함 가능)
 * @param {number} id - 게시글 번호
 * @param {FormData} formData - 수정할 게시글 데이터 (multipart/form-data)
 */
export const updatePostWithImage = async (id, formData) => {
  const res = await fetch(`/api/board/${boardSubject}/${id}`, {
    method: "PUT",
    body: formData,
  });

  if (!res.ok) {
    throw new Error("공지사항 수정 실패");
  }
};

/**
 * 공지사항 등록 (이미지 포함 가능)
 * @param {FormData} formData - 등록할 게시글 데이터 (multipart/form-data)
 * @returns {Object} 등록 결과 응답 (성공 메시지, ID 등)
 */
export const createPostWithImage = async (formData) => {
  const res = await fetch(`/api/board/${boardSubject}`, {
    method: "POST",
    body: formData,
  });

  if (!res.ok) {
    throw new Error("공지사항 등록 실패");
  }

  return res.json();
};
