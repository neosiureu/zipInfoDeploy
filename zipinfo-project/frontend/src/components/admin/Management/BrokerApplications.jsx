import React, { useState, useEffect } from "react";
import { Search, RefreshCw, XCircle } from "lucide-react"; // XCircle 추가

const roleOptions = ["일반회원", "중개인", "관리자"];

const BrokerApplications = ({ initialApplications }) => {
  const [applications, setApplications] = useState(initialApplications);
  const [filteredApps, setFilteredApps] = useState(initialApplications);

  const [searchTerm, setSearchTerm] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [currentPage, setCurrentPage] = useState(1);

  const membersPerPage = 10;

  useEffect(() => {
    let filtered = applications;

    if (searchTerm) {
      filtered = filtered.filter(
        (app) =>
          app.memberId.toLowerCase().includes(searchTerm.toLowerCase()) ||
          app.memberNumber.toString().includes(searchTerm)
      );
    }

    if (roleFilter) {
      filtered = filtered.filter((app) => app.memberRole === roleFilter);
    }

    setFilteredApps(filtered);
    setCurrentPage(1);
  }, [searchTerm, roleFilter, applications]);

  const handleRoleChange = (memberNumber, newRole) => {
    const updated = applications.map((app) =>
      app.memberNumber === memberNumber ? { ...app, memberRole: newRole } : app
    );
    setApplications(updated);
  };

  const handleReject = (applicationId) => {
    const updated = applications.map((app) =>
      app.applicationId === applicationId
        ? { ...app, applicationStatus: "거절됨" }
        : app
    );
    setApplications(updated);
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

      <div className="controls flex gap-4 mb-4 items-center">
        <div className="relative flex-grow">
          <Search
            size={16}
            className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-500"
          />
          <input
            type="text"
            className="pl-8 pr-2 py-1 border rounded w-full"
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
                <tr key={app.applicationId} className="border-t">
                  <td className="p-2 border text-center">{app.memberNumber}</td>
                  <td className="p-2 border">{app.memberId}</td>
                  <td className="p-2 border text-center">{app.joinDate}</td>
                  <td className="p-2 border text-center">
                    <select
                      value={app.memberRole}
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
                    {app.lastLoginDate}
                  </td>
                  <td className="p-2 border text-center">{app.postCount}</td>
                  <td className="p-2 border text-center">
                    <button
                      onClick={() => handleReject(app.applicationId)}
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
                <td colSpan="7" className="text-center py-4 text-gray-500">
                  신청 내역이 없습니다.
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

export default BrokerApplications;
