// src/App.jsx
import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Layout from "./components/common/Layout";
import Main from "./components/Main";
import SalePage from "./components/sale/SalePage";
import StockPage from "./components/stock/StockPage";
import MyInfo from "./components/myPage/MyInfo";
import MyStock from "./components/myPage/MyStock";
import MyAnnounce from "./components/myPage/MyAnnounce";
import MyPost from "./components/myPage/MyPost";
import UpdatePassword from "./components/myPage/UpdatePassword";
import WithDraw from "./components/myPage/WithDraw";
import UpdateInfo from "./components/myPage/UpdateInfo";
import MemberLogin from "./components/member/MemberLogin";
import MemberSignup from "./components/member/MemberSignup";

import { MemberProvider } from "./components/member/MemberContext";

// 관리자
import HousingForm from "./components/admin/HousingForm";
import DashBoard from "./components/admin/DashBoard";
import Chart from "./components/admin/Chart";
import Advertisement from "./components/admin/Advertisement";
import Inquiry from "./components/admin/Inquiry";
import Management from "./components/admin/Management";

// 📢 공지사항 → Announce로 변경
import Announce from "./components/announce/Announce";
import AnnounceDetail from "./components/announce/AnnounceDetail";
import AnnounceWrite from "./components/announce/AnnounceWrite";

// 🏘 우리동네 게시판
import NeighborhoodBoard from "./components/neighborhood/Neighborhood";
import NeighborhoodWrite from "./components/neighborhood/NeighborhoodWrite.jsx";

import { AuthProvider } from "./components/admin/AuthContext";

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <MemberProvider>
          <Routes>
            {/* 공통 사용자 레이아웃 */}
            <Route path="/" element={<Layout />}>
              <Route index element={<Main />} />
              <Route path="sale" element={<SalePage />} />
              <Route path="stock" element={<StockPage />} />
              <Route path="login" element={<MemberLogin />} />
              <Route path="signUp" element={<MemberSignup />} />

              {/* 마이페이지 */}
              <Route path="myPage" element={<MyInfo />} />
              <Route path="myPage/updateInfo" element={<UpdateInfo />} />
              <Route path="myPage/myStock" element={<MyStock />} />
              <Route path="myPage/myAnnounce" element={<MyAnnounce />} />
              <Route path="myPage/myPost" element={<MyPost />} />
              <Route
                path="myPage/updatePassword"
                element={<UpdatePassword />}
              />
              <Route path="myPage/withDraw" element={<WithDraw />} />

              {/* 📢 공지사항 (Announce) */}
              <Route path="announce" element={<Announce />} />
              <Route path="announce/detail/:id" element={<AnnounceDetail />} />
              <Route path="announce/write" element={<AnnounceWrite />} />
              <Route path="announce/edit/:id" element={<AnnounceWrite />} />

              {/* 🏘 우리동네 게시판 */}
              <Route path="neighborhood" element={<NeighborhoodBoard />} />
              <Route path="neighborhoodWrite" element={<NeighborhoodWrite />} />
            </Route>

            {/* 관리자 전용 페이지 */}
            <Route path="/admin/*" element={<DashBoard />}>
              <Route index element={<Chart />} />
              <Route path="dashboard" element={<Chart />} />
              <Route path="chart" element={<Chart />} />
              <Route path="housingForm" element={<HousingForm />} />
              <Route path="advertisement" element={<Advertisement />} />
              <Route path="inquiry" element={<Inquiry />} />
              <Route path="management" element={<Management />} />
            </Route>
          </Routes>
        </MemberProvider>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
