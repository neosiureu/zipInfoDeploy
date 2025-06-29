import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { axiosAPI } from "../../api/axiosApi";
import SummernoteEditor from "./SummernoteEditor";
import { useEffect, useState } from "react";
const NeighborhoodEdit = () => {
  const { boardNo } = useParams();
  const [searchParams] = useSearchParams();
  const cp = Number(searchParams.get("cp") ?? 1);

  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const isEdit = Boolean(boardNo);

  useEffect(() => {
    if (isEdit) {
      setLoading(true);
      axiosAPI
        .get("board/neighborhoodDetail", { params: { boardNo } })
        .then(({ data }) => {
          setTitle(data.boardTitle);
          setContent(data.boardContent);
        })
        .finally(() => setLoading(false));
    }
  }, [boardNo, isEdit]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      if (isEdit) {
        await axiosAPI.post("/board/neighborhoodUpdate", {
          boardNo,
          boardTitle: title,
          boardContent: content,
        });
      } else {
        await axiosAPI.post("/board/neighborhoodWrite", {
          boardTitle: title,
          boardContent: content,
        });
      }
      navigate(`/neighborhoodBoard?cp=${cp}`);
    } catch {
      alert("저장 중 오류 발생");
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        type="text"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="제목"
        disabled={loading}
        required
      />
      <div style={{ margin: "1rem 0" }}>
        <SummernoteEditor
          value={content}
          onChange={setContent}
          disabled={loading}
        />
      </div>
      <button type="submit" disabled={loading}>
        {isEdit ? "수정 완료" : "등록"}
      </button>
      <button
        type="button"
        onClick={() => navigate(`/neighborhoodBoard?cp=${cp}`)}
        disabled={loading}
      >
        취소
      </button>
    </form>
  );
};

export default NeighborhoodEdit;
