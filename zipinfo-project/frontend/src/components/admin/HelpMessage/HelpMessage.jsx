import React, { useState, useEffect } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import styles from "../../../css/admin/HelpMessage/HelpMessage.module.css";

const HelpMessage = () => {
  const [activeTab, setActiveTab] = useState("received");
  const [currentPage, setCurrentPage] = useState(1);
  const [helpMessages, setHelpMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshing, setRefreshing] = useState(false);

  const navigate = useNavigate(); // ✅ 추가

  const fetchHelpMessages = async (showRefreshSpinner = false) => {
    try {
      if (showRefreshSpinner) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      const response = await axios.get("/api/help/list");
      setHelpMessages(response.data);
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.message ||
          "데이터를 불러오는데 실패했습니다."
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    setCurrentPage(1);
    fetchHelpMessages();
  }, [activeTab]);

  const itemsPerPage = 10;
  const totalPages = Math.ceil(helpMessages.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const currentHelpMessages = helpMessages.slice(
    startIndex,
    startIndex + itemsPerPage
  );

  const getStatusBadge = (replyContent) => {
    const baseClass = styles.statusBadge;
    if (!replyContent || replyContent.trim() === "") {
      return `${baseClass} ${styles.statusWaiting}`;
    } else {
      return `${baseClass} ${styles.statusComplete}`;
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

  // 상세 보기 클릭 시 라우팅
  const handleHelpMessageClick = (msg) => {
    navigate(`/admin/help/reply/${msg.messageNo}?senderNo=${msg.senderNo}`);
  };

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
          onClick={() => fetchHelpMessages(true)}
          disabled={refreshing}
        >
          {refreshing ? "새로고침 중..." : "새로고침"}
        </button>
      </div>

      {/* 에러 메시지 */}
      {error && (
        <div className={styles.errorContainer}>
          <div className={styles.errorText}>{error}</div>
        </div>
      )}

      {/* 탭 */}
      <div className={styles.tabContainer}>
        <button
          className={`${styles.tab} ${
            activeTab === "received" ? styles.activeTab : styles.inactiveTab
          }`}
          onClick={() => setActiveTab("received")}
        >
          받은 문의 ({activeTab === "received" ? helpMessages.length : 0})
        </button>
        <button
          className={`${styles.tab} ${
            activeTab === "sent" ? styles.activeTab : styles.inactiveTab
          }`}
          onClick={() => setActiveTab("sent")}
        >
          문의 답변 ({activeTab === "sent" ? helpMessages.length : 0})
        </button>
      </div>

      {/* 테이블 */}
      <div className={styles.tableContainer}>
        <div className={styles.tableHeader}>
          <div>번호</div>
          <div>제목</div>
          <div>작성자</div>
          <div>날짜</div>
          <div>답변</div>
          <div></div>
        </div>

        {currentHelpMessages.length === 0 ? (
          <div className={styles.emptyState}>
            {activeTab === "received"
              ? "받은 문의가 없습니다."
              : "보낸 문의가 없습니다."}
          </div>
        ) : (
          currentHelpMessages.map((msg) => (
            <div key={msg.messageNo} className={styles.tableRow}>
              <div>{msg.messageNo}</div>
              <div>{msg.messageTitle}</div>
              <div>{msg.senderNo}</div>
              <div>
                {new Date(msg.messageWriteDate).toLocaleDateString("ko-KR")}
              </div>
              <div>
                <span className={getStatusBadge(msg.replyContent)}>
                  {msg.replyContent ? "답변 완료" : "답변 대기"}
                </span>
              </div>
              <div
                className={styles.actionButton}
                onClick={() => handleHelpMessageClick(msg)}
              >
                상세 보기
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
            onClick={() => setCurrentPage((prev) => Math.max(1, prev - 1))}
            disabled={currentPage === 1}
          ></button>
          {renderPageNumbers()}
          <button
            className={styles.navButton}
            onClick={() =>
              setCurrentPage((prev) => Math.min(totalPages, prev + 1))
            }
            disabled={currentPage === totalPages}
          ></button>
        </div>
      )}
    </div>
  );
};

export default HelpMessage;
