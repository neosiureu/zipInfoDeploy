import React from "react";

const Pagination = ({ currentPage, totalPages, onPageChange }) => {
  if (totalPages === 0) return null;

  const handlePrev = () => {
    if (currentPage > 1) onPageChange(currentPage - 1);
  };

  const handleNext = () => {
    if (currentPage < totalPages) onPageChange(currentPage + 1);
  };

  // 페이지 버튼 목록 생성 (1부터 totalPages까지)
  const pages = [];
  for (let i = 1; i <= totalPages; i++) {
    pages.push(
      <button
        key={i}
        onClick={() => onPageChange(i)}
        style={{
          margin: "0 5px",
          padding: "6px 12px",
          border: "1px solid #912d2d",
          backgroundColor: currentPage === i ? "#912d2d" : "white",
          color: currentPage === i ? "white" : "#912d2d",
          borderRadius: "4px",
          cursor: "pointer",
          fontWeight: currentPage === i ? "bold" : "normal",
        }}
        aria-current={currentPage === i ? "page" : undefined}
      >
        {i}
      </button>
    );
  }

  return (
    <div
      style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        marginTop: "20px",
        fontFamily: "'Pretendard', sans-serif",
      }}
    >
      <button
        onClick={handlePrev}
        disabled={currentPage === 1}
        style={{
          marginRight: "10px",
          padding: "6px 12px",
          border: "1px solid #912d2d",
          backgroundColor: currentPage === 1 ? "#ccc" : "white",
          color: currentPage === 1 ? "#666" : "#912d2d",
          borderRadius: "4px",
          cursor: currentPage === 1 ? "not-allowed" : "pointer",
        }}
        aria-label="Previous Page"
      ></button>

      {pages}

      <button
        onClick={handleNext}
        disabled={currentPage === totalPages}
        style={{
          marginLeft: "10px",
          padding: "6px 12px",
          border: "1px solid #912d2d",
          backgroundColor: currentPage === totalPages ? "#ccc" : "white",
          color: currentPage === totalPages ? "#666" : "#912d2d",
          borderRadius: "4px",
          cursor: currentPage === totalPages ? "not-allowed" : "pointer",
        }}
        aria-label="Next Page"
      ></button>
    </div>
  );
};

export default Pagination;
