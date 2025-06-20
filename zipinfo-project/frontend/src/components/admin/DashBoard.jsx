import {
  NavLink,
  Route,
  Routes,
  useNavigate,
  Navigate,
} from "react-router-dom";
import { useContext } from "react";
import { AuthContext } from "./AuthContext";

// ... 이하 생략

// 각 컴포넌트 import
import Chart from "./Chart";
import Management from "./Management";
import Inquiry from "./Inquiry";
import Advertisement from "./Advertisement";
import ListSale from "./saleForm/ListSale";
import AddSale from "./saleForm/AddSale";

import "../../css/admin/DashBoard.css";

// 공통 헤더, 푸터 import (경로는 프로젝트 구조에 맞게 수정하세요)
import Header from "../common/Header";
import Footer from "../common/Footer";

export default function DashBoard() {
  const globalState = useContext(AuthContext);
  const navigate = useNavigate();

  const goDashBoardMain = () => {
    navigate("/"); // SPA 방식으로 이동
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
          <NavLink to="/admin">통계</NavLink>
          <NavLink to="/admin/management">서비스관리 권한 발급</NavLink>
          <NavLink to="/admin/inquiry">문의 확인</NavLink>
          <NavLink to="/admin/advertisement">광고 관리</NavLink>
          <NavLink to="/admin/list-sale">분양 관리</NavLink>
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

        <Routes>
          <Route index element={<Chart />} />
          <Route path="/admin/*" element={<DashBoard />} />
          <Route path="management" element={<Management />} />
          <Route path="inquiry" element={<Inquiry />} />
          <Route path="advertisement" element={<Advertisement />} />
          <Route path="list-sale" element={<ListSale />} />
          <Route path="add-sale" element={<AddSale />} />
        </Routes>
      </div>

      <Footer />
    </>
  );
}
