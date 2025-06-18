import "./App.css";
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
import { MemberProvider } from "./components/member/MemberContext";
import MemberSignup from "./components/member/MemberSignup";

// 관리자
import HousingForm from "./components/admin/HousingForm";
import DashBoard from "./components/admin/DashBoard";
import Chart from "./components/admin/Chart";
import Advertisement from "./components/admin/Advertisement";
import Inquiry from "./components/admin/Inquiry";
import Management from "./components/admin/Management";

// 공지사항

function App() {
  return (
    <BrowserRouter>
      <MemberProvider>
        <Routes>
          {/* 공통 레이아웃 라우트 */}
          <Route path="/" element={<Layout />}>
            <Route index element={<Main />} />
            <Route path="sale" element={<SalePage />} />
            <Route path="stock" element={<StockPage />} />
            <Route path="login" element={<MemberLogin />} />
            <Route path="signUp" element={<MemberSignup />} />
            <Route path="myPage" element={<MyInfo />} />
            <Route path="myPage/updateInfo" element={<UpdateInfo />} />
            <Route path="myPage/myStock" element={<MyStock />} />
            <Route path="myPage/myAnnounce" element={<MyAnnounce />} />
            <Route path="myPage/myPost" element={<MyPost />} />
            <Route path="myPage/updatePassword" element={<UpdatePassword />} />
            <Route path="myPage/withDraw" element={<WithDraw />} />
          </Route>

          {/* 관리자 전용 페이지 (별도 레이아웃을 원한다면 여기에 추가 가능) */}
          <Route path="/admin" element={<DashBoard />}>
            <Route index element={<Chart />} />{" "}
            {/* /admin 접속 시 기본 Chart */}
            <Route path="dashboard" element={<Chart />} />{" "}
            {/* /admin/dashboard */}
            <Route path="chart" element={<Chart />} /> {/* /admin/chart */}
            <Route path="housingForm" element={<HousingForm />} />
            <Route path="advertisement" element={<Advertisement />} />
            <Route path="inquiry" element={<Inquiry />} />
            <Route path="management" element={<Management />} />
          </Route>

          {/* 404 페이지 처리를 원한다면 추가 */}
          {/* <Route path="*" element={<NotFound />} /> */}
        </Routes>
      </MemberProvider>
    </BrowserRouter>
  );
}

export default App;
