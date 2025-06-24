// src/App.jsx
import { BrowserRouter, Routes, Route, useNavigate } from "react-router-dom";

import Layout from "./components/common/Layout";
import React, { useEffect, useContext } from "react";

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
import {
  MemberProvider,
  MemberContext,
} from "./components/member/MemberContext";
import LoginHandler from "./components/member/MemberLogin";

import AddSale from "./components/admin/saleForm/AddSale";
import ListSale from "./components/admin/saleForm/ListSale";
import DashBoard from "./components/admin/DashBoard";
import Chart from "./components/admin/Chart";
import Advertisement from "./components/admin/Advertisement";
import Inquiry from "./components/admin/Inquiry";
import Management from "./components/admin/Management/Management";
import { AuthProvider } from "./components/admin/AuthContext";

import Announce from "./components/announce/Announce";
import AnnounceDetail from "./components/announce/AnnounceDetail";
import AnnounceWrite from "./components/announce/AnnounceWrite";

import NeighborhoodBoard from "./components/neighborhood/NeighborhoodBorad";
import NeighborhoodDetail from "./components/neighborhood/NeighborhoodDetail";

import Gonggong from "./components/common/gonggong";
import NaverCallback from "./components/auth/NaverCallback";

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
  const initNaver = () => {
    if (window.naver && !window.naver._loginInitialized) {
      const login = new window.naver.LoginWithNaverId({
        clientId: import.meta.env.VITE_NAVER_CLIENT_ID,
        callbackUrl: import.meta.env.VITE_NAVER_CALLBACK_URI,
        isPopup: true,
        loginButton: { type: 3, height: "48" },
        authType: "reauthenticate",
      });
      login.init();
      window.naverLoginInstance = login;
      window.naver._loginInitialized = true;
    }
  };
  useEffect(() => {
    if (window.Kakao && !window.Kakao.isInitialized()) {
      window.Kakao.init(import.meta.env.VITE_KAKAO_JS_KEY);
      console.log("Kakao SDK 초기화", window.Kakao.isInitialized());
    }
    initNaver();
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
              <Route path="gonggong" element={<Gonggong />} />

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

              {/* 분양페이지 */}
              <Route path="/sale/:saleStockNo" element={<SalePage />} />

              {/* 공지사항 (Announce) */}
              <Route path="announce" element={<Announce />} />
              <Route path="announce/detail/:id" element={<AnnounceDetail />} />
              <Route path="announce/write" element={<AnnounceWrite />} />
              <Route path="announce/edit/:id" element={<AnnounceWrite />} />

              {/* 우리동네 게시판 */}
              <Route path="neighborhoodBoard" element={<NeighborhoodBoard />} />
              <Route
                path="neighborhood/detail/:id"
                element={<NeighborhoodDetail />}
              />
            </Route>

            {/* 관리자 페이지 - DashBoard 레이아웃 하위 중첩 라우팅 */}
            <Route path="/admin/*" element={<DashBoard />}>
              <Route index element={<Chart />} />
              <Route path="dashboard" element={<Chart />} />
              <Route path="chart" element={<Chart />} />
              <Route path="advertisement" element={<Advertisement />} />
              <Route path="inquiry" element={<Inquiry />} />
              <Route path="management" element={<Management />} />
              <Route path="list_sale" element={<ListSale />} />
              <Route path="add_sale" element={<AddSale />} />
            </Route>

            <Route path="/oauth2/kakao/redirect" element={<LoginHandler />} />
            <Route path="/oauth2/naver/redirect" element={<NaverCallback />} />
          </Routes>
        </MemberProvider>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
