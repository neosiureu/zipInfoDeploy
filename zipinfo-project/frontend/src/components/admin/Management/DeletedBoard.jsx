import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { axiosAPI } from "../../../api/axiosApi";
import { toast } from "react-toastify";
import DeletedBoardModal from "./DeletedBoardModal";
import "../../../css/neighborhood/NeighborhoodBoard.css"; // 기존 스타일 그대로 사용
import "../../../css/admin/Management/DeletedBoardModal.css";

export default function DeletedBoard({ initialDeletedBoards = [] }) {
  const [boardList, setBoardList] = useState(initialDeletedBoards);
  const [filteredBoards, setFilteredBoards] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [selectedBoard, setSelectedBoard] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const navigate = useNavigate();

  const boardsPerPage = 10;

  // 삭제된 게시글 목록 API 호출
  const fetchDeletedBoards = async () => {
    setLoading(true);
    try {
      const res = await axiosAPI.get("/admin/management/boards/deleted");
      setBoardList(res.data);
    } catch (error) {
      toast.error("삭제된 게시글 목록을 불러오는 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (initialDeletedBoards.length === 0) {
      fetchDeletedBoards();
    }
  }, [initialDeletedBoards]);

  useEffect(() => {
    setFilteredBoards(boardList);
    setCurrentPage(1);
  }, [boardList]);

  // 복구 함수
  const handleRestore = async (boardNo) => {
    if (!window.confirm("이 게시글을 복구하시겠습니까?")) return;

    setLoading(true);
    try {
      await axiosAPI.put(`/admin/management/boards/${boardNo}/restore`);
      toast.success("게시글이 복구되었습니다.");
      setBoardList((prev) => prev.filter((b) => b.boardNo !== boardNo));

      // 현재 페이지의 마지막 항목을 복구했고, 그 페이지가 비어있다면 이전 페이지로 이동
      const remainingBoards = boardList.filter((b) => b.boardNo !== boardNo);
      const maxPage = Math.ceil(remainingBoards.length / boardsPerPage);
      if (currentPage > maxPage && maxPage > 0) {
        setCurrentPage(maxPage);
      }
    } catch (error) {
      toast.error("복구 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  // 영구 삭제 함수
  const handlePermanentDelete = async (boardNo) => {
    if (
      !window.confirm(
        "이 게시글을 영구적으로 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다."
      )
    )
      return;

    setLoading(true);
    try {
      await axiosAPI.delete(`/admin/management/boards/${boardNo}/permanent`);
      toast.success("게시글이 영구적으로 삭제되었습니다.");
      setBoardList((prev) => prev.filter((b) => b.boardNo !== boardNo));

      // 현재 페이지의 마지막 항목을 삭제했고, 그 페이지가 비어있다면 이전 페이지로 이동
      const remainingBoards = boardList.filter((b) => b.boardNo !== boardNo);
      const maxPage = Math.ceil(remainingBoards.length / boardsPerPage);
      if (currentPage > maxPage && maxPage > 0) {
        setCurrentPage(maxPage);
      }
    } catch (error) {
      toast.error("삭제 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleBoardClick = async (item) => {
    try {
      setLoading(true);
      // 삭제된 게시글 상세 정보 조회
      const response = await axiosAPI.get(
        `/admin/management/boards/${item.boardNo}/detail`
      );
      if (response.data) {
        setSelectedBoard(response.data);
        setIsModalOpen(true);
      } else {
        toast.error("게시글 정보를 불러올 수 없습니다.");
      }
    } catch (error) {
      console.error("게시글 상세 조회 실패:", error);
      toast.error("게시글 상세 정보를 불러오는 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedBoard(null);
  };

  // 페이지네이션 계산
  const indexOfLastBoard = currentPage * boardsPerPage;
  const indexOfFirstBoard = indexOfLastBoard - boardsPerPage;
  const currentPageBoards = filteredBoards.slice(
    indexOfFirstBoard,
    indexOfLastBoard
  );
  const totalPages = Math.ceil(filteredBoards.length / boardsPerPage);

  if (loading) {
    return <div className="loading-indicator">로딩 중...</div>;
  }

  return (
    <div className="nb-container">
      <div className="nb-board-wrapper">
        <h1 className="nb-title">삭제된 게시글 목록</h1>

        <table className="nb-board-table">
          <thead>
            <tr className="nb-header">
              <th className="nb-header-number">번호</th>
              <th className="nb-header-subject">분류</th>
              <th className="nb-header-title">제목</th>
              <th className="nb-header-author">작성자</th>
              <th className="nb-header-date">날짜</th>
              <th className="nb-header-actions">복구/삭제</th>
            </tr>
          </thead>
          <tbody>
            {filteredBoards.length === 0 ? (
              <tr>
                <td
                  colSpan="6"
                  style={{ textAlign: "center", padding: "12px" }}
                >
                  삭제된 게시글이 없습니다.
                </td>
              </tr>
            ) : (
              currentPageBoards.map((item) => (
                <tr key={item.boardNo} className="nb-row">
                  <td className="nb-cell-number">{item.boardNo}</td>
                  <td className="nb-cell-subject">
                    {item.boardSubject === "Q"
                      ? "질문답변"
                      : item.boardSubject === "R"
                      ? "리뷰"
                      : "기타"}
                  </td>
                  <td
                    className="nb-cell-title"
                    onClick={() => handleBoardClick(item)}
                    style={{ cursor: "pointer" }}
                  >
                    {item.boardTitle}
                  </td>
                  <td className="nb-cell-author">{item.memberNickName}</td>
                  <td className="nb-cell-date">{item.boardWriteDate}</td>
                  <td className="nb-cell-actions">
                    <div
                      style={{
                        display: "flex",
                        flexDirection: "row",
                        gap: "4px",
                      }}
                    >
                      <button
                        className="restore-btn"
                        onClick={() => handleRestore(item.boardNo)}
                        style={{
                          padding: "4px 8px",
                          backgroundColor: "#3b82f6",
                          color: "white",
                          border: "none",
                          borderRadius: "4px",
                          cursor: "pointer",
                          fontSize: "12px",
                          minWidth: "50px",
                        }}
                      >
                        복구
                      </button>
                      <button
                        className="delete-btn"
                        onClick={() => handlePermanentDelete(item.boardNo)}
                        style={{
                          padding: "4px 8px",
                          backgroundColor: "#ef4444",
                          color: "white",
                          border: "none",
                          borderRadius: "4px",
                          cursor: "pointer",
                          fontSize: "12px",
                          minWidth: "50px",
                        }}
                      >
                        삭제
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <div
            className="pagination"
            style={{
              display: "flex",
              justifyContent: "center",
              gap: "8px",
              marginTop: "20px",
            }}
          >
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
              <button
                key={page}
                onClick={() => setCurrentPage(page)}
                style={{
                  padding: "8px 12px",
                  border: page === currentPage ? "none" : "1px solid #ddd",
                  backgroundColor: page === currentPage ? "#007bff" : "white",
                  color: page === currentPage ? "white" : "#333",
                  borderRadius: "4px",
                  cursor: "pointer",
                  fontSize: "14px",
                }}
              >
                {page}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 삭제된 게시글 상세 모달 */}
      <DeletedBoardModal
        board={selectedBoard}
        isOpen={isModalOpen}
        onClose={handleCloseModal}
      />
    </div>
  );
}
