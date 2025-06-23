import React, { useState, useEffect } from "react";
import axios from "axios";
import { Shield, User, Trash2, UserPlus } from "lucide-react";
import "../../../css/admin/Management.css";

import MemberList from "./MemberList";
import DeletedMembers from "./DeletedMembers";
import BrokerApplications from "./BrokerApplications";

const Management = () => {
  const [activeTab, setActiveTab] = useState("members");

  const [members, setMembers] = useState([]);
  const [deletedMembers, setDeletedMembers] = useState([]);
  const [brokerApplications, setBrokerApplications] = useState([]);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // 응답 데이터가 배열인지 확인하고 없으면 빈 배열로 처리하는 헬퍼
  const handleResponse = (res, setDataFunc, errorMessage) => {
    console.log("API 응답 데이터(res.data):", res.data); // 여기서 API 응답 전체를 출력

    if (Array.isArray(res.data)) {
      setDataFunc(res.data);
    } else if (res.data && Array.isArray(res.data.data)) {
      setDataFunc(res.data.data);
    } else {
      setError(errorMessage + " (데이터 형식 오류)");
      setDataFunc([]);
    }
  };

  useEffect(() => {
    setError(null);
    setLoading(true);

    if (activeTab === "members") {
      axios
        .get("http://localhost:8080/admin/management/members", {
          withCredentials: true,
        })
        .then((res) =>
          handleResponse(res, setMembers, "회원 목록 불러오기 실패")
        )
        .catch(() => setError("회원 목록 불러오기 실패"))
        .finally(() => setLoading(false));
    } else if (activeTab === "deleted") {
      axios
        .get("/admin/management/members/deleted")
        .then((res) =>
          handleResponse(
            res,
            setDeletedMembers,
            "삭제된 회원 목록 불러오기 실패"
          )
        )
        .catch(() => setError("삭제된 회원 목록 불러오기 실패"))
        .finally(() => setLoading(false));
    } else if (activeTab === "applications") {
      axios
        .get("/admin/management/broker-applications")
        .then((res) =>
          handleResponse(
            res,
            setBrokerApplications,
            "중개인 권한 신청 목록 불러오기 실패"
          )
        )
        .catch(() => setError("중개인 권한 신청 목록 불러오기 실패"))
        .finally(() => setLoading(false));
    }
  }, [activeTab]);

  return (
    <div className="management-container">
      <div className="management-header">
        <h2>
          <Shield className="header-icon" />
          관리자 페이지
        </h2>
      </div>

      <div className="tab-menu">
        <button
          className={`tab-button ${activeTab === "members" ? "active" : ""}`}
          onClick={() => setActiveTab("members")}
        >
          <User className="tab-icon" />
          회원 목록 관리
        </button>
        <button
          className={`tab-button ${activeTab === "deleted" ? "active" : ""}`}
          onClick={() => setActiveTab("deleted")}
        >
          <Trash2 className="tab-icon" />
          삭제한 회원 목록
        </button>
        <button
          className={`tab-button ${
            activeTab === "applications" ? "active" : ""
          }`}
          onClick={() => setActiveTab("applications")}
        >
          <UserPlus className="tab-icon" />
          중개인 권한 신청
        </button>
      </div>

      {/* 로딩 및 에러 메시지 */}
      {loading && <div>로딩 중...</div>}
      {error && <div className="text-red-500 mb-4">{error}</div>}

      {/* 컴포넌트는 항상 렌더링 (로딩/에러 여부 상관없이) */}
      {activeTab === "members" && <MemberList initialMembers={members} />}
      {activeTab === "deleted" && (
        <DeletedMembers initialDeletedMembers={deletedMembers} />
      )}
      {activeTab === "applications" && (
        <BrokerApplications initialApplications={brokerApplications} />
      )}
    </div>
  );
};

export default Management;
