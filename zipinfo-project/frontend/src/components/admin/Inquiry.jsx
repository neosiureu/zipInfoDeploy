import React, { useState, useEffect } from "react";
import styles from "../../css/admin/Inquiry.module.css";

const Inquiry = () => {
  const [activeTab, setActiveTab] = useState("received");
  const [currentPage, setCurrentPage] = useState(1);
  const [inquiries, setInquiries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshing, setRefreshing] = useState(false);

  const fetchInquiries = async (showRefreshSpinner = false) => {
    try {
      if (showRefreshSpinner) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      await new Promise((resolve) => setTimeout(resolve, 1000));

      const mockData = [
        // ... 생략 (기존 mockData 유지)
      ];

      setInquiries(mockData);
    } catch (err) {
      setError(err.message || "데이터를 불러오는데 실패했습니다.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchInquiries();
  }, []);

  const updateInquiryStatus = async (inquiryId, newStatus) => {
    try {
      setInquiries((prev) =>
        prev.map((inquiry) =>
          inquiry.id === inquiryId ? { ...inquiry, status: newStatus } : inquiry
        )
      );
    } catch (err) {
      setError("상태 업데이트에 실패했습니다.");
    }
  };

  const filteredInquiries = inquiries.filter(
    (inquiry) => inquiry.type === activeTab
  );

  const itemsPerPage = 10;
  const totalPages = Math.ceil(filteredInquiries.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const currentInquiries = filteredInquiries.slice(
    startIndex,
    startIndex + itemsPerPage
  );

  const handleInquiryClick = (inquiry) => {
    console.log("문의 상세보기:", inquiry);
  };

  const getStatusBadge = (status) => {
    const baseClass = styles.statusBadge;
    switch (status) {
      case "답변 완료":
        return `${baseClass} ${styles.statusComplete}`;
      case "처리 중":
        return `${baseClass} ${styles.statusProcessing}`;
      case "답변 대기":
      default:
        return `${baseClass} ${styles.statusWaiting}`;
    }
  };

  const renderPageNumbers = () => {
    const pages = [];
    const maxVisiblePages = 10;

    let startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

    if (endPage - startPage + 1 < maxVisiblePages) {
      startPage = Math.max(1, endPage - maxVisiblePages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      pages.push(
        <button
          key={i}
          className={
            i === currentPage ? styles.activePageButton : styles.pageButton
          }
          onClick={() => setCurrentPage(i)}
        >
          {i}
        </button>
      );
    }

    return pages;
  };

  // Loader 대신 텍스트로만 처리
  if (loading) {
    return (
      <div className={styles.container}>
        <div className={styles.loadingContainer}>
          <span className={styles.loadingText}>문의 내용을 불러오는 중...</span>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      {/* 헤더 */}
      <div className={styles.header}>
        <h1 className={styles.title}>부동산 문의 관리</h1>
        <button
          className={styles.refreshButton}
          onClick={() => fetchInquiries(true)}
          disabled={refreshing}
        >
          {/* RefreshCw 아이콘이 없으면 텍스트만 사용하세요 */}
          {refreshing ? "새로고침 중..." : "새로고침"}
        </button>
      </div>

      {/* 에러 메시지 */}
      {error && (
        <div className={styles.errorContainer}>
          <div className={styles.errorText}>{error}</div>
        </div>
      )}

      {/* 탭 메뉴 */}
      <div className={styles.tabContainer}>
        <button
          className={`${styles.tab} ${
            activeTab === "received" ? styles.activeTab : styles.inactiveTab
          }`}
          onClick={() => {
            setActiveTab("received");
            setCurrentPage(1);
          }}
        >
          받은 문의 ({inquiries.filter((i) => i.type === "received").length})
        </button>
        <button
          className={`${styles.tab} ${
            activeTab === "sent" ? styles.activeTab : styles.inactiveTab
          }`}
          onClick={() => {
            setActiveTab("sent");
            setCurrentPage(1);
          }}
        >
          보낸 문의 ({inquiries.filter((i) => i.type === "sent").length})
        </button>
      </div>

      {/* 테이블 */}
      <div className={styles.tableContainer}>
        {/* 테이블 헤더 */}
        <div className={styles.tableHeader}>
          <div>번호</div>
          <div>제목</div>
          <div>작성자</div>
          <div>날짜</div>
          <div>답변</div>
          <div>답변</div>
        </div>

        {/* 테이블 본문 */}
        {currentInquiries.length === 0 ? (
          <div className={styles.emptyState}>
            {activeTab === "received"
              ? "받은 문의가 없습니다."
              : "보낸 문의가 없습니다."}
          </div>
        ) : (
          currentInquiries.map((inquiry) => (
            <div key={inquiry.id} className={styles.tableRow}>
              <div className={styles.inquiryNumber}>{inquiry.id}</div>
              <div
                className={styles.inquiryContent}
                onClick={() => handleInquiryClick(inquiry)}
                title={inquiry.content}
              >
                {inquiry.content}
              </div>
              <div className={styles.inquiryContent}>{inquiry.author}</div>
              <div className={styles.inquiryDate}>{inquiry.date}</div>
              <div>
                <span className={getStatusBadge(inquiry.status)}>
                  {inquiry.status}
                </span>
              </div>
              <div
                className={styles.actionButton}
                onClick={() => handleInquiryClick(inquiry)}
              >
                상세 보기 {/* 아이콘 대신 텍스트 */}
              </div>
            </div>
          ))
        )}
      </div>

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className={styles.pagination}>
          <button
            className={styles.navButton}
            onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
            disabled={currentPage === 1}
          >
            {/* 아이콘 대신 텍스트 */}
            이전
          </button>

          {renderPageNumbers()}

          <button
            className={styles.navButton}
            onClick={() =>
              setCurrentPage(Math.min(totalPages, currentPage + 1))
            }
            disabled={currentPage === totalPages}
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
};

export default Inquiry;
