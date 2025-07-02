// src/App.jsx
import { BrowserRouter, Routes, Route, useNavigate } from "react-router-dom";

import Layout from "./components/common/Layout";
import React, { useEffect, useContext, useRef } from "react";
import ProtectedRoute from "./ProtectedRoute";
import AdminRoute from "./AdminRoute";

import Main from "./components/Main";
import SalePage from "./components/sale/SalePage";

import "./App.css"

//import StockPageCopy from "./components/stock/StockPageCopy"; // ContextProvider 생성하는 방향으로 리팩토링 중!
//import { StockProvider } from "./components/stock/StockContext";
import StockProviderWrapper from "./components/stock/StockProviderWrapper";

import MyInfo from "./components/myPage/MyInfo";
import MyStock from "./components/myPage/MyStock";
import UpdateMyStock from "./components/myPage/UpdateMyStock";
import AddStock from "./components/myPage/AddStock";
import SawStock from "./components/myPage/SawStock";
import LikeStock from "./components/myPage/LikeStock";
import MyMessage from "./components/myPage/MyMessage";
import SeeMyMessage from "./components/myPage/SeeMyMessage";
import DetailMessage from "./components/myPage/DetailMessage";
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
import UpdateSale from "./components/admin/saleForm/UpdateSale";
import DashBoard from "./components/admin/DashBoard";
import Chart from "./components/admin/Chart";
import Advertisement from "./components/admin/Advertisement";
import HelpMessage from "./components/admin/HelpMessage/HelpMessage";
import Reply from "./components/admin/HelpMessage/Reply"; 
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

import SockJS from "sockjs-client";
import { Stomp } from "@stomp/stompjs";
import { ToastContainer, toast } from "react-toastify";

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

function GlobalWebSocketListener() {
  const stompClientRef = useRef(null);

  const { member } = useContext(MemberContext);

  const navigate = useNavigate();

  useEffect(() => {
    console.log(member);
    const socket = new SockJS("http://localhost:8080/ws");
    const client = Stomp.over(socket);
    client.connect({}, () => {
      client.subscribe("/topic/notice", (message) => {
        toast.info(  <div className="toast-announce-container">
        <div className="toast-announce-title">공지 알림!</div>
          <div className="toast-announce-con">
            <div className="toast-announce-body">{message.body}</div>
            <button className="toast-announce-button" onClick={() => navigate("/announce")}>확인하기</button>
          </div>
        </div>,{
          position: "bottom-right",
          autoClose: 10000,
          className: "custom-toast",
          icon:false,
        });
      });
      client.subscribe(`/topic/region/${member.memberLocation}`, (message)=> {
        toast.info(
          <div>
            <strong>관심 지역에 새 글이 등록되었습니다</strong>
            <div>{message.body}</div>
          </div>,{
          position: "bottom-right",
          autoClose: 10000,
          className: "custom-toast",
          icon:false,
        }
        );
      });
    });
    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current?.connected) {
        stompClientRef.current.disconnect();
      }
    };
  }, [member]);

  return null; // 렌더링하지 않음
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
          <GlobalWebSocketListener />
          <Routes>
            {/* 공통 사용자 레이아웃 */}
            <Route path="/" element={<Layout />}>
              <Route index element={<Main />} />
              <Route path="sale" element={<SalePage />} />
              <Route path="stock" element={<StockProviderWrapper />} />

              <Route path="login" element={<MemberLogin />} />
              <Route path="signUp" element={<MemberSignup />} />
              <Route path="gonggong" element={<Gonggong />} />

              {/* 마이페이지 */}
              <Route path="myPage" element={<ProtectedRoute><MyInfo /></ProtectedRoute>} />
              <Route path="myPage/updateInfo" element={<ProtectedRoute><UpdateInfo /></ProtectedRoute>} />
              <Route path="myPage/myStock" element={<ProtectedRoute><MyStock /></ProtectedRoute>} />
              <Route path="myPage/updateMyStock" element={<ProtectedRoute><UpdateMyStock /></ProtectedRoute>} />
              <Route path="myPage/addStock" element={<ProtectedRoute><AddStock /></ProtectedRoute>} />
              <Route path="myPage/sawStock" element={<ProtectedRoute><SawStock /></ProtectedRoute>} />
              <Route path="myPage/likeStock" element={<ProtectedRoute><LikeStock /></ProtectedRoute>} />
              <Route path="myPage/myMessage" element={<ProtectedRoute><MyMessage /></ProtectedRoute>} />
              <Route path="myPage/seeMyMessage" element={<ProtectedRoute><SeeMyMessage /></ProtectedRoute>} />
              <Route
                path="myPage/detailMessage/:messageNo"
                element={<ProtectedRoute><DetailMessage /></ProtectedRoute>}
              />
              <Route path="myPage/myPost" element={<ProtectedRoute><MyPost /></ProtectedRoute>} />
              <Route
                path="myPage/updatePassword"
                element={<ProtectedRoute><UpdatePassword /></ProtectedRoute>}
              />
              <Route path="myPage/withDraw" element={<ProtectedRoute><WithDraw /></ProtectedRoute>} />

              {/*매물페이지*/}
              <Route path="stock/:stockNo" element={<StockProviderWrapper />} />

              {/* 분양페이지 */}
              <Route path="/sale/:saleStockNo" element={<SalePage />} />

              {/* 공지사항 (Announce) */}
              <Route path="announce" element={<Announce />} />
              <Route path="announce/detail/:id" element={<AnnounceDetail />} />
              <Route path="announce/write" element={<ProtectedRoute><AdminRoute><AnnounceWrite /></AdminRoute></ProtectedRoute>} />
              <Route path="announce/edit/:id" element={<ProtectedRoute><AdminRoute><AnnounceWrite /></AdminRoute></ProtectedRoute>} />

              {/* 우리동네 게시판 */}
              <Route path="neighborhoodBoard" element={<NeighborhoodBoard />} />
              <Route
                path="neighborhoodBoard/detail/:boardNo"
                element={<NeighborhoodDetail />}
              />
              <Route
                path="neighborhoodBoard/edit/:boardNo?"
                element={<NeighborhoodEdit />}
              />

              {/* 선택 파라미터 문법으로 ?가 있을 때는 있을수도 없을수도 있다.
              baordNo가 들어가 있으면 수정화면으로 전환
              boardNo가 안 들어가면 글쓰기 화면으로 전환할 예정이다. 하나의 path로 두개의 처리를 하여 jsx파일의 개수 자체를 줄일 수 있을 듯 하다*/}
            </Route>

            {/* 관리자 페이지 - DashBoard 레이아웃 하위 중첩 라우팅 */}
            <Route path="admin/*" element={<ProtectedRoute><AdminRoute><DashBoard /></AdminRoute></ProtectedRoute>}>
              <Route index element={<ProtectedRoute><AdminRoute><Chart /></AdminRoute></ProtectedRoute>} />
              <Route path="dashboard" element={<ProtectedRoute><AdminRoute><Chart /></AdminRoute></ProtectedRoute>} />
              <Route path="chart" element={<ProtectedRoute><AdminRoute><Chart /></AdminRoute></ProtectedRoute>} />
              <Route path="advertisement" element={<ProtectedRoute><AdminRoute><Advertisement /></AdminRoute></ProtectedRoute>} />
              <Route path="helpMessage" element={<ProtectedRoute><AdminRoute><HelpMessage /></AdminRoute></ProtectedRoute>} />
              <Route path="help/reply/:messageNo" element={<ProtectedRoute><AdminRoute><Reply /></AdminRoute></ProtectedRoute>} />{" "}
              <Route path="management" element={<ProtectedRoute><AdminRoute><Management /></AdminRoute></ProtectedRoute>} />
              <Route path="list_sale" element={<ProtectedRoute><AdminRoute><ListSale /></AdminRoute></ProtectedRoute>} />
              <Route path="add_sale" element={<ProtectedRoute><AdminRoute><AddSale /></AdminRoute></ProtectedRoute>} />
              <Route path="edit_sale/:id" element={<ProtectedRoute><AdminRoute><UpdateSale /></AdminRoute></ProtectedRoute>} />
            </Route>

            <Route path="/oauth2/kakao/redirect" element={<LoginHandler />} />
            <Route path="/oauth2/naver/redirect" element={<NaverCallback />} />
          </Routes>
          <ToastContainer  position="top-center" icon={false} autoClose={3000}  />
        </MemberProvider>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
