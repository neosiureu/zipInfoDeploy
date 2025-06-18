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
            <Route path="myPage" element={<MyInfo />} />
            <Route path="myPage/updateInfo" element={<UpdateInfo />} />
            <Route path="myPage/myStock" element={<MyStock />} />
            <Route path="myPage/myAnnounce" element={<MyAnnounce />} />
            <Route path="myPage/myPost" element={<MyPost />} />
            <Route path="myPage/updatePassword" element={<UpdatePassword />} />
            <Route path="myPage/withDraw" element={<WithDraw />} />
          </Route>
        </Routes>
      </MemberProvider>
    </BrowserRouter>
  );
}

export default App;
