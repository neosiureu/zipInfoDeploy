import axios from "axios";

export const fetchMembers = async () => {
  try {
    const response = await axios.get(
      "http://localhost:8080/admin/management/members",
      { withCredentials: true }
    );
    return response.data;
  } catch (error) {
    console.error("회원 목록 가져오기 실패:", error);
    throw error;
  }
};
