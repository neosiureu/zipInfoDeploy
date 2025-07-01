import {
  useNavigate,
  useParams,
  useSearchParams,
  useLocation,
} from "react-router-dom";
import { axiosAPI } from "../../api/axiosApi";
import SummernoteEditor from "./SummernoteEditor";
import { useEffect, useState } from "react";
import NeighborhoodFilters from "./NeighborhoodFilters";

const NeighborhoodEdit = () => {
  const location = useLocation();
  const { cityNo, townNo, boardSubject } = location.state || {};
  // 시도 선택 핸들러
  const handleCityChange = (e) => {
    setSelectedCity(e.target.value);
    setSelectedTown(-1); // 시도 변경시 시군구 초기화
  };

  // 시군구 선택 핸들러
  const handleTownChange = (e) => {
    setSelectedTown(e.target.value);
  };

  // 주제 선택 핸들러
  const handleSubjectChange = (e) => {
    setSelectedSubject(e.target.value);
  };
  const { boardNo } = useParams();
  const [searchParams] = useSearchParams();
  const cp = Number(searchParams.get("cp") ?? 1);

  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const isEdit = Boolean(boardNo);
  const [selectedCity, setSelectedCity] = useState(
    cityNo == undefined ? -1 : cityNo
  ); // 선택된 시도 (e.target)
  const [selectedTown, setSelectedTown] = useState(
    townNo == undefined ? -1 : townNo
  ); // 선택된 시군구 (e.target)
  const [selectedSubject, setSelectedSubject] = useState(
    boardSubject !== undefined ? boardSubject : -1
  );
  const [searchKey, setSearchKey] = useState("t"); // 일단 t만하고 c는 내용 tc는 제목+내용 w는 작성자로 확장하자.
  const [searchQuery, setSearchQuery] = useState(""); //실제로 검색될 대상

  useEffect(() => {
    if (isEdit) {
      setLoading(true);
      axiosAPI
        .get("board/neighborhoodDetail", { params: { boardNo } })
        .then(({ data }) => {
          setTitle(data.boardTitle);
          setContent(data.boardContent);
        })
        .catch((error) => {
          alert("게시글을 불러오는 중 오류가 발생했습니다.");
        })
        .finally(() => {
          setLoading(false);
        });
    } else {
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
    // 선택값 검증: -1이면 미선택 상태이므로 막기
    if (selectedCity === -1) {
      alert("시/도를 선택해주세요.");
      return;
    }
    if (selectedTown === -1) {
      alert("시/군/구를 선택해주세요.");
      return;
    }
    if (selectedSubject === -1) {
      alert("주제를 선택해주세요.");
      return;
    }

    try {
      setLoading(true);

      if (isEdit) {
        const params = {
          boardNo: boardNo,
          boardTitle: title.trim(),
          boardContent: content,
          cityNo: selectedCity, // 시도 코드 (숫자)
          townNo: selectedTown, // 시군구 코드 (숫자)
          boardSubject: selectedSubject, // 주제 코드 (QRE중 하나)
        };
        const { data: result } = await axiosAPI.put("/editBoard", params);

        if (result > 0) {
          alert("글이 수정되었습니다");
          navigate(`/neighborhoodBoard/detail/${boardNo}?cp=${cp}`);
        } else {
          alert("글 수정 실패. 본인의 게시글이 아닙니다!");
          return; // 실패시 페이지 이동 막기
        }
      } else {
        const params = {
          boardTitle: title.trim(),
          boardContent: content,
          cityNo: selectedCity, // 시도 코드 (숫자)
          townNo: selectedTown, // 시군구 코드 (숫자)
          boardSubject: selectedSubject, // 주제 코드 (QRE중 하나)
        };
        const { data: result } = await axiosAPI.post("/editBoard", params);

        if (result > 0) {
          alert("글이 등록되었습니다");
          navigate(`/neighborhoodBoard?cp=${cp}`); // 성공시에 리스트 페이지로 이동
        } else {
          alert("글 등록 실패");
          return; // 실패시  중단
        }
      }
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
    <div
      style={{
        maxWidth: "1000px",
        margin: "0 auto",
        padding: "20px",
      }}
    >
      <h2 style={{ marginBottom: "30px" }}>
        {isEdit ? "수정페이지" : "작성페이지"}
      </h2>

      <NeighborhoodFilters
        selectedCity={selectedCity}
        selectedTown={selectedTown}
        selectedSubject={selectedSubject}
        onCityChange={handleCityChange}
        onTownChange={handleTownChange}
        onSubjectChange={handleSubjectChange}
      />
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
              width: "1000px",
              padding: "12px 16px",
              fontSize: "16px",
              border: "1px solid #ddd",
              borderRadius: "4px",
              outline: "none",
              boxSizing: "border-box",
            }}
          />
        </div>

        {/* 내용 에디터 */}
        <div style={{ marginBottom: "20px" }}>
          <SummernoteEditor
            value={content}
            onChange={setContent}
            disabled={loading}
          />
        </div>

        {/* 버튼 영역 - 에디터 바로 아래 오른쪽 정렬 */}
        <div
          style={{
            display: "flex",
            gap: "10px",
            justifyContent: "flex-end",
            width: "1000px", // 에디터와 같은 너비로 맞춤
          }}
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
            {loading ? "저장 중..." : isEdit ? "수정 완료" : "글 등록"}
          </button>
        </div>
      </form>
    </div>
  );
};

export default NeighborhoodEdit;
