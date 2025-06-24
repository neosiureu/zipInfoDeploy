import React, { useState, useEffect } from "react";
import axios from "axios";
import { Search, RefreshCw, XCircle } from "lucide-react";

const roleOptions = ["관리자", "일반회원", "중개인 신청", "중개인"];

const roleMap = {
  0: "관리자",
  1: "일반회원",
  2: "중개인 신청",
  3: "중개인",
};

const reverseRoleMap = {
  관리자: 0,
  일반회원: 1,
  "중개인 신청": 2,
  중개인: 3,
};

// YYYY-MM-DD 형식으로 변환 함수
function formatDate(dateString) {
  if (!dateString) return "-";
  const d = new Date(dateString);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

const BrokerApplications = () => {
  const [applications, setApplications] = useState([]);
  const [filteredApps, setFilteredApps] = useState([]);

  const [searchTerm, setSearchTerm] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [currentPage, setCurrentPage] = useState(1);

  const membersPerPage = 10;
  const BASE_URL = "http://localhost:8080"; // 백엔드 서버 주소

  useEffect(() => {
    const fetchApplications = async () => {
      try {
        const response = await axios.get(
          `${BASE_URL}/admin/management/broker-applications`
        );
        const data = response?.data || [];
        if (!Array.isArray(data)) {
          console.error("응답이 배열이 아닙니다!", data);
          return;
        }
        setApplications(data);
        setFilteredApps(data);
      } catch (error) {
        console.error("중개회원 신청 목록 조회 실패", error);
      }
    };

    fetchApplications();
  }, []);

  useEffect(() => {
    let filtered = applications;

    if (searchTerm) {
      filtered = filtered.filter(
        (app) =>
          app.memberId?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          app.memberNumber?.toString().includes(searchTerm)
      );
    }

    if (roleFilter) {
      filtered = filtered.filter(
        (app) => roleMap[app.memberRole] === roleFilter
      );
    }

    setFilteredApps(filtered);
    setCurrentPage(1);
  }, [searchTerm, roleFilter, applications]);

  const handleRoleChange = async (memberNumber, newRoleStr) => {
    const newRole = reverseRoleMap[newRoleStr];
    try {
      await axios.put(`/admin/management/members/${memberNumber}/role`, null, {
        params: { authId: newRole },
      });

      const updated = applications.map((app) =>
        app.memberNumber === memberNumber
          ? { ...app, memberRole: newRole }
          : app
      );
      setApplications(updated);
    } catch (error) {
      console.error("회원 권한 변경 실패", error);
      alert("회원 권한 변경에 실패했습니다. 다시 시도해주세요.");
    }
  };

  // handleReject에서 memberNo 기준으로 상태 변경 요청
  const handleReject = async (memberNumber) => {
    try {
      // 1) 신청 상태 '거절됨' 으로 변경
      await axios.put(
        `/admin/management/broker-applications/${memberNumber}/status`,
        null,
        { params: { status: "거절됨" } }
      );

      // 2) 회원 권한을 일반회원(1)으로 변경
      await axios.put(`/admin/management/members/${memberNumber}/role`, null, {
        params: { authId: 1 },
      });

      // 3) 로컬 상태 반영
      const updated = applications.map((app) =>
        app.memberNumber === memberNumber
          ? { ...app, applicationStatus: "거절됨", memberRole: 1 }
          : app
      );
      setApplications(updated);
    } catch (error) {
      console.error("신청 거절 처리 실패", error);
      alert("거절 처리에 실패했습니다. 다시 시도해주세요.");
    }
  };

  const handleRefresh = () => {
    setSearchTerm("");
    setRoleFilter("");
  };

  const totalPages = Math.ceil(filteredApps.length / membersPerPage);
  const indexOfLast = currentPage * membersPerPage;
  const indexOfFirst = indexOfLast - membersPerPage;
  const currentApps = filteredApps.slice(indexOfFirst, indexOfLast);

  const handlePageChange = (pageNum) => {
    if (pageNum < 1 || pageNum > totalPages) return;
    setCurrentPage(pageNum);
  };

  return (
    <div className="member-table-container p-4 border rounded shadow mt-4">
      <h3 className="text-xl font-bold mb-4">중개인 권한 신청 목록</h3>

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
          className="bg-gray-200 hover:bg-gray-300 px-3 py-1 rounded flex items-center"
          onClick={handleRefresh}
          aria-label="초기화"
        >
          <RefreshCw size={16} className="mr-1" />
        </button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left table-auto border-collapse">
          <thead className="bg-gray-200">
            <tr>
              <th className="p-2 border">회원 번호</th>
              <th className="p-2 border">아이디</th>
              <th className="p-2 border">회원 가입일</th>
              <th className="p-2 border">회원 권한</th>
              <th className="p-2 border">최근 접속일</th>
              <th className="p-2 border">올린 글 개수</th>
              <th className="p-2 border">관리</th>
            </tr>
          </thead>
          <tbody>
            {currentApps.length > 0 ? (
              currentApps.map((app) => (
                <tr key={app.memberNumber} className="border-t">
                  <td className="p-2 border text-center">{app.memberNumber}</td>
                  <td className="p-2 border">{app.memberId}</td>
                  <td className="p-2 border text-center">
                    {formatDate(app.joinDate)}
                  </td>
                  <td className="p-2 border text-center">
                    <select
                      value={roleMap[app.memberRole] || ""}
                      onChange={(e) =>
                        handleRoleChange(app.memberNumber, e.target.value)
                      }
                      className="border px-2 py-1 rounded"
                    >
                      {roleOptions.map((role) => (
                        <option key={role} value={role}>
                          {role}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="p-2 border text-center">
                    {formatDate(app.lastLoginDate)}
                  </td>
                  <td className="p-2 border text-center">{app.postCount}</td>
                  <td className="p-2 border text-center">
                    <button
                      onClick={() => handleReject(app.memberNumber)}
                      disabled={app.applicationStatus === "거절됨"}
                      className={`flex items-center gap-1 px-3 py-1 rounded ${
                        app.applicationStatus === "거절됨"
                          ? "bg-gray-200 cursor-not-allowed"
                          : "bg-white hover:bg-gray-100 border border-gray-400"
                      }`}
                    >
                      <XCircle
                        size={18}
                        color="red"
                        strokeWidth={2}
                        className="flex-shrink-0"
                      />
                      <span
                        className={`font-semibold ${
                          app.applicationStatus === "거절됨"
                            ? "text-gray-500"
                            : "text-black"
                        }`}
                      >
                        {app.applicationStatus === "거절됨"
                          ? "거절됨"
                          : "권한신청 거절"}
                      </span>
                    </button>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={7} className="text-center py-4 text-gray-500">
                  신청 내역이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* 페이지 버튼 */}
      <div className="pagination flex justify-center mt-4 gap-2">
        {Array.from({ length: totalPages || 1 }, (_, i) => i + 1).map(
          (page) => (
            <button
              key={page}
              className={`page-button px-2 py-1 border rounded ${
                page === currentPage ? "bg-gray-300 font-bold" : "bg-white"
              }`}
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

export default BrokerApplications;
