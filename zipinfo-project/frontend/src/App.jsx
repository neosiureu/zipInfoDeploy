// src/App.jsx
import { BrowserRouter, Routes, Route, useNavigate } from "react-router-dom";

// 공통 레이아웃
import Layout from "./components/common/Layout";
import React, { useEffect, useContext } from "react";

// 페이지
import Main from "./components/Main";
import SalePage from "./components/sale/SalePage";
import StockPage from "./components/stock/StockPage";

// 마이페이지
import MyInfo from "./components/myPage/MyInfo";
import MyStock from "./components/myPage/MyStock";
import MyAnnounce from "./components/myPage/MyAnnounce";
import MyPost from "./components/myPage/MyPost";
import UpdatePassword from "./components/myPage/UpdatePassword";
import WithDraw from "./components/myPage/WithDraw";
import UpdateInfo from "./components/myPage/UpdateInfo";

// 회원
import MemberLogin from "./components/member/MemberLogin";
import MemberSignup from "./components/member/MemberSignup";
import {
  MemberProvider,
  MemberContext,
} from "./components/member/MemberContext";
import LoginHandler from "./components/member/MemberLogin";

// 관리자
import AddSale from "./components/admin/saleForm/AddSale";
import DashBoard from "./components/admin/DashBoard";
import Chart from "./components/admin/Chart";
import Advertisement from "./components/admin/Advertisement";
import Inquiry from "./components/admin/Inquiry";
import Management from "./components/admin/Management";
import { AuthProvider } from "./components/admin/AuthContext";

import Announce from "./components/announce/Announce";
import AnnounceDetail from "./components/announce/AnnounceDetail";
import AnnounceWrite from "./components/announce/AnnounceWrite";

// 우리동네 게시판
import Neighborhood from "./components/neighborhood/Neighborhood";
import NeighborhoodDetail from "./components/neighborhood/NeighborhoodDetail";
function MessageListener() {
  const { setMember } = useContext(MemberContext);
  const navigate = useNavigate();

  useEffect(() => {
    const onMessage = (e) => {
      if (e.origin !== window.location.origin) return;
      if (e.data?.type === "KAKAO_LOGIN_SUCCESS") {
        const member = e.data.member;
        localStorage.setItem("loginMember", JSON.stringify(member));
        setMember(member);
        navigate("/");
      }
    };
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [setMember, navigate]);

  return null;
}

function App() {
  useEffect(() => {
    if (window.Kakao && !window.Kakao.isInitialized()) {
      window.Kakao.init(import.meta.env.VITE_KAKAO_JS_KEY);
      console.log("Kakao SDK 초기화", window.Kakao.isInitialized());
    }
  }, []);
  return (
    <AuthProvider>
      <BrowserRouter>
        <MemberProvider>
          <MessageListener />

          <Routes>
            {/* 공통 사용자 레이아웃 */}
            <Route path="/" element={<Layout />}>
              <Route index element={<Main />} />
              <Route path="sale" element={<SalePage />} />
              <Route path="stock" element={<StockPage />} />
              <Route path="login" element={<MemberLogin />} />
              <Route path="signUp" element={<MemberSignup />} />
              <Route path="/oauth2/kakao/redirect" element={<LoginHandler />} />

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

              {/*  우리동네 게시판 */}
              <Route path="neighborhood" element={<Neighborhood />} />
              <Route
                path="neighborhood/detail/:id"
                element={<NeighborhoodDetail />}
              />
            </Route>

            {/* 관리자 전용 페이지 */}
            <Route path="/admin/*" element={<DashBoard />}>
              <Route index element={<Chart />} />
              <Route path="dashboard" element={<Chart />} />
              <Route path="chart" element={<Chart />} />
              <Route path="housingForm" element={<AddSale />} />{" "}
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
