// AnnounceApi.js (또는 비슷한 이름의 파일)
import axios from "axios";

const BASE_URL = "http://localhost:8080";

export const createPost = async (postData) => {
  try {
    const response = await axios.post(
      `${BASE_URL}/api/board/announce`, // <-- 여기를 반드시 맞춰야 합니다.
      postData,
      { withCredentials: true }
    );
    return response.data;
  } catch (error) {
    console.error("공지사항 등록 실패", error);
    throw new Error("공지사항 등록 실패");
  }
};
