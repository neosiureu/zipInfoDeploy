// src/components/notice/noticeApi.js
// 공지사항 전체 목록 가져오기
export const fetchPosts = async () => {
  const res = await fetch("/api/notice");
  if (!res.ok) {
    throw new Error("공지사항 목록을 불러오지 못했습니다.");
  }
  return res.json();
};
// 공지사항 상세 조회
export const fetchPostById = async (id) => {
  const res = await fetch(`/api/notice/${id}`);
  if (!res.ok) throw new Error("공지사항을 불러오지 못했습니다");
  return res.json();
};

// 공지사항 삭제
export const deletePost = async (id) => {
  const res = await fetch(`/api/notice/${id}`, {
    method: "DELETE",
  });
  if (!res.ok) throw new Error("공지사항 삭제 실패");
};
