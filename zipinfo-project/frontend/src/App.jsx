// src/App.jsx
import { BrowserRouter, Routes, Route, useNavigate } from "react-router-dom";

import Layout from "./components/common/Layout";
import React, { useEffect, useContext } from "react";
import ProtectedRoute from "./ProtectedRoute";

import Main from "./components/Main";
import SalePage from "./components/sale/SalePage";
import StockPage from "./components/stock/StockPage"; // ContextProvider 생성하는 방향으로 리팩토링 중!
import { StockProvider } from "./components/stock/StockContext";
import MyInfo from "./components/myPage/MyInfo";
import MyStock from "./components/myPage/MyStock";
import UpdateMyStock from "./components/myPage/UpdateMyStock";
import AddStock from "./components/myPage/AddStock";
import SawStock from "./components/myPage/SawStock";
import LikeStock from "./components/myPage/LikeStock";
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

import NeighborhoodBoard from "./components/neighborhood/NeighborhoodBoard";
import NeighborhoodDetail from "./components/neighborhood/NeighborhoodBoardDetail";

import Gonggong from "./components/common/Gonggong";
import NaverCallback from "./components/auth/NaverCallback";
import NeighborhoodEdit from "./components/neighborhood/NeighborhoodEdit";

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
    // initNaver();
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
              <Route
                path="stock"
                element={
                  <StockProvider>
                    <StockPage />
                  </StockProvider>
                }
              />

              <Route path="login" element={<MemberLogin />} />
              <Route path="signUp" element={<MemberSignup />} />
              <Route path="gonggong" element={<Gonggong />} />

              {/* 마이페이지 */}

              <Route
                path="myPage"
                element={
                  <ProtectedRoute>
                    <MyInfo />
                  </ProtectedRoute>
                }
              />
              <Route path="myPage/updateInfo" element={<UpdateInfo />} />
              <Route path="myPage/myStock" element={<MyStock />} />
              <Route path="myPage/updateMyStock" element={<UpdateMyStock />} />
              <Route path="myPage/addStock" element={<AddStock />} />
              <Route path="myPage/sawStock" element={<SawStock />} />
              <Route path="myPage/likeStock" element={<LikeStock />} />
              <Route path="myPage/myAnnounce" element={<MyAnnounce />} />
              <Route path="myPage/myPost" element={<MyPost />} />
              <Route
                path="myPage/updatePassword"
                element={<UpdatePassword />}
              />
              <Route path="myPage/withDraw" element={<WithDraw />} />
              {/*매물페이지*/}
              <Route path="/stock/:stockNo" element={<StockPage />} />
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
                path="neighborhoodBoard/detail/:boardNo"
                element={<NeighborhoodDetail />}
              />
              <Route
                path="neighborhoodBoard/edit/:boardNo"
                element={<NeighborhoodEdit />}
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
