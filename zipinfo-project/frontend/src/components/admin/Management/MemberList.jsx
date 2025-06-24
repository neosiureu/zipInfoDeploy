import React, { useEffect, useState } from "react";
import { Search, RefreshCw } from "lucide-react";
import "../../../css/admin/Management.css";

const MemberList = ({ initialMembers }) => {
  const [currentMembers, setCurrentMembers] = useState(
    Array.isArray(initialMembers) ? initialMembers : []
  );
  const [filteredMembers, setFilteredMembers] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [searchTerm, setSearchTerm] = useState("");
  const [roleFilter, setRoleFilter] = useState("");

  const membersPerPage = 10;

  // 권한 매핑
  const authMap = {
    0: "관리자",
    1: "일반회원",
    2: "중개회원 신청",
    3: "중개회원",
  };

  const roleOptions = ["관리자", "일반회원", "중개회원 신청", "중개회원"];

  useEffect(() => {
    if (Array.isArray(initialMembers)) {
      setCurrentMembers(initialMembers);
    } else {
      setCurrentMembers([]);
    }
  }, [initialMembers]);

  // 필터 및 검색
  useEffect(() => {
    let updated = [...currentMembers];

    if (searchTerm.trim()) {
      updated = updated.filter(
        (member) =>
          member.memberId?.includes(searchTerm) ||
          member.memberEmail?.includes(searchTerm) ||
          member.memberNo?.toString().includes(searchTerm)
      );
    }

    if (roleFilter) {
      updated = updated.filter(
        (member) => authMap[member.memberAuth] === roleFilter
      );
    }

    setFilteredMembers(updated);
    setCurrentPage(1); // 필터나 검색 변경 시 첫 페이지로
  }, [searchTerm, roleFilter, currentMembers]);

  const indexOfLastMember = currentPage * membersPerPage;
  const indexOfFirstMember = indexOfLastMember - membersPerPage;
  const currentPageMembers = filteredMembers.slice(
    indexOfFirstMember,
    indexOfLastMember
  );

  const totalPages = Math.ceil(filteredMembers.length / membersPerPage);

  const handleRefresh = () => {
    setSearchTerm("");
    setRoleFilter("");
    setFilteredMembers(currentMembers);
    setCurrentPage(1);
  };

  return (
    <div className="member-list p-4">
      <h3>회원 목록</h3>

      {/* 🔽 검색, 필터, 새로고침 */}
      <div className="controls flex gap-4 mb-4 items-center">
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

        <button
          className="bg-gray-200 hover:bg-gray-300 px-3 py-1 rounded"
          onClick={handleRefresh}
        >
          <RefreshCw size={16} className="inline-block mr-1" />
        </button>
      </div>

      <table>
        <thead>
          <tr>
            <th>번호</th>
            <th>아이디</th>
            <th>권한</th>
            <th>가입일</th>
            <th>최근 로그인</th>
            <th>게시글 수</th>
            <th>차단 여부</th>
          </tr>
        </thead>
        <tbody>
          {filteredMembers.length === 0 ? (
            <tr>
              <td colSpan="7" style={{ textAlign: "center" }}>
                회원 정보가 없습니다.
              </td>
            </tr>
          ) : (
            currentPageMembers.map((member) => (
              <tr key={member.memberNo}>
                <td>{member.memberNo}</td>
                <td>{member.memberEmail || member.memberId}</td>
                <td>{authMap[member.memberAuth] || "알 수 없음"}</td>
                <td>
                  {new Date(
                    member.joinDate || member.createdAt
                  ).toLocaleDateString()}
                </td>
                <td>
                  {member.lastLoginDate
                    ? new Date(member.lastLoginDate).toLocaleDateString()
                    : "-"}
                </td>
                <td>{member.postCount ?? 0}</td>
                <td>{member.blockFlag ? "차단됨" : "정상"}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {/* 페이지 버튼 */}
      <div className="pagination mt-4">
        {Array.from({ length: totalPages || 1 }, (_, i) => i + 1).map(
          (page) => (
            <button
              key={page}
              className={`page-button ${page === currentPage ? "active" : ""}`}
              onClick={() => setCurrentPage(page)}
            >
              {page}
            </button>
          )
        )}
      </div>
    </div>
  );
};

export default MemberList;
