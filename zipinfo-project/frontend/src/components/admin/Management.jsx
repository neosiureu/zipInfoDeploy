// 관리자 페이지-회원 목록 관리
import React, { useState, useEffect } from "react";
import {
  Search,
  RefreshCw,
  Ban,
  Calendar,
  User,
  Shield,
  Trash2,
  UserPlus,
  CheckCircle,
  XCircle,
  RotateCcw,
} from "lucide-react";
import "../../css/admin/Management.css";

// 더미 데이터
const dummyMembers = Array.from({ length: 47 }, (_, i) => ({
  memberNumber: i + 1,
  memberId: `user${String(i + 1).padStart(3, "0")}`,
  joinDate: new Date(
    2023,
    Math.floor(Math.random() * 12),
    Math.floor(Math.random() * 28) + 1
  )
    .toISOString()
    .split("T")[0],
  memberRole: ["관리자", "중개인", "일반회원"][Math.floor(Math.random() * 3)],
  lastLoginDate: new Date(2025, 5, Math.floor(Math.random() * 16) + 1)
    .toISOString()
    .split("T")[0],
  postCount: Math.floor(Math.random() * 50),
  isBlocked: Math.random() > 0.9,
}));

const dummyDeletedMembers = Array.from({ length: 23 }, (_, i) => ({
  memberNumber: i + 100,
  memberId: `deleted${String(i + 1).padStart(3, "0")}`,
  joinDate: new Date(
    2023,
    Math.floor(Math.random() * 12),
    Math.floor(Math.random() * 28) + 1
  )
    .toISOString()
    .split("T")[0],
  deleteDate: new Date(
    2025,
    Math.floor(Math.random() * 6),
    Math.floor(Math.random() * 16) + 1
  )
    .toISOString()
    .split("T")[0],
  deleteReason: ["회원 탈퇴", "관리자 삭제", "정책 위반"][
    Math.floor(Math.random() * 3)
  ],
  postCount: Math.floor(Math.random() * 30),
}));

const dummyBrokerApplications = Array.from({ length: 15 }, (_, i) => ({
  applicationId: i + 1,
  memberId: `applicant${String(i + 1).padStart(3, "0")}`,
  memberNumber: i + 200,
  applicationDate: new Date(2025, 5, Math.floor(Math.random() * 16) + 1)
    .toISOString()
    .split("T")[0],
  businessLicense: `사업자등록번호-${String(i + 1).padStart(3, "0")}`,
  experience: `${Math.floor(Math.random() * 10) + 1}년`,
  status: ["대기중", "승인됨", "거절됨"][Math.floor(Math.random() * 3)],
  applicationReason: `중개업 경력 ${
    Math.floor(Math.random() * 10) + 1
  }년차로 전문성을 바탕으로 서비스 제공하고자 합니다.`,
}));

const Management = () => {
  const [activeTab, setActiveTab] = useState("members");
  const [members, setMembers] = useState(dummyMembers);
  const [deletedMembers, setDeletedMembers] = useState(dummyDeletedMembers);
  const [brokerApplications, setBrokerApplications] = useState(
    dummyBrokerApplications
  );

  // 회원 목록 관리 상태
  const [filteredMembers, setFilteredMembers] = useState(dummyMembers);
  const [searchTerm, setSearchTerm] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [dateFilter, setDateFilter] = useState("");
  const [currentPage, setCurrentPage] = useState(1);

  // 삭제된 회원 목록 상태
  const [filteredDeletedMembers, setFilteredDeletedMembers] =
    useState(dummyDeletedMembers);
  const [deletedSearchTerm, setDeletedSearchTerm] = useState("");
  const [deleteReasonFilter, setDeleteReasonFilter] = useState("");
  const [deletedCurrentPage, setDeletedCurrentPage] = useState(1);

  // 중개인 신청 관리 상태
  const [filteredApplications, setFilteredApplications] = useState(
    dummyBrokerApplications
  );
  const [applicationSearchTerm, setApplicationSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [applicationCurrentPage, setApplicationCurrentPage] = useState(1);

  const membersPerPage = 10;
  const roleOptions = ["관리자", "중개인", "일반회원"];
  const deleteReasonOptions = ["회원 탈퇴", "관리자 삭제", "정책 위반"];
  const statusOptions = ["대기중", "승인됨", "거절됨"];

  // 회원 목록 필터링
  useEffect(() => {
    let filtered = members;

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

    if (dateFilter) {
      const now = new Date();
      const filterDate = new Date();

      switch (dateFilter) {
        case "recent30":
          filterDate.setDate(now.getDate() - 30);
          break;
        case "recent90":
          filterDate.setDate(now.getDate() - 90);
          break;
        case "recent365":
          filterDate.setFullYear(now.getFullYear() - 1);
          break;
        default:
          filterDate.setFullYear(2000);
      }

      filtered = filtered.filter(
        (member) => new Date(member.joinDate) >= filterDate
      );
    }

    setFilteredMembers(filtered);
    setCurrentPage(1);
  }, [searchTerm, roleFilter, dateFilter, members]);

  // 삭제된 회원 목록 필터링
  useEffect(() => {
    let filtered = deletedMembers;

    if (deletedSearchTerm) {
      filtered = filtered.filter(
        (member) =>
          member.memberId
            .toLowerCase()
            .includes(deletedSearchTerm.toLowerCase()) ||
          member.memberNumber.toString().includes(deletedSearchTerm)
      );
    }

    if (deleteReasonFilter) {
      filtered = filtered.filter(
        (member) => member.deleteReason === deleteReasonFilter
      );
    }

    setFilteredDeletedMembers(filtered);
    setDeletedCurrentPage(1);
  }, [deletedSearchTerm, deleteReasonFilter, deletedMembers]);

  // 중개인 신청 필터링
  useEffect(() => {
    let filtered = brokerApplications;

    if (applicationSearchTerm) {
      filtered = filtered.filter(
        (application) =>
          application.memberId
            .toLowerCase()
            .includes(applicationSearchTerm.toLowerCase()) ||
          application.applicationId.toString().includes(applicationSearchTerm)
      );
    }

    if (statusFilter) {
      filtered = filtered.filter(
        (application) => application.status === statusFilter
      );
    }

    setFilteredApplications(filtered);
    setApplicationCurrentPage(1);
  }, [applicationSearchTerm, statusFilter, brokerApplications]);

  // 핸들러 함수들
  const handleRoleChange = (memberNumber, newRole) => {
    setMembers((prevMembers) =>
      prevMembers.map((member) =>
        member.memberNumber === memberNumber
          ? { ...member, memberRole: newRole }
          : member
      )
    );
  };

  const handleBlockToggle = (memberNumber) => {
    setMembers((prevMembers) =>
      prevMembers.map((member) =>
        member.memberNumber === memberNumber
          ? { ...member, isBlocked: !member.isBlocked }
          : member
      )
    );
  };

  const handleDeleteMember = (memberNumber) => {
    const memberToDelete = members.find((m) => m.memberNumber === memberNumber);
    if (memberToDelete) {
      const deletedMember = {
        ...memberToDelete,
        deleteDate: new Date().toISOString().split("T")[0],
        deleteReason: "관리자 삭제",
      };
      setDeletedMembers((prev) => [...prev, deletedMember]);
      setMembers((prev) => prev.filter((m) => m.memberNumber !== memberNumber));
    }
  };

  const handleRestoreMember = (memberNumber) => {
    const memberToRestore = deletedMembers.find(
      (m) => m.memberNumber === memberNumber
    );
    if (memberToRestore) {
      const { deleteDate, deleteReason, ...restoredMember } = memberToRestore;
      setMembers((prev) => [...prev, restoredMember]);
      setDeletedMembers((prev) =>
        prev.filter((m) => m.memberNumber !== memberNumber)
      );
    }
  };

  const handleApplicationStatusChange = (applicationId, newStatus) => {
    setBrokerApplications((prev) =>
      prev.map((app) =>
        app.applicationId === applicationId
          ? { ...app, status: newStatus }
          : app
      )
    );

    if (newStatus === "승인됨") {
      const application = brokerApplications.find(
        (app) => app.applicationId === applicationId
      );
      if (application) {
        setMembers((prev) =>
          prev.map((member) =>
            member.memberNumber === application.memberNumber
              ? { ...member, memberRole: "중개인" }
              : member
          )
        );
      }
    }
  };

  const handleRefresh = () => {
    setSearchTerm("");
    setRoleFilter("");
    setDateFilter("");
    setDeletedSearchTerm("");
    setDeleteReasonFilter("");
    setApplicationSearchTerm("");
    setStatusFilter("");
    setCurrentPage(1);
    setDeletedCurrentPage(1);
    setApplicationCurrentPage(1);
  };

  const getRoleClass = (role) => {
    switch (role) {
      case "관리자":
        return "role-admin";
      case "중개인":
        return "role-broker";
      case "일반회원":
        return "role-user";
      default:
        return "role-user";
    }
  };

  const getStatusClass = (status) => {
    switch (status) {
      case "대기중":
        return "status-pending";
      case "승인됨":
        return "status-approved";
      case "거절됨":
        return "status-rejected";
      default:
        return "status-pending";
    }
  };

  // 페이지네이션 계산
  const getCurrentPageData = () => {
    let data, currentPageState;

    switch (activeTab) {
      case "members":
        data = filteredMembers;
        currentPageState = currentPage;
        break;
      case "deleted":
        data = filteredDeletedMembers;
        currentPageState = deletedCurrentPage;
        break;
      case "applications":
        data = filteredApplications;
        currentPageState = applicationCurrentPage;
        break;
      default:
        data = [];
        currentPageState = 1;
    }

    const indexOfLast = currentPageState * membersPerPage;
    const indexOfFirst = indexOfLast - membersPerPage;
    const currentData = data.slice(indexOfFirst, indexOfLast);
    const totalPages = Math.ceil(data.length / membersPerPage);

    return { currentData, totalPages, currentPageState };
  };

  const { currentData, totalPages, currentPageState } = getCurrentPageData();

  const handlePageChange = (pageNumber) => {
    switch (activeTab) {
      case "members":
        setCurrentPage(pageNumber);
        break;
      case "deleted":
        setDeletedCurrentPage(pageNumber);
        break;
      case "applications":
        setApplicationCurrentPage(pageNumber);
        break;
    }
  };

  const tabConfig = [
    { key: "members", label: "회원 목록 관리", icon: User },
    { key: "deleted", label: "삭제한 회원 목록", icon: Trash2 },
    { key: "applications", label: "중개인 권한 신청", icon: UserPlus },
  ];

  const getCurrentSearchValue = () => {
    switch (activeTab) {
      case "members":
        return searchTerm;
      case "deleted":
        return deletedSearchTerm;
      case "applications":
        return applicationSearchTerm;
      default:
        return "";
    }
  };

  const getCurrentSearchPlaceholder = () => {
    switch (activeTab) {
      case "members":
        return "회원번호 또는 아이디 검색";
      case "deleted":
        return "삭제된 회원 검색";
      case "applications":
        return "신청자 아이디 검색";
      default:
        return "검색";
    }
  };

  const handleSearchChange = (value) => {
    switch (activeTab) {
      case "members":
        setSearchTerm(value);
        break;
      case "deleted":
        setDeletedSearchTerm(value);
        break;
      case "applications":
        setApplicationSearchTerm(value);
        break;
    }
  };

  const getCurrentCount = () => {
    switch (activeTab) {
      case "members":
        return filteredMembers.length;
      case "deleted":
        return filteredDeletedMembers.length;
      case "applications":
        return filteredApplications.length;
      default:
        return 0;
    }
  };

  const getCurrentCountLabel = () => {
    switch (activeTab) {
      case "members":
        return "명의 회원";
      case "deleted":
        return "명의 삭제된 회원";
      case "applications":
        return "건의 신청";
      default:
        return "";
    }
  };

  return (
    <div className="management-container">
      {/* 헤더 */}
      <div className="management-header">
        <h2>
          <Shield className="header-icon" />
          관리자 페이지
        </h2>
      </div>

      {/* 탭 메뉴 */}
      <div className="tab-menu">
        {tabConfig.map((tab) => (
          <button
            key={tab.key}
            className={`tab-button ${activeTab === tab.key ? "active" : ""}`}
            onClick={() => setActiveTab(tab.key)}
          >
            <tab.icon className="tab-icon" />
            {tab.label}
          </button>
        ))}
        <button className="refresh-button" onClick={handleRefresh}>
          <RefreshCw size={18} />
        </button>
      </div>

      {/* 검색 및 필터 */}
      <div className="controls">
        <div className="search-box">
          <Search size={18} />
          <input
            type="text"
            value={getCurrentSearchValue()}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder={getCurrentSearchPlaceholder()}
          />
        </div>

        {activeTab === "members" && (
          <>
            <select
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
            >
              <option value="">전체 역할</option>
              {roleOptions.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </select>
            <select
              value={dateFilter}
              onChange={(e) => setDateFilter(e.target.value)}
            >
              <option value="">전체 기간</option>
              <option value="recent30">최근 30일</option>
              <option value="recent90">최근 90일</option>
              <option value="recent365">최근 1년</option>
            </select>
          </>
        )}

        {activeTab === "deleted" && (
          <select
            value={deleteReasonFilter}
            onChange={(e) => setDeleteReasonFilter(e.target.value)}
          >
            <option value="">전체 삭제 사유</option>
            {deleteReasonOptions.map((reason) => (
              <option key={reason} value={reason}>
                {reason}
              </option>
            ))}
          </select>
        )}

        {activeTab === "applications" && (
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">전체 상태</option>
            {statusOptions.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
        )}
      </div>

      {/* 결과 수 표시 */}
      <div className="count-info">
        총 <strong>{getCurrentCount()}</strong> {getCurrentCountLabel()}
      </div>

      {/* 목록 테이블 */}
      <div className="data-table">
        {/* Render conditional content here, example for activeTab === 'members' */}
        {activeTab === "members" && (
          <table>
            <thead>
              <tr>
                <th>회원번호</th>
                <th>아이디</th>
                <th>가입일</th>
                <th>최근 로그인</th>
                <th>게시글 수</th>
                <th>역할</th>
                <th>차단</th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {currentData.map((member) => (
                <tr key={member.memberNumber}>
                  <td>{member.memberNumber}</td>
                  <td>{member.memberId}</td>
                  <td>{member.joinDate}</td>
                  <td>{member.lastLoginDate}</td>
                  <td>{member.postCount}</td>
                  <td>
                    <select
                      value={member.memberRole}
                      onChange={(e) =>
                        handleRoleChange(member.memberNumber, e.target.value)
                      }
                      className={getRoleClass(member.memberRole)}
                    >
                      {roleOptions.map((role) => (
                        <option key={role} value={role}>
                          {role}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <button
                      onClick={() => handleBlockToggle(member.memberNumber)}
                    >
                      {member.isBlocked ? (
                        <Ban color="red" />
                      ) : (
                        <CheckCircle color="green" />
                      )}
                    </button>
                  </td>
                  <td>
                    <button
                      onClick={() => handleDeleteMember(member.memberNumber)}
                    >
                      <Trash2 color="#912d2d" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* 유사하게 deleted / applications 도 각 조건에 맞게 테이블 구성 */}
      </div>

      {/* 페이지네이션 */}
      <div className="pagination">
        {Array.from({ length: totalPages }, (_, i) => (
          <button
            key={i + 1}
            className={currentPageState === i + 1 ? "active-page" : ""}
            onClick={() => handlePageChange(i + 1)}
          >
            {i + 1}
          </button>
        ))}
      </div>
    </div>
  );
};

export default Management;
