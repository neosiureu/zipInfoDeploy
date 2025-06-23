// AnnounceApi.js
import axios from "axios";

const BASE_URL = "http://localhost:8080";

export const fetchPosts = async (page, size, keyword) => {
  const url = encodeURI(`${BASE_URL}/api/board/공지사항`);
  const response = await axios.get(url, {
    params: { cp: page + 1, key: "title", query: keyword },
  });
  return response.data;
};
