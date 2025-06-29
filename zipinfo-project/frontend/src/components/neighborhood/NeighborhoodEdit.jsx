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

  console.log(" NeighborhoodEdit 렌더링:", { boardNo, isEdit, loading });

  useEffect(() => {
    if (isEdit) {
      console.log(" 수정 모드: 기존 데이터 로딩 시작");
      setLoading(true);
      axiosAPI
        .get("board/neighborhoodDetail", { params: { boardNo } })
        .then(({ data }) => {
          console.log(" 데이터 로딩 완료:", data);
          setTitle(data.boardTitle);
          setContent(data.boardContent);
        })
        .catch((error) => {
          console.error(" 데이터 로딩 오류:", error);
          alert("게시글을 불러오는 중 오류가 발생했습니다.");
        })
        .finally(() => {
          console.log(" 데이터 로딩 완료");
          setLoading(false);
        });
    } else {
      console.log(" 새 글 작성 모드");
    }
  }, [boardNo, isEdit]);

  const handleSubmit = async (e) => {
    e.preventDefault();

    // 기본 유효성 검사
    if (!title.trim()) {
      alert("제목을 입력해주세요.");
      return;
    }

    if (!content.trim() || content.trim() === "<p><br></p>") {
      alert("내용을 입력해주세요.");
      return;
    }

    console.log(" 저장 시작:", {
      title: title.trim(),
      contentLength: content.length,
    });

    try {
      setLoading(true);

      if (isEdit) {
        console.log(" 수정 요청");
        await axiosAPI.post("/board/neighborhoodUpdate", {
          boardNo,
          boardTitle: title.trim(),
          boardContent: content,
        });
      } else {
        console.log(" 새 글 작성 요청");
        await axiosAPI.post("/board/neighborhoodWrite", {
          boardTitle: title.trim(),
          boardContent: content,
        });
      }

      console.log(" 저장 완료, 목록으로 이동");
      navigate(`/neighborhoodBoard?cp=${cp}`);
    } catch (error) {
      console.error(" 저장 오류:", error);
      alert("저장 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    navigate(`/neighborhoodBoard?cp=${cp}`);
  };

  return (
    <div style={{ maxWidth: "800px", margin: "0 auto", padding: "20px" }}>
      <h2 style={{ marginBottom: "30px" }}>
        {isEdit ? "수정페이지" : "작성페이지"}
      </h2>

      <form onSubmit={handleSubmit}>
        {/* 제목 입력 */}
        <div style={{ marginBottom: "20px" }}>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="제목을 입력하세요"
            disabled={loading}
            required
            style={{
              width: "100%",
              padding: "12px 16px",
              fontSize: "16px",
              border: "1px solid #ddd",
              borderRadius: "4px",
              outline: "none",
            }}
          />
        </div>

        {/* 내용 에디터 */}
        <div style={{ marginBottom: "30px" }}>
          <SummernoteEditor
            value={content}
            onChange={setContent}
            disabled={loading}
          />
        </div>

        {/* 버튼 영역 */}
        <div
          style={{ display: "flex", gap: "10px", justifyContent: "flex-end" }}
        >
          <button
            type="button"
            onClick={handleCancel}
            disabled={loading}
            style={{
              padding: "10px 20px",
              fontSize: "14px",
              border: "1px solid #ddd",
              borderRadius: "4px",
              backgroundColor: "#f8f9fa",
              cursor: loading ? "not-allowed" : "pointer",
            }}
          >
            취소
          </button>

          <button
            type="submit"
            disabled={loading}
            style={{
              padding: "10px 20px",
              fontSize: "14px",
              border: "none",
              borderRadius: "4px",
              backgroundColor: loading ? "#ccc" : "#007bff",
              color: "white",
              cursor: loading ? "not-allowed" : "pointer",
            }}
          >
            {loading ? "저장 중..." : isEdit ? "수정 완료" : "등록"}
          </button>
        </div>
      </form>
    </div>
  );
};

export default NeighborhoodEdit;
