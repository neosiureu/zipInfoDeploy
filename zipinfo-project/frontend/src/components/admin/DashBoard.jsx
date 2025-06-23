// src/components/admin/DashBoard.jsx
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useContext } from "react";
import { AuthContext } from "./AuthContext";

import "../../css/admin/DashBoard.css";

import Header from "../common/Header";
import Footer from "../common/Footer";

export default function DashBoard() {
  const globalState = useContext(AuthContext);
  const navigate = useNavigate();

  const goDashBoardMain = () => {
    navigate("/admin"); // 관리자 메인 페이지로 이동
  };

  return (
    <>
      <Header />

      <div className="dash-board-container">
        <div className="dash-board-header">
          <h2 onClick={goDashBoardMain} style={{ cursor: "pointer" }}>
            관리자 대시보드
          </h2>
        </div>

        <div className="router-tab-box">
          <NavLink
            to="/admin"
            end
            className={({ isActive }) => (isActive ? "active" : "")}
          >
            통계
          </NavLink>
          <NavLink
            to="/admin/management"
            className={({ isActive }) => (isActive ? "active" : "")}
          >
            서비스관리 권한 발급
          </NavLink>
          <NavLink
            to="/admin/inquiry"
            className={({ isActive }) => (isActive ? "active" : "")}
          >
            문의 확인
          </NavLink>
          <NavLink
            to="/admin/advertisement"
            className={({ isActive }) => (isActive ? "active" : "")}
          >
            광고 관리
          </NavLink>
          <NavLink
            to="/admin/list-sale"
            className={({ isActive }) => (isActive ? "active" : "")}
          >
            분양 관리
          </NavLink>
        </div>

        <div className="admin-info">
          {globalState && globalState.user ? (
            <>
              <p>현재 {globalState.user.memberNickname} 으로 접속중입니다.</p>
              <p>계정 ID: {globalState.user.memberId}</p>
            </>
          ) : (
            <p>로그인 정보가 없습니다.</p>
          )}
        </div>

        {/* 중첩 라우트 컴포넌트 렌더링 자리 */}
        <Outlet />
      </div>

      <Footer />
    </>
  );
}
