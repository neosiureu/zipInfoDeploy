import React, { useState, useEffect } from "react";
import axios from "axios";
import { Search, RefreshCw } from "lucide-react";
import "../../../css/admin/Management/DeletedMembers.css";
import { toast } from "react-toastify";

const roleOptions = ["일반회원", "중개인", "관리자"];
const BASE_URL = "http://localhost:8080"; // API 주소 맞게 조정하세요

const DeletedMembers = () => {
  const [deletedMembers, setDeletedMembers] = useState([]);
  const [filteredDeletedMembers, setFilteredDeletedMembers] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const membersPerPage = 10;

  // 서버에서 삭제된 회원 목록 불러오기
  const fetchDeletedMembers = async () => {
    try {
      const response = await axios.get(
        `${BASE_URL}/admin/management/members/deleted`
      );
      setDeletedMembers(response.data);
    } catch (error) {
      console.error("삭제된 회원 목록 로드 실패:", error);
    }
  };

  // 컴포넌트 마운트 시 목록 로드
  useEffect(() => {
    fetchDeletedMembers();
  }, []);

  // deletedMembers 상태 변경 시 필터 초기화 및 적용
  useEffect(() => {
    setFilteredDeletedMembers(deletedMembers);
  }, [deletedMembers]);

  // 검색어, 권한 필터, deletedMembers 변경 시 필터링 및 페이지 초기화
  useEffect(() => {
    let filtered = deletedMembers;

    if (searchTerm) {
      filtered = filtered.filter(
        (member) =>
          member.memberEmail
            ?.toLowerCase()
            .includes(searchTerm.toLowerCase()) ||
          member.memberNo?.toString().includes(searchTerm)
      );
    }

    if (roleFilter) {
      filtered = filtered.filter((member) => member.memberRole === roleFilter);
    }

    setFilteredDeletedMembers(filtered);
    setCurrentPage(1);
  }, [searchTerm, roleFilter, deletedMembers]);

  // 회원 복구 함수
  const handleRestoreMember = async (memberNo) => {
    try {
      const response = await axios.put(
        `${BASE_URL}/admin/management/members/${memberNo}/restore`
      );

      if (response.status === 200) {
        toast.success(
          <div>
            <div className="toast-success-title">복구 성공 알림!</div>
            <div className="toast-success-body">
              계정이 성공적으로 복구되었습니다.
            </div>
          </div>
        );
        await fetchDeletedMembers(); // 복구 후 목록 최신화
      } else {
        toast.error(
          <div>
            <div className="toast-error-title">오류 알림!</div>
            <div className="toast-error-body">복구에 실패하였습니다.</div>
          </div>
        );
      }
    } catch (error) {
      console.error("계정 복구 중 오류:", error);
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">복구 중 오류가 발생하였습니다.</div>
        </div>
      );
    }
  };

  const handleRoleChange = (index, newRole) => {
    const updated = [...filteredDeletedMembers];
    updated[index].memberRole = newRole;
    setFilteredDeletedMembers(updated);
  };

  const totalPages = Math.ceil(filteredDeletedMembers.length / membersPerPage);
  const indexOfLast = currentPage * membersPerPage;
  const indexOfFirst = indexOfLast - membersPerPage;
  const currentData = filteredDeletedMembers.slice(indexOfFirst, indexOfLast);

  const handlePageChange = (page) => {
    if (page < 1 || page > totalPages) return;
    setCurrentPage(page);
  };

  const handleRefresh = () => {
    setSearchTerm("");
    setRoleFilter("");
  };

  return (
    <div className="deleted-members-container p-4">
      <h3 className="deleted-members-header">삭제된 회원 목록</h3>

      <div className="controls">
        <div className="search-box">
          <Search size={18} className="search-icon" />
          <input
            type="text"
            className="search-input"
            placeholder="회원 아이디 또는 번호 검색"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <select
          className="filter-select"
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
        >
          <option value="">전체 권한</option>
          {roleOptions.map((role) => (
            <option key={role} value={role}>
              {role}
            </option>
          ))}
        </select>

        <button
          className="refresh-button"
          onClick={handleRefresh}
          aria-label="초기화"
        >
          <RefreshCw size={16} className="icon-inline" />
        </button>
      </div>

      <div className="overflow-x-auto">
        <table className="deleted-members-table">
          <thead>
            <tr>
              <th>회원 번호</th>
              <th>아이디</th>
              <th>가입일</th>
              <th>회원 권한</th>
              <th>최근 접속일</th>
              <th>올린 글 개수</th>
              <th>계정 복구</th>
            </tr>
          </thead>
          <tbody>
            {currentData.length === 0 ? (
              <tr>
                <td colSpan="7" className="no-data">
                  삭제된 회원이 없습니다.
                </td>
              </tr>
            ) : (
              currentData.map((member, idx) => (
                <tr key={member.memberNo}>
                  <td>{member.memberNo}</td>
                  <td>{member.memberEmail || member.memberId}</td>
                  <td>
                    {member.joinDate
                      ? new Date(member.joinDate).toLocaleDateString()
                      : "-"}
                  </td>
                  <td>
                    <select
                      className="filter-select"
                      value={member.memberRole || "일반회원"}
                      onChange={(e) =>
                        handleRoleChange(idx + indexOfFirst, e.target.value)
                      }
                    >
                      {roleOptions.map((role) => (
                        <option key={role} value={role}>
                          {role}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    {member.lastLoginDate
                      ? new Date(member.lastLoginDate).toLocaleDateString()
                      : "-"}
                  </td>
                  <td>{member.postCount ?? 0}</td>
                  <td>
                    <button
                      className="restore-button"
                      onClick={() => handleRestoreMember(member.memberNo)}
                    >
                      계정 복구
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="pagination">
        {Array.from({ length: totalPages || 1 }, (_, i) => i + 1).map(
          (page) => (
            <button
              key={page}
              className={`page-button ${page === currentPage ? "active" : ""}`}
              onClick={() => handlePageChange(page)}
              aria-label={`페이지 ${page}`}
            >
              {page}
            </button>
          )
        )}
      </div>
    </div>
  );
};

export default DeletedMembers;
