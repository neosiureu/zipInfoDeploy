import React, { useState, useEffect } from "react";
import axios from "axios";
import { Search, RefreshCw } from "lucide-react";

const roleOptions = ["일반회원", "중개인", "관리자"];

const DeletedMembers = ({ initialDeletedMembers }) => {
  const [deletedMembers, setDeletedMembers] = useState(initialDeletedMembers);
  const [filteredDeletedMembers, setFilteredDeletedMembers] = useState(
    initialDeletedMembers
  );
  const [searchTerm, setSearchTerm] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const membersPerPage = 10;

  useEffect(() => {
    let filtered = deletedMembers;

    if (searchTerm) {
      filtered = filtered.filter(
        (member) =>
          member.memberId.toLowerCase().includes(searchTerm.toLowerCase()) ||
          member.memberNumber.toString().includes(searchTerm)
      );
    }

    if (roleFilter) {
      filtered = filtered.filter((member) => member.memberRole === roleFilter);
    }

    setFilteredDeletedMembers(filtered);
    setCurrentPage(1);
  }, [searchTerm, roleFilter, deletedMembers]);

  // 계정 복구 API 호출 및 상태 업데이트
  const handleRestoreMember = async (memberNumber) => {
    try {
      const response = await axios.put(
        `http://localhost:8080/api/admin/members/restore/${memberNumber}`
      );

      if (response.status === 200) {
        setDeletedMembers((prev) =>
          prev.filter((m) => m.memberNumber !== memberNumber)
        );
        alert("계정이 성공적으로 복구되었습니다.");
      } else {
        alert("복구에 실패했습니다. 다시 시도해주세요.");
      }
    } catch (error) {
      console.error("계정 복구 중 오류:", error);
      alert("복구 중 오류가 발생했습니다.");
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
      {/* 검색, 권한 필터, 새로고침 버튼 */}
      <div className="controls flex gap-4 mb-4 items-center">
        {/* 검색창 */}
        <div className="search-box relative">
          <Search size={18} className="absolute left-2 top-2.5 text-gray-400" />
          <input
            type="text"
            className="pl-8 pr-2 py-1 border rounded"
            placeholder="회원 아이디 또는 번호 검색"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        {/* 권한 필터 드롭다운 */}
        <select
          className="border px-2 py-1 rounded"
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

        {/* 새로고침 버튼 */}
        <button
          className="bg-gray-200 hover:bg-gray-300 px-3 py-1 rounded"
          onClick={handleRefresh}
        >
          <RefreshCw size={16} className="inline-block mr-1" />
        </button>
      </div>

      {/* 삭제 회원 수 표시 */}
      <div className="mb-2">
        총 <strong>{filteredDeletedMembers.length}</strong> 명의 삭제된 회원
      </div>

      {/* 테이블 */}
      <div className="overflow-x-auto border rounded shadow">
        <table className="w-full text-sm text-left table-auto border-collapse">
          <thead className="bg-gray-100">
            <tr>
              <th className="p-2 border-t">회원번호</th>
              <th className="p-2 border-t">아이디</th>
              <th className="p-2 border-t">가입일</th>
              <th className="p-2 border-t">회원 권한</th>
              <th className="p-2 border-t">최근 접속일</th>
              <th className="p-2 border-t">올린 글 개수</th>
              <th className="p-2 border-t">계정 복구</th>
            </tr>
          </thead>
          <tbody>
            {currentData.length > 0 ? (
              currentData.map((member, index) => (
                <tr key={member.memberNumber} className="border-t">
                  <td className="p-2 border-t text-center">
                    {member.memberNumber}
                  </td>
                  <td className="p-2 border-t">{member.memberId}</td>
                  <td className="p-2 border-t text-center">
                    {member.joinDate}
                  </td>
                  <td className="p-2 border-t text-center">
                    <select
                      className="border px-2 py-1 rounded"
                      value={member.memberRole || "일반회원"}
                      onChange={(e) =>
                        handleRoleChange(index + indexOfFirst, e.target.value)
                      }
                    >
                      {roleOptions.map((role) => (
                        <option key={role} value={role}>
                          {role}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="p-2 border-t text-center">
                    {member.lastLoginDate || "-"}
                  </td>
                  <td className="p-2 border-t text-center">
                    {member.postCount}
                  </td>
                  <td className="p-2 border-t text-center">
                    <button
                      onClick={() => handleRestoreMember(member.memberNumber)}
                      className="bg-green-500 hover:bg-green-600 text-white px-3 py-1 rounded"
                    >
                      ✓ 계정 복구
                    </button>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="7" className="text-center py-4 text-gray-500">
                  삭제된 회원이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* 페이지 버튼 */}
      <div className="pagination">
        {Array.from({ length: totalPages || 1 }, (_, i) => i + 1).map(
          (page) => (
            <button
              key={page}
              className={`page-button ${page === currentPage ? "active" : ""}`}
              onClick={() => handlePageChange(page)}
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
